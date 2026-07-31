#!/usr/bin/env node

// Use this script to create a handoff ticket - the ticket that gives access to the howibuy-front directly from a retailer.
// First authenticate as service and obtain the access token:
//  > SVC_ACCESS_TOKEN=`./keycloak-auth-service.js -u the_client -p secret`
// Call this script to print the ticket:
// 	> ./handover.js -c ABCD -a $SVC_ACCESS_TOKEN`
// The previous command has printed the ticket; use it to direct the browser to:
//  http://localhost:5175/#ticket=<the-ticket>
//
// This script requires only NodeJS (no external dependencies).

'use strict';

const http = require('node:http');
const https = require('node:https');

let howibuyUrl = 'http://localhost:8180/api/howibuy/v1';
let accessToken;
let correlationId = '';
let printTicketResponse = false;
let frontendUrl = '';

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
	const arg = argv[i];
	switch (arg) {
		case '-s':
		case '--server-url':
			howibuyUrl = argv[++i];
			break;
		case '-a':
		case '--access-token':
			accessToken = argv[++i];
			break;
		case '-c':
		case '--correlation-id':
			correlationId = argv[++i];
			break;
		case '--print-ticket-response':
			printTicketResponse = true;
			break;
		case '-f':
		case '--frontend-url':
			frontendUrl = argv[++i];
			break;
		default:
			console.error(`Unknown argument: ${arg}`);
			process.exit(2);
	}
}

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

async function main() {
	const body = JSON.stringify({ correlationId });

	const response = await request(`${howibuyUrl}/handoff`, {
		method: 'POST',
		headers: {
			'Authorization': `Bearer ${accessToken}`,
			'Content-Type': 'application/json',
			'Content-Length': Buffer.byteLength(body),
		},
		body,
	});

	if (printTicketResponse) {
		console.log(response.body);
		console.log();
	}

	let ticketResponse;
	try {
		ticketResponse = JSON.parse(response.body);
	} catch {
		console.error(`Unexpected response (HTTP ${response.status}): ${response.body}`);
		process.exit(1);
	}

	const newTicket = ticketResponse.ticket;
	if (!newTicket) {
		console.error(`No access token in response: ${response.body}`);
		process.exit(1);
	}

	if (frontendUrl) {
		console.log(`${frontendUrl}#ticket=${newTicket}`)
	} else {
		console.log(newTicket);
	}
}

main().catch((err) => {
	console.error(err.message);
	process.exit(1);
});
