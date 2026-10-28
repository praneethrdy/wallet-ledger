import type { Metadata } from "next";

import { StyleGuide } from "./style-guide";

export const metadata: Metadata = {
  title: "Design system",
  robots: { index: false },
};

/** Living style guide: every token, component and state, in the current theme. */
export default function DesignPage() {
  return <StyleGuide />;
}
