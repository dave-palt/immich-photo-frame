/**
 * Mock Immich server for testing keymgr.
 * Implements the minimal API contract needed:
 *   POST /api/auth/login
 *   GET  /api/api-keys
 *   POST /api/api-keys
 *   DELETE /api/api-keys/:id
 *   GET  /api/server/ping
 *   GET  /api/users/me
 *   GET  /api/albums
 *   POST /api/search/metadata
 *   GET  /api/assets/:id/thumbnail
 */
const PORT = 18923;

// State
const existingKeys: Array<{ id: string; name: string }> = [];
let keyIdCounter = 0;

const VALID_EMAIL = "test@immich.fake";
const VALID_PASSWORD = "password123";
const VALID_API_KEY = "mock-api-key-abc";

Bun.serve({
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);
    const path = url.pathname;
    const method = req.method;
    const apiKey = req.headers.get("x-api-key");
    const authHeader = req.headers.get("authorization");
    const token = authHeader?.replace("Bearer ", "");
    const isAuthed = token === "mock-access-token" || apiKey === VALID_API_KEY;

    // POST /api/auth/login
    if (path === "/api/auth/login" && method === "POST") {
      const body = await req.json();
      if (body.email === VALID_EMAIL && body.password === VALID_PASSWORD) {
        return Response.json({ accessToken: "mock-access-token" });
      }
      return new Response(JSON.stringify({ message: "Invalid credentials" }), {
        status: 401,
      });
    }

    // GET /api/api-keys
    if (path === "/api/api-keys" && method === "GET") {
      if (token !== "mock-access-token") {
        return new Response("{}", { status: 401 });
      }
      return Response.json(existingKeys);
    }

    // POST /api/api-keys
    if (path === "/api/api-keys" && method === "POST") {
      if (token !== "mock-access-token") {
        return new Response("{}", { status: 401 });
      }
      keyIdCounter++;
      const id = `key-${keyIdCounter}`;
      existingKeys.push({ id, name: "ImmichPhotoFrame" });
      return Response.json({ secret: `${VALID_API_KEY}-${keyIdCounter}` });
    }

    // DELETE /api/api-keys/:id
    if (path.startsWith("/api/api-keys/") && method === "DELETE") {
      const id = path.split("/").pop();
      const idx = existingKeys.findIndex((k) => k.id === id);
      if (idx >= 0) existingKeys.splice(idx, 1);
      return new Response(null, { status: 204 });
    }

    // --- Endpoints below require x-api-key auth ---

    // GET /api/server/ping
    if (path === "/api/server/ping" && method === "GET") {
      if (!isAuthed && apiKey) {
        return new Response("Forbidden", { status: 403 });
      }
      return Response.json({ resp: "pong" });
    }

    // GET /api/users/me
    if (path === "/api/users/me" && method === "GET") {
      if (!isAuthed) return new Response("Forbidden", { status: 403 });
      return Response.json({ id: "user-1", email: VALID_EMAIL });
    }

    // GET /api/albums
    if (path === "/api/albums" && method === "GET") {
      if (!isAuthed) return new Response("Forbidden", { status: 403 });
      return Response.json([
        { id: "album-1", albumName: "Test Album", assetCount: 2 },
      ]);
    }

    // POST /api/search/metadata
    if (path === "/api/search/metadata" && method === "POST") {
      if (!isAuthed) return new Response("Forbidden", { status: 403 });
      return Response.json({
        assets: {
          total: 2,
          count: 2,
          items: [{ id: "asset-1", type: "IMAGE" }],
        },
      });
    }

    // GET /api/assets/:id/thumbnail
    if (path.match(/^\/api\/assets\/[^/]+\/thumbnail$/) && method === "GET") {
      if (!isAuthed) return new Response("Forbidden", { status: 403 });
      return new Response("fake-image-data", {
        status: 200,
        headers: { "Content-Type": "image/jpeg" },
      });
    }

    return new Response("Not found", { status: 404 });
  },
});

console.log(`Mock Immich server on http://localhost:${PORT}`);
console.log(`  Email: ${VALID_EMAIL}`);
console.log(`  Password: ${VALID_PASSWORD}`);
console.log(`  Valid API key: ${VALID_API_KEY}`);
