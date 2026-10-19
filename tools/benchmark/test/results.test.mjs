// node --test tools/benchmark/test/results.test.mjs
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { summarize, table } from '../results.mjs';

// A run as Macrobenchmark writes it (benchmarkData.json), made up and cut down to two benchmarks.
const run = {
  context: { build: { brand: 'samsung', model: 'SM-F966B', version: { sdk: 37 } } },
  benchmarks: [
    {
      name: 'coldStart[BaselineProfile]',
      className: 'app.liora.benchmark.StartupBenchmark',
      metrics: {
        timeToInitialDisplayMs: { minimum: 380.2, maximum: 455.9, median: 401.04, runs: [401.04] },
        timeToFullDisplayMs: { minimum: 520.0, maximum: 610.3, median: 560.27, runs: [560.27] },
      },
      sampledMetrics: {},
    },
    {
      name: 'scrollHistory[glass=true]',
      className: 'app.liora.benchmark.FrameBenchmark',
      metrics: { frameCount: { median: 412 } },
      sampledMetrics: {
        frameDurationCpuMs: { P50: 6.14, P90: 9.81, P95: 11.0, P99: 14.25, runs: [[6, 7, 9]] },
        frameOverrunMs: { P50: -8.0, P90: -3.1, P95: 0.5, P99: 2.4, runs: [[-8, -3, 2.4, -1]] },
      },
    },
  ],
};

test('a run boils down to the medians, the percentiles and the janky frames', () => {
  const summary = summarize(run);
  assert.equal(summary.device, 'samsung SM-F966B');
  assert.equal(summary.android, 37);
  assert.deepEqual(summary.results['StartupBenchmark.coldStart[BaselineProfile]'], {
    timeToInitialDisplayMs: 401,
    timeToFullDisplayMs: 560.3,
  });
  assert.deepEqual(summary.results['FrameBenchmark.scrollHistory[glass=true]'], {
    frameCount: 412,
    frameDurationCpuMs: { P50: 6.1, P90: 9.8, P99: 14.3 },
    frameOverrunMs: { P50: -8, P90: -3.1, P99: 2.4 },
    jankPercent: 25,
  });
});

test('the table shows the change against a saved run', () => {
  const before = summarize(run);
  before.results['StartupBenchmark.coldStart[BaselineProfile]'].timeToFullDisplayMs = 700;
  const text = table(summarize(run), { name: '0.4.10-cover', ...before });
  assert.match(text, /against 0\.4\.10-cover/);
  assert.match(text, /StartupBenchmark\.coldStart\[BaselineProfile\] +401 \(0%\) +560\.3 \(-20%\)/);
  assert.match(text, /FrameBenchmark\.scrollHistory\[glass=true\] +412 \(0%\)/);
});
