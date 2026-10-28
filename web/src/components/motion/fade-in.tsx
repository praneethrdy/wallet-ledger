"use client";

import { motion, useReducedMotion } from "motion/react";

type FadeInProps = React.ComponentProps<typeof motion.div> & {
  /** Seconds to wait before animating (for gentle staggering of siblings). */
  delay?: number;
};

/** Content fades in and rises 4px on mount (just fades with reduced motion). */
export function FadeIn({ delay = 0, children, ...props }: FadeInProps) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      initial={{ opacity: 0, y: reduce ? 0 : 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: "easeOut", delay }}
      {...props}
    >
      {children}
    </motion.div>
  );
}
