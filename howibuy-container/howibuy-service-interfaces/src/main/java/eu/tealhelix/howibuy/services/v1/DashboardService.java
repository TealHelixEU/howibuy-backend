package eu.tealhelix.howibuy.services.v1;

import java.time.YearMonth;

import eu.tealhelix.common.v1.model.User;
import eu.tealhelix.howibuy.v1.model.Dashboard;
import io.smallrye.mutiny.Uni;

/**
 * Reads what the dashboard of a user shows.
 */
public interface DashboardService {
	/**
	 * Read the dashboard of a user. Its reads are made one after the other, without a transaction: a purchase registered
	 * between two of them shows fully on the next read.
	 *
	 * @param user     The user, a person
	 * @param month    The month whose averages the dashboard compares with those of the month before it, the month the
	 *                 user calls this month; required
	 * @param language The language of the labels of the badges, an ISO 639-1 code; English when {@code null} or blank
	 * @return The dashboard; a failure with {@code NotFoundException} if there is no such user
	 * @throws eu.tealhelix.common.types.validation.RequiredInputMissingException without a month
	 */
	Uni<Dashboard> retrieveDashboard(User user, YearMonth month, String language);
}
