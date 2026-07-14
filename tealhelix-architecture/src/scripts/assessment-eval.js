#!/usr/bin/env node

// Runs the product-assessment eval harness: for every row of the ground-truth CSV it invokes the backend
// /assessment/single endpoint with the product name, then scores how far the classifier's diagnostics
// (L1/L2/L3 category + archetype product) agree with the hand-matched ground truth.
//
// The output CSV is the input CSV verbatim plus six columns:
//   Assessed L1 cat, Assessed L2 cat, Assessed L3 cat, Assessed product, Outcome type, Score
// so inputs, outputs and score sit in one file. Score is the length of the correct prefix of the descent
// (L1, then L2, then L3, then product): counting stops at the first mismatch, so 4 means a full match and
// anything less pinpoints the level at which the classifier first diverged.
//
// Authentication mirrors the manual flow: this script shells out to the sibling keycloak-auth-service.js
// (service-account client-credentials token) and correlation-id.js (impersonation token), and re-acquires
// them transparently if a request comes back 401 (token expiry).
//
// Usage:
//   ./assessment-eval.js -p <service-account-secret> [options]
//   TH_SVC_SECRET=... ./assessment-eval.js [options]
// Options:
//   -i, --input <csv>        ground-truth CSV (default: ../data/test_data_kritis_filled.csv)
//   -o, --output <csv>       results CSV (default: <input dir>/<input name>-results.csv)
//       --base <url>         howibuy v1 base URL (default: http://localhost:8180/api/howibuy/v1)
//       --keycloak <url>     Keycloak base URL (default: http://localhost:8280)
//   -u, --client <id>        service-account client id (default: lime_fresh)
//   -p, --secret <secret>    service-account client secret (or env TH_SVC_SECRET)
//   -c, --correlation-id <s> correlation id for impersonation (default: abc)
//   -l, --language <code>    ProductData language (default: el -- the glossary is Greek)
//       --limit <n>          process only the first n data rows (for a quick smoke run)
//
// This script requires only NodeJS (no external dependencies).

'use strict';

const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

// ---------------------------------------------------------------------------
// Pure scoring logic (exported for the test).
// ---------------------------------------------------------------------------

function norm(value) {
	return (value == null ? '' : String(value)).trim();
}

// The length of the correct prefix of the descent L1 -> L2 -> L3 -> product. Counting stops at the first
// mismatch, so a deeper level that happens to coincide after an earlier miss does not earn a point.
function scoreRow(expected, assessed) {
	const levels = [['l1', expected.l1], ['l2', expected.l2], ['l3', expected.l3], ['product', expected.product]];
	let score = 0;
	for (const [key, expectedValue] of levels) {
		if (norm(expectedValue) !== norm(assessed[key])) break;
		score++;
	}
	return score;
}

// True when two strings differ only by letter case or by whitespace -- i.e. a "mismatch" that is really a
// normalization artifact worth surfacing, not a genuine classification disagreement.
function looksLikeNormalizationArtifact(a, b) {
	const aggressive = (s) => norm(s).normalize('NFC').toLowerCase().replace(/\s+/g, ' ');
	return norm(a) !== norm(b) && aggressive(a) === aggressive(b);
}

// ---------------------------------------------------------------------------
// CSV (RFC 4180) parsing / writing -- same conventions as generate-archetype-data.js.
// ---------------------------------------------------------------------------

function parseCsv(text) {
	const rows = [];
	let field = '';
	let row = [];
	let inQuotes = false;
	for (let i = 0; i < text.length; i++) {
		const c = text[i];
		if (inQuotes) {
			if (c === '"') {
				if (text[i + 1] === '"') { field += '"'; i++; }
				else { inQuotes = false; }
			} else {
				field += c;
			}
		} else if (c === '"') {
			inQuotes = true;
		} else if (c === ',') {
			row.push(field); field = '';
		} else if (c === '\n') {
			row.push(field); field = '';
			rows.push(row); row = [];
		} else if (c === '\r') {
			// ignore; handled with the following \n
		} else {
			field += c;
		}
	}
	if (field !== '' || row.length > 0) { row.push(field); rows.push(row); }
	return rows;
}

