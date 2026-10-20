import { describe, it } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

describe("Theme and Brand Identity Invariants", () => {
  const rootDir = path.resolve(__dirname, "../../..");
  const frontendDir = path.resolve(rootDir, "frontend");

  it("verifies authoritative brand logo assets exist and obsolete temporary assets are deleted", () => {
    const darkAssetPath = path.join(frontendDir, "public", "yukira-logo.png");
    const lightAssetPath = path.join(frontendDir, "public", "yukira-logo-light.png");
    assert.ok(
      fs.existsSync(darkAssetPath),
      "Dark theme asset yukira-logo.png must exist in frontend/public"
    );
    assert.ok(
      fs.existsSync(lightAssetPath),
      "Light theme asset yukira-logo-light.png must exist in frontend/public"
    );

    const deletedAssets = [
      "yukira-emblem-dark.png",
      "yukira-emblem-light.png",
      "yukira-logo-dark.png",
      "yukira-logo-v4.png",
      "yukira-logo-card.jpg",
    ];

    for (const deleted of deletedAssets) {
      assert.ok(
        !fs.existsSync(path.join(frontendDir, "public", deleted)),
        `Obsolete asset ${deleted} must not exist`
      );
    }
  });

  it("verifies globals.css defines light theme tokens on :root", () => {
    const cssPath = path.join(frontendDir, "app", "globals.css");
    assert.ok(fs.existsSync(cssPath), "globals.css must exist");
    const content = fs.readFileSync(cssPath, "utf-8");

    // Must define light theme tokens on :root
    assert.ok(content.includes("--background: #faf8f5;"), "Light theme background token must be #faf8f5");
    assert.ok(content.includes("--surface-midnight: #ffffff;"), "Light theme surface token must be #ffffff");
    assert.ok(content.includes("--text-cream-bright: #111622;"), "Light theme text token must be #111622");
    assert.ok(content.includes("--accent-gold: #b88e28;"), "Light theme gold token must be #b88e28 for WCAG contrast");
    assert.ok(content.includes("--border: #e6dcce;"), "Light theme border must be #e6dcce");
    assert.ok(content.includes("--border-subtle: #dfd7c8;"), "Light theme border subtle must be #dfd7c8");
  });

  it("verifies globals.css defines dark theme overrides for .dark and [data-theme='dark']", () => {
    const cssPath = path.join(frontendDir, "app", "globals.css");
    const content = fs.readFileSync(cssPath, "utf-8");

    assert.ok(content.includes(".dark, [data-theme=\"dark\"]"), "Must contain .dark, [data-theme='dark'] selector");
    assert.ok(content.includes("--background: #0a0e17;"), "Dark theme background must be #0a0e17");
    assert.ok(content.includes("--surface-midnight: #101826;"), "Dark theme surface must be #101826");
    assert.ok(content.includes("--text-cream-bright: #f8f6f0;"), "Dark theme text must be #f8f6f0");
    assert.ok(content.includes("--accent-gold: #d4af37;"), "Dark theme gold must be #d4af37");
    assert.ok(content.includes("--border: #2c281e;"), "Dark theme border must be #2c281e");
    assert.ok(content.includes("--border-subtle: rgba(212, 175, 55, 0.15);"), "Dark theme border subtle must be rgba(212, 175, 55, 0.15)");
  });

  it("verifies Tailwind @theme dynamically maps to theme CSS variables", () => {
    const cssPath = path.join(frontendDir, "app", "globals.css");
    const content = fs.readFileSync(cssPath, "utf-8");

    assert.ok(content.includes("@theme {"), "Must contain @theme block");
    assert.ok(content.includes("--color-surface-midnight: var(--surface-midnight);"), "Must map surface-midnight dynamically");
    assert.ok(content.includes("--color-text-cream-bright: var(--text-cream-bright);"), "Must map text-cream-bright dynamically");
    assert.ok(content.includes("--color-accent-gold: var(--accent-gold);"), "Must map accent-gold dynamically");
  });

  it("verifies YukiraBrandLogo supports dark and light theme assets", () => {
    const logoCompPath = path.join(frontendDir, "components", "brand", "YukiraBrandLogo.tsx");
    assert.ok(fs.existsSync(logoCompPath), "YukiraBrandLogo component must exist");
    const content = fs.readFileSync(logoCompPath, "utf-8");

    assert.ok(content.includes("/yukira-logo.png"), "Must reference /yukira-logo.png asset for dark theme");
    assert.ok(content.includes("/yukira-logo-light.png"), "Must reference /yukira-logo-light.png asset for light theme");
    assert.ok(!content.includes("/yukira-emblem-dark.png"), "Must not reference deleted dark emblem");
    assert.ok(!content.includes("/yukira-emblem-light.png"), "Must not reference deleted light emblem");
  });

  it("verifies ThemeToggle contains accessible labels and controls", () => {
    const togglePath = path.join(frontendDir, "components", "theme", "ThemeToggle.tsx");
    assert.ok(fs.existsSync(togglePath), "ThemeToggle component must exist");
    const content = fs.readFileSync(togglePath, "utf-8");

    assert.ok(content.includes("aria-label="), "ThemeToggle must provide an accessible aria-label");
    assert.ok(content.includes("toggleTheme"), "ThemeToggle must call toggleTheme");
    assert.ok(content.includes("title="), "ThemeToggle must provide title attribute for tooltip");
  });

  it("verifies layout.tsx defines themeColor viewport settings", () => {
    const layoutPath = path.join(frontendDir, "app", "layout.tsx");
    assert.ok(fs.existsSync(layoutPath), "layout.tsx must exist");
    const content = fs.readFileSync(layoutPath, "utf-8");

    assert.ok(content.includes("#faf8f5"), "Light theme color #faf8f5 must be in viewport");
    assert.ok(content.includes("#0a0e17"), "Dark theme color #0a0e17 must be in viewport");
  });
});
