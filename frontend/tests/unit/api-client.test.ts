import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { apiFetch } from "../../lib/api/client";
import { ApiError } from "../../types/api";

describe("apiFetch client tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("successfully parses JSON data on HTTP 200", async () => {
    const mockData = { id: 1, schemeName: "HDFC Top 100" };
    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockData), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const result = await apiFetch<typeof mockData>("/api/v1/schemes/1");
    assert.deepEqual(result, mockData);
  });

  it("throws structured ApiError on HTTP 404", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ error: "Scheme not found" }), {
        status: 404,
        statusText: "Not Found",
        headers: { "Content-Type": "application/json" },
      });

    await assert.rejects(
      async () => {
        await apiFetch("/api/v1/schemes/9999");
      },
      (err: ApiError) => {
        assert.equal(err.code, "HTTP_404");
        assert.equal(err.status, 404);
        assert.ok(err.message.includes("404"));
        return true;
      }
    );
  });

  it("throws structured ApiError on HTTP 500", async () => {
    globalThis.fetch = async () =>
      new Response("Internal Server Error", {
        status: 500,
        statusText: "Internal Server Error",
      });

    await assert.rejects(
      async () => {
        await apiFetch("/api/v1/calculations/run");
      },
      (err: ApiError) => {
        assert.equal(err.code, "HTTP_500");
        assert.equal(err.status, 500);
        return true;
      }
    );
  });

  it("throws NETWORK_ERROR on connection failure", async () => {
    globalThis.fetch = async () => {
      throw new Error("connect ECONNREFUSED 127.0.0.1:8080");
    };

    await assert.rejects(
      async () => {
        await apiFetch("/api/v1/health");
      },
      (err: ApiError) => {
        assert.equal(err.code, "NETWORK_ERROR");
        assert.ok(err.message.includes("Failed to connect"));
        return true;
      }
    );
  });
});
