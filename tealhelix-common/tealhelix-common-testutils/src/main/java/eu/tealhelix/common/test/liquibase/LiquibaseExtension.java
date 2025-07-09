package eu.tealhelix.common.test.liquibase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

import liquibase.Liquibase;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JUnit 5 extension that runs Liquibase before all tests.
 */
public class LiquibaseExtension implements BeforeAllCallback {

	private static final String DEFAULT_CHANGELOG_FILE = "db.changelog.xml";

	private static final Logger LOG = LoggerFactory.getLogger(LiquibaseExtension.class);

	private final Supplier<String> dburlSupplier;
	private final String dbuser;
	private final String dbpass;
	private final String changeLogFile;
	private final String contexts;

	public LiquibaseExtension(Supplier<String> dburlSupplier, String dbuser, String dbpass) {
		this(dburlSupplier, dbuser, dbpass, DEFAULT_CHANGELOG_FILE);
	}

	public LiquibaseExtension(Supplier<String> dburlSupplier, String dbuser, String dbpass, String changeLogFile) {
		this(dburlSupplier, dbuser, dbpass, changeLogFile, null);
	}

	public LiquibaseExtension(Supplier<String> dburlSupplier, String dbuser, String dbpass, String changeLogFile, String contexts) {
		this.dburlSupplier = dburlSupplier;
		this.dbuser = dbuser;
		this.dbpass = dbpass;
		this.changeLogFile = changeLogFile;
		this.contexts = contexts;
	}

	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {
		if (LOG.isDebugEnabled()) {
			LOG.debug(
					"""
							Running...
							**************************************************************
							* Liquibase Junit 5 Extension
							* DB URL: {}
							* Username: {}
							* Password: {}
							* Changelog File: {}
							**************************************************************""",
					dburlSupplier.get(), dbuser, (dbpass != null ? "(set to secret value)" : "null"), changeLogFile
			);
		}

		executeUpdate(dburlSupplier.get(), dbuser, dbpass, changeLogFile, contexts);
	}

	public static void executeUpdate(String dbUrl, String user, String password, String changeLogFile, String contexts) throws SQLException, LiquibaseException {
		try (Connection conn = DriverManager.getConnection(dbUrl, user, password)) {
			Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
			Liquibase liquibase = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(), database);
			CommandScope updateCommand = new CommandScope(UpdateCommandStep.COMMAND_NAME);
			updateCommand.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database);
			updateCommand.addArgumentValue(UpdateCommandStep.CHANGELOG_ARG, liquibase.getDatabaseChangeLog());
			if (contexts != null) {
				updateCommand.addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, contexts);
			}
			updateCommand.execute();
		}
	}
}
