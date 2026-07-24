package eu.tealhelix.common.web;

import jakarta.ws.rs.container.ContainerRequestContext;

import eu.tealhelix.common.v1.model.User;

public interface JaxRsUtils {
	static User currentUser(ContainerRequestContext crc) {
		return (User) crc.getSecurityContext().getUserPrincipal();
	}
}
