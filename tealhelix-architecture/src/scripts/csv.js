// CSV (RFC 4180) parsing and writing for the scripts of this directory.
// Requires only NodeJS (no external dependencies).

'use strict';

// Parses RFC 4180 CSV text (quoted fields may contain commas, quotes and newlines) into rows of fields.
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

module.exports = { parseCsv, csvEscape, toCsvLine };
