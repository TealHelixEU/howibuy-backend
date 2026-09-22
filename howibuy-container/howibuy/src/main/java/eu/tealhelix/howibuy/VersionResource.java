package eu.tealhelix.howibuy;

import static eu.tealhelix.howibuy.OpenApiTagNames.MISCELLANEOUS;
import static eu.tealhelix.howibuy.OpenApiTagNames.MISCELLANEOUS_DESC;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("version")
@Tag(name = MISCELLANEOUS, description = MISCELLANEOUS_DESC)
public class VersionResource {
	@ConfigProperty(name = "howibuy.version", defaultValue = "unknown")
	String version;

	@ConfigProperty(name = "howibuy.git-hash", defaultValue = "unknown")
	String gitHash;

	@Operation(
			summary = "Version info for the running application",
			description = "For telling which build a client is talking to. Unauthenticated endpoint.")
	@APIResponse(responseCode = "200", description = "The version and the commit of the running build.")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public VersionInfo getVersion() {
		return new VersionInfo(version, gitHash);
	}

	@Schema(description = "The build this application is running: its release version and the commit it was built from.")
	public record VersionInfo(
			@Schema(description = "The release version of the build.")
			String version,
			@Schema(description = "The commit the build was made from.")
			String gitHash) {
	}
}
