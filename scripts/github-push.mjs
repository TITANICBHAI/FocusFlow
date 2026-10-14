#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readdir, readFile, stat } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const OWNER = 'TITANICBHAI';
const REPO = 'FocusFlow';
const API_ROOT = 'https://api.github.com';
const BASE = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const BRANCH = process.env.GITHUB_SYNC_BRANCH || 'main';
const MAX_RETRIES = 6;
const REQUEST_TIMEOUT_MS = 120_000;
const MAX_FILE_SIZE = 50 * 1024 * 1024;
const BLOB_CONCURRENCY = 3;
const TREE_CHUNK_SIZE = 100;

const EXCLUDED_DIRECTORIES = new Set([
  '.git',
  '.agents',
  '.local',
  '.cache',
  '.expo',
  '.gradle',
  '.kotlin',
  '.conversation',
  '__pycache__',
  '.pytest_cache',
  'node_modules',
  'attached_assets',
  'build',
  'dist',
  'out',
  'tmp',
  'lib',
]);

const EXCLUDED_FILES = new Set([
  'pnpm-workspace.yaml',
  'tsconfig.base.json',
  '.npmrc',
  '.replitignore',
  'scripts/src/hello.ts',
  'local.properties',
  'secrets.properties',
]);

const EXCLUDED_EXTENSIONS = new Set([
  '.apk',
  '.aab',
  '.class',
  '.dex',
  '.jks',
  '.keystore',
  '.key',
  '.log',
  '.p12',
  '.pem',
  '.tsbuildinfo',
]);

function printUsage() {
  console.log(`Usage: node scripts/github-push.mjs [--dry-run]

Sync the eligible workspace files to ${OWNER}/${REPO} on the configured branch.
Remote-only files are preserved unless GITHUB_SYNC_DELETE_MISSING=true is set.
Set GITHUB_SYNC_COMMIT_MESSAGE to override the generated commit message.`);
}

function isEnabled(value) {
  return value === '1' || value?.toLowerCase() === 'true';
}

function validateOptions(args) {
  if (args.includes('--help') || args.includes('-h')) {
    printUsage();
    process.exit(0);
  }

  if (args.some((arg) => arg !== '--dry-run')) {
    throw new Error(`Unknown argument: ${args.find((arg) => arg !== '--dry-run')}`);
  }

  if (!/^[A-Za-z0-9._/-]+$/.test(BRANCH) || BRANCH.includes('..') || BRANCH.endsWith('/')) {
    throw new Error('GITHUB_SYNC_BRANCH is not a valid branch name.');
  }
}

function shouldExclude(relativePath) {
  const normalized = relativePath.split(sep).join('/');
  const segments = normalized.split('/');
  const fileName = segments.at(-1) ?? '';
  const extension = fileName.slice(fileName.lastIndexOf('.')).toLowerCase();

  if (EXCLUDED_FILES.has(normalized) || EXCLUDED_FILES.has(fileName)) return true;
  if (segments.some((segment) => EXCLUDED_DIRECTORIES.has(segment))) return true;
  if (segments.some((segment) => segment === '.env' || segment.startsWith('.env.'))) return true;
  if (fileName === '.DS_Store' || fileName === 'Thumbs.db') return true;
  if (fileName === 'credentials.json' || fileName.endsWith('.credentials.json')) return true;
  return EXCLUDED_EXTENSIONS.has(extension);
}

async function collectFiles(directory = BASE, files = []) {
  const entries = await readdir(directory, { withFileTypes: true });
  entries.sort((a, b) => a.name.localeCompare(b.name));

  for (const entry of entries) {
    const fullPath = resolve(directory, entry.name);
    const relativePath = relative(BASE, fullPath);
    if (shouldExclude(relativePath)) continue;

    if (entry.isDirectory()) {
      await collectFiles(fullPath, files);
    } else if (entry.isFile()) {
      const fileStat = await stat(fullPath);
      if (fileStat.size > MAX_FILE_SIZE) {
        throw new Error(
          `Refusing to upload ${relativePath}: ${fileStat.size} bytes exceeds the 50 MiB per-file limit.`,
        );
      }
      files.push({
        path: relativePath.split(sep).join('/'),
        fullPath,
        mode: (fileStat.mode & 0o111) !== 0 ? '100755' : '100644',
        size: fileStat.size,
      });
    }
  }
  return files;
}

function gitBlobSha(buffer) {
  return createHash('sha1')
    .update(`blob ${buffer.length}\0`)
    .update(buffer)
    .digest('hex');
}

const sleep = (milliseconds) => new Promise((resolveSleep) => setTimeout(resolveSleep, milliseconds));

