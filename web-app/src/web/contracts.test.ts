import { describe, expect, it } from "vitest";
import { encodeWebIri, normalizePageRequest } from "./contracts";

describe("web contract helpers", () => {
  it("encodes IRIs before they are placed in a path", () => {
    expect(encodeWebIri("https://example.com/entio/simple#Customer")).toBe(
      "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Customer",
    );
  });

  it("keeps pagination within the server bounds", () => {
    expect(normalizePageRequest({ offset: 10, limit: 25 })).toEqual({ offset: 10, limit: 25 });
    expect(() => normalizePageRequest({ limit: 101 })).toThrow("limit-must-be-between-1-and-100");
  });
});
