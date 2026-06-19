package eu.tealhelix.common.v1.model.impl;

import eu.tealhelix.common.types.EmailAddress;
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
	private final boolean serviceFlag;

	public UserImpl(UserId id, String name, EmailAddress email, boolean systemFlag, boolean serviceFlag) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.systemFlag = systemFlag;
		this.serviceFlag = serviceFlag;
	}

	@Override
	public String toString() {
		return "UserImpl{" +
				"id=" + id +
				(name != null ? ", name='" + name + '\'' : "") +
				(email != null ? ", email=" + email : "") +
				(systemFlag ? ", system" : "") +
				(serviceFlag ? ", service" : "") +
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
	public boolean isService() {
		return serviceFlag;
	}

	@Override
	public boolean isUnauthenticated() {
		return id == null;
	}
}
