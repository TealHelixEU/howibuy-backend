package eu.tealhelix.cataloguesync.howibuy;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * HowiBuy answers 409 to a page that does not start at its cursor; every other error is left to the REST client.
 */
public class HowiBuyClientTest {
	@Test
	void mapsAConflictToACursorConflict() {
		var exception = HowiBuyClient.toException(Response.status(409).build());

		assertThat(exception).isInstanceOf(CursorConflictException.class);
	}

	@Test
	void leavesAnyOtherErrorToTheRestClient() {
		assertThat(HowiBuyClient.toException(Response.status(400).build())).isNull();
		assertThat(HowiBuyClient.toException(Response.status(500).build())).isNull();
	}
}
