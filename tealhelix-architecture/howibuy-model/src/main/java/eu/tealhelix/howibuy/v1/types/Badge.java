package eu.tealhelix.howibuy.v1.types;

/**
 * An award a user earns once and keeps. Each level of a series is a badge of its own. The name of a constant is the
 * badge's code: it is stored with every badge earned and keys its label, so it is renamed only together with the
 * stored badges and the labels.
 */
public enum Badge {
	ENVIRONMENTAL_HERO_1,
	ENVIRONMENTAL_HERO_2,
	ENVIRONMENTAL_HERO_3,
	ANIMAL_WELFARE_HERO_1,
	ANIMAL_WELFARE_HERO_2,
	ANIMAL_WELFARE_HERO_3,
	SOCIO_ECONOMIC_HERO_1,
	SOCIO_ECONOMIC_HERO_2,
	SOCIO_ECONOMIC_HERO_3,
	HEALTH_HERO_1,
	HEALTH_HERO_2,
	HEALTH_HERO_3
}
