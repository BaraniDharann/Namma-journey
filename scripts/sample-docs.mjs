#!/usr/bin/env node
// Locks the real identity documents in "sample document/" (Aadhaar, PAN, ...) used as local test
// fixtures. Everything stays on this machine; nothing is uploaded.
//
//   node scripts/sample-docs.mjs encrypt          encrypt every plain file, then delete the original
//   node scripts/sample-docs.mjs check            decrypt each .enc in memory and list it
//   node scripts/sample-docs.mjs decrypt <dir>    write decrypted copies to <dir> (delete them after!)
//
// Password: SAMPLE_DOCS_KEY in the environment or in .env (at least 12 characters). Lose it and the
// files cannot be recovered - keep it in your password manager.
//
// File format (read by SampleDocuments.java in the tests):
//   "NJDOC1" | salt (16) | iv (12) | AES-256-GCM ciphertext | tag (16)
//   key = PBKDF2-HMAC-SHA256(password, salt, 600000 rounds, 32 bytes)

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIR = process.env.SAMPLE_DOCS_DIR || path.join(ROOT, 'sample document');
const MAGIC = Buffer.from('NJDOC1');
const ROUNDS = 600_000;

function password() {
  let key = process.env.SAMPLE_DOCS_KEY;
  if (!key) {
    const envFile = path.join(ROOT, '.env');
    if (fs.existsSync(envFile)) {
      const line = fs.readFileSync(envFile, 'utf8').split(/\r?\n/).find((l) => l.startsWith('SAMPLE_DOCS_KEY='));
      if (line) key = line.slice('SAMPLE_DOCS_KEY='.length).trim().replace(/^["']|["']$/g, '');
    }
  }
  if (!key || key.length < 12) {
    fail('Set SAMPLE_DOCS_KEY (12+ characters) in your environment or in .env first.');
  }
  return key;
}

function encrypt(plain, pw) {
  const salt = crypto.randomBytes(16);
  const iv = crypto.randomBytes(12);
  const key = crypto.pbkdf2Sync(pw, salt, ROUNDS, 32, 'sha256');
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const body = Buffer.concat([cipher.update(plain), cipher.final()]);
  return Buffer.concat([MAGIC, salt, iv, body, cipher.getAuthTag()]);
}

function decrypt(blob, pw) {
  if (!blob.subarray(0, MAGIC.length).equals(MAGIC)) throw new Error('not a sample-docs file');
  const salt = blob.subarray(6, 22);
  const iv = blob.subarray(22, 34);
  const tag = blob.subarray(blob.length - 16);
  const body = blob.subarray(34, blob.length - 16);
  const key = crypto.pbkdf2Sync(pw, salt, ROUNDS, 32, 'sha256');
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]);
}

function files() {
  if (!fs.existsSync(DIR)) fail(`Folder not found: ${DIR}`);
  return fs.readdirSync(DIR).filter((f) => fs.statSync(path.join(DIR, f)).isFile());
}

function fail(msg) {
  console.error(msg);
  process.exit(1);
}

const [cmd, arg] = process.argv.slice(2);
const pw = password();

if (cmd === 'encrypt') {
  const plain = files().filter((f) => !f.endsWith('.enc'));
  if (plain.length === 0) fail(`Nothing to encrypt in ${DIR}`);
  for (const f of plain) {
    const src = path.join(DIR, f);
    const data = fs.readFileSync(src);
    const blob = encrypt(data, pw);
    // Prove it opens again before the original goes.
    if (!decrypt(blob, pw).equals(data)) fail(`Round-trip failed for ${f}; original kept.`);
    fs.writeFileSync(src + '.enc', blob);
    // Overwrite, then delete. (On an SSD the old blocks may survive; full-disk BitLocker covers that.)
    fs.writeFileSync(src, crypto.randomBytes(data.length));
    fs.unlinkSync(src);
    console.log(`encrypted ${f} -> ${f}.enc`);
  }
} else if (cmd === 'check') {
  const enc = files().filter((f) => f.endsWith('.enc'));
  if (enc.length === 0) fail(`No .enc files in ${DIR}`);
  for (const f of enc) {
    const data = decrypt(fs.readFileSync(path.join(DIR, f)), pw);
    console.log(`ok  ${f}  (${data.length} bytes)`);
  }
} else if (cmd === 'decrypt') {
  if (!arg) fail('Usage: node scripts/sample-docs.mjs decrypt <output-folder>');
  fs.mkdirSync(arg, { recursive: true });
  for (const f of files().filter((x) => x.endsWith('.enc'))) {
    fs.writeFileSync(path.join(arg, f.slice(0, -4)), decrypt(fs.readFileSync(path.join(DIR, f)), pw));
    console.log(`decrypted ${f} -> ${path.join(arg, f.slice(0, -4))}`);
  }
  console.log('Delete these copies when you are done.');
} else {
  fail('Usage: node scripts/sample-docs.mjs encrypt | check | decrypt <dir>');
}
