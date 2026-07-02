#!/usr/bin/env node

// Use this script to easily authenticate with client credentials to Keycloak. Example usage:
// > SVC_ACCESS_TOKEN=`./keycloak-auth-service.js -u the_client -p secret`
// > curl -iS -X GET -H "Authorization: Bearer $SVC_ACCESS_TOKEN" -H "Accepts: text/html" http://localhost:8180/api/howibuy/v1/greeting
// Or:
// > curl -iS -X POST -H "Authorization: Bearer $SVC_ACCESS_TOKEN" -H "Content-Type: application/json" -d '{"correlationId": "abc"}' http://localhost:8180/api/howibuy/v1/tokenexchange
//
// This script requires only NodeJS (no external dependencies).

'use strict';

const http = require('node:http');
const https = require('node:https');

const REALM = 'tealhelix';

let keycloakUrl = 'http://localhost:8280';
let clientId;
let password;
let decoded = false;
let printTokenResponse = false;

const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
	const arg = argv[i];
	switch (arg) {
		case '-i':
		case '--idm':
			keycloakUrl = argv[++i];
			break;
		case '-u':
		case '--client':
		case '--client-id':
			clientId = argv[++i];
			break;
		case '-p':
		case '--password':
			password = argv[++i];
			break;
		case '--decoded':
			decoded = true;
			break;
		case '--print-token-response':
			printTokenResponse = true;
			break;
		default:
			console.error(`Unknown argument: ${arg}`);
			process.exit(2);
	}
}

if (!clientId) {
	console.error('Client ID (-u|--client|--client-id) is required');
	process.exit(1);
}
if (!password) {
	console.error('Password/client secret (-p|--password) is required');
	process.exit(1);
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

function decode(token) {
	const payload = token.split('.')[1];
	return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
}

async function main() {
	const body = new URLSearchParams({
		grant_type: 'client_credentials',
		client_id: clientId,
		client_secret: password,
	}).toString();

	const response = await request(`${keycloakUrl}/realms/${REALM}/protocol/openid-connect/token`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/x-www-form-urlencoded',
			'Content-Length': Buffer.byteLength(body),
		},
		body,
	});

	let tokenResponse;
	try {
		tokenResponse = JSON.parse(response.body);
	} catch {
		console.error(`Unexpected response (HTTP ${response.status}): ${response.body}`);
		process.exit(1);
	}

	if (printTokenResponse) {
		console.log(JSON.stringify(tokenResponse, null, 2));
		console.log();
	}

	const accessToken = tokenResponse.access_token;
	if (!accessToken) {
		console.error(`No access token in response: ${response.body}`);
		process.exit(1);
	}

	console.log(accessToken);

	if (decoded) {
		process.stdout.write('\n\nDecoded Access Token:\n');
		console.log(JSON.stringify(decode(accessToken), null, 2));
	}
}

main().catch((err) => {
	console.error(err.message);
	process.exit(1);
});
