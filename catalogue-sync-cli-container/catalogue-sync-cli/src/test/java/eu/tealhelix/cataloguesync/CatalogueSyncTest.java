package eu.tealhelix.cataloguesync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import jakarta.ws.rs.WebApplicationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import eu.tealhelix.cataloguesync.howibuy.CatalogueChangeCursorResponse;
import eu.tealhelix.cataloguesync.howibuy.CatalogueChangesRequest;
import eu.tealhelix.cataloguesync.howibuy.CursorConflictException;
import eu.tealhelix.cataloguesync.howibuy.HowiBuyClient;
import eu.tealhelix.cataloguesync.ingestion.IngestionClient;
import eu.tealhelix.cataloguesync.ingestion.ProductChangePageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The Catalogue Sync carries the pages of Product Changes from the Ingestion to HowiBuy, starting from HowiBuy's cursor,
 * until the Ingestion has no more; the products pass through as they were read.
 */
@ExtendWith(MockitoExtension.class)
public class CatalogueSyncTest {
	private static final int PAGE_SIZE = 3;

	@Mock
	IngestionClient ingestion;

	@Mock
	HowiBuyClient howiBuy;

	private CatalogueSync sut;

	@BeforeEach
	void setUp() {
		sut = new CatalogueSync(ingestion, howiBuy, PAGE_SIZE);
	}

	@Test
	void appliesEachPageFromTheCursorUntilAPageComesBackEmpty() {
		when(howiBuy.readCursor()).thenReturn(new CatalogueChangeCursorResponse(10));
		when(ingestion.findChanges(10, PAGE_SIZE)).thenReturn(page(13, 11, 12, 13));
		when(ingestion.findChanges(13, PAGE_SIZE)).thenReturn(page(15, 14, 15));
		when(ingestion.findChanges(15, PAGE_SIZE)).thenReturn(page(15));

		var result = sut.sync();

		var applied = inOrder(howiBuy);
		applied.verify(howiBuy).apply(new CatalogueChangesRequest(10, 13, products(11, 12, 13)));
		applied.verify(howiBuy).apply(new CatalogueChangesRequest(13, 15, products(14, 15)));
		assertThat(result).isEqualTo(new SyncResult(2, 5, 15));
	}

	@Test
	void appliesNothingWhenTheFirstPageIsEmpty() {
		when(howiBuy.readCursor()).thenReturn(new CatalogueChangeCursorResponse(7));
		when(ingestion.findChanges(7, PAGE_SIZE)).thenReturn(page(7));

		var result = sut.sync();

		verify(howiBuy, never()).apply(any());
		assertThat(result).isEqualTo(new SyncResult(0, 0, 7));
	}

	@Test
	void readsTheCursorAgainAndGoesOnWhenAnotherSyncMovedIt() {
		when(howiBuy.readCursor()).thenReturn(new CatalogueChangeCursorResponse(10), new CatalogueChangeCursorResponse(13));
		when(ingestion.findChanges(10, PAGE_SIZE)).thenReturn(page(13, 11, 12, 13));
		doThrow(new CursorConflictException("The page does not start at the cursor")).when(howiBuy).apply(new CatalogueChangesRequest(10, 13, products(11, 12, 13)));
		when(ingestion.findChanges(13, PAGE_SIZE)).thenReturn(page(15, 14, 15));
		when(ingestion.findChanges(15, PAGE_SIZE)).thenReturn(page(15));

		var result = sut.sync();

		verify(howiBuy).apply(new CatalogueChangesRequest(13, 15, products(14, 15)));
		assertThat(result).isEqualTo(new SyncResult(1, 2, 15));
	}

	@Test
	void stopsAtAnyOtherErrorOfHowiBuy() {
		var error = new WebApplicationException(500);
		when(howiBuy.readCursor()).thenReturn(new CatalogueChangeCursorResponse(10));
		when(ingestion.findChanges(10, PAGE_SIZE)).thenReturn(page(13, 11, 12, 13));
		doThrow(error).when(howiBuy).apply(any());

		assertThatThrownBy(() -> sut.sync()).isSameAs(error);
		verify(ingestion, never()).findChanges(13, PAGE_SIZE);
	}

	@Test
	void stopsAtAnErrorOfTheIngestion() {
		var error = new WebApplicationException(503);
		when(howiBuy.readCursor()).thenReturn(new CatalogueChangeCursorResponse(10));
		when(ingestion.findChanges(anyLong(), anyInt())).thenThrow(error);

		assertThatThrownBy(() -> sut.sync()).isSameAs(error);
		verify(howiBuy, never()).apply(any());
	}

	private static ProductChangePageResponse page(long toSequence, long... changeSequences) {
		return new ProductChangePageResponse(toSequence, products(changeSequences));
	}

	private static List<JsonNode> products(long... changeSequences) {
		return Arrays.stream(changeSequences)
				.<JsonNode>mapToObj(sequence -> JsonNodeFactory.instance.objectNode().put("productKey", "p-" + sequence).put("changeSequence", sequence))
				.toList();
	}
}
