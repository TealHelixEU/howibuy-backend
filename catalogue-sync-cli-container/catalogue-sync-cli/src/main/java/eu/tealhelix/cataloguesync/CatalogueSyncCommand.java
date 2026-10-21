package eu.tealhelix.cataloguesync;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "catalogue-sync", mixinStandardHelpOptions = true, subcommands = SyncCommand.class,
		description = "Carries the Product Changes of the Ingestion into HowiBuy.")
public class CatalogueSyncCommand {
}
