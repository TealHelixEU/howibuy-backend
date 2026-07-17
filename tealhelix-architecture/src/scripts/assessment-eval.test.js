#!/usr/bin/env node

// Tests for the pure scoring logic of assessment-eval.js. Run with:
//   node --test assessment-eval.test.js
// Requires only NodeJS (node:test, node:assert), no external dependencies.

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { scoreRow, looksLikeNormalizationArtifact, nutriScoreGrade, identityConflict, missSeverity } = require('./assessment-eval.js');

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

test('nutriScoreGrade extracts the A-E grade and treats 0/blank/unknown as no grade', () => {
	assert.equal(nutriScoreGrade('Nutriscore_A'), 'A');
	assert.equal(nutriScoreGrade('Nutriscore_E'), 'E');
	assert.equal(nutriScoreGrade('  Nutriscore_C  '), 'C');
	assert.equal(nutriScoreGrade('0'), null);
	assert.equal(nutriScoreGrade(''), null);
	assert.equal(nutriScoreGrade(null), null);
	assert.equal(nutriScoreGrade('Nutriscore_F'), null);
});

test('identityConflict flags a species swap or a raw<->cooked flip, but not a mere variety change', () => {
	// animal / milk-species disagreement between the two archetypes
	assert.equal(identityConflict("Cheese, from goat's milk", "Feta-type cheese from cow's milk"), true);
	assert.equal(identityConflict("Semi-hard cheese, from ewe's milk", "Semi-hard cheese, from cow's milk"), true);
	// same species, different named variety -> not an identity conflict (a grade shift, at most)
	assert.equal(identityConflict("Semi-hard cheese, from ewe's milk", "Ossau-Iraty cheese, from ewe's milk"), false);
	// raw classified as a cooked preparation (and vice versa)
	assert.equal(identityConflict('Beef, round, raw', 'Beef, roast beef, roasted/baked'), true);
	assert.equal(identityConflict('Pork, chop, raw', 'Pork, chop, grilled'), true);
	// both raw, different cut -> a leaf error, not a prep conflict
	assert.equal(identityConflict('Beef, round, raw', 'Beef, shoulder, raw'), false);
	// neither side carries a species or prep token -> no conflict
	assert.equal(identityConflict('Biscuit (cookie), plain', 'Biscuit (cookie), reduced sugar'), false);
});

test('missSeverity: an exact archetype match (score 4) is not a miss', () => {
	assert.equal(missSeverity(4, 'A', 'A', 'X', 'X'), '');
});

test('missSeverity: a wrong L1 or L2 is a category miss -- the recommendation pool itself is wrong', () => {
	assert.equal(missSeverity(0, 'E', null, "Feta cheese, from ewe's milk", 'Wine, red'), 'category');
	assert.equal(missSeverity(1, 'E', 'B', "Feta cheese, from ewe's milk", 'Milk, semi-skimmed, UHT'), 'category');
});

test('missSeverity: a species/prep conflict is an identity miss even when the Nutri-Score grade matches', () => {
	// line 13: goat-ewe cheese classified as cow feta, both grade E -> identity, NOT cosmetic
	assert.equal(missSeverity(2, 'E', 'E', "Semi-hard cheese, from ewe's milk", "Feta-type cheese from cow's milk"), 'identity');
	assert.equal(missSeverity(3, 'A', 'A', 'Beef, round, raw', 'Beef, roast beef, roasted/baked'), 'identity');
});

test('missSeverity: within the right pool, a grade change is grade-shift and a matching grade is cosmetic', () => {
	// same species, grade moves E->D (the Ossau-Iraty attractor): the displayed score would move
	assert.equal(missSeverity(3, 'E', 'D', "Semi-hard cheese, from ewe's milk", "Ossau-Iraty cheese, from ewe's milk"), 'grade-shift');
	// a missing grade cannot vouch for the displayed score -> treat as a shift
	assert.equal(missSeverity(3, 'C', null, 'X', 'Y'), 'grade-shift');
	// same grade, no identity conflict -> near-equivalent output
	assert.equal(missSeverity(3, 'C', 'C', "Emmental cheese, from cow's milk", "Emmental cheese, grated, from cow's milk"), 'cosmetic');
	assert.equal(missSeverity(2, 'E', 'E', 'Butter oil or concentrated butter', 'Butter, 80% fat, lightly salted'), 'cosmetic');
});
