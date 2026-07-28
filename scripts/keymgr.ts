#!/usr/bin/env bun
/**
 * keymgr.ts - Immich API Key Manager CLI
 *
 * A unified cross-platform tool for generating and validating Immich API keys
 * with the 5 permissions Immich Media Frame needs.
 *
 * Usage:
 *   bun run scripts/keymgr.ts generate <server-url> <email> [password]
 *   bun run scripts/keymgr.ts check <server-url> <api-key>
 *
 * Or compile to standalone binary:
 *   bun build scripts/keymgr.ts --compile --outfile keymgr
 *   ./keymgr generate https://immich.example.com user@example.com
 *   ./keymgr check https://immich.example.com immich_apikey_xxx
 */

const REQUIRED_PERMS = ["album.read", "asset.read", "asset.view", "asset.download", "user.read"] as const;
const KEY_NAME = "ImmichMediaFrame";

type Perm = typeof REQUIRED_PERMS[number];

interface ApiKey {
  id: string;
  name: string;
  key?: string;
  permissions: string[];
}

interface LoginResponse {
  accessToken: string;
}

interface SearchMetadataRequest {
  albumIds: string[];
  size: number;
  type?: string;
}

interface SearchMetadataResponse {
  assets: { total: number; count: number; items: { id: string; type: string }[] };
}

interface ServerInfo {
  version: string;
}

class Colors {
  static reset = "\x1b[0m";
  static bold = "\x1b[1m";
  static dim = "\x1b[2m";
  static red = "\x1b[31m";
  static green = "\x1b[32m";
  static yellow = "\x1b[33m";
  static blue = "\x1b[34m";
  static cyan = "\x1b[36m";
}

function colorize(text: string, color: string): string {
  return `${color}${text}${Colors.reset}`;
}

function logInfo(msg: string): void {
  console.log(`${colorize("ℹ", Colors.blue)} ${msg}`);
}

function logSuccess(msg: string): void {
  console.log(`${colorize("✓", Colors.green)} ${msg}`);
}

function logWarn(msg: string): void {
  console.log(`${colorize("⚠", Colors.yellow)} ${msg}`);
}

function logError(msg: string): void {
  console.error(`${colorize("✗", Colors.red)} ${msg}`);
}

function logStep(msg: string): void {
  console.log(`${colorize("▸", Colors.cyan)} ${msg}`);
}

async function fetchJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const text = await res.text();
  let data: any;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { raw: text };
  }

  if (!res.ok) {
    const errMsg = data?.error || data?.message || data?.raw || `HTTP ${res.status}`;
    throw new Error(errMsg);
  }

  return data;
}

async function login(serverUrl: string, email: string, password: string): Promise<string> {
  logStep(`Logging in to ${serverUrl} as ${email}...`);
  const data = await fetchJson<LoginResponse>(`${serverUrl}/api/auth/login`, {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });

  if (!data.accessToken) {
    throw new Error("Login succeeded but no access token returned");
  }

  logSuccess("Login successful");
  return data.accessToken;
}

