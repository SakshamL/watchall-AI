// diag22 — Real Edge, hardened: fresh profile, full logging (clicks, rows,
// player console, pepper body), Chris-forced test with live post-press events.
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");
puppeteer.use(StealthPlugin());
const { spawn } = require("child_process");
const fs = require("fs");

const EDGE = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const PORT = 9344;
const PROFILE = "C:\\Users\\saksh\\AppData\\Local\\Temp\\opencode\\edge-prof-" + Date.now();

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  fs.mkdirSync(PROFILE, { recursive: true });
  const child = spawn(EDGE, [
    `--remote-debugging-port=${PORT}`,
    "--remote-allow-origins=*",
    `--user-data-dir=${PROFILE}`,
    "--window-position=-32000,-32000",
    "--window-size=1300,780",
    "--no-first-run",
    "--no-default-browser-check",
  ], { stdio: "ignore" });

  let ready = false;
  for (let i = 0; i < 40; i++) {
    try { const r = await fetch(`http://127.0.0.1:${PORT}/json/version`); if (r.ok) { ready = true; break; } } catch {}
    await sleep(300);
  }
  if (!ready) { console.error("Edge CDP not ready"); child.kill(); process.exit(1); }
  console.log("Edge CDP ready (pid", child.pid + ")");

  const browser = await puppeteer.connect({ browserURL: `http://127.0.0.1:${PORT}`, defaultViewport: { width: 1280, height: 720 } });
  const ctx = await browser.createBrowserContext();
  const page = await ctx.newPage();

  // close site-spawned popups, keep main tab
  browser.on("targetcreated", async (t) => {
    try {
      if (t === page.target()) return;
      if (await t.opener()) await t.close();
    } catch {}
  });

  page.on("console", (msg) => {
    const txt = msg.text().slice(0, 160);
    if (/pepper|envelope|decrypt|fail|error/i.test(txt)) console.log(`[pg.${msg.type()}] ${txt}`);
  });
  page.on("pageerror", (err) => console.log(`[pageerror] ${String(err).slice(0, 160)}`));

  const state = { tPress: 0 };
  page.on("response", async (res) => {
    try {
      const url = res.url();
      const ct = (res.headers()["content-type"] || "").slice(0, 30);
      const isApi = /viduki\.net\/(main|pepper)/.test(url);
      const isMedia = /\.(m3u8|mpd|mp4|ts)(\?|#|$)/i.test(url) || /video\/|mpegurl|dash/.test(ct);
      if (isApi) {
        if (/pepper/.test(url)) {
          let head = "";
          try { head = (await res.text()).slice(0, 90).replace(/\s+/g, " "); } catch { head = "(body unreadable)"; }
          console.log(`[pepper] ${res.status()} ${url.slice(0, 70)} body: ${head}`);
        } else if (state.tPress) {
          console.log(`+${((Date.now() - state.tPress) / 1000).toFixed(1)}s api ${res.status()} ${url.slice(0, 95)}`);
        }
      } else if (isMedia && state.tPress) {
        console.log(`+${((Date.now() - state.tPress) / 1000).toFixed(1)}s MEDIA ${res.status()} ${ct} ${url.slice(0, 92)}`);
      }
    } catch {}
  });

  await page.goto("https://www.viduki.net/1/movie/1375156?color=e01621", { waitUntil: "networkidle2", timeout: 60000 });

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
    await sleep(350);
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

  for (let i = 0; i < 50; i++) {
    if (await findAria("Settings")) break;
    await sleep(500);
  }
  await sleep(1500);

  let rows = [];
  for (let attempt = 0; attempt < 5 && rows.length < 3; attempt++) {
    const s1 = await clickAria("Settings");
    await sleep(900);
    const s2 = await clickAria("Servers");
    console.log(`panel attempt ${attempt + 1}: settings=${s1} servers=${s2}`);
    for (let i = 0; i < 20; i++) {
      rows = await panelRows();
      if (rows.length >= 3) break;
      await sleep(400);
    }
    if (rows.length < 3) await sleep(1500);
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
  console.log("Enter → Chris (live events follow)");

  await sleep(45000);
  console.log("=== done ===");
  try { await browser.disconnect(); } catch {}
  try { child.kill(); } catch {}
  try { spawn("taskkill", ["/PID", String(child.pid), "/T", "/F"], { stdio: "ignore" }); } catch {}
  try { fs.rmSync(PROFILE, { recursive: true, force: true }); } catch {}
})().catch((e) => { console.error("FATAL", e.message); process.exit(1); });