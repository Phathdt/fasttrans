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

// ---------------------------------------------------------------------------
// Envelope transform
//
// springdoc generates schemas from method return types and does NOT reflect
// the runtime ResponseBodyAdvice wrapping. We apply the transform here:
//
//   Success (2xx with JSON body)  → { data: <original>, meta: Meta }
//   Error  (4xx/5xx)              → ErrorResponse
//
// /auth/verify is ResponseEntity<Void> with no JSON body so it is naturally
// excluded (no content[*].schema to transform).
// ---------------------------------------------------------------------------

// Named success-envelope components, one per operation, collected while walking
// the paths and appended to components.schemas. Using a named component (rather
// than an inline object) makes orval emit a self-documenting type per endpoint —
// e.g. `ListAccountsEnvelope` instead of an anonymous `ListAccounts200`.
const generatedEnvelopeSchemas = {};

/** PascalCase an operationId: `listAccounts` → `ListAccounts`. */
function pascalCase(operationId) {
  return operationId.charAt(0).toUpperCase() + operationId.slice(1);
}

/**
 * Register a `${OperationId}Envelope` component wrapping the original 2xx schema
 * as `{ data, meta }` and return a $ref to it. Falls back to an inline wrapper
 * when the operation has no operationId to name the component after.
 */
function wrapInDataEnvelope(originalSchema, operationId) {
  const wrapper = {
    type: 'object',
    properties: {
      data: originalSchema,
      meta: { $ref: '#/components/schemas/Meta' },
    },
    required: ['data', 'meta'],
  };
  if (!operationId) return wrapper;

  const name = `${pascalCase(operationId)}Envelope`;
  generatedEnvelopeSchemas[name] = wrapper;
  return { $ref: `#/components/schemas/${name}` };
}

/** Standard error responses added to every operation. */
const errorResponses = {
  '400': {
    description: 'Bad Request',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
  '401': {
    description: 'Unauthorized',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
  '403': {
    description: 'Forbidden',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
  '404': {
    description: 'Not Found',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
  '500': {
    description: 'Internal Server Error',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
  '503': {
    description: 'Service Unavailable',
    content: { 'application/json': { schema: { $ref: '#/components/schemas/ErrorResponse' } } },
  },
};

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'options', 'head', 'trace'];

for (const pathItem of Object.values(mergedPaths)) {
  for (const method of HTTP_METHODS) {
    const operation = pathItem[method];
    if (!operation || typeof operation !== 'object' || !operation.responses) continue;

    for (const [statusCode, response] of Object.entries(operation.responses)) {
      const code = parseInt(statusCode, 10);

      // Wrap 2xx JSON bodies in { data, meta }
      if (code >= 200 && code < 300 && response.content) {
        for (const mediaObj of Object.values(response.content)) {
          if (mediaObj && mediaObj.schema) {
            mediaObj.schema = wrapInDataEnvelope(mediaObj.schema, operation.operationId);
          }
        }
      }
    }

    // Merge in standard error responses (do not overwrite explicit ones)
    operation.responses = { ...errorResponses, ...operation.responses };
  }
}

/** Envelope component schemas added to every merged spec. */
const envelopeSchemas = {
  Meta: {
    type: 'object',
    properties: {
      requestId: { type: 'string', description: 'Correlation id — equals the current traceId (fallback UUID)' },
      timestamp: { type: 'string', format: 'date-time', description: 'ISO-8601 UTC instant the response was built' },
    },
    required: ['requestId', 'timestamp'],
  },
  FieldError: {
    type: 'object',
    properties: {
      field:   { type: 'string', description: 'The request field that failed validation' },
      message: { type: 'string', description: 'The validation message for that field' },
    },
    required: ['field', 'message'],
  },
  ErrorBody: {
    type: 'object',
    properties: {
      code: {
        type: 'string',
        enum: [
          'VALIDATION_FAILED',
          'MISSING_HEADER',
          'UNAUTHORIZED',
          'ACCOUNT_NOT_FOUND',
          'TRANSFER_NOT_FOUND',
          'OWNERSHIP_DENIED',
          'ACCOUNT_UNAVAILABLE',
          'INTERNAL_ERROR',
        ],
        description: 'Machine-readable error code',
      },
      message:    { type: 'string', description: 'Human-readable error message' },
      details:    { type: 'array', items: { $ref: '#/components/schemas/FieldError' }, description: 'Validation failures; omitted when empty' },
      stackTrace: { type: 'string', description: 'Populated only when the stacktrace flag is enabled; omitted otherwise' },
    },
    required: ['code', 'message'],
  },
  ErrorResponse: {
    type: 'object',
    properties: {
      error: { $ref: '#/components/schemas/ErrorBody' },
      meta:  { $ref: '#/components/schemas/Meta' },
    },
    required: ['error', 'meta'],
  },
};

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
    schemas: {
      // Envelope schemas come first so $ref targets are defined before use
      ...envelopeSchemas,
      // Per-operation success envelopes (ListAccountsEnvelope, LoginEnvelope, ...)
      ...generatedEnvelopeSchemas,
      ...mergedSchemas,
    },
  },
};

const outDir = resolve(projectRoot, 'docs');
mkdirSync(outDir, { recursive: true });

const yamlOut = resolve(outDir, 'openapi.yaml');
writeFileSync(yamlOut, stringify(output, { lineWidth: 120 }), 'utf8');

console.log(`Written: ${yamlOut}`);
console.log(`Paths  : ${Object.keys(mergedPaths).join(', ')}`);
console.log(`Schemas: ${Object.keys(mergedSchemas).join(', ')}`);
