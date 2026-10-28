package eu.tealhelix.catalogue.services.v1.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import eu.tealhelix.catalogue.dao.IngestionRunDao;
import eu.tealhelix.catalogue.v1.model.ImmutableFeed;
import eu.tealhelix.catalogue.v1.model.ImmutableIngestionRun;
import eu.tealhelix.catalogue.v1.model.IngestionRun;
import eu.tealhelix.catalogue.v1.types.CatalogueId;
import eu.tealhelix.catalogue.v1.types.FeedType;
import eu.tealhelix.catalogue.v1.types.IngestionRunStatus;
import eu.tealhelix.catalogue.v1.types.impl.CatalogueIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.FeedIdImpl;
import eu.tealhelix.catalogue.v1.types.impl.IngestionRunIdImpl;
import eu.tealhelix.common.test.jpa.MockReactivePersistenceContextFactory;
import eu.tealhelix.common.v1.types.impl.GenericRetailerId;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The runs of a Catalogue are processed one at a time, in the order the database gives, on the executor; a wake-up that
 * arrives while they are processed is never lost. The executor here runs its tasks only when the test says so.
 */
@ExtendWith(MockitoExtension.class)
public class IngestionRunDispatcherTest {
	private static final CatalogueId CATALOGUE = new CatalogueIdImpl("rimi-lt");
	private static final CatalogueId OTHER_CATALOGUE = new CatalogueIdImpl("rimi-lv");

	@Mock
	IngestionRunDao ingestionRunDao;

	@Mock
	IngestionRunProcessor processor;

	@Mock
	ManagedExecutor executor;

	@RegisterExtension
	private MockReactivePersistenceContextFactory persistenceContextFactory = new MockReactivePersistenceContextFactory();

	private final UniAwaiter uniAwaiter = new UniAwaiter() {
		@Override
		public <T> T await(Supplier<Uni<T>> call) {
			return call.get().await().atMost(Duration.ofSeconds(300));
		}
	};

	private final Queue<Runnable> tasks = new ArrayDeque<>();

	private final List<LogRecord> logged = new ArrayList<>();

	private final Handler logHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logged.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	private IngestionRunDispatcher sut;

	@BeforeEach
	void setUp() {
		sut = new IngestionRunDispatcher(ingestionRunDao, processor, uniAwaiter, persistenceContextFactory, executor);
		lenient().doAnswer(i -> tasks.add(i.getArgument(0))).when(executor).execute(any());
		var logger = LogContext.getLogContext().getLogger(IngestionRunDispatcher.class.getName());
		logger.addHandler(logHandler);
		logger.setUseParentHandlers(false);
	}

	@AfterEach
	void tearDown() {
		var logger = LogContext.getLogContext().getLogger(IngestionRunDispatcher.class.getName());
		logger.removeHandler(logHandler);
		logger.setUseParentHandlers(true);
	}

	@Test
	void processesTheWaitingRunsOfACatalogueOneAfterTheOther() {
		var first = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
		var second = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");
		givenTheNextRunsToProcess(CATALOGUE, first, second, null);

		sut.wake(CATALOGUE);
		runTasks();

		var order = inOrder(processor);
		order.verify(processor).process(first);
		order.verify(processor).process(second);
		order.verifyNoMoreInteractions();
	}

	@Test
	void processesOnTheExecutor() {
		sut.wake(CATALOGUE);

		verify(processor, times(0)).process(any());
		assertEquals(1, tasks.size());
	}

	@Test
	void wakeUpsBeforeProcessingStartsShareOneTask() {
		givenTheNextRunsToProcess(CATALOGUE, (IngestionRun) null);

		sut.wake(CATALOGUE);
		sut.wake(CATALOGUE);

		assertEquals(1, tasks.size());
		runTasks();
		verify(ingestionRunDao, times(1)).findNextToProcess(any(), eq(CATALOGUE));
	}