async function githubRequest(path, method = 'GET', body = undefined) {
  const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
  if (!token) {
    throw new Error('GITHUB_PERSONAL_ACCESS_TOKEN is not available to this workflow.');
  }

  for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
    let response;
    let responseText = '';

    try {
      response = await fetch(`${API_ROOT}${path}`, {
        method,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
          'User-Agent': 'FocusFlow-Replit-GitHub-Sync',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
      responseText = await response.text();
    } catch (error) {
      if (attempt < MAX_RETRIES) {
        const delay = Math.min(60_000, 1_000 * 2 ** attempt);
        console.warn(`Network error contacting GitHub; retrying in ${Math.ceil(delay / 1000)}s.`);
        await sleep(delay);
        continue;
      }
      throw new Error(`GitHub request failed after retries: ${error.message}`);
    }

    if (response.ok) {
      if (!responseText) return {};
      try {
        return JSON.parse(responseText);
      } catch {
        throw new Error(`GitHub returned invalid JSON for ${method} ${path}.`);
      }
    }

    const rateLimited =
      response.status === 429 ||
      (response.status === 403 && /rate limit|secondary limit|abuse/i.test(responseText));
    const temporaryFailure = [408, 502, 503, 504].includes(response.status);
    if ((rateLimited || temporaryFailure) && attempt < MAX_RETRIES) {
      const retryAfter = Number.parseInt(response.headers.get('retry-after') ?? '', 10);
      const delay = Number.isFinite(retryAfter) && retryAfter > 0
        ? retryAfter * 1000
        : Math.min(60_000, 1_000 * 2 ** attempt);
      console.warn(`GitHub returned HTTP ${response.status}; retrying in ${Math.ceil(delay / 1000)}s.`);
      await sleep(delay);
      continue;
    }

    let detail = '';
    try {
      const parsed = JSON.parse(responseText);
      if (typeof parsed.message === 'string') detail = ` ${parsed.message}`;
    } catch {
      // Keep the status and endpoint useful even if GitHub returns a non-JSON error.
    }
    if (response.status === 401) detail += ' Check that the configured GitHub token is valid.';
    if (response.status === 403) detail += ` Confirm it has Contents read/write access to ${OWNER}/${REPO}.`;
    if (response.status === 404) detail += ` Confirm ${OWNER}/${REPO} and branch "${BRANCH}" exist and are accessible.`;
    throw new Error(`GitHub ${method} ${path} returned HTTP ${response.status}.${detail}`);
  }

  throw new Error(`GitHub request exhausted retries: ${method} ${path}`);
}

async function readRemoteTree(treeSha) {
  const result = await githubRequest(`/repos/${OWNER}/${REPO}/git/trees/${treeSha}?recursive=1`);
  if (result.truncated) {
    throw new Error('GitHub returned a truncated tree; refusing to sync from incomplete remote state.');
  }
  return (result.tree ?? []).filter((entry) => entry.type === 'blob');
}

async function uploadBlob(file, expectedSha) {
  const buffer = await readFile(file.fullPath);
  const actualSha = gitBlobSha(buffer);
  if (actualSha !== expectedSha) {
    throw new Error(`${file.path} changed while the push was running; rerun the push to sync a consistent snapshot.`);
  }

  const result = await githubRequest(`/repos/${OWNER}/${REPO}/git/blobs`, 'POST', {
    content: buffer.toString('base64'),
    encoding: 'base64',
  });
  return { path: file.path, mode: file.mode, type: 'blob', sha: result.sha };
}

async function uploadChangedFiles(files, concurrency) {
  const treeEntries = [];
  for (let offset = 0; offset < files.length; offset += concurrency) {
    const batch = files.slice(offset, offset + concurrency);
    const uploaded = await Promise.all(batch.map((file) => uploadBlob(file, file.gitSha)));
    treeEntries.push(...uploaded);
    console.log(`  Uploaded ${Math.min(offset + batch.length, files.length)}/${files.length} changed files.`);
  }
  return treeEntries;
}

async function createCommitAndUpdateRef(treeEntries, baseTreeSha, parentSha, encodedBranch) {
  let treeSha = baseTreeSha;
  for (let offset = 0; offset < treeEntries.length; offset += TREE_CHUNK_SIZE) {
    const treeResult = await githubRequest(`/repos/${OWNER}/${REPO}/git/trees`, 'POST', {
      base_tree: treeSha,
      tree: treeEntries.slice(offset, offset + TREE_CHUNK_SIZE),
    });
    treeSha = treeResult.sha;
    console.log(`  Prepared ${Math.min(offset + TREE_CHUNK_SIZE, treeEntries.length)}/${treeEntries.length} tree changes.`);
  }

  const timestamp = new Date().toISOString();
  const message = process.env.GITHUB_SYNC_COMMIT_MESSAGE || `Sync FocusFlow workspace — ${timestamp}`;
  const commit = await githubRequest(`/repos/${OWNER}/${REPO}/git/commits`, 'POST', {
    message,
    tree: treeSha,
    parents: [parentSha],
  });

  if (commit.parents?.[0]?.sha !== parentSha) {
    throw new Error('GitHub created a commit with an unexpected parent; the branch was not updated.');
  }

  await githubRequest(`/repos/${OWNER}/${REPO}/git/refs/heads/${encodedBranch}`, 'PATCH', {
    sha: commit.sha,
    force: false,
  });

  const verifiedRef = await githubRequest(`/repos/${OWNER}/${REPO}/git/ref/heads/${encodedBranch}`);
  if (verifiedRef.object?.sha !== commit.sha) {
    throw new Error('GitHub branch verification failed after the update.');
  }
  return { commitSha: commit.sha, treeSha };
}

async function run(args) {
  validateOptions(args);
  if (!process.env.GITHUB_PERSONAL_ACCESS_TOKEN) {
    throw new Error('GITHUB_PERSONAL_ACCESS_TOKEN is not available to this workflow.');
  }

  const dryRun = args.includes('--dry-run') || isEnabled(process.env.GITHUB_SYNC_DRY_RUN);
  const deleteMissing = isEnabled(process.env.GITHUB_SYNC_DELETE_MISSING);
  const encodedBranch = encodeURIComponent(BRANCH);

  console.log(`== FocusFlow GitHub sync ==`);
  console.log(`Repository: https://github.com/${OWNER}/${REPO}`);
  console.log(`Branch:     ${BRANCH}`);
  console.log(`Mode:       ${dryRun ? 'dry run (no writes)' : 'push'}`);

  const ref = await githubRequest(`/repos/${OWNER}/${REPO}/git/ref/heads/${encodedBranch}`);
  const parentSha = ref.object?.sha;
  if (!parentSha) throw new Error('GitHub returned no commit for the target branch.');

  const parentCommit = await githubRequest(`/repos/${OWNER}/${REPO}/git/commits/${parentSha}`);
  const baseTreeSha = parentCommit.tree?.sha;
  if (!baseTreeSha) throw new Error('GitHub returned no base tree for the target branch.');

  const remoteTree = await readRemoteTree(baseTreeSha);
  const remoteByPath = new Map(remoteTree.map((entry) => [entry.path, entry]));

  console.log('Scanning eligible workspace files...');
  const files = await collectFiles();
  for (const file of files) {
    const buffer = await readFile(file.fullPath);
    file.gitSha = gitBlobSha(buffer);
  }
  const localByPath = new Map(files.map((file) => [file.path, file]));
  const changedFiles = files.filter((file) => {
    const remote = remoteByPath.get(file.path);
    return !remote || remote.sha !== file.gitSha || remote.mode !== file.mode;
  });

  const deletionEntries = deleteMissing
    ? remoteTree
        .filter((entry) => !localByPath.has(entry.path) && !shouldExclude(entry.path))
        .map((entry) => ({ path: entry.path, mode: entry.mode ?? '100644', type: 'blob', sha: null }))
    : [];

  console.log(`Scanned ${files.length} eligible files; ${changedFiles.length} additions/updates found.`);
  console.log(`Remote-only files to remove: ${deletionEntries.length}.`);
  if (deleteMissing) {
    console.log('Remote-only file deletion is enabled.');
  } else {
    console.log('Remote-only files will be preserved.');
  }

  if (changedFiles.length > 0) {
    console.log('Planned additions/updates (first 40):');
    for (const file of changedFiles.slice(0, 40)) {
      console.log(`  ${remoteByPath.has(file.path) ? 'UPDATE' : 'ADD'} ${file.path}`);
    }
    if (changedFiles.length > 40) console.log(`  ... and ${changedFiles.length - 40} more.`);
  }
  if (deletionEntries.length > 0) {
    console.log('Planned deletions (first 20):');
    for (const entry of deletionEntries.slice(0, 20)) console.log(`  DELETE ${entry.path}`);
    if (deletionEntries.length > 20) console.log(`  ... and ${deletionEntries.length - 20} more.`);
  }

  if (dryRun) {
    console.log('DRY RUN complete; no GitHub writes were made.');
    return;
  }

  if (changedFiles.length === 0 && deletionEntries.length === 0) {
    console.log('GitHub already matches the eligible workspace files; no commit needed.');
    return;
  }

  const uploadedEntries = await uploadChangedFiles(changedFiles, BLOB_CONCURRENCY);
  const treeEntries = [...uploadedEntries, ...deletionEntries].sort((a, b) => a.path.localeCompare(b.path));
  const { commitSha } = await createCommitAndUpdateRef(treeEntries, baseTreeSha, parentSha, encodedBranch);

  console.log('\nPush completed and verified without force.');
  console.log(`Commit: ${commitSha}`);
  console.log(`Parent: ${parentSha}`);
  console.log(`Repo:   https://github.com/${OWNER}/${REPO}`);
}

run(process.argv.slice(2)).catch((error) => {
  console.error(`ERROR: ${error.message}`);
  process.exitCode = 1;
});
