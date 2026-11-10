#!/usr/bin/env node
/**
 * Performance budgets.
 *
 * Build artefacts are checked against the numbers we promised, so a dependency
 * or a page that quietly adds weight fails the build instead of shipping.
 *
 * Run after `npm run build` (and optionally after the Gradle release build):
 *
 *   npm run perf:budget
 *
 * Missing artefacts are reported as "skipped" rather than failures, so the web
 * check works without an Android SDK and vice versa.
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { gzipSync } from "node:zlib";

const KB = 1024;
const MB = 1024 * 1024;
const DIST = join("dist", "assets");

const gzipSize = (file) => gzipSync(readFileSync(file)).length;
const rawSize = (file) => statSync(file).size;

const findIn = (dir, pattern) => {
  if (!existsSync(dir)) return null;
  const match = readdirSync(dir).find((name) => pattern.test(name));
  return match ? join(dir, match) : null;
};

const findApk = (app) =>
  findIn(join("native", app, "build", "outputs", "apk", "release"), /\.apk$/);

const checks = [
  {
    label: "Web entry JavaScript",
    limit: 150 * KB,
    measure: gzipSize,
    artifact: () => findIn(DIST, /^index-.*\.js$/),
  },
  {
    label: "Web entry CSS",
    limit: 25 * KB,
    measure: gzipSize,
    artifact: () => findIn(DIST, /^index-.*\.css$/),
  },
  {
    // Loaded only on the watch page, so it gets its own, looser budget.
    label: "hls.js player chunk",
    limit: 260 * KB,
    measure: gzipSize,
    artifact: () => findIn(DIST, /^hls-.*\.js$/),
  },
  {
    label: "TV release APK",
    limit: 25 * MB,
    measure: rawSize,
    artifact: () => findApk("tv"),
  },
  {
    label: "Mobile release APK",
    limit: 25 * MB,
    measure: rawSize,
    artifact: () => findApk("mobile"),
  },
];

const format = (bytes) =>
  bytes >= MB ? `${(bytes / MB).toFixed(2)} MB` : `${(bytes / KB).toFixed(1)} KB`;

const failures = [];
let checked = 0;

console.log("\nPerformance budgets\n");

for (const check of checks) {
  const artifact = check.artifact();
  if (!artifact) {
    console.log(`  ~  ${check.label.padEnd(24)} skipped (not built)`);
    continue;
  }
  const size = check.measure(artifact);
  const ratio = size / check.limit;
  const ok = ratio <= 1;
  checked += 1;
  if (!ok) failures.push(`${check.label}: ${format(size)} > ${format(check.limit)}`);
  const used = `${Math.round(ratio * 100)}% of budget`;
  console.log(
    `  ${ok ? "OK " : "FAIL"} ${check.label.padEnd(24)} ${format(size).padStart(9)} / ${format(
      check.limit,
    ).padStart(9)}  (${used})`
  );
}

console.log("");

if (!checked) {
  console.log("No artefacts found. Run `npm run build` first.\n");
  process.exit(0);
}

if (failures.length) {
  console.error("Over budget:\n" + failures.map((line) => `  - ${line}`).join("\n") + "\n");
  process.exit(1);
}

console.log("All measured artefacts are within budget.\n");
