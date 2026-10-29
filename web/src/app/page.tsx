import type { Metadata } from "next";

import { LandingPage } from "@/components/landing/landing-page";

export const metadata: Metadata = {
  title: "Wallet Ledger: money that always adds up",
  description:
    "A digital wallet on a double-entry ledger: idempotent transfers, signed webhooks, reversals instead of edits and an audited staff console. Java 21, Spring Boot, PostgreSQL, Next.js.",
};

/** Signed-out visitors land here; the proxy sends signed-in users straight to their home area. */
export default function Home() {
  return <LandingPage />;
}
