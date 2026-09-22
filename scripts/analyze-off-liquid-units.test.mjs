import test from 'node:test';
import assert from 'node:assert/strict';
import {classifyOffLiquidUnit} from './analyze-off-liquid-units.mjs';

test('beverage category plus 100ml is source-supported', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:beverages,en:milk-drinks', nutritionDataPer: '100ml'}), 'ML_SOURCE_SUPPORTED');
});

test('beverage category plus 100g requires a mass-volume bridge', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:beverages', nutritionDataPer: '100g'}), 'MASS_VOLUME_BRIDGE_REQUIRED');
});

test('beverage category without a source basis enters a focused evidence queue', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:beverages,en:waters'}), 'LIQUID_CATEGORY_SOURCE_BASIS_REQUIRED');
});

test('powder conflict overrides parent beverage category', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:beverages,en:drink-powders', nutritionDataPer: '100ml'}), 'FORM_CONFLICT_REVIEW');
});

test('umbrella beverage group does not veto a concrete beverage category', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:beverages-and-beverages-preparations,en:beverages'}), 'LIQUID_CATEGORY_SOURCE_BASIS_REQUIRED');
});

test('coffee food category alone does not classify beans or powder as liquid', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:coffees'}), 'NOT_LIQUID_BY_CATEGORY');
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:coffee-drinks'}), 'LIQUID_CATEGORY_SOURCE_BASIS_REQUIRED');
});

test('water in a non-liquid product name is irrelevant because names are not inputs', () => {
  assert.equal(classifyOffLiquidUnit({categoriesTags: 'en:rice-dishes', nutritionDataPer: '100ml'}), 'NOT_LIQUID_BY_CATEGORY');
});
