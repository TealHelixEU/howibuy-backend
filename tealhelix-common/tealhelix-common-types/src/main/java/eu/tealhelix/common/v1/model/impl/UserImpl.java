package eu.tealhelix.common.v1.model.impl;

import eu.tealhelix.common.types.Email;
import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;

/**
 * {@code User} implementation.
 */
public class UserImpl implements User {
	private final UserId id;
	private final String name;
	private final Email email;
	private final boolean systemFlag;
	private final boolean serviceFlag;

	public UserImpl(UserId id, String name, Email email, boolean systemFlag, boolean serviceFlag) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.systemFlag = systemFlag;
		this.serviceFlag = serviceFlag;
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
	public Email getEmail() {
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
