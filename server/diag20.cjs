// diag20 — headful (off-screen) extraction test: 1375156 forced onto Chris.
// If the manifest appears after the row activation, headless detection was the cause.
const puppeteer = require("puppeteer-extra");
const StealthPlugin = require("puppeteer-extra-plugin-stealth");
puppeteer.use(StealthPlugin());

(async () => {
  const browser = await puppeteer.launch({
    headless: false,
    args: [
      "--window-position=-32000,-32000",
      "--window-size=1300,780",
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-blink-features=AutomationControlled",
    ],
  });
  // close any ad popups the site spawns; keep the main tab
  const mainPage = await browser.newPage();
  browser.on("targetcreated", async (t) => {
    try { if (t !== mainPage.target() && (await t.opener())) await t.close(); } catch {}
  });

  await mainPage.setViewport({ width: 1280, height: 720 });

  const state = { tPress: 0 };
  const seen = [];
  mainPage.on("response", async (res) => {
    try {
      const url = res.url();
      const keep =
        /main\/(servers|movie)/.test(url) ||
        /\.(m3u8|mpd|mp4|ts)(\?|#|$)/i.test(url) ||
        /video\/|mpegurl|dash/.test((res.headers()["content-type"] || "").slice(0, 30));
      if (state.tPress && keep) seen.push({ t: Date.now(), s: res.status(), url: url.slice(0, 110) });
    } catch {}
  });

  await mainPage.goto("https://www.viduki.net/1/movie/1375156?color=e01621", { waitUntil: "networkidle2", timeout: 45000 });
  await new Promise((r) => setTimeout(r, 2500));

  const findAria = (lbl) =>
    mainPage.evaluate((l) => {
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
    await mainPage.mouse.move(pos.x - 300, pos.y, { steps: 4 });
    await new Promise((r) => setTimeout(r, 350));
    await mainPage.mouse.click(Math.round(pos.x), Math.round(pos.y));
    return true;
  };

  const panelRows = () =>
    mainPage.evaluate(() => {
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
  for (let attempt = 0; attempt < 3 && rows.length < 3; attempt++) {
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

  // activate Chris: focus + trusted Enter
  const fo = await mainPage.evaluate(() => {
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
  await mainPage.keyboard.press("Enter");
  console.log("Enter → Chris");

  await new Promise((r) => setTimeout(r, 55000));
  console.log(`\n=== post-press (${seen.length}) ===`);
  for (const r of seen) console.log(`+${((r.t - state.tPress) / 1000).toFixed(1)}s ${r.s} ${r.url}`);
  await browser.close();
})().catch((e) => { console.error("FATAL", e.message); process.exit(1); });