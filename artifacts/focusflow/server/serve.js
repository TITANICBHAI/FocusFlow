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
const DIAGNOSTICS_WEBHOOK = process.env.DISCORD_DIAGNOSTICS_WEBHOOK_URL;
const reportAttempts = new Map();

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

function writeJson(res, status, body) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "content-type",
  });
  res.end(JSON.stringify(body));
}

function sanitizeReportValue(value, maxLength) {
  return String(value || "")
    .replace(/\bhttps?:\/\/\S+/gi, "[redacted-url]")
    .replace(/\bwww\.\S+/gi, "[redacted-url]")
    .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, "[redacted-email]")
    .replace(/\b(password|passwd|token|secret|api[_-]?key)\s*[:=]\s*\S+/gi, "$1=[redacted]")
    .slice(0, maxLength);
}

function getClientKey(req) {
  const forwarded = req.headers["x-forwarded-for"];
  return String(forwarded || req.socket.remoteAddress || "unknown").split(",")[0].trim();
}

function isRateLimited(req) {
  const now = Date.now();
  const key = getClientKey(req);
  const recent = (reportAttempts.get(key) || []).filter((time) => now - time < 60 * 60 * 1000);
  if (recent.length >= 5) {
    reportAttempts.set(key, recent);
    return true;
  }
  recent.push(now);
  reportAttempts.set(key, recent);
  return false;
}

function readRequestBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 50_000) {
        reject(new Error("request too large"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(body));
    req.on("error", reject);
  });
}

function splitDiscordContent(text) {
  const chunks = [];
  for (let i = 0; i < text.length; i += 1_700) {
    chunks.push(text.slice(i, i + 1_700));
  }
  return chunks.length > 0 ? chunks : ["(no diagnostic logs provided)"];
}

async function forwardDiagnosticsReport(req, res) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "content-type",
      "access-control-allow-methods": "POST, OPTIONS",
    });
    res.end();
    return;
  }

  if (req.method !== "POST") {
    writeJson(res, 405, { error: "Method not allowed" });
    return;
  }

  if (!DIAGNOSTICS_WEBHOOK) {
    writeJson(res, 503, { error: "Diagnostics reporting is not configured" });
    return;
  }

  if (isRateLimited(req)) {
    writeJson(res, 429, { error: "Too many reports. Please try again later." });
    return;
  }

  try {
    const raw = await readRequestBody(req);
    const input = JSON.parse(raw);
    const description = sanitizeReportValue(input.description, 2_000) || "(no description provided)";
    const logs = sanitizeReportValue(input.logs, 18_000);
    const app = input.app || {};
    const metadata = [
      "FocusFlow diagnostic report",
      `App version: ${sanitizeReportValue(app.version, 40) || "unknown"}`,
      `Platform: ${sanitizeReportValue(app.platform, 20) || "unknown"}`,
      `OS version: ${sanitizeReportValue(app.osVersion, 40) || "unknown"}`,
      `User description: ${description}`,
    ].join("\n");
    const chunks = splitDiscordContent(logs);

    for (let index = 0; index < chunks.length; index += 1) {
      const prefix = index === 0
        ? metadata
        : `FocusFlow diagnostic report (continued ${index + 1}/${chunks.length})`;
      const content = `${prefix}\n\nLogs:\n\`\`\`\n${chunks[index]}\n\`\`\``.slice(0, 2_000);
      const discordResponse = await fetch(DIAGNOSTICS_WEBHOOK, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          content,
          allowed_mentions: { parse: [] },
        }),
      });
      if (!discordResponse.ok) {
        throw new Error(`Discord returned ${discordResponse.status}`);
      }
    }

    writeJson(res, 200, { ok: true });
  } catch (error) {
    console.error("Diagnostics report failed:", error.message);
    writeJson(res, 400, { error: "Could not send diagnostics report" });
  }
}

const landingPageTemplate = fs.readFileSync(TEMPLATE_PATH, "utf-8");
const appName = getAppName();

const server = http.createServer((req, res) => {
  const url = new URL(req.url || "/", `http://${req.headers.host}`);
  let pathname = url.pathname;

  if (basePath && pathname.startsWith(basePath)) {
    pathname = pathname.slice(basePath.length) || "/";
  }

  if (pathname === "/api/diagnostics/report") {
    return void forwardDiagnosticsReport(req, res);
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
