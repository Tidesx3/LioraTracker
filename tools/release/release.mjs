#!/usr/bin/env node
// Publishes a Liora release on GitHub, built and signed on this machine (the key never leaves it):
//
//   node tools/release/release.mjs [--dry-run] [--skip-check]
//
// The version comes from version.properties and what's new from docs/releases/<version>.md (## English and
// ## Deutsch, of which the app shows the one in its language). On a clean main that GitHub already has, it
// runs `check`, builds the release APK, makes sure it's Liora at that version and signed with the key in
// keystore.properties, then tags v<version> and publishes the release with liora-<version>.apk attached.
// --dry-run stops before tagging; --skip-check skips `check` (for a dry run after a green one).
//
// Needs JDK 21 (JAVA_HOME), the Android SDK (ANDROID_HOME or local.properties) and gh, signed in.

import { execFileSync, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const flags = new Set(process.argv.slice(2));
const dryRun = flags.has('--dry-run');
const skipCheck = flags.has('--skip-check');
const windows = process.platform === 'win32';

const fail = (message) => {
  console.error(`release: ${message}`);
  process.exit(1);
};

/** Runs a command in the repo with its output shown, and stops if it fails. */
const run = (command, args) => {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', shell: windows && command.endsWith('.bat') });
  if (result.status !== 0) fail(`${command} ${args.join(' ')} failed`);
};

/** Runs a command in the repo and returns what it printed. */
const output = (command, args) => execFileSync(command, args, { cwd: root, encoding: 'utf8' }).trim();

/** A .properties file as an object; local.properties escapes `:` and `\`. */
const properties = (file) =>
  Object.fromEntries(
    readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line.includes('=') && !line.trimStart().startsWith('#'))
      .map((line) => {
        const at = line.indexOf('=');
        return [line.slice(0, at).trim(), line.slice(at + 1).trim().replace(/\\(.)/g, '$1')];
      }),
  );

// What's to be released.
const { version } = properties(join(root, 'version.properties'));
if (!/^\d+\.\d+\.\d+$/.test(version ?? '')) fail(`version.properties: expected version=major.minor.patch`);
const tag = `v${version}`;
const notesFile = join(root, 'docs', 'releases', `${version}.md`);
if (!existsSync(notesFile)) fail(`write what's new first: docs/releases/${version}.md`);
const notes = readFileSync(notesFile, 'utf8');
for (const section of ['## English', '## Deutsch']) {
  if (!notes.includes(section)) fail(`docs/releases/${version}.md needs a "${section}" section`);
}
const signingFile = join(root, 'keystore.properties');
if (!existsSync(signingFile)) fail('keystore.properties is missing, so the APK would be unsigned (README › Release builds)');
const signing = properties(signingFile);

// The release is built from what GitHub has: main, clean, pushed, and not released yet. A dry run only
// builds and checks, so there these are warnings.
const unfit = dryRun ? (message) => console.warn(`release: (dry run) ${message}`) : fail;
if (output('git', ['rev-parse', '--abbrev-ref', 'HEAD']) !== 'main') unfit('releases are made from main');
if (output('git', ['status', '--porcelain'])) unfit('commit or stash your changes first');
run('git', ['fetch', '--quiet', '--tags', 'origin', 'main']);
if (output('git', ['rev-parse', 'HEAD']) !== output('git', ['rev-parse', 'origin/main'])) {
  unfit('push main first: the release is tagged on what GitHub has');
}
if (output('git', ['tag', '--list', tag])) unfit(`${tag} exists already: bump version.properties`);

// Build it.
run(join(root, windows ? 'gradlew.bat' : 'gradlew'), [...(skipCheck ? [] : ['check']), ':app:android:assembleRelease']);
const built = join(root, 'app', 'android', 'build', 'outputs', 'apk', 'release', 'android-release.apk');
if (!existsSync(built)) fail(`no APK at ${built}`);

// Make sure it's Liora at this version, signed with the release key.
const javaHome = process.env.JAVA_HOME ?? fail('set JAVA_HOME to a JDK 21');
const java = join(javaHome, 'bin', windows ? 'java.exe' : 'java');
const keytool = join(javaHome, 'bin', windows ? 'keytool.exe' : 'keytool');
const sdk =
  process.env.ANDROID_HOME ??
  process.env.ANDROID_SDK_ROOT ??
  (existsSync(join(root, 'local.properties')) ? properties(join(root, 'local.properties'))['sdk.dir'] : undefined) ??
  fail('set ANDROID_HOME, or sdk.dir in local.properties');
const buildToolsVersion = readdirSync(join(sdk, 'build-tools'))
  .filter((name) => /^\d+\.\d+\.\d+$/.test(name))
  .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
  .at(-1);
const buildTools = join(sdk, 'build-tools', buildToolsVersion);

const badging = output(join(buildTools, windows ? 'aapt2.exe' : 'aapt2'), ['dump', 'badging', built]);
const packageLine = badging.split(/\r?\n/)[0];
if (!packageLine.includes("name='app.liora'")) fail(`the APK isn't app.liora: ${packageLine}`);
if (!packageLine.includes(`versionName='${version}'`)) fail(`the APK isn't version ${version}: ${packageLine}`);

const certificates = output(java, ['-jar', join(buildTools, 'lib', 'apksigner.jar'), 'verify', '--print-certs', built]);
// "V2 Signer: certificate SHA-256 digest: …" or "Signer #1 …", depending on the schemes: one key, once.
const signers = new Set([...certificates.matchAll(/Signer[^:\n]*: certificate SHA-256 digest: ([0-9a-f]+)/g)].map((m) => m[1]));
const signedWith = signers.size === 1 ? [...signers][0] : undefined;
const keystore = output(keytool, [
  '-list',
  '-v',
  '-keystore',
  resolve(root, signing.storeFile),
  '-alias',
  signing.keyAlias,
  '-storepass',
  signing.storePassword,
]);
const releaseKey = /SHA256: ([0-9A-F:]+)/.exec(keystore)?.[1]?.replaceAll(':', '').toLowerCase();
if (!signedWith || signedWith !== releaseKey) fail(`the APK isn't signed with the release key (${signedWith} vs ${releaseKey})`);

const apk = join(root, 'build', 'release', `liora-${version}.apk`);
mkdirSync(dirname(apk), { recursive: true });
copyFileSync(built, apk);
const sha256 = createHash('sha256').update(readFileSync(apk)).digest('hex');
console.log(`\nLiora ${version}: ${apk}\nSHA-256 ${sha256}\nSigned with ${signedWith}`);

if (dryRun) {
  console.log('Dry run: nothing tagged or published.');
  process.exit(0);
}

// Publish: the tag first, then the release on it.
run('git', ['tag', '--annotate', tag, '--message', `Liora ${version}`]);
run('git', ['push', 'origin', tag]);
run('gh', ['release', 'create', tag, apk, '--title', `Liora ${version}`, '--notes-file', notesFile, '--verify-tag']);
console.log(`\nPublished ${tag}.`);
