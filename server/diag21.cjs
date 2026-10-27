// diag21 — REAL Chromium (Edge) off-screen + CDP attach: 1375156 → Chris test.
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");
puppeteer.use(StealthPlugin());
const { spawn } = require("child_process");
const fs = require("fs");

const EDGE = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const PORT = 9333;
const PROFILE = "C:\\Users\\saksh\\AppData\\Local\\Temp\\opencode\\edge-prof";

(async () => {
  const child = spawn(EDGE, [
    `--remote-debugging-port=${PORT}`,
    "--remote-allow-origins=*",
    `--user-data-dir=${PROFILE}`,
    "--window-position=-32000,-32000",
    "--window-size=1300,780",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-background-networking",
  ], { detached: false, stdio: "ignore" });

  // wait for CDP
  let ready = false;
  for (let i = 0; i < 30; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/version`);
      if (r.ok) { ready = true; break; }
    } catch {}
    await new Promise((res) => setTimeout(res, 300));
  }
  if (!ready) { console.error("Edge CDP never became ready"); child.kill(); process.exit(1); }
  console.log("Edge CDP ready");

  const browser = await puppeteer.connect({ browserURL: `http://127.0.0.1:${PORT}`, defaultViewport: { width: 1280, height: 720 } });
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();

  const state = { tPress: 0 };
  const seen = [];
  page.on("response", async (res) => {
    try {
      const url = res.url();
      const keep =
        /viduki|pepper/i.test(url) ||
        /\.(m3u8|mpd|mp4|ts)(\?|#|$)/i.test(url) ||
        /video\/|mpegurl|dash/.test((res.headers()["content-type"] || "").slice(0, 30));
      if (keep) {
        const rec = { t: Date.now(), s: res.status(), url: url.slice(0, 110) };
        if (state.tPress) seen.push(rec);
        if (/pepper/i.test(url)) console.log("PEPPER CALL:", res.status(), url.slice(0, 90));
      }
    } catch {}
  });

  await page.goto("https://www.viduki.net/1/movie/1375156?color=e01621", { waitUntil: "networkidle2", timeout: 45000 });
  await new Promise((r) => setTimeout(r, 2500));

  const findAria = (lbl) =>
    page.evaluate((l) => {
      const out = [];
      (function walk(root, d) {
        root.querySelectorAll("*").forEach((el) => {
          if ((el.getAttribute("aria-label") || "").toLowerCase() === l.toLowerCase()) {
            const r = el.getBoundingClientRect();
            out.push({ x: r.x + r.width / 2, y: r.y + r.height / 2 });
          }
          if (el.shadowRoot && d < 10) walk(el.shadowRoot, d + 1);
        });
      })(document, 0);
      return out[0] || null;
    }, lbl);

  const clickAria = async (label) => {
    const pos = await findAria(label);
    if (!pos) return false;
    await page.mouse.move(pos.x - 300, pos.y, { steps: 4 });
    await new Promise((r) => setTimeout(r, 350));
    await page.mouse.click(Math.round(pos.x), Math.round(pos.y));
    return true;
  };

  const panelRows = () =>
    page.evaluate(() => {
      const out = [];
      (function walk(root, d) {
        root.querySelectorAll("button.vp-panel-row").forEach((b) => {
          const s = b.querySelector("span.vp-panel-row-label");
          const own = (s ? [s.textContent] : [...b.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent)).join("").trim();
          if (own) out.push({ label: own });
        });
        if (d < 10) root.querySelectorAll("*").forEach((el) => { if (el.shadowRoot) walk(el.shadowRoot, d + 1); });
      })(document, 0);
      return out;
    });

  let rows = [];
  // wait for the player's control bar before driving anything
  for (let i = 0; i < 40; i++) {
    const s = await findAria("Settings");
    if (s) break;
    await new Promise((r) => setTimeout(r, 500));
  }
  for (let attempt = 0; attempt < 4 && rows.length < 3; attempt++) {
    console.log("panel attempt", attempt + 1);
    await clickAria("Settings");
    await new Promise((r) => setTimeout(r, 900));
    await clickAria("Servers");
    for (let i = 0; i < 20; i++) {
      rows = await panelRows();
      if (rows.length >= 3) break;
      await new Promise((r) => setTimeout(r, 400));
    }
    await new Promise((r) => setTimeout(r, 1200));
  }
  console.log("rows:", rows.length, rows.map((r) => r.label).join(","));

  const fo = await page.evaluate(() => {
    let hit = null;
    (function walk(root, d) {
      if (hit) return;
      root.querySelectorAll("button.vp-panel-row").forEach((b) => {
        if (hit) return;
        const s = b.querySelector("span.vp-panel-row-label");
        const own = (s ? [s.textContent] : [...b.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent)).join("").trim();
        if (own === "Chris") hit = b;
      });
      if (!hit && d < 10) root.querySelectorAll("*").forEach((el) => { if (el.shadowRoot) walk(el.shadowRoot, d + 1); });
    })(document, 0);
    if (!hit) return { ok: 0 };
    hit.focus();
    return { ok: document.activeElement === hit };
  });
  console.log("focus:", JSON.stringify(fo));

  state.tPress = Date.now();
  await page.keyboard.press("Enter");
  console.log("Enter → Chris");

  await new Promise((r) => setTimeout(r, 55000));
  console.log(`\n=== post-press (${seen.length}) ===`);
  for (const r of seen) console.log(`+${((r.t - state.tPress) / 1000).toFixed(1)}s ${r.s} ${r.url}`);

  await browser.disconnect();
  try { child.kill(); } catch {}
  try { spawn("taskkill", ["/PID", String(child.pid), "/T", "/F"], { stdio: "ignore" }); } catch {}
})().catch((e) => { console.error("FATAL", e.message); process.exit(1); });