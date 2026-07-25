#!/usr/bin/env bun
/**
 * ImmichFrame Key Manager
 *
 * Creates or verifies a least-privilege Immich API key for ImmichPhotoFrame.
 * The key is scoped to only the 4 permissions the app needs:
 *   album.read  — list and get albums
 *   asset.read  — asset metadata
 *   asset.view  — view thumbnails and preview images
 *   user.read   — current user info (setup validation)
 *
 * Usage:
 *   keymgr generate <server-url> <email> [password]
 *   keymgr check   <server-url> <api-key>
 *   keymgr help
 *
 * Compiled into standalone executables via `bun build --compile`.
 * See tools/keymgr/build.sh.
 */

// ─── ANSI colors ──────────────────────────────────────────────────

const isTTY = process.stdout.isTTY !== false;
const C = {
  red: isTTY ? "\x1b[0;31m" : "",
  green: isTTY ? "\x1b[0;32m" : "",
  yellow: isTTY ? "\x1b[0;33m" : "",
  cyan: isTTY ? "\x1b[0;36m" : "",
  bold: isTTY ? "\x1b[1m" : "",
  dim: isTTY ? "\x1b[2m" : "",
  reset: isTTY ? "\x1b[0m" : "",
};

function pass(msg: string): void {
  console.log(`${C.green}✓${C.reset} ${msg}`);
}
function fail(msg: string): void {
  console.error(`${C.red}✗${C.reset} ${msg}`);
}
function info(msg: string): void {
  console.log(`${C.yellow}→${C.reset} ${msg}`);
}

// ─── Constants ────────────────────────────────────────────────────

const KEY_NAME = "ImmichPhotoFrame";
const SCOPED_PERMISSIONS = ["album.read", "asset.read", "asset.view", "user.read"];

// ─── Input helpers ────────────────────────────────────────────────

/**
 * Read a single line from stdin (interactive).
 */
function readLine(): Promise<string> {
  return new Promise((resolve) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.resume();
    const onData = (chunk: string) => {
      data += chunk;
      if (data.includes("\n") || data.includes("\r")) {
        process.stdin.pause();
        process.stdin.removeListener("data", onData);
        resolve(data.split(/[\r\n]/)[0]);
      }
    };
    process.stdin.on("data", onData);
  });
}

/**
 * Read a password with echo suppression.
 * On POSIX: suppresses terminal echo via `stty`.
 * On Windows: input is visible (no portable way to suppress without native deps).
 */
async function readPassword(prompt: string): Promise<string> {
  const isWindows = process.platform === "win32";

  process.stdout.write(prompt);

  if (!isWindows) {
    try {
      const { spawnSync } = await import("node:child_process");
      spawnSync("stty", ["-echo"], { stdio: "inherit" });
    } catch {
      // best-effort; if stty fails, input will be visible
    }
  } else {
    console.log(`${C.dim}(input will be visible)${C.reset}`);
    process.stdout.write(prompt);
  }

  const password = await readLine();

  if (!isWindows) {
    try {
      const { spawnSync } = await import("node:child_process");
      spawnSync("stty", ["echo"], { stdio: "inherit" });
    } catch {
      // ignore
    }
  }
  process.stdout.write("\n");
  return password;
}

/**
 * Prompt for y/N confirmation.
 */
async function confirm(prompt: string): Promise<boolean> {
  process.stdout.write(`${prompt} [y/N] `);
  const answer = (await readLine()).toLowerCase().trim();
  return answer === "y" || answer === "yes";
}

// ─── URL helpers ──────────────────────────────────────────────────

function normalizeUrl(raw: string): string {
  return raw.replace(/\/+$/, "");
}

function apiUrl(server: string, path: string): string {
  return `${normalizeUrl(server)}/api${path}`;
}

// ─── Immich API client ────────────────────────────────────────────

interface ImmichKey {
  id: string;
  name: string;
}

