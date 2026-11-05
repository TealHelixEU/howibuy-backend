#!/usr/bin/env node

// Writes the fixture of the scoring engine's parity test: every product of WP3's database, with the inputs the engine
// scores it from and the scores WP3's notebook gave it under the normalisation profile log_tails. One CSV, keyed by AGB
// code, with the columns
//   agb_code, nutri_score, nutri_score_points, <the 32 indicators under their SustainabilityIndicator names>,
//   ENVIRONMENT, ANIMAL_WELFARE, SOCIO_ECONOMIC, HEALTH, OVERALL
// The database and the scores are joined by TH code; every product must have exactly one log_tails row, and every
// log_tails row must name a product. Values are copied as written, so nothing is lost to rounding.
//
// Usage:
//   ./generate-scoring-parity-fixture.js [-d <database.csv>] [-s <scores.csv>] [-o <fixture.csv>]
// Defaults: the 10-27 database and CORE_scores_by_profile.csv in tealhelix-architecture/src/data/wp3, and the
// fixture in the test resources of howibuy-container/sustainability-scoring.
//
// This script requires only NodeJS (no external dependencies). The database is read as latin1, matching the R
// notebook's read.csv(encoding = "latin1"); the fixture is written as UTF-8.

'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { parseCsv, toCsvLine } = require('./csv.js');

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const wp3 = path.join(repoRoot, 'tealhelix-architecture', 'src', 'data', 'wp3');

let databasePath = path.join(wp3, 'TH_WP3-FULL DATABASE-v2026-10-27.csv');
let scoresPath = path.join(wp3, 'CORE_scores_by_profile.csv');
let outputPath = path.join(repoRoot, 'howibuy-container', 'sustainability-scoring', 'src', 'test', 'resources',
	'log-tails-parity-v2026-10-27.csv');

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
	const arg = argv[i];
	switch (arg) {
		case '-d':
		case '--database':
			databasePath = argv[++i];
			break;
		case '-s':
		case '--scores':
			scoresPath = argv[++i];
			break;
		case '-o':
		case '--output':
			outputPath = argv[++i];
			break;
		case '-h':
		case '--help':
			console.log('Usage: ./generate-scoring-parity-fixture.js [-d <database.csv>] [-s <scores.csv>] [-o <fixture.csv>]');
			process.exit(0);
			break;
		default:
			console.error(`Unknown argument: ${arg}`);
			process.exit(2);
	}
}

// The database column of each indicator -> its SustainabilityIndicator name.
const INDICATORS = [
	{ csv: 'E_climate_change', name: 'CLIMATE_CHANGE' },
	{ csv: 'E_ozon_depletion', name: 'OZONE_DEPLETION' },
	{ csv: 'E_ionizing_radiation', name: 'IONIZING_RADIATION' },
	{ csv: 'E_ozon_formation', name: 'OZONE_FORMATION' },
	{ csv: 'E_particulate_matter', name: 'PARTICULATE_MATTER' },
	{ csv: 'E_non-carcinogenic_toxicity', name: 'NON_CARCINOGENIC_TOXICITY' },
	{ csv: 'E_carcinogenic_toxicity', name: 'CARCINOGENIC_TOXICITY' },
	{ csv: 'E_land_water_acidification', name: 'LAND_WATER_ACIDIFICATION' },
	{ csv: 'E_freshwater_eutrophication', name: 'FRESHWATER_EUTROPHICATION' },
	{ csv: 'E_marine_eutrophication', name: 'MARINE_EUTROPHICATION' },
	{ csv: 'E_terrestrial_eutrophication', name: 'TERRESTRIAL_EUTROPHICATION' },
	{ csv: 'E_freshwater_ecotoxicity', name: 'FRESHWATER_ECOTOXICITY' },
	{ csv: 'E_land_use', name: 'LAND_USE' },
	{ csv: 'E_water_use', name: 'WATER_USE' },
	{ csv: 'E_energy_use', name: 'ENERGY_USE' },
	{ csv: 'E_mineral_use', name: 'MINERAL_USE' },
	{ csv: 'AW_AW_index', name: 'ANIMAL_WELFARE_INDEX' },
	{ csv: 'AW_antibio_index', name: 'ANTIBIOTIC_INDEX' },
	{ csv: 'S_child_labour', name: 'CHILD_LABOUR' },
	{ csv: 'S_forced_labour', name: 'FORCED_LABOUR' },
	{ csv: 'S_fair_salary', name: 'FAIR_SALARY' },
	{ csv: 'S_working_time', name: 'WORKING_TIME' },
	{ csv: 'S_discrimination', name: 'DISCRIMINATION' },
	{ csv: 'S_health_safety_workers', name: 'HEALTH_SAFETY_WORKERS' },
	{ csv: 'S_social_benefits_legal_issues', name: 'SOCIAL_BENEFITS_LEGAL_ISSUES' },
	{ csv: 'S_workers_rights', name: 'WORKERS_RIGHTS' },
	{ csv: 'S_fair_competition', name: 'FAIR_COMPETITION' },
	{ csv: 'S_corruption', name: 'CORRUPTION' },
	{ csv: 'S_contribution_econ_dev', name: 'CONTRIBUTION_ECON_DEV' },
	{ csv: 'S_illiteracy', name: 'ILLITERACY' },
	{ csv: 'S_health_safety_society', name: 'HEALTH_SAFETY_SOCIETY' },
	{ csv: 'S_indigenous_rights', name: 'INDIGENOUS_RIGHTS' },
];

