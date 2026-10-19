#!/usr/bin/env node
// Reads what the benchmarks measured on the phone (benchmark/, README › Measuring speed) and prints it as a table:
//
//   node tools/benchmark/results.mjs [--file <benchmarkData.json>] [--save <name>] [--compare <name>]
//
// Without --file it reads the newest run under benchmark/build/outputs. --save keeps the run's summary as
// benchmark/results/<name>.json (say "0.4.10-cover"), to commit; --compare puts a saved one beside this run, with
// the change in percent. Startup is in milliseconds to the first frame (TTID) and to the Train tab's content
// (TTFD), the median of the runs. Frames are the time each took on the CPU, at the 50th, 90th and 99th
// percentile; a frame is janky when it ran over its deadline (overrun above 0).

import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const outputs = join(root, 'benchmark', 'build', 'outputs');
const saved = join(root, 'benchmark', 'results');

/** A run as Macrobenchmark wrote it, boiled down to what's worth comparing. */
export function summarize(data) {
  const build = data.context?.build ?? {};
  const results = {};
  for (const benchmark of data.benchmarks ?? []) {
    const name = `${benchmark.className.split('.').pop()}.${benchmark.name}`;
    const metrics = benchmark.metrics ?? {};
    const sampled = benchmark.sampledMetrics ?? {};
    const result = {};
    for (const key of ['timeToInitialDisplayMs', 'timeToFullDisplayMs', 'frameCount']) {
      if (metrics[key]) result[key] = round(metrics[key].median);
    }
    for (const key of ['frameDurationCpuMs', 'frameOverrunMs']) {
      const metric = sampled[key];
      if (metric) result[key] = { P50: round(metric.P50), P90: round(metric.P90), P99: round(metric.P99) };
    }
    const overruns = sampled.frameOverrunMs?.runs?.flat();
    if (overruns?.length) result.jankPercent = round((100 * overruns.filter((ms) => ms > 0).length) / overruns.length);
    results[name] = result;
  }
  return {
    device: [build.brand, build.model].filter(Boolean).join(' '),
    android: build.version?.sdk ?? null,
    measuredAt: new Date().toISOString().slice(0, 10),
    results,
  };
}

/** The summary as text, with [baseline]'s values beside it when there is one. */
export function table(summary, baseline) {
  const lines = [`Liora on ${summary.device || 'a phone'}${summary.android ? ` (API ${summary.android})` : ''}`];
  if (baseline) lines.push(`against ${baseline.name}: ${baseline.device} (API ${baseline.android}), ${baseline.measuredAt}`);
  const was = (name) => baseline?.results?.[name];
  const cell = (value, before) => {
    if (value === undefined) return '–';
    if (before === undefined || before === 0) return `${value}`;
    const change = Math.round((100 * (value - before)) / before);
    return `${value} (${change > 0 ? '+' : ''}${change}%)`;
  };
  const entries = Object.entries(summary.results);

  const startups = entries.filter(([, result]) => 'timeToInitialDisplayMs' in result);
  if (startups.length) {
    lines.push('', row(['Startup', 'first frame ms', 'Train tab ms']));
    for (const [name, result] of startups) {
      lines.push(
        row([
          name,
          cell(result.timeToInitialDisplayMs, was(name)?.timeToInitialDisplayMs),
          cell(result.timeToFullDisplayMs, was(name)?.timeToFullDisplayMs),
        ]),
      );
    }
  }

  const frames = entries.filter(([, result]) => 'frameDurationCpuMs' in result);
  if (frames.length) {
    lines.push('', row(['Frames', 'frames', 'P50 ms', 'P90 ms', 'P99 ms', 'janky %']));
    for (const [name, result] of frames) {
      const before = was(name);
      lines.push(
        row([
          name,
          cell(result.frameCount, before?.frameCount),
          cell(result.frameDurationCpuMs.P50, before?.frameDurationCpuMs?.P50),
          cell(result.frameDurationCpuMs.P90, before?.frameDurationCpuMs?.P90),
          cell(result.frameDurationCpuMs.P99, before?.frameDurationCpuMs?.P99),
          cell(result.jankPercent, before?.jankPercent),
        ]),
      );
    }
  }
  return lines.join('\n');
}

const row = ([name, ...cells]) => name.padEnd(46) + cells.map((cell) => cell.padStart(16)).join('');

const round = (value) => Math.round(value * 10) / 10;

/** The newest benchmarkData.json under [dir], or null. */
function newest(dir) {
  if (!existsSync(dir)) return null;
  let found = null;
  for (const entry of readdirSync(dir, { recursive: true })) {
    if (!entry.endsWith('benchmarkData.json')) continue;
    const path = join(dir, entry);
    if (!found || statSync(path).mtimeMs > statSync(found).mtimeMs) found = path;
  }
  return found;
}

function main(args) {
  const option = (name) => {
    const index = args.indexOf(name);
    return index >= 0 ? args[index + 1] : undefined;
  };
  const file = option('--file') ?? newest(outputs);
  if (!file) {
    console.error('results: nothing measured yet; run the benchmarks first (README › Measuring speed)');
    process.exit(1);
  }
  const summary = summarize(JSON.parse(readFileSync(file, 'utf8')));
  const compare = option('--compare');
  const baseline = compare && { name: compare, ...JSON.parse(readFileSync(join(saved, `${compare}.json`), 'utf8')) };
  console.log(table(summary, baseline));
  const save = option('--save');
  if (save) {
    mkdirSync(saved, { recursive: true });
    writeFileSync(join(saved, `${save}.json`), `${JSON.stringify(summary, null, 2)}\n`);
    console.log(`\nSaved as benchmark/results/${save}.json`);
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2));