async function getExistingKeys(serverUrl: string, token: string): Promise<ApiKey[]> {
  const data = await fetchJson<ApiKey[]>(`${serverUrl}/api/api-keys`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return data;
}

async function findKeyByName(keys: ApiKey[], name: string): Promise<ApiKey | null> {
  return keys.find((k) => k.name === name) || null;
}

async function deleteKey(serverUrl: string, token: string, keyId: string): Promise<void> {
  await fetchJson(`${serverUrl}/api/api-keys/${keyId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}

/**
 * Invalidate the login session so this device doesn't linger in Immich's
 * "Authorized Devices". The API key we created is independent of the session
 * and remains valid. Best-effort — failures are logged, not fatal.
 */
async function logout(serverUrl: string, token: string): Promise<void> {
  try {
    await fetch(`${serverUrl}/api/auth/logout`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    logWarn(`Could not log out (session may linger): ${e instanceof Error ? e.message : String(e)}`);
  }
}

async function updateKey(
  serverUrl: string,
  token: string,
  keyId: string,
  permissions: readonly string[],
): Promise<void> {
  logStep(`Updating permissions on existing key (ID: ${keyId})...`);

  await fetchJson(`${serverUrl}/api/api-keys/${keyId}`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name: KEY_NAME, permissions: [...permissions] }),
  });

  logSuccess("Existing key updated with all required permissions");
}

async function createKey(serverUrl: string, token: string, permissions: readonly string[]): Promise<string> {
  logStep(`Creating API key "${KEY_NAME}" with ${permissions.length} permissions...`);

  const data = await fetchJson<{ apiKey: string }>(`${serverUrl}/api/api-keys`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ name: KEY_NAME, permissions: [...permissions] }),
  });

  if (!data.apiKey) {
    throw new Error("API key created but no key returned");
  }

  logSuccess(`API key created: ${colorize(data.apiKey, Colors.cyan)}`);
  return data.apiKey;
}

async function checkServerVersion(serverUrl: string): Promise<string | null> {
  try {
    const data = await fetchJson<ServerInfo>(`${serverUrl}/api/server-info/version`);
    return data.version || "unknown";
  } catch {
    return null;
  }
}

/**
 * Test an API key against a specific endpoint.
 * Returns the HTTP status code and whether the request succeeded.
 *
 * When useQueryAuth is true, the API key is appended as ?apiKey= instead of
 * using the x-api-key header. This mirrors how the app loads media via
 * Coil/ExoPlayer, so the probe tests the same auth path the app actually uses.
 */
async function testEndpoint(
  serverUrl: string,
  apiKey: string,
  endpoint: string,
  method: string = "GET",
  body?: any,
  useQueryAuth: boolean = false,
): Promise<{ ok: boolean; status: number; error?: string }> {
  try {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    let url = `${serverUrl}${endpoint}`;
    if (useQueryAuth) {
      const sep = endpoint.includes("?") ? "&" : "?";
      url = `${url}${sep}apiKey=${encodeURIComponent(apiKey)}`;
    } else {
      headers["x-api-key"] = apiKey;
    }
    const res = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });

    return { ok: res.ok, status: res.status };
  } catch (e) {
    return { ok: false, status: 0, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * Check an API key against all 4 required Immich Media Frame endpoints.
 * Exits 0 if all pass, 1 if any fail.
 */
async function checkApiKey(serverUrl: string, apiKey: string): Promise<void> {
  const version = await checkServerVersion(serverUrl);
  if (version) {
    logInfo(`Server version: ${colorize(version, Colors.cyan)}`);
  }

  console.log();
  let allPassed = true;

  // --- 1. Ping (no auth needed, just checks server is reachable) ---
  {
    logStep("Testing GET /api/server/ping ...");
    const result = await testEndpoint(serverUrl, apiKey, "/api/server/ping");
    if (result.ok) {
      logSuccess(`Server reachable (HTTP ${result.status})`);
    } else {
      logError(`Server unreachable at ${serverUrl}/api/server/ping (HTTP ${result.status})`);
      allPassed = false;
    }
  }

  // --- 2. user.read: GET /users/me ---
  {
    logStep("Testing GET /api/users/me (user.read) ...");
    const result = await testEndpoint(serverUrl, apiKey, "/api/users/me");
    if (result.ok) {
      logSuccess(`${colorize("user.read", Colors.cyan)} → Get current user (${result.status})`);
    } else {
      logError(`${colorize("user.read", Colors.red)} → Get current user (${result.status})`);
      allPassed = false;
    }
  }

  // --- 3. album.read: GET /albums ---
  let firstAlbumId: string | null = null;
  {
    logStep("Testing GET /api/albums (album.read) ...");
    const res = await fetch(`${serverUrl}/api/albums`, {
      headers: { "x-api-key": apiKey },
    });
    if (res.ok) {
      logSuccess(`${colorize("album.read", Colors.cyan)} → List albums (${res.status})`);
      try {
        const albums = await res.json();
        if (Array.isArray(albums) && albums.length > 0) {
          firstAlbumId = albums[0].id;
        }
      } catch {}
    } else {
      logError(`${colorize("album.read", Colors.red)} → List albums (${res.status})`);
      allPassed = false;
    }
  }

  // --- 4. asset.read: POST /search/metadata ---
  let firstAssetId: string | null = null;
  if (firstAlbumId) {
    logStep(`Testing POST /api/search/metadata (asset.read, album: ${firstAlbumId}) ...`);
    const searchBody: SearchMetadataRequest = { albumIds: [firstAlbumId], size: 100, type: "IMAGE" };
    const res = await fetch(`${serverUrl}/api/search/metadata`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-api-key": apiKey },
      body: JSON.stringify(searchBody),
    });
    if (res.ok) {
      const data = (await res.json()) as SearchMetadataResponse;
      const count = data.assets?.items?.length ?? 0;
      logSuccess(`${colorize("asset.read", Colors.cyan)} → Search assets (${res.status}, ${count} found)`);
      if (count > 0 && data.assets.items[0]?.id) {
        firstAssetId = data.assets.items[0].id;
      }
    } else {
      logError(`${colorize("asset.read", Colors.red)} → Search assets (${res.status})`);
      allPassed = false;
    }
  } else {
    logWarn("Skipping asset.read test — no albums found to search within");
  }

  // --- 5. asset.view: GET /assets/{id}/thumbnail?size=preview ---
  // Uses ?apiKey= query param (same as Coil image loading in the app).
  if (firstAssetId) {
    logStep(`Testing GET /api/assets/${firstAssetId}/thumbnail (asset.view) ...`);
    const result = await testEndpoint(serverUrl, apiKey, `/api/assets/${firstAssetId}/thumbnail?size=preview`, "GET", undefined, true);
    if (result.ok) {
      logSuccess(`${colorize("asset.view", Colors.cyan)} → View thumbnail (${result.status})`);
    } else {
      logError(`${colorize("asset.view", Colors.red)} → View thumbnail (${result.status})`);
      allPassed = false;
    }
  } else {
    logWarn("Skipping asset.view test — no assets found to test thumbnail with");
  }

  // --- 6. asset.download: GET /assets/{id}/original ---
  // Uses ?apiKey= query param (same as ExoPlayer video loading in the app).
  if (firstAssetId) {
    logStep(`Testing GET /api/assets/${firstAssetId}/original (asset.download) ...`);
    const result = await testEndpoint(serverUrl, apiKey, `/api/assets/${firstAssetId}/original`, "GET", undefined, true);
    if (result.ok) {
      logSuccess(`${colorize("asset.download", Colors.cyan)} → Download original (${result.status})`);
    } else {
      logError(`${colorize("asset.download", Colors.red)} → Download original (${result.status})`);
      allPassed = false;
    }
  } else {
    logWarn("Skipping asset.download test — no assets found to test download with");
  }

  console.log();

  if (allPassed) {
    logSuccess("All required permissions work correctly!");
    logInfo("This API key is ready to use with Immich Media Frame.");
  } else {
    logError("Some permissions failed. Check your API key permissions in Immich.");
    logWarn(`Required permissions: ${REQUIRED_PERMS.join(", ")}`);
    logInfo("Go to Immich Settings → API Keys → Edit key → ensure all 5 permissions are checked.");
    process.exit(1);
  }
}

async function generateKey(serverUrl: string, email: string, password: string): Promise<void> {
  logInfo(`Immich Media Frame API Key Generator`);
  logInfo(`Server: ${colorize(serverUrl, Colors.cyan)}`);
  logInfo(`Account: ${colorize(email, Colors.cyan)}`);
  console.log();

  const token = await login(serverUrl, email, password);

  // Ensure we always log out after key creation/update so this device doesn't
  // linger in Immich's "Authorized Devices". The API key survives logout.
  try {
    logStep("Checking for existing Immich Media Frame API key...");
    const existingKeys = await getExistingKeys(serverUrl, token);
    const existing = await findKeyByName(existingKeys, KEY_NAME);

    if (existing) {
      logWarn(`Found existing "${KEY_NAME}" key (ID: ${existing.id})`);

      if (process.stdin.isTTY) {
        const confirm = await prompt("Update permissions on this key? [Y/n] ");
        if (confirm.toLowerCase().startsWith("n")) {
          logInfo("Keeping existing key as-is. Use 'check' command to verify permissions.");
          return;
        }
      }

      // Edit in-place — preserves the key value so the app keeps working
      await updateKey(serverUrl, token, existing.id, REQUIRED_PERMS);

      console.log();
      logSuccess(`Done! "${KEY_NAME}" key updated with ${REQUIRED_PERMS.length} permissions.`);
      logInfo("The key value is unchanged — no need to re-enter it in Immich Media Frame.");
      logInfo("Run 'keymgr check <server-url> <api-key>' to verify.");
      return;
    }

    const apiKey = await createKey(serverUrl, token, REQUIRED_PERMS);

    console.log();
    logSuccess("Done! Your Immich Media Frame API key:");
    console.log(colorize(`  ${apiKey}`, Colors.bold + Colors.green));
    console.log();
    logInfo("Copy this key into Immich Media Frame Settings → API Key");
    logInfo("Run 'keymgr check <server-url> <api-key>' to verify it works.");
  } finally {
    await logout(serverUrl, token);
  }
}

async function prompt(question: string): Promise<string> {
  return new Promise((resolve) => {
    process.stdout.write(question);
    process.stdin.once("data", (data) => {
      resolve(data.toString().trim());
    });
  });
}

function printUsage(): void {
  console.log(colorize("Immich Media Frame API Key Manager", Colors.bold + Colors.cyan));
  console.log();
  console.log("Usage:");
  console.log("  keymgr generate <server-url> <email> [password]");
  console.log("  keymgr check <server-url> <api-key>");
  console.log();
  console.log("Commands:");
  console.log("  generate  Create or recreate an Immich Media Frame API key with required permissions");
  console.log("  check     Test an API key against all 4 required endpoints");
  console.log();
  console.log("Required permissions:");
  console.log(`  ${REQUIRED_PERMS.map((p) => colorize(p, Colors.cyan)).join(", ")}`);
  console.log();
  console.log("Examples:");
  console.log("  keymgr generate https://photos.example.com user@example.com");
  console.log("  keymgr check https://photos.example.com immich_apikey_abc123");
  console.log();
  console.log("Compile to standalone binary:");
  console.log("  bun build scripts/keymgr.ts --compile --outfile keymgr");
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const cmd = args[0];

  if (!cmd || cmd === "-h" || cmd === "--help") {
    printUsage();
    process.exit(cmd ? 0 : 1);
  }

  try {
    switch (cmd) {
      case "generate": {
        if (args.length < 3) {
          logError("Usage: keymgr generate <server-url> <email> [password]");
          process.exit(1);
        }
        const [, serverUrl, email, password] = args;
        await generateKey(serverUrl.replace(/\/$/, ""), email, password || (await prompt("Password: ")));
        break;
      }

      case "check": {
        if (args.length < 3) {
          logError("Usage: keymgr check <server-url> <api-key>");
          process.exit(1);
        }
        const [, serverUrl, apiKey] = args;
        await checkApiKey(serverUrl.replace(/\/$/, ""), apiKey);
        break;
      }

      default:
        logError(`Unknown command: ${cmd}`);
        printUsage();
        process.exit(1);
    }
  } catch (err) {
    logError(err instanceof Error ? err.message : String(err));
    process.exit(1);
  }
}

main();
