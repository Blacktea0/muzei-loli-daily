const express = require("express");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = process.env.PORT || 50303;
const FIXTURES_DIR = path.join(__dirname, "fixtures");

app.use(express.json());

// Request logging middleware
app.use((req, res, next) => {
  const timestamp = new Date().toISOString();
  const logParts = [
    `[${timestamp}]`,
    `${req.method} ${req.originalUrl || req.url}`,
  ];
  if (Object.keys(req.query).length > 0) {
    logParts.push(`query=${JSON.stringify(req.query)}`);
  }
  if (req.body && Object.keys(req.body).length > 0) {
    logParts.push(`body=${JSON.stringify(req.body)}`);
  }
  console.log("[mock] " + logParts.join(" "));
  next();
});

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
    // 每次调用都给图片 URL 加随机参数，模拟"新图片"
    const mockId = Date.now() + "_" + Math.random().toString(36).substring(2, 10);
    fixture.cards.forEach(card => {
      if (card.imgUrl) {
        const sep = card.imgUrl.includes("?") ? "&" : "?";
        card.imgUrl = card.imgUrl + sep + "mock=" + mockId;
      }
    });
    // 更新日期为今天，模拟"换日"
    fixture.date = new Date().toISOString().split("T")[0];
    console.log(`[mock] Returning daily with mockId=${mockId}, date=${fixture.date}`);
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
  res.json({ ok: true });
});

// GET /api/v1/oauth/request — interactive mock OAuth flow
app.get("/api/v1/oauth/request", (req, res) => {
  const expiresAt = Date.now() + 86400000 * 7; // 7 days from now
  const mockToken = "mock_lc_token_" + Math.random().toString(36).substring(2, 10);

  // If the app passes a redirect_uri, use it for the final redirect
  const redirectUri = req.query.redirect_uri || req.query.redirectUrl;

  res.type("html").send(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Mock OAuth — Bangumi Login</title>
  <style>
    body { font-family: sans-serif; max-width: 400px; margin: 60px auto; padding: 0 16px; background: #f5f5f5; }
    .card { background: #fff; border-radius: 8px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }
    h1 { margin: 0 0 8px; font-size: 20px; }
    p { color: #666; font-size: 14px; margin: 0 0 16px; }
    .btn { display: block; width: 100%; padding: 12px; border: none; border-radius: 6px; background: #3579f8; color: #fff; font-size: 16px; cursor: pointer; margin-bottom: 8px; }
    .btn:hover { background: #2b66d6; }
    .btn.cancel { background: #999; }
    .btn.cancel:hover { background: #888; }
    pre { background: #f0f0f0; padding: 8px; border-radius: 4px; font-size: 12px; word-break: break-all; }
    label { display: block; margin-bottom: 4px; font-weight: bold; font-size: 14px; }
    input { width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; margin-bottom: 12px; font-size: 14px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Mock Bangumi OAuth</h1>
    <p>Simulate login to Bangumi for reaction submission.</p>
    <label for="username">Username</label>
    <input id="username" type="text" value="mock_user" placeholder="Bangumi username">
    <button class="btn" onclick="login()">Login</button>
    <button class="btn cancel" onclick="location.href='about:blank'">Cancel</button>
  </div>
  <script>
    function login() {
      var user = document.getElementById('username').value || 'mock_user';
      var token = '${mockToken}';
      var expires = ${expiresAt};
      var redirectUri = ${redirectUri ? JSON.stringify(redirectUri) : 'null'};

      if (redirectUri) {
        var sep = redirectUri.indexOf('?') >= 0 ? '&' : '?';
        window.location.href = redirectUri + sep + 'bgm-lcjs-session=' + token + '&expiresAt=' + expires + '&user=' + encodeURIComponent(user);
      } else {
        window.location.href = '/user/' + encodeURIComponent(user) + '?bgm-lcjs-session=' + token + '&expiresAt=' + expires;
      }
    }
  </script>
</body>
</html>`);
});

// /user/{username}?bgm-lcjs-session=... — mock OAuth callback landing page
app.get("/user/:username", (_req, res) => {
  const { username } = _req.params;
  const { 'bgm-lcjs-session': token, expiresAt } = _req.query;
  res.type("html").send(`<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Login Complete</title></head>
<body>
  <h1>Login Successful</h1>
  <p>Username: <strong>${username}</strong></p>
  <p>Token: <code>${token}</code></p>
  <p>This page is handled by the app's WebView callback parser.</p>
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
