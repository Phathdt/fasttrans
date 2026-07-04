#!/usr/bin/env node
/**
 * merge-openapi.mjs
 *
 * Merges three OpenAPI 3.x JSON specs (auth + transfer + account) into a single document,
 * and writes docs/openapi.yaml. Paths are kept as-is (no /api prefix).
 *
 * Usage:
 *   node scripts/merge-openapi.mjs <auth-spec.json> <transfer-spec.json> <account-spec.json>
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
  console.error('Usage: node scripts/merge-openapi.mjs <auth-spec.json> <transfer-spec.json> <account-spec.json>');
  process.exit(1);
}

const [authPath, transferPath, accountPath] = process.argv.slice(2);
if (!authPath || !transferPath || !accountPath) usage();

const authSpec = JSON.parse(readFileSync(resolve(authPath), 'utf8'));
const transferSpec = JSON.parse(readFileSync(resolve(transferPath), 'utf8'));
const accountSpec = JSON.parse(readFileSync(resolve(accountPath), 'utf8'));

// Merge paths and component schemas from all three specs.
// Paths are used as-is — the FE calls Traefik directly (no /api prefix).
const mergedPaths = {
  ...(authSpec.paths ?? {}),
  ...(transferSpec.paths ?? {}),
  ...(accountSpec.paths ?? {}),
};

const mergedSchemas = {
  ...(authSpec.components?.schemas ?? {}),
  ...(transferSpec.components?.schemas ?? {}),
  ...(accountSpec.components?.schemas ?? {}),
};

const mergedTags = [
  ...(authSpec.tags ?? []),
  ...(transferSpec.tags ?? []),
  ...(accountSpec.tags ?? []),
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