// The score columns of the notebook -> the fixture's, named after the ScoredSustainabilityDimension they score.
const SCORES = [
	{ csv: 'E', name: 'ENVIRONMENT' },
	{ csv: 'AW', name: 'ANIMAL_WELFARE' },
	{ csv: 'SE', name: 'SOCIO_ECONOMIC' },
	{ csv: 'H', name: 'HEALTH' },
	{ csv: 'overall', name: 'OVERALL' },
];

const PROFILE = 'log_tails';

function fail(message) {
	console.error(`ERROR: ${message}`);
	process.exit(1);
}

// The data rows of a CSV file, as objects keyed by its header; fails when a required column is missing.
function readRows(file, encoding, required) {
	const [header, ...rows] = parseCsv(fs.readFileSync(file, encoding));
	if (!header) fail(`no header in ${file}`);
	const missing = required.filter((name) => !header.includes(name));
	if (missing.length) fail(`${file} is missing columns: ${missing.join(', ')}`);
	return rows
		.filter((r) => r.length > 1 || (r.length === 1 && r[0] !== ''))
		.map((r) => Object.fromEntries(header.map((name, i) => [name, r[i]])));
}

function requireValue(row, column, thCode) {
	const value = row[column];
	if (value == null || value.trim() === '' || value.trim().toUpperCase() === 'NA') fail(`empty/NA ${column} for TH code ${thCode}`);
	if (/[",\n\r]/.test(value)) fail(`${column} of TH code ${thCode} would need quoting: ${JSON.stringify(value)}`);
	return value;
}

function main() {
	const products = readRows(databasePath, 'latin1',
		['TH_code', 'AGB_code', 'H_nutriscore', 'H_FNS_score', ...INDICATORS.map((c) => c.csv)]);
	const scores = readRows(scoresPath, 'utf8', ['TH_code', 'profile', ...SCORES.map((c) => c.csv)])
		.filter((row) => row.profile === PROFILE);

	const scoresByThCode = new Map();
	for (const row of scores) {
		if (scoresByThCode.has(row.TH_code)) fail(`more than one ${PROFILE} row for TH code ${row.TH_code}`);
		scoresByThCode.set(row.TH_code, row);
	}
	const thCodes = new Set(products.map((p) => p.TH_code));
	if (thCodes.size !== products.length) fail(`duplicate TH codes in ${databasePath}`);
	const withoutScores = products.filter((p) => !scoresByThCode.has(p.TH_code)).map((p) => p.TH_code);
	if (withoutScores.length) fail(`no ${PROFILE} scores for TH codes ${withoutScores.join(', ')}`);
	const withoutProduct = [...scoresByThCode.keys()].filter((thCode) => !thCodes.has(thCode));
	if (withoutProduct.length) fail(`${PROFILE} scores for TH codes not in the database: ${withoutProduct.join(', ')}`);

	const lines = [toCsvLine(['agb_code', 'nutri_score', 'nutri_score_points', ...INDICATORS.map((c) => c.name), ...SCORES.map((c) => c.name)])];
	for (const product of products) {
		const thCode = product.TH_code;
		const productScores = scoresByThCode.get(thCode);
		lines.push(toCsvLine([
			requireValue(product, 'AGB_code', thCode),
			requireValue(product, 'H_nutriscore', thCode),
			requireValue(product, 'H_FNS_score', thCode),
			...INDICATORS.map((c) => requireValue(product, c.csv, thCode)),
			...SCORES.map((c) => requireValue(productScores, c.csv, thCode)),
		]));
	}

	fs.mkdirSync(path.dirname(outputPath), { recursive: true });
	fs.writeFileSync(outputPath, lines.join('\n') + '\n', 'utf8');

	console.log(`Database: ${databasePath}`);
	console.log(`Scores:   ${scoresPath} (profile ${PROFILE})`);
	console.log(`Products: ${products.length}`);
	console.log(`Wrote ${outputPath}`);
}

main();
