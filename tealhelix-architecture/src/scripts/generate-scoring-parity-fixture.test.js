#!/usr/bin/env node

// Tests for generate-scoring-parity-fixture.js, run as a command on a small database and a small score file shaped like
// WP3's. Run with:
//   node --test generate-scoring-parity-fixture.test.js
// Requires only NodeJS (node:test, node:assert), no external dependencies.

'use strict';

const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const SCRIPT = path.join(__dirname, 'generate-scoring-parity-fixture.js');

// The indicator columns of WP3's database, in the order of the indicators' names in the fixture.
const INDICATOR_COLUMNS = [
	'E_climate_change', 'E_ozon_depletion', 'E_ionizing_radiation', 'E_ozon_formation', 'E_particulate_matter',
	'E_non-carcinogenic_toxicity', 'E_carcinogenic_toxicity', 'E_land_water_acidification',
	'E_freshwater_eutrophication', 'E_marine_eutrophication', 'E_terrestrial_eutrophication',
	'E_freshwater_ecotoxicity', 'E_land_use', 'E_water_use', 'E_energy_use', 'E_mineral_use',
	'AW_AW_index', 'AW_antibio_index',
	'S_child_labour', 'S_forced_labour', 'S_fair_salary', 'S_working_time', 'S_discrimination',
	'S_health_safety_workers', 'S_social_benefits_legal_issues', 'S_workers_rights', 'S_fair_competition',
	'S_corruption', 'S_contribution_econ_dev', 'S_illiteracy', 'S_health_safety_society', 'S_indigenous_rights',
];

const INDICATOR_NAMES = [
	'CLIMATE_CHANGE', 'OZONE_DEPLETION', 'IONIZING_RADIATION', 'OZONE_FORMATION', 'PARTICULATE_MATTER',
	'NON_CARCINOGENIC_TOXICITY', 'CARCINOGENIC_TOXICITY', 'LAND_WATER_ACIDIFICATION',
	'FRESHWATER_EUTROPHICATION', 'MARINE_EUTROPHICATION', 'TERRESTRIAL_EUTROPHICATION',
	'FRESHWATER_ECOTOXICITY', 'LAND_USE', 'WATER_USE', 'ENERGY_USE', 'MINERAL_USE',
	'ANIMAL_WELFARE_INDEX', 'ANTIBIOTIC_INDEX',
	'CHILD_LABOUR', 'FORCED_LABOUR', 'FAIR_SALARY', 'WORKING_TIME', 'DISCRIMINATION',
	'HEALTH_SAFETY_WORKERS', 'SOCIAL_BENEFITS_LEGAL_ISSUES', 'WORKERS_RIGHTS', 'FAIR_COMPETITION',
	'CORRUPTION', 'CONTRIBUTION_ECON_DEV', 'ILLITERACY', 'HEALTH_SAFETY_SOCIETY', 'INDIGENOUS_RIGHTS',
];

// Each product's indicators hold its own value, so that a fixture row shows which product it was read from.
const WATER = { thCode: 'TH0427', agbCode: '18066', name: 'Tap water', nutriScore: 'Nutriscore_A', points: '-14', value: '0.25' };
const BABY_BISCUIT = { thCode: 'TH0011', agbCode: '24689', name: 'Biscuit for baby, "petit-beurre"', nutriScore: '0', points: '0', value: '1.5e-07' };

const workDirs = [];
after(() => workDirs.forEach((dir) => fs.rmSync(dir, { recursive: true, force: true })));

