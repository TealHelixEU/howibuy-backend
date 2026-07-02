package eu.tealhelix.howibuy.dao.jpa.values;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The 14 social impact indicators of an archetype product, as provided by the WP3 database.
 * The single social scores are computed by the application, not stored.
 */
@Embeddable
public class SocialImpact {
	@Column(name = "s_child_labour")
	private double childLabour;

	@Column(name = "s_forced_labour")
	private double forcedLabour;

	@Column(name = "s_fair_salary")
	private double fairSalary;

	@Column(name = "s_working_time")
	private double workingTime;

	@Column(name = "s_discrimination")
	private double discrimination;

	@Column(name = "s_health_safety_workers")
	private double healthSafetyWorkers;

	@Column(name = "s_social_benefits_legal_issues")
	private double socialBenefitsLegalIssues;

	@Column(name = "s_workers_rights")
	private double workersRights;

	@Column(name = "s_fair_competition")
	private double fairCompetition;

	@Column(name = "s_corruption")
	private double corruption;

	@Column(name = "s_contribution_econ_dev")
	private double contributionEconDev;

	@Column(name = "s_illiteracy")
	private double illiteracy;

	@Column(name = "s_health_safety_society")
	private double healthSafetySociety;

	@Column(name = "s_indigenous_rights")
	private double indigenousRights;

	public double getChildLabour() {
		return childLabour;
	}

	public double getForcedLabour() {
		return forcedLabour;
	}

	public double getFairSalary() {
		return fairSalary;
	}

	public double getWorkingTime() {
		return workingTime;
	}

	public double getDiscrimination() {
		return discrimination;
	}

	public double getHealthSafetyWorkers() {
		return healthSafetyWorkers;
	}

	public double getSocialBenefitsLegalIssues() {
		return socialBenefitsLegalIssues;
	}

	public double getWorkersRights() {
		return workersRights;
	}

	public double getFairCompetition() {
		return fairCompetition;
	}

	public double getCorruption() {
		return corruption;
	}

	public double getContributionEconDev() {
		return contributionEconDev;
	}

	public double getIlliteracy() {
		return illiteracy;
	}

	public double getHealthSafetySociety() {
		return healthSafetySociety;
	}

	public double getIndigenousRights() {
		return indigenousRights;
	}
}
