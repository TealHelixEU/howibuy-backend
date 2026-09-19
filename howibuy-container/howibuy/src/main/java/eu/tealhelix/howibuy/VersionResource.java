package eu.tealhelix.howibuy;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("version")
public class VersionResource {
	@ConfigProperty(name = "howibuy.version", defaultValue = "unknown")
	String version;

	@ConfigProperty(name = "howibuy.git-hash", defaultValue = "unknown")
	String gitHash;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public VersionInfo getVersion() {
		return new VersionInfo(version, gitHash);
	}

	public record VersionInfo(String version, String gitHash) {}
}
