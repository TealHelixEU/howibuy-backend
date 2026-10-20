package eu.tealhelix.common.web.authentication;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import eu.tealhelix.common.v1.model.Role;

/**
 * The realm roles of the IDM that the services of TealHelix know, and the {@link Role} each one grants. The IDM gives
 * every subject other realm roles as well, which grant nothing here.
 */
public final class IdmRealmRoles {
	private static final Map<String, Role> ROLES_BY_REALM_ROLE = Map.of(
			"retailer", Role.RETAILER,
			"catalogue-sync", Role.CATALOGUE_SYNC,
			"operator", Role.OPERATOR
	);

	private IdmRealmRoles() {
	}

	/**
	 * The roles granted by the realm roles a subject has.
	 *
	 * @param hasRealmRole Whether the subject has a realm role, given its name
	 * @return The roles, empty if it has none
	 */
	public static Set<Role> rolesOf(Predicate<String> hasRealmRole) {
		return ROLES_BY_REALM_ROLE.entrySet().stream()
				.filter(entry -> hasRealmRole.test(entry.getKey()))
				.map(Map.Entry::getValue)
				.collect(toUnmodifiableSet());
	}
}
