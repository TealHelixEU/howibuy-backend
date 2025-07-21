package eu.tealhelix.common.services.generic;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.common.v1.types.UserId;
import io.smallrye.mutiny.Uni;

public interface UserService {
	Uni<User> requireUserFromValidIdmId(String userIdFromIdm, String name, boolean serviceFlag);

	Uni<User> requireUserWithId(UserId userId, String name, boolean serviceFlag);
}
