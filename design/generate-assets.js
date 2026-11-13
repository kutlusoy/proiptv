// Generates all raster graphics of the app from the SVG files in this folder:
// ProIPTV_icon.svg (the P mark), ProIPTV_wordmark.svg (the lettering), ProIPTV_logo.svg (both).
// Usage: npm install playwright (once, in any folder), then: node design/generate-assets.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const res = path.join(root, 'app/src/main/res');
const read = (f) => fs.readFileSync(path.join(__dirname, f), 'utf8');
const dataUri = (svg) => 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');
const markUri = dataUri(read('ProIPTV_icon.svg'));
const wordUri = dataUri(read('ProIPTV_wordmark.svg'));
const logoUri = dataUri(read('ProIPTV_logo.svg'));

const MARK_RATIO = 518 / 662;
const WORD_RATIO = 886 / 148;
const BLACK = '#000';
const DEBUG_BACKGROUND = '#FFB300';
const DEBUG_LABEL = '#7A0F25';
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const mark = (h, filter = '') =>
  `<img src="${markUri}" style="display:block;height:${h}px;width:${h * MARK_RATIO}px;${filter ? `filter:${filter};` : ''}">`;
const word = (w) => `<img src="${wordUri}" style="display:block;width:${w}px;height:${w / WORD_RATIO}px">`;

const page = (w, h, body, background = 'transparent') =>
  `<html><body style="margin:0;width:${w}px;height:${h}px;background:${background};overflow:hidden;` +
  `display:flex;align-items:center;justify-content:center;font-family:Inter,Arial,sans-serif">${body}</body></html>`;

(async () => {
  const browser = await chromium.launch();
  const tab = await browser.newPage();

  async function shot(file, w, h, body, background) {
    await tab.setViewportSize({ width: Math.round(w), height: Math.round(h) });
    await tab.setContent(page(Math.round(w), Math.round(h), body, background));
    const target = path.isAbsolute(file) ? file : path.join(root, file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    await tab.screenshot({ path: target, omitBackground: !background });
    console.log('wrote', path.relative(root, target));
  }

  // Adaptive launcher icon: 108dp canvas at 4x, the mark inside the safe zone
  await shot(`${res}/drawable-nodpi/ic_launcher_foreground.png`, 432, 432, mark(244));
  await shot(`${res}/drawable-nodpi/ic_launcher_monochrome.png`, 432, 432, mark(244, 'brightness(0)'));

  // Legacy launcher icons (API 24 and 25)
  for (const [name, factor] of Object.entries(DENSITIES)) {
    const s = 48 * factor;
    const tile = (radius, share) =>
      `<div style="width:${s}px;height:${s}px;border-radius:${radius};background:${BLACK};` +
      `display:flex;align-items:center;justify-content:center">${mark(s * share)}</div>`;
    await shot(`${res}/mipmap-${name}/ic_launcher.png`, s, s, tile('12%', 0.68));
    await shot(`${res}/mipmap-${name}/ic_launcher_round.png`, s, s, tile('50%', 0.58));
  }

  // Store icon, full bleed
  await shot('app/src/main/ic_launcher-playstore.png', 512, 512, mark(512 * 0.64), BLACK);

  // Android TV banner: 320x180dp, mark and lettering side by side
  for (const [name, factor] of Object.entries(DENSITIES)) {
    if (name === 'xxxhdpi') continue;
    await shot(`${res}/drawable-${name}/tv_banner.png`, 320 * factor, 180 * factor,
      `<div style="transform:scale(${factor});transform-origin:center;display:flex;align-items:center;gap:16px">${mark(124)}${word(160)}</div>`, BLACK);
  }

  // Splash layers (drawn and animated by the app, see ui/splash)
  await shot(`${res}/drawable-nodpi/splash_mark.png`, Math.ceil(900 * MARK_RATIO), 900, mark(900));
  await shot(`${res}/drawable-nodpi/splash_title.png`, 1100, Math.ceil(1100 / WORD_RATIO), word(1100));
  const glow = (blur, widthFactor, heightFactor, file) =>
    shot(`${res}/drawable-nodpi/${file}`, Math.ceil(360 * MARK_RATIO * widthFactor), Math.ceil(360 * heightFactor),
      `<div style="filter:blur(${blur}px) saturate(1.7) brightness(1.6)">${mark(360)}</div>`);
  await glow(30, 2.1, 1.6, 'splash_glow_wide.png');
  await glow(9, 1.5, 1.2, 'splash_glow_near.png');
  await shot(`${res}/drawable-nodpi/splash_spark.png`, 256, 256,
    `<svg width="256" height="256" viewBox="-128 -128 256 256"><defs><radialGradient id="g"><stop offset="0" stop-color="#fff"/>` +
    `<stop offset=".16" stop-color="#ffd9e2" stop-opacity=".9"/><stop offset="1" stop-color="#e60041" stop-opacity="0"/></radialGradient></defs>` +
    `<circle r="120" fill="url(#g)" opacity=".6"/><path d="M0-122L8-8L122 0L8 8L0 122L-8 8L-122 0L-8-8Z" fill="#fff" opacity=".92"/></svg>`);

  // Repository graphics
  await shot('doc/logo.png', 1254, 1254, `<img src="${logoUri}" style="display:block;width:1254px;height:1254px">`, BLACK);
  await shot('doc/social-preview.png', 1280, 640,
    `<div style="display:flex;align-items:center;gap:70px">${mark(430)}${word(560)}</div>`, BLACK);

  // Debug build: amber background and a DEBUG label, so it is not mixed up with the release app
  const debugRes = path.join(root, 'app/src/debug/res');
  const debugBox = (box) =>
    `<div style="position:relative;width:${box}px;height:${box}px">` +
    `<div style="position:absolute;left:${(box - box * 0.58 * MARK_RATIO) / 2}px;top:${box * 0.06}px">${mark(box * 0.58)}</div>` +
    `<div style="position:absolute;left:${box * 0.1}px;bottom:${box * 0.05}px;width:${box * 0.8}px;height:${box * 0.24}px;` +
    `border-radius:${box * 0.06}px;background:${DEBUG_LABEL};color:#fff;display:flex;align-items:center;justify-content:center;` +
    `font-weight:800;font-size:${box * 0.16}px;letter-spacing:${box * 0.015}px">DEBUG</div></div>`;

  await shot(`${debugRes}/drawable-nodpi/ic_launcher_foreground.png`, 432, 432, debugBox(300));
  for (const [name, factor] of Object.entries(DENSITIES)) {
    const size = 48 * factor;
    const tile = (radius) =>
      `<div style="width:${size}px;height:${size}px;border-radius:${radius};background:${DEBUG_BACKGROUND};` +
      `display:flex;align-items:center;justify-content:center">${debugBox(size * 0.8)}</div>`;
    await shot(`${debugRes}/mipmap-${name}/ic_launcher.png`, size, size, tile('12%'));
    await shot(`${debugRes}/mipmap-${name}/ic_launcher_round.png`, size, size, tile('50%'));
  }
  for (const [name, factor] of Object.entries(DENSITIES)) {
    if (name === 'xxxhdpi') continue;
    await shot(`${debugRes}/drawable-${name}/tv_banner.png`, 320 * factor, 180 * factor,
      `<div style="transform:scale(${factor});transform-origin:center;display:flex;align-items:center;gap:16px">${debugBox(130)}${word(150)}</div>`, DEBUG_BACKGROUND);
  }

  await browser.close();
})();
