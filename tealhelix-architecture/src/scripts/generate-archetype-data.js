#!/usr/bin/env node

// Transforms the WP3 "FULL DATABASE" CSV into the two normalized CSVs that Liquibase loads:
//   - archetype_category.csv  (id, parent_id, level, name)  -- the SAFAD L1/L2/L3 taxonomy tree
//   - archetype_product.csv   (id, category_id, name, agb_code, <impact columns>)  -- the leaf archetypes, under the
//                             name given with --product-file; one file per WP3 version, each loaded by its own changeset
//
// Category and product ids are deterministic UUIDv5, so re-running on a newer source CSV reuses the
// same ids for unchanged categories/products and any foreign keys to them survive a re-import.
//
// Usage:
//   ./generate-archetype-data.js [-i <source.csv>] [-o <output-dir>] [-p <product-file-name>]
// Defaults: source is the WP3 database in tealhelix-architecture/src/data/wp3, output is the
// howibuy-dao-hibernate-reactive resources, product file name is archetype_product.csv.
//
// This script requires only NodeJS (no external dependencies). The source CSV is read as latin1, matching
// the R algorithm's read.csv(encoding = "latin1"); the outputs are written as UTF-8.

'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { parseCsv, toCsvLine } = require('./csv.js');

const repoRoot = path.resolve(__dirname, '..', '..', '..');

let inputPath = path.join(repoRoot, 'tealhelix-architecture', 'src', 'data', 'wp3', 'TH_WP3-FULL DATABASE-v2026-10-27.csv');
let outputDir = path.join(repoRoot, 'howibuy-container', 'howibuy-dao-hibernate-reactive', 'src', 'main', 'resources', 'db', 'archetype');
let productFileName = 'archetype_product.csv';

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
	const arg = argv[i];
	switch (arg) {
		case '-i':
		case '--input':
			inputPath = argv[++i];
			break;
		case '-o':
		case '--output-dir':
			outputDir = argv[++i];
			break;
		case '-p':
		case '--product-file':
			productFileName = argv[++i];
			break;
		case '-h':
		case '--help':
			console.log('Usage: ./generate-archetype-data.js [-i <source.csv>] [-o <output-dir>] [-p <product-file-name>]');
			process.exit(0);
			break;
		default:
			console.error(`Unknown argument: ${arg}`);
			process.exit(2);
	}
}

// The per-product algorithm inputs we persist: source CSV column -> database column.
// Computed scores (E_scientific_SS, overall_*, ...) are deliberately excluded; the application recomputes them.
// A column marked integer must hold a whole number, in the source as in the database.
const IMPACT_COLUMNS = [
	{ csv: 'E_climate_change', db: 'e_climate_change' },
	{ csv: 'E_ozon_depletion', db: 'e_ozone_depletion' },
	{ csv: 'E_ionizing_radiation', db: 'e_ionizing_radiation' },
	{ csv: 'E_ozon_formation', db: 'e_ozone_formation' },
	{ csv: 'E_particulate_matter', db: 'e_particulate_matter' },
	{ csv: 'E_non-carcinogenic_toxicity', db: 'e_non_carcinogenic_toxicity' },
	{ csv: 'E_carcinogenic_toxicity', db: 'e_carcinogenic_toxicity' },
	{ csv: 'E_land_water_acidification', db: 'e_land_water_acidification' },
	{ csv: 'E_freshwater_eutrophication', db: 'e_freshwater_eutrophication' },
	{ csv: 'E_marine_eutrophication', db: 'e_marine_eutrophication' },
	{ csv: 'E_terrestrial_eutrophication', db: 'e_terrestrial_eutrophication' },
	{ csv: 'E_freshwater_ecotoxicity', db: 'e_freshwater_ecotoxicity' },
	{ csv: 'E_land_use', db: 'e_land_use' },
	{ csv: 'E_water_use', db: 'e_water_use' },
	{ csv: 'E_energy_use', db: 'e_energy_use' },
	{ csv: 'E_mineral_use', db: 'e_mineral_use' },
	{ csv: 'AW_AW_index', db: 'aw_index' },
	{ csv: 'AW_antibio_index', db: 'aw_antibio_index' },
	{ csv: 'S_child_labour', db: 's_child_labour' },
	{ csv: 'S_forced_labour', db: 's_forced_labour' },
	{ csv: 'S_fair_salary', db: 's_fair_salary' },
	{ csv: 'S_working_time', db: 's_working_time' },
	{ csv: 'S_discrimination', db: 's_discrimination' },
	{ csv: 'S_health_safety_workers', db: 's_health_safety_workers' },
	{ csv: 'S_social_benefits_legal_issues', db: 's_social_benefits_legal_issues' },
	{ csv: 'S_workers_rights', db: 's_workers_rights' },
	{ csv: 'S_fair_competition', db: 's_fair_competition' },
	{ csv: 'S_corruption', db: 's_corruption' },
	{ csv: 'S_contribution_econ_dev', db: 's_contribution_econ_dev' },
	{ csv: 'S_illiteracy', db: 's_illiteracy' },
	{ csv: 'S_health_safety_society', db: 's_health_safety_society' },
	{ csv: 'S_indigenous_rights', db: 's_indigenous_rights' },
	{ csv: 'H_nutriscore', db: 'nutri_score' },
	{ csv: 'H_FNS_score', db: 'nutri_score_points', integer: true },
];

