"use client";

import { animate, useReducedMotion } from "motion/react";
import { useEffect, useRef, useState } from "react";

type AnimatedNumberProps = {
  /** Integer value (e.g. minor units). Animation interpolates between integers only. */
  value: number;
  format: (value: number) => string;
  className?: string;
  /** Count up from 0 on first mount (true) or start at the initial value (false). */
  fromZero?: boolean;
};

/**
 * Counts from the previous value to the new one when it changes (~0.6s, ease-out).
 * With reduced motion it simply shows the value. Screen readers only get the final value.
 */
export function AnimatedNumber({ value, format, className, fromZero = true }: AnimatedNumberProps) {
  const reduce = useReducedMotion();
  const [animated, setAnimated] = useState(fromZero ? 0 : value);
  const previous = useRef(fromZero ? 0 : value);

  useEffect(() => {
    if (reduce) {
      previous.current = value;
      return;
    }
    const controls = animate(previous.current, value, {
      duration: 0.6,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (v) => setAnimated(Math.round(v)),
    });
    previous.current = value;
    return () => controls.stop();
  }, [value, reduce]);

  const shown = reduce ? value : animated;
  return (
    <span className={className}>
      <span aria-hidden>{format(shown)}</span>
      <span className="sr-only">{format(value)}</span>
    </span>
  );
}
