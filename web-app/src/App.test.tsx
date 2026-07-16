import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("web workbench shell", () => {
  it("renders the Entio foundation view", () => {
    render(<App />);

    expect(screen.getByText("Web workbench")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Phase 6 foundation" })).toBeInTheDocument();
  });
});
