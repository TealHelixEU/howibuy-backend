#!/usr/bin/env node

// Tests for generate-archetype-data.js, run as a command on a small source CSV shaped like WP3's. Run with:
//   node --test generate-archetype-data.test.js
// Requires only NodeJS (node:test, node:assert), no external dependencies.

'use strict';

const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const SCRIPT = path.join(__dirname, 'generate-archetype-data.js');

// The source columns the script reads, besides the hierarchy and identity ones.
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

const WATER = { agbCode: '18066', name: 'Tap water', nutriScore: 'Nutriscore_A', points: '-14' };
const BABY_BISCUIT = { agbCode: '24689', name: 'Biscuit for baby', nutriScore: '0', points: '0' };

const workDirs = [];
after(() => workDirs.forEach((dir) => fs.rmSync(dir, { recursive: true, force: true })));

function writeSource(dir, products) {
	const header = ['food_product', 'SAFAD_L1_cat', 'SAFAD_L2_cat', 'SAFAD_L3_cat', 'AGB_code', ...INDICATOR_COLUMNS,
		'H_nutriscore', 'H_FNS_score'];
	const rows = products.map((p) => [p.name, 'Level 1', 'Level 2', 'Level 3', p.agbCode, ...INDICATOR_COLUMNS.map(() => '0.5'),
		p.nutriScore, p.points]);
	const file = path.join(dir, 'source.csv');
	fs.writeFileSync(file, [header, ...rows].map((fields) => fields.join(',')).join('\n') + '\n', 'latin1');
	return file;
}

function generate(products, ...options) {
	const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'generate-archetype-data-'));
	workDirs.push(dir);
	const outputDir = path.join(dir, 'out');
	const result = spawnSync(process.execPath, [SCRIPT, '-i', writeSource(dir, products), '-o', outputDir, ...options], { encoding: 'utf8' });
	return { ...result, outputDir };
}

// The data rows of a generated CSV, as objects keyed by its header. The fixture's values hold no commas or quotes.
function readCsv(file) {
	const [header, ...rows] = fs.readFileSync(file, 'utf8').trimEnd().split('\n').map((line) => line.split(','));
	return rows.map((row) => Object.fromEntries(header.map((name, i) => [name, row[i]])));
}

test('writes the Nutri-Score points of every product, the placeholder 0 included', () => {
	const result = generate([WATER, BABY_BISCUIT]);

	assert.equal(result.status, 0, result.stderr);
	const products = readCsv(path.join(result.outputDir, 'archetype_product.csv'));
	assert.deepEqual(products.map((p) => [p.agb_code, p.nutri_score, p.nutri_score_points]), [
		['18066', 'Nutriscore_A', '-14'],
		['24689', '0', '0'],
	]);
});

test('writes the products to the file named by --product-file, next to the categories', () => {
	const result = generate([WATER], '--product-file', 'archetype_product_v2026-10-27.csv');

	assert.equal(result.status, 0, result.stderr);
	assert.deepEqual(fs.readdirSync(result.outputDir).sort(), ['archetype_category.csv', 'archetype_product_v2026-10-27.csv']);
	assert.equal(readCsv(path.join(result.outputDir, 'archetype_product_v2026-10-27.csv'))[0].agb_code, '18066');
});

test('fails, writing nothing, when the Nutri-Score points of a product are not an integer', () => {
	const result = generate([WATER, { ...BABY_BISCUIT, points: '3.5' }]);

	assert.equal(result.status, 1);
	assert.match(result.stderr, /H_FNS_score is not an integer.*"24689".*"3\.5"/);
	assert.equal(fs.existsSync(result.outputDir), false);
});
