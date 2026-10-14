#!/usr/bin/env node
/**
 * Bundles each test file for Node and runs them with the built-in test runner.
 *
 * Bundled rather than run as TypeScript directly because the source imports without extensions and
 * from `cloudflare:workers`, neither of which Node resolves. The shim stands in for the second; the
 * real Durable Object classes, SQL and route handlers run unchanged against an in-memory SQLite
 * (see harness.ts).
 *
 * Run it with `npm test`.
 */
import { build } from 'esbuild';
import { readdirSync, rmSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', '.test-build');
rmSync(out, { recursive: true, force: true });

const entries = readdirSync(here).filter((f) => f.endsWith('.test.ts')).map((f) => join(here, f));

await build({
  entryPoints: entries,
  outdir: out,
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node22',
  outExtension: { '.js': '.mjs' },
  loader: { '.txt': 'text', '.sql': 'text' },
  logLevel: 'warning',
  plugins: [{
    name: 'cloudflare-shim',
    setup(b) {
      b.onResolve({ filter: /^cloudflare:(workers|email)$/ }, (args) => ({
        path: join(here, 'shims', `cloudflare-${args.path.slice('cloudflare:'.length)}.ts`),
      }));
    },
  }],
});

const files = readdirSync(out).filter((f) => f.endsWith('.mjs')).map((f) => join(out, f));
const result = spawnSync(process.execPath, ['--no-warnings', '--test', '--test-reporter=spec', ...files], { stdio: 'inherit' });
process.exit(result.status ?? 1);
