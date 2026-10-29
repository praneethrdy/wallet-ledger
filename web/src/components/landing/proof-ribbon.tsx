"use client";

import { animate, useInView, useReducedMotion } from "motion/react";
import { useEffect, useRef, useState } from "react";

type Fact = { label: string } & ({ value: number; prefix?: string; suffix?: string } | { text: string });

/** All true for this project (see README and the test suites). */
const FACTS: Fact[] = [
  { value: 234, label: "backend tests on real PostgreSQL" },
  { value: 26, label: "end-to-end browser tests" },
  { value: 0, label: "ways to edit history" },
  { value: 100, label: "parallel transfers, zero overspend" },
  { value: 2, label: "ledger legs in every transfer" },
  { value: 1, label: "Idempotency-Key, one transfer, however many retries" },
  { value: 80, suffix: "%+", label: "test coverage gate on the money code" },
  { text: "WCAG AA", label: "contrast, light and dark" },
];

/**
 * The "in numbers" strip as an endless ribbon gliding left to right. Two identical copies sit
 * side by side and each slides one full width, so the loop has no seam; the edges fade out.
 * Hover or keyboard focus pauses it. Screen readers get one plain list (the copy is hidden),
 * and with reduced motion it's a still, wrapped row.
 */
export function ProofRibbon() {
  return (
    <section aria-label="In numbers" className="relative overflow-hidden border-y border-border bg-card/50">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-[linear-gradient(90deg,transparent,var(--brand),transparent)] opacity-40" />
      <div
        tabIndex={0}
        aria-label="Project facts (pauses on hover or focus)"
        className="group flex [mask-image:linear-gradient(to_right,transparent,black_10%,black_90%,transparent)] focus-visible:outline-none motion-reduce:[mask-image:none]"
      >
        <Track />
        <Track copy />
      </div>
    </section>
  );
}

function Track({ copy = false }: { copy?: boolean }) {
  return (
    <ul
      aria-hidden={copy || undefined}
      className={
        "flex shrink-0 animate-marquee-ltr items-center group-hover:[animation-play-state:paused] group-focus-visible:[animation-play-state:paused] " +
        "motion-reduce:w-full motion-reduce:shrink motion-reduce:animate-none motion-reduce:flex-wrap motion-reduce:justify-center" +
        (copy ? " motion-reduce:hidden" : "")
      }
    >
      {FACTS.map((fact) => (
        <li key={fact.label} className="flex items-center">
          <div className="flex items-baseline gap-3 px-8 py-7">
            <span className="animate-shine bg-[linear-gradient(110deg,var(--foreground)_40%,var(--brand)_50%,var(--foreground)_60%)] bg-[length:250%_100%] bg-clip-text num text-3xl font-medium tracking-tight text-transparent motion-reduce:animate-none">
              {"text" in fact ? fact.text : <CountUp to={fact.value} prefix={fact.prefix} suffix={fact.suffix} />}
            </span>
            <span className="text-sm whitespace-nowrap text-muted-foreground">{fact.label}</span>
          </div>
          <span
            className="size-1.5 shrink-0 rounded-full bg-brand shadow-[0_0_10px_2px_color-mix(in_oklab,var(--brand)_60%,transparent)]"
            aria-hidden
          />
        </li>
      ))}
    </ul>
  );
}

/** Counts from 0 the first time it scrolls into view (instantly with reduced motion). */
function CountUp({ to, prefix = "", suffix = "" }: { to: number; prefix?: string; suffix?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: "-40px" });
  const reduce = useReducedMotion();
  const [n, setN] = useState(0);

  useEffect(() => {
    if (!inView || reduce) return;
    const controls = animate(0, to, {
      duration: 1.6,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => setN(Math.round(v)),
    });
    return () => controls.stop();
  }, [inView, reduce, to]);

  const shown = reduce ? to : n;
  return (
    <span ref={ref}>
      <span aria-hidden>
        {prefix}
        {shown}
        {suffix}
      </span>
      <span className="sr-only">
        {prefix}
        {to}
        {suffix}
      </span>
    </span>
  );
}
