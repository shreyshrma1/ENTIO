import { describe, expect, it } from "vitest";
import { entityKindPresentation } from "./entityKindPresentation";

describe("entity kind presentation", () => {
  it("maps semantic kinds to the sidebar markers", () => {
    expect(entityKindPresentation("Class").marker).toBe("C");
    expect(entityKindPresentation("Individual").marker).toBe("O");
    expect(entityKindPresentation("ObjectProperty").marker).toBe("P");
    expect(entityKindPresentation("DatatypeProperty").marker).toBe("P");
    expect(entityKindPresentation("AnnotationProperty").marker).toBe("P");
  });
});
