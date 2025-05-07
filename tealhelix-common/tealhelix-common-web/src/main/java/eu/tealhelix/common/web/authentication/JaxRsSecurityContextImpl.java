package eu.tealhelix.common.web.authentication;

import java.security.Principal;

import jakarta.ws.rs.core.SecurityContext;

/**
 * Simple implementation of {@code javax.ws.rs.core.SecurityContext}.
 */
public class JaxRsSecurityContextImpl implements SecurityContext {

	private final Principal principal;
	private final boolean secure;
	private final String authenticationScheme;

	/**
	 * Full constructor.
	 *
	 * @param principal The user
	 * @param secure    If the transport is secure
	 */
	public JaxRsSecurityContextImpl(Principal principal, boolean secure, String authenticationScheme) {
		this.principal = principal;
		this.secure = secure;
		this.authenticationScheme = authenticationScheme;
	}

	@Override
	public Principal getUserPrincipal() {
		return principal;
	}

	@Override
	public boolean isUserInRole(String role) {
		return false;
	}

	@Override
	public boolean isSecure() {
		return secure;
	}

	@Override
	public String getAuthenticationScheme() {
		return authenticationScheme;
	}
}
