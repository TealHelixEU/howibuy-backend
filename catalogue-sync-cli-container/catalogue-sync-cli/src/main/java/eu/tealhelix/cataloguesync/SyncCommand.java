package eu.tealhelix.cataloguesync;

import jakarta.inject.Inject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "sync", mixinStandardHelpOptions = true,
		description = "Brings HowiBuy up to date with the Ingestion, and prints the pages and products applied and HowiBuy's cursor. Any error stops it with a non-zero exit code; running it again resumes from HowiBuy's cursor.")
public class SyncCommand implements Runnable {
	@Inject
	CatalogueSync catalogueSync;

	@Spec
	CommandSpec spec;

	@Override
	public void run() {
		var result = catalogueSync.sync();
		spec.commandLine().getOut().printf("HowiBuy is up to date with the Ingestion, pages: %d, products: %d, cursor: %d%n", result.pages(), result.products(), result.cursor());
	}
}