	@Test
	void aWakeUpDuringProcessingLooksForRunsAgain() {
		var first = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
		var arrivedMeanwhile = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");
		givenTheNextRunsToProcess(CATALOGUE, first, null, arrivedMeanwhile, null);
		doAnswer(i -> {
			sut.wake(CATALOGUE);
			return null;
		}).when(processor).process(first);

		sut.wake(CATALOGUE);
		runTasks();

		verify(processor).process(arrivedMeanwhile);
	}

	@Test
	void aWakeUpAfterProcessingStartsANewTask() {
		givenTheNextRunsToProcess(CATALOGUE, (IngestionRun) null);
		sut.wake(CATALOGUE);
		runTasks();

		sut.wake(CATALOGUE);

		assertEquals(1, tasks.size());
	}

	@Test
	void theCataloguesAreProcessedApart() {
		sut.wake(CATALOGUE);
		sut.wake(OTHER_CATALOGUE);

		assertEquals(2, tasks.size());
	}

	@Test
	void aFailureStopsTheProcessingOfTheCatalogueUntilTheNextWakeUp() {
		var first = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
		givenTheNextRunsToProcess(CATALOGUE, first, null);
		var failure = new IllegalStateException("The database is gone");
		doThrow(failure).doNothing().when(processor).process(first);

		sut.wake(CATALOGUE);
		runTasks();

		assertEquals(1, logged.size());
		assertEquals(Level.SEVERE.intValue(), logged.getFirst().getLevel().intValue());
		assertEquals("Processing the Ingestion Runs of a Catalogue failed, catalogueId: rimi-lt", logged.getFirst().getMessage());
		assertEquals(failure, logged.getFirst().getThrown());

		givenTheNextRunsToProcess(CATALOGUE, first, null);
		sut.wake(CATALOGUE);
		runTasks();

		verify(processor, times(2)).process(first);
	}

	@Test
	void onRecoveryTheRunsLeftProcessingAreProcessedAgainWithTheWaitingOnes() {
		when(ingestionRunDao.resetProcessing(any())).thenReturn(Uni.createFrom().item(1));
		when(ingestionRunDao.findCataloguesWithReceivedRuns(any())).thenReturn(Uni.createFrom().item(List.of(CATALOGUE, OTHER_CATALOGUE)));
		var run = run(CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e01");
		var otherRun = run(OTHER_CATALOGUE, "5d1c9a3e-0f4b-4a52-9e6d-2b7c8a1f3e02");
		givenTheNextRunsToProcess(CATALOGUE, run, null);
		givenTheNextRunsToProcess(OTHER_CATALOGUE, otherRun, null);

		sut.recover();
		runTasks();

		var order = inOrder(ingestionRunDao, processor);
		order.verify(ingestionRunDao).resetProcessing(any());
		order.verify(ingestionRunDao).findCataloguesWithReceivedRuns(any());
		verify(processor).process(run);
		verify(processor).process(otherRun);
	}

	private void givenTheNextRunsToProcess(CatalogueId catalogueId, IngestionRun... runs) {
		var stubbing = when(ingestionRunDao.findNextToProcess(any(), eq(catalogueId)));
		for (var run : runs) {
			stubbing = stubbing.thenReturn(Uni.createFrom().item(run));
		}
	}

	private void runTasks() {
		Runnable task;
		while ((task = tasks.poll()) != null) {
			task.run();
		}
	}

	private static IngestionRun run(CatalogueId catalogueId, String id) {
		return ImmutableIngestionRun.builder()
				.id(new IngestionRunIdImpl(id))
				.feed(ImmutableFeed.builder()
						.id(new FeedIdImpl("3a0c5e7d-9b1f-4d2e-8c6a-5f4b3e2d1c0a"))
						.retailerId(new GenericRetailerId("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11"))
						.feedType(FeedType.SNAPSHOT)
						.receivedAt(LocalDateTime.of(2026, 10, 21, 9, 0, 0))
						.filePath("6f1f0b52-8a0e-4c1b-9d53-1c7a3e0b2f11/3a0c5e7d-9b1f-4d2e-8c6a-5f4b3e2d1c0a.gz")
						.fileSize(10L)
						.fileSha256("aa")
						.build())
				.catalogueId(catalogueId)
				.status(IngestionRunStatus.RECEIVED)
				.build();
	}
}
