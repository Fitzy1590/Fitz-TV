import { useEffect, useState } from "react";

export const THEMES = [
  { id: "midnight", name: "Midnight", swatch: ["#151d33", "#00ceb8"] },
  { id: "blackout", name: "Blackout", swatch: ["#000000", "#2bd9fe"] },
  { id: "daylight", name: "Daylight", swatch: ["#f4f6fb", "#2563eb"] },
  { id: "turf", name: "Turf", swatch: ["#0d1f17", "#4ade80"] },
  { id: "neon", name: "Neon", swatch: ["#140c26", "#ff4fd8"] },
  { id: "ember", name: "Ember", swatch: ["#1c1412", "#ff8a3d"] },
] as const;

export type ThemeId = (typeof THEMES)[number]["id"];

function read(): ThemeId {
  try {
    const t = localStorage.getItem("theme") as ThemeId | null;
    if (t && THEMES.some((x) => x.id === t)) return t;
  } catch {
    /* ignore */
  }
  return "midnight";
}

export function applyTheme(t: ThemeId) {
  document.documentElement.dataset.theme = t;
  const bg = getComputedStyle(document.documentElement).getPropertyValue("--bg").trim();
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", bg || "#151d33");
}

export function useTheme() {
  const [theme, setTheme] = useState<ThemeId>(read);
  useEffect(() => {
    applyTheme(theme);
    try {
      localStorage.setItem("theme", theme);
    } catch {
      /* ignore */
    }
  }, [theme]);
  return [theme, setTheme] as const;
}

applyTheme(read());
