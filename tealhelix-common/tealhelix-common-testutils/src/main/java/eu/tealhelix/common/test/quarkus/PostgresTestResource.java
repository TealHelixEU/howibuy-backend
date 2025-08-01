package eu.tealhelix.common.test.quarkus;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.POSTGRES_IMAGE_TEALHELIX;

import java.util.HashMap;
import java.util.Map;

import eu.tealhelix.common.test.liquibase.LiquibaseExtension;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Start Postgres as a test resource, implement injection of the Postgres test container,
 * expose settings to the Quarkus test environment, apply DB migrations.
 * <p>
 * It accepts the following parameters:
 * <dl>
 *     <dt>{@code skipMigration}</dt>
 *     <dd>if {@code "true"}, skip the Liquibase migration</dd>
 *     <dt>{@code contexts}</dt>
 *     <dd>a comma-separated list of Liquibase contexts to apply</dd>
 *     <dt>{@code changeLogFile}</dt>
 *     <dd>the Liquibase changelog file, defaults to {@code "db.changelog.xml"}</dd>
 *     <dt>{@code migrationsUser}</dt>
 *     <dd>username for running the migrations, defaults to the one created by Testcontainers</dd>
 *     <dt>{@code migrationsPassword}</dt>
 *     <dd>password for running the migrations, defaults to the one created by Testcontainers</dd>
 *     <dt>{@code runtimeDbUser}</dt>
 *     <dd>username for connecting to the DB, defaults to the one created by Testcontainers</dd>
 *     <dt>{@code runtimeDbPassword}</dt>
 *     <dd>password for connecting to the DB, defaults to the one created by Testcontainers</dd>
 *     <dt>{@code connectionDbUser}</dt>
 *     <dd>username for connecting to the DB and running the migrations, defaults to the one created by Testcontainers</dd>
 *     <dt>{@code connectionDbPassword}</dt>
 *     <dd>password for connecting to the DB and running the migrations, defaults to the one created by Testcontainers</dd>
 * </dl>
 * <p>
 * There are 3 sets of DB usernames/passwords. To determine which one to use, this resource will try in order:
 * </p>
 * <ol>
 *     <li>To use the specific, if specified ({@code migrations*} for running the migrations, {@code runtimeDb*} for running the application</li>
 *     <li>To use the generic {@code connectionDb*}</li>
 *     <li>To use the one created by Testcontainers</li>
 * </ol>
 * <p>
 * Set the parameters using the {@code @QuarkusTestResource} annotation as:
 * {@snippet :
 * @QuarkusTestResource(value = PostgresTestResource.class,
 * initArgs = {
 *   @ResourceArg(name = "contexts", value = "test,test-data"),
 *   @ResourceArg(name = "changeLogFile", value = "test-db.changelog.xml")
 * })
 * public class MyTest {
 * // ...
 * }
 *}
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {
	private final PostgreSQLContainer<?> postgres;
	private boolean skipMigration;
	private String contexts;
	private String changeLogFile = "db.changelog.xml";
	private String migrationsUser;
	private String migrationsPassword;
	private String runtimeDbUser;
	private String runtimeDbPassword;
	private String connectionDbUser;
	private String connectionDbPassword;

	public PostgresTestResource() {
		postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE_TEALHELIX);
	}

	@Override
	public void init(Map<String, String> initArgs) {
		if ("true".equals(initArgs.get("skipMigration"))) skipMigration = true;
		contexts = initArgs.get("contexts");
		if (initArgs.containsKey("changeLogFile")) changeLogFile = initArgs.get("changeLogFile");
		migrationsUser = initArgs.get("migrationsUser");
		migrationsPassword = initArgs.get("migrationsPassword");
		runtimeDbUser = initArgs.get("runtimeDbUser");
		runtimeDbPassword = initArgs.get("runtimeDbPassword");
		connectionDbUser = initArgs.get("connectionDbUser");
		connectionDbPassword = initArgs.get("connectionDbPassword");
	}

	@Override
	public Map<String, String> start() {
		postgres.withDatabaseName("tealhelix").withUsername("postgres").start();
		applyDbMigrations();
		Map<String, String> sysprops = new HashMap<>();
		sysprops.put("quarkus.datasource.jdbc.url", postgres.getJdbcUrl());
		sysprops.put("quarkus.datasource.reactive.url", postgres.getJdbcUrl().replace("jdbc:", "vertx-reactive:"));
		sysprops.put("quarkus.datasource.username", getEffectiveRuntimeDbUser());
		sysprops.put("quarkus.datasource.password", getEffectiveRuntimeDbPassword());
		return sysprops;
	}

	@Override
	public void stop() {
		postgres.stop();
	}

	private void applyDbMigrations() {
		if (skipMigration) return;
		try {
			LiquibaseExtension.executeUpdate(postgres.getJdbcUrl(), getEffectiveMigrationsDbUser(), getEffectiveMigrationsDbPassword(), changeLogFile, contexts);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void inject(TestInjector testInjector) {
		testInjector.injectIntoFields(postgres, new TestInjector.AnnotatedAndMatchesType(InjectPostgres.class, PostgreSQLContainer.class));
	}

	PostgresTestResource withNetwork(Network network) {
		postgres.withNetwork(network);
		return this;
	}

	PostgresTestResource withNetworkAliases(String... aliases) {
		postgres.withNetworkAliases(aliases);
		return this;
	}

	private String getEffectiveMigrationsDbUser() {
		if (migrationsUser != null) return migrationsUser;
		else if (connectionDbUser != null) return connectionDbUser;
		else return postgres.getUsername();
	}

	private String getEffectiveMigrationsDbPassword() {
		if (migrationsPassword != null) return migrationsPassword;
		else if (connectionDbPassword != null) return connectionDbPassword;
		else return postgres.getPassword();
	}

	private String getEffectiveRuntimeDbUser() {
		if (runtimeDbUser != null) return runtimeDbUser;
		else if (connectionDbUser != null) return connectionDbUser;
		else return postgres.getUsername();
	}

	private String getEffectiveRuntimeDbPassword() {
		if (runtimeDbPassword != null) return runtimeDbPassword;
		else if (connectionDbPassword != null) return connectionDbPassword;
		else return postgres.getPassword();
	}
}