function csvEscape(value) {
	const s = value == null ? '' : String(value);
	if (/[",\n\r]/.test(s)) {
		return '"' + s.replace(/"/g, '""') + '"';
	}
	return s;
}

function toCsvLine(fields) {
	return fields.map(csvEscape).join(',');
}

// ---------------------------------------------------------------------------
// HTTP + token acquisition.
// ---------------------------------------------------------------------------

function request(urlString, { method = 'GET', headers = {}, body } = {}) {
	return new Promise((resolve, reject) => {
		const url = new URL(urlString);
		const lib = url.protocol === 'https:' ? https : http;
		const req = lib.request(url, { method, headers }, (res) => {
			let data = '';
			res.setEncoding('utf8');
			res.on('data', (chunk) => { data += chunk; });
			res.on('end', () => resolve({ status: res.statusCode, body: data }));
		});
		req.on('error', reject);
		if (body) {
			req.write(body);
		}
		req.end();
	});
}

// Runs the two sibling auth scripts (via the current node binary, so PATH need not contain node) and returns
// the impersonation token to put in the Authorization header.
function acquireImpersonationToken(cfg) {
	const run = (script, scriptArgs) =>
		execFileSync(process.execPath, [path.join(__dirname, script), ...scriptArgs], { encoding: 'utf8' }).trim();

	const serviceToken = run('keycloak-auth-service.js', ['-i', cfg.keycloak, '-u', cfg.client, '-p', cfg.secret]);
	return run('correlation-id.js', ['-s', cfg.base, '-c', cfg.correlationId, '-a', serviceToken]);
}

function fail(message) {
	console.error(`ERROR: ${message}`);
	process.exit(1);
}

// ---------------------------------------------------------------------------
// Argument parsing.
// ---------------------------------------------------------------------------

function parseArgs(argv) {
	const cfg = {
		input: path.resolve(__dirname, '..', 'data', 'test_data_kritis_filled.csv'),
		output: null,
		base: 'http://localhost:8180/api/howibuy/v1',
		keycloak: 'http://localhost:8280',
		client: 'lime_fresh',
		secret: process.env.TH_SVC_SECRET,
		correlationId: 'abc',
		language: 'el',
		limit: Infinity,
	};
	for (let i = 0; i < argv.length; i++) {
		const arg = argv[i];
		switch (arg) {
			case '-i': case '--input': cfg.input = argv[++i]; break;
			case '-o': case '--output': cfg.output = argv[++i]; break;
			case '--base': cfg.base = argv[++i]; break;
			case '--keycloak': cfg.keycloak = argv[++i]; break;
			case '-u': case '--client': cfg.client = argv[++i]; break;
			case '-p': case '--secret': cfg.secret = argv[++i]; break;
			case '-c': case '--correlation-id': cfg.correlationId = argv[++i]; break;
			case '-l': case '--language': cfg.language = argv[++i]; break;
			case '--limit': cfg.limit = Number(argv[++i]); break;
			case '-h': case '--help':
				console.log(fs.readFileSync(__filename, 'utf8').split('\n').filter((l) => l.startsWith('//')).map((l) => l.slice(3)).join('\n'));
				process.exit(0);
				break;
			default:
				fail(`Unknown argument: ${arg}`);
		}
	}
	if (!cfg.secret) fail('Service-account secret is required (-p|--secret or TH_SVC_SECRET)');
	if (!cfg.output) {
		const dir = path.dirname(cfg.input);
		const base = path.basename(cfg.input, path.extname(cfg.input));
		cfg.output = path.join(dir, `${base}-results.csv`);
	}
	return cfg;
}

// ---------------------------------------------------------------------------
// Assessment run.
// ---------------------------------------------------------------------------

const REQUIRED_COLUMNS = ['Product name', 'L1 cat', 'L2 cat', 'L3 cat', 'Archetype', 'Match reliability'];

function columnIndex(header, name) {
	const i = header.indexOf(name);
	if (i < 0) fail(`input CSV is missing column: ${name}`);
	return i;
}

// Extracts {l1,l2,l3,product} from a /assessment/single response body, or null if the shape is unexpected.
function extractDiagnostics(json) {
	const d = json && json.diagnostics;
	if (!d) return null;
	return { l1: d.l1Category ?? null, l2: d.l2Category ?? null, l3: d.l3Category ?? null, product: d.product ?? null };
}

async function main() {
	const cfg = parseArgs(process.argv.slice(2));
	const assessmentUrl = `${cfg.base}/assessment/single`;

	const rows = parseCsv(fs.readFileSync(cfg.input, 'utf8'));
	if (rows.length < 2) fail(`no data rows in ${cfg.input}`);
	const header = rows[0];
	const col = {
		name: columnIndex(header, 'Product name'),
		l1: columnIndex(header, 'L1 cat'),
		l2: columnIndex(header, 'L2 cat'),
		l3: columnIndex(header, 'L3 cat'),
		product: columnIndex(header, 'Archetype'),
		reliability: columnIndex(header, 'Match reliability'),
	};
	const dataRows = rows.slice(1).filter((r) => r.length > 1 || (r.length === 1 && r[0] !== ''));
	const total = Math.min(dataRows.length, cfg.limit);

	console.error(`Input:      ${cfg.input} (${dataRows.length} products${cfg.limit < dataRows.length ? `, limited to ${total}` : ''})`);
	console.error(`Assessment: ${assessmentUrl}`);
	console.error(`Language:   ${cfg.language}`);
	console.error('Acquiring impersonation token...');
	let token = acquireImpersonationToken(cfg);

	const out = fs.createWriteStream(cfg.output, { encoding: 'utf8' });
	out.write(toCsvLine([...header, 'Assessed L1 cat', 'Assessed L2 cat', 'Assessed L3 cat', 'Assessed product', 'Outcome type', 'Score']) + '\n');

	const scoreHistogram = [0, 0, 0, 0, 0];
	const byReliability = new Map(); // reliability -> { count, scoreSum, successes }
	const byType = new Map();        // outcome type -> count
	let firstResponseLogged = false;

	for (let n = 0; n < total; n++) {
		const r = dataRows[n];
		const name = r[col.name];
		const expected = { l1: r[col.l1], l2: r[col.l2], l3: r[col.l3], product: r[col.product] };

		const requestBody = JSON.stringify({
			productKey: `eval-${n + 1}`,
			language: cfg.language,
			name,
			price: 0,
			currency: 'EUR',
			characteristics: {},
			tags: [],
		});

		let type;
		let assessed = { l1: null, l2: null, l3: null, product: null };
		try {
			let response = await postAssessment(assessmentUrl, token, requestBody);
			if (response.status === 401 || response.status === 403) {
				console.error('  token rejected, re-acquiring...');
				token = acquireImpersonationToken(cfg);
				response = await postAssessment(assessmentUrl, token, requestBody);
			}
			if (!firstResponseLogged) {
				console.error(`  first response (HTTP ${response.status}): ${response.body.slice(0, 400)}`);
				firstResponseLogged = true;
			}
			if (response.status !== 200) {
				type = `HTTP_${response.status}`;
			} else {
				const json = JSON.parse(response.body);
				type = json.type ?? 'UNKNOWN';
				const diagnostics = extractDiagnostics(json);
				if (!diagnostics) {
					type = 'NO_DIAGNOSTICS';
				} else {
					assessed = diagnostics;
				}
			}
		} catch (err) {
			type = 'ERROR';
			console.error(`  request failed for "${name}": ${err.message}`);
		}

		const score = scoreRow(expected, assessed);
		reportArtifacts(name, expected, assessed, score);

		scoreHistogram[score]++;
		const reliability = norm(r[col.reliability]) || '(none)';
		const rel = byReliability.get(reliability) ?? { count: 0, scoreSum: 0, successes: 0 };
		rel.count++; rel.scoreSum += score; rel.successes += score === 4 ? 1 : 0;
		byReliability.set(reliability, rel);
		byType.set(type, (byType.get(type) ?? 0) + 1);

		out.write(toCsvLine([...r, assessed.l1, assessed.l2, assessed.l3, assessed.product, type, String(score)]) + '\n');
		const successes = scoreHistogram[4];
		console.error(`[${n + 1}/${total}] score ${score} (${type})  ${successes}/${n + 1} full so far  ${name}`);
	}

	await new Promise((resolve) => out.end(resolve));
	printSummary(cfg.output, total, scoreHistogram, byReliability, byType);
}

async function postAssessment(url, token, body) {
	return request(url, {
		method: 'POST',
		headers: {
			'Authorization': `Bearer ${token}`,
			'Content-Type': 'application/json',
			'Accept': 'application/json',
			'Content-Length': Buffer.byteLength(body),
		},
		body,
	});
}

// Warns (only) when an assessed value differs from the expected one purely by case/whitespace -- a real
// scoring artifact to fix -- while leaving genuine disagreements to be read from the results CSV.
function reportArtifacts(name, expected, assessed, score) {
	const levels = ['l1', 'l2', 'l3', 'product'];
	// The level at which counting stopped is levels[score] (undefined when score === 4).
	const divergedAt = levels[score];
	if (divergedAt && looksLikeNormalizationArtifact(expected[divergedAt], assessed[divergedAt])) {
		console.error(`  WARNING: ${divergedAt} differs only by case/whitespace for "${name}": ` +
			`expected ${JSON.stringify(norm(expected[divergedAt]))} vs assessed ${JSON.stringify(norm(assessed[divergedAt]))}`);
	}
}

function printSummary(outputPath, total, scoreHistogram, byReliability, byType) {
	const successes = scoreHistogram[4];
	console.error('');
	console.error(`Wrote ${outputPath}`);
	console.error(`Products assessed: ${total}`);
	console.error(`Full matches (score 4): ${successes} (${pct(successes, total)})`);
	console.error('Score distribution (0=L1 wrong ... 4=archetype correct):');
	for (let s = 0; s <= 4; s++) {
		console.error(`  ${s}: ${scoreHistogram[s]} (${pct(scoreHistogram[s], total)})`);
	}
	console.error('By match reliability (mean score / archetype hit-rate):');
	for (const reliability of [...byReliability.keys()].sort()) {
		const r = byReliability.get(reliability);
		console.error(`  ${reliability}: n=${r.count}  mean=${(r.scoreSum / r.count).toFixed(2)}  full=${r.successes} (${pct(r.successes, r.count)})`);
	}
	console.error('By outcome type:');
	for (const type of [...byType.keys()].sort()) {
		console.error(`  ${type}: ${byType.get(type)}`);
	}
}

function pct(n, total) {
	return total ? `${(100 * n / total).toFixed(1)}%` : '-';
}

if (require.main === module) {
	main().catch((err) => {
		console.error(err.stack || err.message);
		process.exit(1);
	});
}

module.exports = { scoreRow, norm, looksLikeNormalizationArtifact, parseCsv, csvEscape, toCsvLine, extractDiagnostics };
