import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import MarkdownContent from "./MarkdownContent";

describe("MarkdownContent", () => {
  it("formats assistant headings, lists, emphasis, code, and links", () => {
    render(<MarkdownContent>{"## Ontology\n\n1. **Classes**\n2. `ownsAccount`\n\n[Evidence](https://example.com)"}</MarkdownContent>);

    expect(screen.getByRole("heading", { name: "Ontology" })).toBeInTheDocument();
    expect(screen.getByRole("list")).toBeInTheDocument();
    expect(screen.getByText("Classes").tagName).toBe("STRONG");
    expect(screen.getByText("ownsAccount").tagName).toBe("CODE");
    expect(screen.getByRole("link", { name: "Evidence" })).toHaveAttribute("href", "https://example.com");
  });
});
