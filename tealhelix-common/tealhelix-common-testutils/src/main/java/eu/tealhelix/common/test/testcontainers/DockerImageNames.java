package eu.tealhelix.common.test.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface DockerImageNames {
	DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres");
}