function csv(rows) {
	return rows.map((fields) => fields.map((f) => (/[",]/.test(f) ? `"${f.replace(/"/g, '""')}"` : f)).join(',')).join('\n') + '\n';
}

function writeDatabase(dir, products) {
	const header = ['food_product', 'TH_code', 'AGB_code', ...INDICATOR_COLUMNS, 'H_FNS_score', 'H_nutriscore'];
	const rows = products.map((p) => [p.name, p.thCode, p.agbCode, ...INDICATOR_COLUMNS.map(() => p.value), p.points, p.nutriScore]);
	const file = path.join(dir, 'database.csv');
	fs.writeFileSync(file, csv([header, ...rows]), 'latin1');
	return file;
}

// One score row per product and profile, as WP3's notebook writes them; `scores` maps a TH code to E, AW, SE, H, overall.
function writeScores(dir, profiles, scores) {
	const header = ['row_id', 'TH_code', 'food_product', 'SAFAD_L1_cat', 'SAFAD_L2_cat', 'profile', 'E', 'AW', 'SE', 'H', 'overall'];
	const rows = [];
	for (const profile of profiles) {
		for (const [thCode, values] of Object.entries(scores)) {
			rows.push([String(rows.length + 1), thCode, 'a product', 'L1', 'L2', profile, ...values(profile)]);
		}
	}
	const file = path.join(dir, 'scores.csv');
	fs.writeFileSync(file, csv([header, ...rows]), 'utf8');
	return file;
}

function generate(products, profiles, scores) {
	const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'generate-scoring-parity-fixture-'));
	workDirs.push(dir);
	const output = path.join(dir, 'out', 'parity.csv');
	const result = spawnSync(process.execPath,
		[SCRIPT, '-d', writeDatabase(dir, products), '-s', writeScores(dir, profiles, scores), '-o', output], { encoding: 'utf8' });
	return { ...result, output };
}

// The data rows of the fixture, as objects keyed by its header. The fixture's values hold no commas or quotes.
function readFixture(file) {
	const [header, ...rows] = fs.readFileSync(file, 'utf8').trimEnd().split('\n').map((line) => line.split(','));
	return { header, rows: rows.map((row) => Object.fromEntries(header.map((name, i) => [name, row[i]]))) };
}

// Scores that tell the profile and the product apart: log_tails gives 0.1.., any other profile 0.9...
const SCORES = {
	TH0011: (profile) => (profile === 'log_tails' ? ['0.11', '0.12', '0.13', '0.14', '0.125'] : ['0.9', '0.9', '0.9', '0.9', '0.9']),
	TH0427: (profile) => (profile === 'log_tails' ? ['0.21', '0.22', '0.23', '0.24', '0.225'] : ['0.9', '0.9', '0.9', '0.9', '0.9']),
};

test('writes each product with its indicators under their names and the log_tails scores of the same TH code', () => {
	const result = generate([WATER, BABY_BISCUIT], ['initial', 'log_tails', 'log_tails_TL'], SCORES);

	assert.equal(result.status, 0, result.stderr);
	const { header, rows } = readFixture(result.output);
	assert.deepEqual(header, ['agb_code', 'nutri_score', 'nutri_score_points', ...INDICATOR_NAMES,
		'ENVIRONMENT', 'ANIMAL_WELFARE', 'SOCIO_ECONOMIC', 'HEALTH', 'OVERALL']);
	assert.deepEqual(rows.map((r) => [r.agb_code, r.nutri_score, r.nutri_score_points, r.CLIMATE_CHANGE, r.INDIGENOUS_RIGHTS,
		r.ENVIRONMENT, r.ANIMAL_WELFARE, r.SOCIO_ECONOMIC, r.HEALTH, r.OVERALL]), [
		['18066', 'Nutriscore_A', '-14', '0.25', '0.25', '0.21', '0.22', '0.23', '0.24', '0.225'],
		['24689', '0', '0', '1.5e-07', '1.5e-07', '0.11', '0.12', '0.13', '0.14', '0.125'],
	]);
});

test('fails, writing nothing, when a product of the database has no log_tails scores', () => {
	const result = generate([WATER, BABY_BISCUIT], ['log_tails'], { TH0427: SCORES.TH0427 });

	assert.notEqual(result.status, 0);
	assert.match(result.stderr, /TH0011/);
	assert.equal(fs.existsSync(result.output), false);
});

test('fails, writing nothing, when log_tails scores name a product the database does not hold', () => {
	const result = generate([WATER], ['log_tails'], SCORES);

	assert.notEqual(result.status, 0);
	assert.match(result.stderr, /TH0011/);
	assert.equal(fs.existsSync(result.output), false);
});
