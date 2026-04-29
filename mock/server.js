const express = require("express");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 50303;
const FIXTURES_DIR = path.join(__dirname, "fixtures");

app.use(express.json());

app.use((_req, res, next) => {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Headers", "*");
  res.header("Access-Control-Allow-Methods", "GET, PATCH, POST, OPTIONS");
  if (_req.method === "OPTIONS") return res.sendStatus(200);
  next();
});

function readFixture(name) {
  const file = path.join(FIXTURES_DIR, `${name}.json`);
  if (fs.existsSync(file)) {
    return JSON.parse(fs.readFileSync(file, "utf-8"));
  }
  return null;
}

// GET /api/v1/daily?badge=...
app.get("/api/v1/daily", (_req, res) => {
  const fixture = readFixture("daily");
  if (fixture) {
    res.json(fixture);
  } else {
    res.status(404).json({ error: "fixture not found: daily.json" });
  }
});

// GET /api/v1/daily/react?badge=...
app.get("/api/v1/daily/react", (_req, res) => {
  const fixture = readFixture("react");
  if (fixture) {
    res.json(fixture);
  } else {
    res.status(404).json({ error: "fixture not found: react.json" });
  }
});

// PATCH /api/v1/daily/react?cardTypeIdx=...
app.patch("/api/v1/daily/react", (req, res) => {
  console.log("[PATCH /api/v1/daily/react] body:", req.body);
  res.json({ ok: true });
});

// GET /api/v1/oauth/request
app.get("/api/v1/oauth/request", (_req, res) => {
  res.type("html").send(`<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Mock OAuth</title></head>
<body>
  <h1>Mock OAuth</h1>
  <p>This is a mock Loli Commons API server.</p>
  <p>Fixtures are in <code>mock/fixtures/</code> — edit them to change responses.</p>
  <hr>
  <p><strong>Endpoints:</strong></p>
  <ul>
    <li><code>GET /api/v1/daily</code> → mock/fixtures/daily.json</li>
    <li><code>GET /api/v1/daily/react</code> → mock/fixtures/react.json</li>
    <li><code>PATCH /api/v1/daily/react</code> → {"ok": true} (always succeeds)</li>
    <li><code>GET /api/v1/oauth/request</code> → this page</li>
  </ul>
</body>
</html>`);
});

// Health check
app.get("/", (_req, res) => {
  res.json({ server: "loli-daily-mock", version: "1.0.0" });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`loli-daily-mock running on http://0.0.0.0:${PORT}`);
  console.log(`Fixtures: ${FIXTURES_DIR}`);
});
