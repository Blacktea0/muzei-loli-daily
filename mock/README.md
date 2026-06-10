# Loli Daily Mock Server

Local mock server for developing and testing the Loli Daily app without a real backend.

## Getting Started

The mock server is managed via Gradle tasks defined in the root `build.gradle.kts`. No need to `cd mock` or run Node.js manually.

```bash
# Start the server (runs in background, auto-installs npm deps on first run)
./gradlew startMockServer

# Stop the server
./gradlew stopMockServer

# View server logs
./gradlew mockLogs
```

On Windows, use `gradlew.bat` instead of `./gradlew`:

```batch
gradlew.bat startMockServer
gradlew.bat stopMockServer
gradlew.bat mockLogs
```

The server listens on `0.0.0.0:50303`. Override with the `PORT` env var:

```batch
set PORT=8080 && gradlew.bat startMockServer
```

Server output is written to `mock/server.log`. The PID is tracked in `mock/.server.pid`; `startMockServer` automatically kills any previously running instance before starting a new one.

## API Endpoints

### Health Check

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Returns `{ server, version }` |

### Mock Image

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/image?w=<width>&h=<height>&t=<cachebuster>` | Random-colour PNG with timestamp rendered at the centre |

Parameters:
- `w` — width in pixels (default 1080)
- `h` — height in pixels (default 1920)
- `t` — cache-buster (any string)

### Daily Content API (`/api/v1/daily`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/daily?badge=<tag>` | Returns the daily card list |
| `GET` | `/api/v1/daily/react?badge=<tag>` | Returns reaction data |
| `PATCH` | `/api/v1/daily/react?cardTypeIdx=<index>` | Submit a reaction (always returns `{ ok: true }`) |

**Daily response** (`fixtures/daily.json`):

```json
{
  "date": "2026-04-29",
  "cards": [
    {
      "characterNames": ["Character Name"],
      "comment": "Description",
      "imgUrl": "{{mockServer}}/image?w=1000&h=1000",
      "sourceUrl": "https://example.com/source",
      "tags": "LC0",
      "artistName": "Artist",
      "artistUrl": "https://example.com/artist",
      "characterIds": [1],
      "suggestedBy": { "nickname": "Submitter", "username": "submitter" }
    }
  ]
}
```

- The `{{mockServer}}` placeholder in `imgUrl` is replaced at runtime with `http://10.0.2.2:<port>` (emulator → host)
- `date` uses a shifted calendar where the day boundary is 07:21 GMT+8
- `tags` accepts values like `LC0`, `LC ES`, `LC YJ`, etc. (see tag list below)

**React response** (`fixtures/react.json`):

```json
{
  "reactions": [
    { "0": [["username", "Nickname"], ...], "54": [...] },
    { "104": [...] }
  ],
  "discussions": [
    { "id": "0", "count": 0 },
    { "id": "443088", "count": 0 }
  ]
}
```

### Bangumi Topics (`/p1/groups/-/topics`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/p1/groups/-/topics/<topicID>` | Returns a group topic with replies |

- `topicID = 0` returns an empty topic
- Even IDs serve `topic_es.json`, odd IDs serve `topic.json`
- Reply dates are patched to the current shifted date at runtime

### User & Authentication

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/oauth/request` | Interactive mock OAuth login page |
| `GET` | `/user/<username>?bgm-lcjs-session=<token>&expiresAt=<ts>` | OAuth callback landing page |
| `GET` | `/api/v1/user/<username>` | LC user info (requires `Authorization: Bearer <token>`) |
| `GET` | `/p1/users/<username>` | Bangumi user profile |

**OAuth flow:**

1. App opens `/api/v1/oauth/request?redirect_uri=<callback>`
2. A page appears with a username input (default `mock_user`) and a Login button
3. Clicking Login generates a mock token and redirects to `redirect_uri` with `bgm-lcjs-session`, `expiresAt`, and `user` params
4. If no `redirect_uri` is provided, it redirects to `/user/<username>` as a fallback landing page

**LC user info** (`fixtures/user.json`):

```json
{ "badge": "LC0", "privacy": "public", "sd": "", "subPrivacy": "public" }
```

**Bangumi user** (`fixtures/bangumi_user.json`):

```json
{
  "id": 1000001,
  "username": "mock_user",
  "nickname": "Mock User",
  "avatar": { "small": "...", "medium": "...", "large": "..." }
}
```

## Fixture Files

All mock data lives in `fixtures/`:

| File | Purpose |
|------|---------|
| `daily.json` | Daily card data |
| `react.json` | Reaction and discussion data |
| `topic.json` | Bangumi group topic (odd IDs) |
| `topic_es.json` | Bangumi group topic (even IDs) |
| `user.json` | LC user info |
| `bangumi_user.json` | Bangumi user profile |

## Modifying Mock Data

### Adding / Editing Daily Cards

Edit `fixtures/daily.json` and add entries to the `cards` array:

```json
{
  "characterNames": ["New Character"],
  "comment": "Test description",
  "imgUrl": "{{mockServer}}/image?w=1200&h=800",
  "sourceUrl": "https://example.com/source",
  "tags": "LC ES",
  "artistName": "Artist",
  "artistUrl": "https://example.com/artist",
  "characterIds": [100],
  "suggestedBy": null
}
```

- `imgUrl` **must** contain `{{mockServer}}` to be replaced with the mock image endpoint
- Set to `null` or omit the placeholder to use the original URL
- `suggestedBy: null` means no submitter info

### Simulating Different Tag Scenarios

Change the `tags` field on each card in `daily.json`. Available tags:

```
LC0, LC ES, LC ES-PG, LC ES-NC, LC ES-NC-PG, LC ES-NC-GR, LC ES-NC-PG-GR,
LC YJ, LC YJ-ES, LC YJ-ES-PG, LC YJ-ES-NC, LC YJ-ES-NC-PG, LC YJ-ES-NC-GR, LC YJ-ES-NC-PG-GR
```

### Simulating Network Errors

Modify the corresponding route in `server.js`:

```js
// 500 error
app.get("/api/v1/daily", (_req, res) => {
  res.status(500).json({ error: "internal server error" });
});

