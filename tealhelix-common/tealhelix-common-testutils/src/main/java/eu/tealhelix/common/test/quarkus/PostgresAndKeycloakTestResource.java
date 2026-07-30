package eu.tealhelix.common.test.quarkus;

import static eu.tealhelix.common.test.testcontainers.DockerImageNames.KEYCLOAK_IMAGE_TEALHELIX;
import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class PostgresAndKeycloakTestResource implements QuarkusTestResourceLifecycleManager {
	private static final String PG_PREFIX = "pg";
	public static final Integer KEYCLOAK_PORT = 8080;

	private final PostgresTestResource postgresTestResource;
	private Network network;
	private GenericContainer<?> keycloak;

	public PostgresAndKeycloakTestResource() {
		postgresTestResource = new PostgresTestResource();
	}

	@Override
	public void init(Map<String, String> initArgs) {
		var postgresInitArgs = initArgs.entrySet().stream().filter(e -> e.getKey().startsWith(PG_PREFIX))
				.collect(toMap(e -> removePgPrefixAndDecapitalize(e.getKey()), Map.Entry::getValue));
		postgresTestResource.init(postgresInitArgs);
	}

	@Override
	public Map<String, String> start() {
		var network = Network.newNetwork();
		postgresTestResource.withNetwork(network).withNetworkAliases("postgresql");
		var keycloak = new GenericContainer<>(KEYCLOAK_IMAGE_TEALHELIX);
		var postgresStartMap = postgresTestResource.start();
		keycloak.withNetwork(network).withExposedPorts(KEYCLOAK_PORT).start();
		this.network = network;
		this.keycloak = keycloak;
		var result = new HashMap<>(postgresStartMap);
		var host = keycloak.getHost();
		var port = keycloak.getMappedPort(KEYCLOAK_PORT);
		var realmUrl = "http://" + host + ":" + port + "/realms/tealhelix";
		result.put("config.jwk.url", realmUrl + "/protocol/openid-connect/certs");
		result.put("config.jwt.expectedIssuer", realmUrl);
		return result;
	}

	@Override
	public void stop() {
		if (keycloak != null) keycloak.stop();
		postgresTestResource.stop();
		if (network != null) network.close();
	}

	@Override
	public void inject(TestInjector testInjector) {
		postgresTestResource.inject(testInjector);
		testInjector.injectIntoFields(keycloak, new TestInjector.AnnotatedAndMatchesType(InjectKeycloak.class, GenericContainer.class));
	}

	private String removePgPrefixAndDecapitalize(String input) {
		if (input.length() < PG_PREFIX.length()) {
			throw new IllegalArgumentException("Invalid init argument key: " + input);
		}
		return Character.toLowerCase(input.charAt(PG_PREFIX.length())) + input.substring(PG_PREFIX.length() + 1);
	}
}
