import type { Metadata } from "next";
import { Suspense } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { env } from "@/lib/env";

import { LoginForm, type DemoAccount } from "./login-form";

export const metadata: Metadata = { title: "Sign in" };

/** Seeded dev users (backend DevDataSeeder). Only sent to the browser when the mock provider is on. */
const DEMO_ACCOUNTS: DemoAccount[] = [
  { label: "Alice", role: "Customer", email: "alice@wallet.local", password: "alice-pass-123" },
  { label: "Bob", role: "Customer", email: "bob@wallet.local", password: "bob-pass-123" },
  { label: "Support", role: "Support", email: "support@wallet.local", password: "support-pass-123" },
  { label: "Admin", role: "Admin", email: "admin@wallet.local", password: "admin-pass-123" },
];

export default function LoginPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Welcome back</h1>
      <p className="mt-1.5 text-sm text-muted-foreground">Sign in to your wallet.</p>
      <Suspense fallback={<FormSkeleton />}>
        <LoginForm demoAccounts={env.mockProviderEnabled ? DEMO_ACCOUNTS : undefined} />
      </Suspense>
    </>
  );
}

function FormSkeleton() {
  return (
    <div className="mt-8 space-y-5">
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
    </div>
  );
}
