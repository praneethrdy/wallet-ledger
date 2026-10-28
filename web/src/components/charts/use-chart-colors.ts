"use client";

import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

export type ChartColors = { brand: string; credit: string; muted: string; line: string; text: string; surface: string };

const FALLBACK: ChartColors = {
  brand: "#5b5bd6",
  credit: "#10b981",
  muted: "#a1a1aa",
  line: "#e4e4e7",
  text: "#71717a",
  surface: "#ffffff",
};

/**
 * Recharts writes colours into SVG attributes, where CSS variables aren't reliable, so we read
 * the design tokens' resolved values and re-read them whenever the theme changes.
 */
export function useChartColors(): ChartColors {
  const { resolvedTheme } = useTheme();
  const [colors, setColors] = useState<ChartColors>(FALLBACK);

  useEffect(() => {
    const css = getComputedStyle(document.documentElement);
    const read = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
    // Deferred so the theme class has been applied before we read.
    const id = requestAnimationFrame(() =>
      setColors({
        brand: read("--brand", FALLBACK.brand),
        credit: read("--credit", FALLBACK.credit),
        muted: read("--line-strong", FALLBACK.muted),
        line: read("--line", FALLBACK.line),
        text: read("--text-muted", FALLBACK.text),
        surface: read("--surface", FALLBACK.surface),
      }),
    );
    return () => cancelAnimationFrame(id);
  }, [resolvedTheme]);

  return colors;
}