// Hierarchy and identity columns in the source CSV.
const COL_FOOD_PRODUCT = 'food_product';
const COL_L1 = 'SAFAD_L1_cat';
const COL_L2 = 'SAFAD_L2_cat';
const COL_L3 = 'SAFAD_L3_cat';
const COL_AGB_CODE = 'AGB_code';

// Separator for building category path keys; the unit separator never occurs in category names.
const PATH_SEP = '';

// RFC 4122 namespace for URLs, used to derive our own stable namespace for archetype ids.
const URL_NAMESPACE = '6ba7b811-9dad-11d1-80b4-00c04fc964f9';

function uuidv5(namespaceUuid, name) {
	const ns = Buffer.from(namespaceUuid.replace(/-/g, ''), 'hex');
	const hash = crypto.createHash('sha1').update(ns).update(Buffer.from(name, 'utf8')).digest();
	const bytes = hash.subarray(0, 16);
	bytes[6] = (bytes[6] & 0x0f) | 0x50; // version 5
	bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 variant
	const hex = bytes.toString('hex');
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const ARCHETYPE_NAMESPACE = uuidv5(URL_NAMESPACE, 'https://tealhelix.eu/ns/archetype');

function fail(message) {
	console.error(`ERROR: ${message}`);
	process.exit(1);
}

function main() {
	const text = fs.readFileSync(inputPath, 'latin1');
	const rows = parseCsv(text);
	if (rows.length < 2) fail(`no data rows in ${inputPath}`);

	const header = rows[0];
	const idx = {};
	header.forEach((name, i) => { idx[name] = i; });

	const required = [COL_FOOD_PRODUCT, COL_L1, COL_L2, COL_L3, COL_AGB_CODE, ...IMPACT_COLUMNS.map((c) => c.csv)];
	const missing = required.filter((name) => !(name in idx));
	if (missing.length) fail(`source CSV is missing columns: ${missing.join(', ')}`);

	const dataRows = rows.slice(1).filter((r) => r.length > 1 || (r.length === 1 && r[0] !== ''));

	// Build the category tree keyed by path; assign deterministic ids and remember insertion at each level.
	const categories = new Map(); // pathKey -> { id, parentId, level, name }
	const products = [];
	const seenAgbCodes = new Set();

	function categoryId(pathKey) {
		return uuidv5(ARCHETYPE_NAMESPACE, pathKey);
	}

	function ensureCategory(pathKey, parentId, level, name) {
		if (!categories.has(pathKey)) {
			categories.set(pathKey, { id: categoryId(pathKey), parentId, level, name });
		}
		return categories.get(pathKey);
	}

	for (const r of dataRows) {
		const l1 = r[idx[COL_L1]];
		const l2 = r[idx[COL_L2]];
		const l3 = r[idx[COL_L3]];
		const name = r[idx[COL_FOOD_PRODUCT]];
		const agbCode = r[idx[COL_AGB_CODE]];

		for (const [value, label] of [[l1, COL_L1], [l2, COL_L2], [l3, COL_L3], [name, COL_FOOD_PRODUCT], [agbCode, COL_AGB_CODE]]) {
			if (value == null || value.trim() === '' || value.trim().toUpperCase() === 'NA') {
				fail(`empty/NA ${label} for product ${JSON.stringify(name)} (agb_code ${JSON.stringify(agbCode)})`);
			}
		}
		if (seenAgbCodes.has(agbCode)) fail(`duplicate AGB_code ${JSON.stringify(agbCode)}`);
		seenAgbCodes.add(agbCode);

		const l1Key = l1;
		const l2Key = l1 + PATH_SEP + l2;
		const l3Key = l1 + PATH_SEP + l2 + PATH_SEP + l3;
		const c1 = ensureCategory(l1Key, null, 1, l1);
		const c2 = ensureCategory(l2Key, c1.id, 2, l2);
		const c3 = ensureCategory(l3Key, c2.id, 3, l3);

		const impacts = IMPACT_COLUMNS.map((col) => {
			const v = r[idx[col.csv]];
			if (v == null || v.trim() === '' || v.trim().toUpperCase() === 'NA') {
				fail(`empty/NA ${col.csv} for agb_code ${JSON.stringify(agbCode)}`);
			}
			if (col.integer && !/^-?\d+$/.test(v)) {
				fail(`${col.csv} is not an integer for agb_code ${JSON.stringify(agbCode)}: ${JSON.stringify(v)}`);
			}
			return v;
		});

		products.push({ id: uuidv5(ARCHETYPE_NAMESPACE, agbCode), categoryId: c3.id, name, agbCode, impacts });
	}

	const orderedCategories = [...categories.values()].sort((a, b) => a.level - b.level);

	const categoryLines = ['id,parent_id,level,name'];
	for (const c of orderedCategories) {
		categoryLines.push(toCsvLine([c.id, c.parentId ?? '', String(c.level), c.name]));
	}

	const productHeader = ['id', 'category_id', 'name', 'agb_code', ...IMPACT_COLUMNS.map((c) => c.db)];
	const productLines = [productHeader.join(',')];
	for (const p of products) {
		productLines.push(toCsvLine([p.id, p.categoryId, p.name, p.agbCode, ...p.impacts]));
	}

	fs.mkdirSync(outputDir, { recursive: true });
	const categoryFile = path.join(outputDir, 'archetype_category.csv');
	const productFile = path.join(outputDir, productFileName);
	fs.writeFileSync(categoryFile, categoryLines.join('\n') + '\n', 'utf8');
	fs.writeFileSync(productFile, productLines.join('\n') + '\n', 'utf8');

	const levelCounts = { 1: 0, 2: 0, 3: 0 };
	for (const c of orderedCategories) levelCounts[c.level]++;

	console.log(`Source:   ${inputPath}`);
	console.log(`Namespace: ${ARCHETYPE_NAMESPACE}`);
	console.log(`Categories: L1=${levelCounts[1]} L2=${levelCounts[2]} L3=${levelCounts[3]} (total ${orderedCategories.length})`);
	console.log(`Products:   ${products.length}`);
	console.log(`Wrote ${categoryFile}`);
	console.log(`Wrote ${productFile}`);
	// A stable sample to lock into the integration test:
	const sample = products.find((p) => p.agbCode === '1019');
	if (sample) console.log(`Sample agb_code 1019 -> product id ${sample.id}, category id ${sample.categoryId}`);
}

main();