async function immichLogin(server: string, email: string, password: string): Promise<string> {
  const res = await fetch(apiUrl(server, "/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    throw new Error(`Login failed (HTTP ${res.status}). Check URL, email, and password.`);
  }
  const data = (await res.json()) as { accessToken?: string };
  if (!data.accessToken) {
    throw new Error("Login succeeded but no accessToken in response.");
  }
  return data.accessToken;
}

async function immichListKeys(server: string, token: string): Promise<ImmichKey[]> {
  const res = await fetch(apiUrl(server, "/api-keys"), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return [];
  const data = (await res.json()) as ImmichKey[];
  return Array.isArray(data) ? data : [];
}

async function immichDeleteKey(server: string, token: string, keyId: string): Promise<void> {
  const res = await fetch(apiUrl(server, `/api-keys/${keyId}`), {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error(`Could not delete old key (HTTP ${res.status}).`);
  }
}

async function immichCreateKey(server: string, token: string): Promise<string> {
  const res = await fetch(apiUrl(server, "/api-keys"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      name: KEY_NAME,
      permissions: SCOPED_PERMISSIONS,
    }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(
      `API key creation failed (HTTP ${res.status}). ` +
        "Your Immich server may not support scoped keys (requires v1.135+)." +
        (body ? `\nServer response: ${body}` : ""),
    );
  }
  const data = (await res.json()) as { secret?: string };
  if (!data.secret) {
    throw new Error("Key created but no secret in response.");
  }
  return data.secret;
}

// ─── generate command ─────────────────────────────────────────────

async function cmdGenerate(args: string[]): Promise<number> {
  if (args.length < 2) {
    printGenerateUsage();
    return 1;
  }

  const server = args[0]!;
  const email = args[1]!;
  const password = args[2] ?? (await readPassword("Password: "));

  console.log(`Connecting to ${normalizeUrl(server)} ...`);

  // Step 1: Login
  let token: string;
  try {
    token = await immichLogin(server, email, password);
  } catch (e) {
    fail((e as Error).message);
    return 1;
  }
  pass("Login successful.");

  // Step 2: Check for existing key
  const existing = await immichListKeys(server, token);
  const match = existing.find((k) => k.name === KEY_NAME);

  if (match) {
    console.log(`Found existing '${KEY_NAME}' key.`);
    console.log(`  Existing key ID: ${match.id}`);
    console.log();

    if (await confirm("Delete and recreate it with fresh permissions?")) {
      console.log("Deleting old key...");
      try {
        await immichDeleteKey(server, token, match.id);
      } catch (e) {
        fail((e as Error).message);
        return 1;
      }
      pass("Old key deleted.");
    } else {
      console.log("Keeping existing key. Done.");
      return 0;
    }
  }

  // Step 3: Create scoped key
  console.log("Creating scoped key...");
  let apiKey: string;
  try {
    apiKey = await immichCreateKey(server, token);
  } catch (e) {
    fail((e as Error).message);
    return 1;
  }

  // Done
  console.log();
  console.log(`${C.bold}================================================${C.reset}`);
  console.log(`  ${KEY_NAME} API Key (save this now)`);
  console.log(`${C.bold}================================================${C.reset}`);
  console.log();
  console.log(apiKey);
  console.log();
  console.log(`Permissions: ${SCOPED_PERMISSIONS.join(", ")}`);
  console.log("This key will NOT be shown again.");
  console.log();
  console.log("Enter it in the ImmichPhotoFrame app under Setup.");
  return 0;
}

// ─── check command ────────────────────────────────────────────────

async function cmdCheck(args: string[]): Promise<number> {
  if (args.length < 2) {
    printCheckUsage();
    return 1;
  }

  const server = args[0]!;
  const apiKey = args[1]!;
  const base = normalizeUrl(server);

  console.log("========================================");
  console.log("  Immich API Key Diagnostics");
  console.log("========================================");
  console.log(`Server: ${base}`);
  console.log();

  // 1. Ping
  info("Testing GET /server/ping ...");
  let pingRes: Response;
  try {
    pingRes = await fetch(apiUrl(base, "/server/ping"), {
      headers: { "x-api-key": apiKey },
    });
  } catch {
    fail(`Server unreachable at ${apiUrl(base, "/server/ping")}`);
    return 1;
  }
  if (pingRes.ok) {
    const pingBody = await pingRes.text().catch(() => "");
    pass(`Ping OK (HTTP ${pingRes.status}) — ${pingBody}`);
  } else {
    fail(`Ping failed (HTTP ${pingRes.status})`);
    return 1;
  }
  console.log();

  // 2. User
  info("Testing GET /users/me ...");
  const userRes = await fetch(apiUrl(base, "/users/me"), {
    headers: { "x-api-key": apiKey },
  });
  if (userRes.ok) {
    const user = (await userRes.json()) as { email?: string };
    pass(`User authenticated as: ${user.email ?? "(unknown email)"}`);
  } else {
    fail(`GET /users/me failed (HTTP ${userRes.status})`);
    if (userRes.status === 403) {
      console.log("  → Key is missing 'user.read' permission");
    }
    return 1;
  }
  console.log();

  // 3. Albums
  info("Testing GET /albums ...");
  const albumsRes = await fetch(apiUrl(base, "/albums"), {
    headers: { "x-api-key": apiKey },
  });
  let albumCount = 0;
  let firstAlbumId: string | null = null;
  if (albumsRes.ok) {
    const albums = (await albumsRes.json()) as Array<{ id: string }>;
    albumCount = albums.length;
    firstAlbumId = albums[0]?.id ?? null;
    pass(`Found ${albumCount} album(s)`);
  } else {
    fail(`GET /albums failed (HTTP ${albumsRes.status})`);
    if (albumsRes.status === 403) {
      console.log("  → Key is missing 'album.read' permission");
    }
    return 1;
  }
  console.log();

  if (albumCount === 0) {
    fail("No albums found — cannot test album assets");
    return 0;
  }

  // 4. Album assets via POST /search/metadata
  info(`Testing POST /search/metadata (album: ${firstAlbumId}) ...`);
  const searchRes = await fetch(apiUrl(base, "/search/metadata"), {
    method: "POST",
    headers: { "x-api-key": apiKey, "Content-Type": "application/json" },
    body: JSON.stringify({
      albumIds: [firstAlbumId],
      type: "IMAGE",
      size: 100,
    }),
  });
  let assetCount = 0;
  let firstAssetId: string | null = null;
  if (searchRes.ok) {
    const data = (await searchRes.json()) as {
      assets?: { items?: Array<{ id: string }> };
    };
    assetCount = data.assets?.items?.length ?? 0;
    firstAssetId = data.assets?.items?.[0]?.id ?? null;
    pass(`Album has ${assetCount} image asset(s)`);
    if (assetCount === 0) {
      info("No assets to test thumbnails with.");
    }
  } else {
    fail(`POST /search/metadata failed (HTTP ${searchRes.status})`);
    if (searchRes.status === 403) {
      console.log("  → Key is missing 'asset.read' permission");
    }
    return 1;
  }
  console.log();

  // 5. Thumbnail
  if (assetCount > 0 && firstAssetId) {
    info(`Testing thumbnail: GET /assets/${firstAssetId}/thumbnail?size=preview ...`);
    const thumbRes = await fetch(
      apiUrl(base, `/assets/${firstAssetId}/thumbnail?size=preview`),
      { headers: { "x-api-key": apiKey } },
    );
    if (thumbRes.ok) {
      pass(`Thumbnail loaded (HTTP ${thumbRes.status})`);
    } else {
      fail(`Thumbnail failed (HTTP ${thumbRes.status})`);
      if (thumbRes.status === 403) {
        console.log("  → Key is missing 'asset.view' permission");
      }
    }
  }

  console.log();
  console.log("========================================");
  pass("Diagnostics complete");
  console.log("========================================");
  return 0;
}

// ─── Help / usage ─────────────────────────────────────────────────

function printHelp(): void {
  console.log(`${C.bold}ImmichFrame Key Manager${C.reset}`);
  console.log();
  console.log("Create or verify a scoped Immich API key for ImmichPhotoFrame.");
  console.log();
  console.log(`${C.bold}Usage:${C.reset}`);
  console.log("  keymgr generate <server-url> <email> [password]");
  console.log("  keymgr check   <server-url> <api-key>");
  console.log();
  console.log(`${C.bold}Commands:${C.reset}`);
  console.log(`  ${C.cyan}generate${C.reset}  Create a least-privilege API key (4 permissions only).`);
  console.log("           You'll be prompted for the password if not provided.");
  console.log("           If an existing 'ImmichPhotoFrame' key is found, you'll be");
  console.log("           asked to update or recreate it.");
  console.log();
  console.log(`  ${C.cyan}check${C.reset}     Diagnose an existing API key — tests the 5 endpoints`);
  console.log("           ImmichPhotoFrame uses and reports which permissions are missing.");
  console.log();
  console.log(`${C.bold}Examples:${C.reset}`);
  console.log("  keymgr generate https://photos.example.com:2283 user@example.com");
  console.log("  keymgr check   https://photos.example.com:2283 myApiKey123");
}

function printGenerateUsage(): void {
  console.error("Usage: keymgr generate <server-url> <email> [password]");
  console.error("");
  console.error("Example:");
  console.error("  keymgr generate https://photos.example.com:2283 user@example.com");
}

function printCheckUsage(): void {
  console.error("Usage: keymgr check <server-url> <api-key>");
  console.error("");
  console.error("Example:");
  console.error("  keymgr check https://photos.example.com:2283 myApiKey123");
}

// ─── Entry point ──────────────────────────────────────────────────

async function main(): Promise<number> {
  const [subcommand, ...rest] = process.argv.slice(2);

  switch (subcommand) {
    case "generate":
    case "gen":
      return cmdGenerate(rest);
    case "check":
    case "verify":
      return cmdCheck(rest);
    case "help":
    case "--help":
    case "-h":
    case undefined:
      printHelp();
      return 0;
    default:
      console.error(`Unknown command: ${subcommand}`);
      console.error("");
      printHelp();
      return 1;
  }
}

main().then((code) => process.exit(code));
