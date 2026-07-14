#!/usr/bin/env node

// Tests for the pure scoring logic of assessment-eval.js. Run with:
//   node --test assessment-eval.test.js
// Requires only NodeJS (node:test, node:assert), no external dependencies.

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { scoreRow, looksLikeNormalizationArtifact } = require('./assessment-eval.js');

const EXPECTED = {
	l1: 'Milk and dairy products',
	l2: 'Cheese',
	l3: 'Cheese, Manchego',
	product: "Semi-hard cheese, from ewe's milk",
};

test('all four levels match scores 4', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED }), 4);
});

test('product differs scores 3', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED, product: 'Cheese, from cow milk' }), 3);
});

test('L3 differs scores 2', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED, l3: 'Cheese, Feta', product: 'Feta cheese' }), 2);
});

test('L2 differs scores 1', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED, l2: 'Cream and cream products' }), 1);
});

test('L1 differs scores 0', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED, l1: 'Meat and meat products' }), 0);
});

test('counting stops at the first mismatch: an L3 that coincides after an L2 miss does NOT count', () => {
	// L2 differs, but the assessed L3 and product happen to equal the expected ones
	// (same L3 name living under a different L2). Score must be 1, not 3.
	const assessed = { ...EXPECTED, l2: 'Cream and cream products' };
	assert.equal(scoreRow(EXPECTED, assessed), 1);
});

test('a partial diagnostics (nulls after a failure) caps the score at the divergence depth', () => {
	// The pipeline gave up after L2: L3/product come back null.
	const assessed = { l1: EXPECTED.l1, l2: EXPECTED.l2, l3: null, product: null };
	assert.equal(scoreRow(EXPECTED, assessed), 2);
});

test('surrounding whitespace is ignored when comparing', () => {
	const assessed = { l1: '  Milk and dairy products ', l2: 'Cheese', l3: 'Cheese, Manchego\t', product: "Semi-hard cheese, from ewe's milk" };
	assert.equal(scoreRow(EXPECTED, assessed), 4);
});

test('comparison is case-sensitive: a case-only difference is a mismatch', () => {
	assert.equal(scoreRow(EXPECTED, { ...EXPECTED, l2: 'cheese' }), 1);
});

test('normalization-artifact detector flags case/whitespace-only differences', () => {
	assert.equal(looksLikeNormalizationArtifact('Cheese', 'cheese'), true);
	assert.equal(looksLikeNormalizationArtifact('Cheese,  Manchego', 'Cheese, Manchego'), true);
	assert.equal(looksLikeNormalizationArtifact('Cheese', 'Feta'), false);
});
