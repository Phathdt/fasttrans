#!/usr/bin/env node
/**
 * merge-openapi.mjs
 *
 * Merges two OpenAPI 3.x JSON specs (auth + transfer) into a single document,
 * prefixes every path with /api, and writes docs/openapi.yaml.
 *
 * Usage:
 *   node scripts/merge-openapi.mjs <auth-spec.json> <transfer-spec.json>
 *
 * The script is idempotent: running it multiple times overwrites the output.
 */

import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { stringify } from 'yaml';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '..');

function usage() {
  console.error('Usage: node scripts/merge-openapi.mjs <auth-spec.json> <transfer-spec.json>');
  process.exit(1);
}

const [authPath, transferPath] = process.argv.slice(2);
if (!authPath || !transferPath) usage();

const authSpec = JSON.parse(readFileSync(resolve(authPath), 'utf8'));
const transferSpec = JSON.parse(readFileSync(resolve(transferPath), 'utf8'));

/**
 * Prefix every top-level path key with /api.
 * Idempotent: already-prefixed keys are skipped.
 */
function prefixPaths(paths) {
  const result = {};
  for (const [key, value] of Object.entries(paths ?? {})) {
    const newKey = key.startsWith('/api') ? key : `/api${key}`;
    result[newKey] = value;
  }
  return result;
}

// Merge paths and component schemas from both specs.
const mergedPaths = {
  ...prefixPaths(authSpec.paths),
  ...prefixPaths(transferSpec.paths),
};

const mergedSchemas = {
  ...(authSpec.components?.schemas ?? {}),
  ...(transferSpec.components?.schemas ?? {}),
};

const mergedTags = [
  ...(authSpec.tags ?? []),
  ...(transferSpec.tags ?? []),
];

const output = {
  openapi: '3.0.1',
  info: {
    title: 'FastTrans API',
    version: '1.0.0',
  },
  servers: [{ url: '/' }],
  tags: mergedTags,
  paths: mergedPaths,
  components: {
    schemas: mergedSchemas,
  },
};

const outDir = resolve(projectRoot, 'docs');
mkdirSync(outDir, { recursive: true });

const yamlOut = resolve(outDir, 'openapi.yaml');
writeFileSync(yamlOut, stringify(output, { lineWidth: 120 }), 'utf8');

console.log(`Written: ${yamlOut}`);
console.log(`Paths  : ${Object.keys(mergedPaths).join(', ')}`);
console.log(`Schemas: ${Object.keys(mergedSchemas).join(', ')}`);
