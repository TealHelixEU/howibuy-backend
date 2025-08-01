package eu.tealhelix.common.test.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface DockerImageNames {
	DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres");
	DockerImageName POSTGRES_IMAGE_TEALHELIX = DockerImageName.parse("tealhelix-postgres:latest").asCompatibleSubstituteFor("postgres");
	DockerImageName KEYCLOAK_IMAGE_TEALHELIX = DockerImageName.parse("tealhelix-keycloak:latest");
}
