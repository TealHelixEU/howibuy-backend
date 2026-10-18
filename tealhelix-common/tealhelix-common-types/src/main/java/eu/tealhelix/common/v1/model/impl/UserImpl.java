package eu.tealhelix.common.v1.model.impl;

import java.util.Set;

import eu.tealhelix.common.types.EmailAddress;
import eu.tealhelix.common.v1.model.Role;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;

/**
 * {@code User} implementation.
 */
public class UserImpl implements User {
	private final UserId id;
	private final String name;
	private final EmailAddress email;
	private final boolean systemFlag;
	private final boolean personFlag;
	private final Set<Role> roles;

	public UserImpl(UserId id, String name, EmailAddress email, boolean systemFlag, boolean personFlag, Set<Role> roles) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.systemFlag = systemFlag;
		this.personFlag = personFlag;
		this.roles = Set.copyOf(roles);
	}

	@Override
	public String toString() {
		return "UserImpl{" +
				"id=" + id +
				(name != null ? ", name='" + name + '\'' : "") +
				(email != null ? ", email=" + email : "") +
				(systemFlag ? ", system" : "") +
				(personFlag ? ", person" : "") +
				(!roles.isEmpty() ? ", roles=" + roles : "") +
				'}';
	}

	@Override
	public UserId getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public EmailAddress getEmail() {
		return email;
	}

	@Override
	public boolean isSystem() {
		return systemFlag;
	}

	@Override
	public boolean isPerson() {
		return personFlag;
	}

	@Override
	public Set<Role> getRoles() {
		return roles;
	}

	@Override
	public boolean isUnauthenticated() {
		return id == null;
	}
}
