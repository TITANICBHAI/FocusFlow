/**
 * Standalone production server for Expo static builds.
 *
 * Serves the output of build.js (static-build/) with two special routes:
 * - GET / or /manifest with expo-platform header → platform manifest JSON
 * - GET / without expo-platform → landing page HTML
 * Everything else falls through to static file serving from ./static-build/.
 *
 * Zero external dependencies — uses only Node.js built-ins (http, fs, path).
 */

const http = require("http");
const fs = require("fs");
const path = require("path");

const STATIC_ROOT = path.resolve(__dirname, "..", "static-build");
const TEMPLATE_PATH = path.resolve(__dirname, "templates", "landing-page.html");
const basePath = (process.env.BASE_PATH || "/").replace(/\/+$/, "");

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".otf": "font/otf",
  ".map": "application/json",
};

function getAppName() {
  try {
    const appJsonPath = path.resolve(__dirname, "..", "app.json");
    const appJson = JSON.parse(fs.readFileSync(appJsonPath, "utf-8"));
    return appJson.expo?.name || "App Landing Page";
  } catch {
    return "App Landing Page";
  }
}

function serveManifest(platform, res) {
  const manifestPath = path.join(STATIC_ROOT, platform, "manifest.json");

  if (!fs.existsSync(manifestPath)) {
    res.writeHead(404, { "content-type": "application/json" });
    res.end(
      JSON.stringify({ error: `Manifest not found for platform: ${platform}` }),
    );
    return;
  }

  const manifest = fs.readFileSync(manifestPath, "utf-8");
  res.writeHead(200, {
    "content-type": "application/json",
    "expo-protocol-version": "1",
    "expo-sfv-version": "0",
  });
  res.end(manifest);
}

function serveLandingPage(req, res, landingPageTemplate, appName) {
  const forwardedProto = req.headers["x-forwarded-proto"];
  const protocol = forwardedProto || "https";
  const host = req.headers["x-forwarded-host"] || req.headers["host"];
  const baseUrl = `${protocol}://${host}`;
  const expsUrl = `${host}`;

  const html = landingPageTemplate
    .replace(/BASE_URL_PLACEHOLDER/g, baseUrl)
    .replace(/EXPS_URL_PLACEHOLDER/g, expsUrl)
    .replace(/APP_NAME_PLACEHOLDER/g, appName);

  res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
  res.end(html);
}

function serveStaticFile(urlPath, res) {
  const safePath = path.normalize(urlPath).replace(/^(\.\.(\/|\\|$))+/, "");
  const filePath = path.join(STATIC_ROOT, safePath);

  if (!filePath.startsWith(STATIC_ROOT)) {
    res.writeHead(403);
    res.end("Forbidden");
    return;
  }

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    res.writeHead(404);
    res.end("Not Found");
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || "application/octet-stream";
  const content = fs.readFileSync(filePath);
  res.writeHead(200, { "content-type": contentType });
  res.end(content);
}

const landingPageTemplate = fs.readFileSync(TEMPLATE_PATH, "utf-8");
const appName = getAppName();

// ── Helpers ──────────────────────────────────────────────────────────────────

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => { data += chunk; });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
}

function starBar(n) {
  return "⭐".repeat(n) + "☆".repeat(5 - n);
}

function ratingColor(n) {
  if (n <= 2) return 0xef4444;
  if (n === 3) return 0xf59e0b;
  if (n === 4) return 0x84cc16;
  return 0x10b981;
}

async function handleReview(req, res) {
  cors(res);

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method !== "POST") {
    res.writeHead(405, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "Method not allowed" }));
    return;
  }

  let webhookUrl = process.env.DISCORD_WEBHOOK_URL ?? "";
  // If the secret was stored as base64, decode it transparently.
  // A valid Discord webhook always starts with "https://".
  if (webhookUrl && !webhookUrl.startsWith("http")) {
    try {
      const decoded = Buffer.from(webhookUrl, "base64").toString("utf-8").trim();
      if (decoded.startsWith("http")) webhookUrl = decoded;
    } catch {
      // leave as-is — the URL parse below will reject it cleanly
    }
  }
  if (!webhookUrl) {
    res.writeHead(503, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "Webhook not configured" }));
    return;
  }

  let body;
  try {
    const raw = await readBody(req);
    body = JSON.parse(raw);
  } catch {
    res.writeHead(400, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "Invalid JSON" }));
    return;
  }

  const stars = Number(body.stars) || 0;
  const text = String(body.text || "").slice(0, 500);

  if (stars < 1 || stars > 5) {
    res.writeHead(400, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "stars must be 1–5" }));
    return;
  }

  const labels = ["", "Not great", "Needs work", "It's okay", "Pretty good", "Love it!"];

  const embed = {
    title: `${starBar(stars)}  New FocusFlow Review`,
    color: ratingColor(stars),
    fields: [
      { name: "Rating", value: `**${stars}/5** — ${labels[stars]}`, inline: true },
      { name: "Submitted", value: new Date().toUTCString(), inline: true },
    ],
    footer: { text: "FocusFlow · In-app review" },
    timestamp: new Date().toISOString(),
  };

  if (text) {
    embed.fields.push({ name: "Review", value: text, inline: false });
  }

  const payload = JSON.stringify({ embeds: [embed] });

  try {
    const { URL: NodeURL } = require("url");
    const https = require("https");
    const http2 = require("http");
    const parsedUrl = new NodeURL(webhookUrl);
    const lib = parsedUrl.protocol === "https:" ? https : http2;

    await new Promise((resolve, reject) => {
      const discordReq = lib.request(
        {
          hostname: parsedUrl.hostname,
          path: parsedUrl.pathname + parsedUrl.search,
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Content-Length": Buffer.byteLength(payload),
          },
        },
        (discordRes) => {
          discordRes.resume();
          if (discordRes.statusCode >= 200 && discordRes.statusCode < 300) {
            resolve();
          } else {
            reject(new Error(`Discord responded ${discordRes.statusCode}`));
          }
        }
      );
      discordReq.on("error", reject);
      discordReq.write(payload);
      discordReq.end();
    });

    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true }));
  } catch (err) {
    console.error("[review] Discord delivery failed:", err.message);
    res.writeHead(502, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "Failed to deliver to Discord" }));
  }
}

// ─────────────────────────────────────────────────────────────────────────────

const server = http.createServer((req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host}`);
  let pathname = url.pathname;

  if (basePath && pathname.startsWith(basePath)) {
    pathname = pathname.slice(basePath.length) || "/";
  }

  // ── Review API ─────────────────────────────────────────────────────────────
  if (pathname === "/api/review") {
    return handleReview(req, res);
  }

  if (pathname === "/" || pathname === "/manifest") {
    const platform = req.headers["expo-platform"];
    if (platform === "ios" || platform === "android") {
      return serveManifest(platform, res);
    }

    if (pathname === "/") {
      return serveLandingPage(req, res, landingPageTemplate, appName);
    }
  }

  serveStaticFile(pathname, res);
});

const port = parseInt(process.env.PORT || "3000", 10);
server.listen(port, "0.0.0.0", () => {
  console.log(`Serving static Expo build on port ${port}`);
});