// Timeout (30s delay)
app.get("/api/v1/daily", (_req, res) => {
  setTimeout(() => res.json(readFixture("daily")), 30000);
});

// Empty data
app.get("/api/v1/daily", (_req, res) => {
  res.json({ cards: [], date: shiftedDate(new Date()) });
});
```

### Simulating Auth Failures

```js
// Expired token
app.get("/api/v1/user/:username", (_req, res) => {
  res.status(401).json({ error: "token expired" });
});
```

### Adding New Endpoints

Add a route in `server.js` and drop the fixture file into `fixtures/`:

```js
app.get("/api/v1/custom", (_req, res) => {
  const fixture = readFixture("custom");
  res.json(fixture || { error: "no fixture" });
});
```

## ADB Commands for App Configuration

The app stores configuration in SharedPreferences file `lolidaily_prefs` under package `me.eroi.lolidaily.muzei`.

### Pointing the App at the Mock Server

**Option A — Write SharedPreferences directly via ADB (recommended)**

```bash
adb shell 'run-as me.eroi.lolidaily.muzei sh -c "cat > /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml << EOF
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <string name=\"debug_api_base_url\">http://10.0.2.2:50303</string>
    <string name=\"debug_bangumi_base_url\">http://10.0.2.2:50303</string>
</map>
EOF"'
```

> **Note:** This overwrites the entire prefs file. Use Option B if you have existing settings to preserve.

**Option B — Edit individual keys (preserves other settings)**

```bash
# Read current config first
adb shell 'run-as me.eroi.lolidaily.muzei cat /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml'

# Then manually edit the XML to add or change:
#   debug_api_base_url       = http://10.0.2.2:50303
#   debug_bangumi_base_url   = http://10.0.2.2:50303
```

**Option C — Use the in-app settings screen**

1. Open the app → Settings → Additional Settings
2. Under "API", change the LC API URL and Bangumi API URL
3. Enter `http://10.0.2.2:50303`

> `10.0.2.2` is the Android emulator's alias for the host machine. Replace with your computer's LAN IP when testing on a physical device.

### Simulating a Logged-In State

```bash
adb shell 'run-as me.eroi.lolidaily.muzei sh -c "cat > /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml << EOF
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <string name=\"debug_api_base_url\">http://10.0.2.2:50303</string>
    <string name=\"debug_bangumi_base_url\">http://10.0.2.2:50303</string>
    <string name=\"lc_session\">{\"token\":\"mock_lc_token_test\",\"expiresAt\":9999999999999}</string>
    <string name=\"bgm_username\">mock_user</string>
    <string name=\"bgm_nickname\">Mock User</string>
    <string name=\"lc_badge\">LC0</string>
</map>
EOF"'
```

### Overriding Tag Filter

```bash
# Enable tag override to force a specific tag
adb shell 'run-as me.eroi.lolidaily.muzei sh -c "cat >> /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml << EOF
    <boolean name=\"debug_override_api_tag_enabled\" value=\"true\" />
    <string name=\"debug_override_api_tag\">LC ES</string>
EOF"'
```

### Adjusting Refresh Time

```bash
# Set refresh time to 08:30
adb shell 'run-as me.eroi.lolidaily.muzei sh -c "cat >> /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml << EOF
    <int name=\"debug_refresh_hour\" value=\"8\" />
    <int name=\"debug_refresh_minute\" value=\"30\" />
EOF"'
```

### Resetting to Defaults

```bash
# Delete prefs file — app reverts to defaults on next launch
adb shell 'run-as me.eroi.lolidaily.muzei rm /data/data/me.eroi.lolidaily.muzei/shared_prefs/lolidaily_prefs.xml'
```

### Triggering a Refresh

```bash
# Force a daily refresh via WorkManager
adb shell am broadcast -a me.eroi.lolidaily.muzei.FORCE_REFRESH -n me.eroi.lolidaily.muzei/.RefreshReceiver
```

### Viewing Logs

```bash
# App network logs
adb logcat -s LoliApiClient BangumiApiClient ReactionService LoliDailyWorker

# Mock server logs appear directly in the terminal running server.js
```

## Allowing Cleartext HTTP

The app blocks cleartext HTTP by default. Connecting to a local mock server (`http://10.0.2.2`) requires allowing it:

- **Debug builds** — Android Studio permits cleartext to `localhost` / `10.0.2.2` by default
- **If still blocked** — temporarily edit `app/src/main/res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

> ⚠️ Revert to `false` after testing. Never commit this change.
