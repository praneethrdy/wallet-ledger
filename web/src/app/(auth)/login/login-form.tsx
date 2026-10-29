"use client";

import { ArrowRightIcon, FlaskConicalIcon } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useActionState, useState } from "react";

import { Field, FormError } from "@/components/forms/field";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { login, type AuthFormState } from "../actions";

export type DemoAccount = { label: string; role: string; email: string; password: string };

export function LoginForm({ demoAccounts }: { demoAccounts?: DemoAccount[] }) {
  const params = useSearchParams();
  const [state, action, pending] = useActionState<AuthFormState, FormData>(login, undefined);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const shownEmail = email || state?.email || "";

  return (
    <div className="mt-8 space-y-6">
      {params.get("expired") && (
        <Alert>
          <AlertDescription>Your session has ended. Please sign in again.</AlertDescription>
        </Alert>
      )}
      {params.get("signedOut") === "all" && (
        <Alert>
          <AlertDescription>You’ve been signed out on all devices.</AlertDescription>
        </Alert>
      )}

      <form action={action} className="space-y-5" noValidate>
        <input type="hidden" name="next" value={params.get("next") ?? ""} />
        <FormError error={state?.error?.code === "VALIDATION_FAILED" ? undefined : state?.error} />
        <Field label="Email" htmlFor="email" error={state?.error?.fieldErrors.email}>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            required
            value={shownEmail}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={Boolean(state?.error?.fieldErrors.email) || undefined}
          />
        </Field>
        <Field label="Password" htmlFor="password" error={state?.error?.fieldErrors.password}>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(state?.error?.fieldErrors.password) || undefined}
          />
        </Field>
        <Button type="submit" className="w-full" loading={pending}>
          Sign in <ArrowRightIcon />
        </Button>
      </form>

      <p className="text-center text-sm text-muted-foreground">
        New here?{" "}
        <Link href="/register" className="font-medium text-foreground underline-offset-4 hover:underline">
          Create an account
        </Link>
      </p>

      {demoAccounts && (
        <section aria-labelledby="demo-heading" className="rounded-xl border border-dashed border-border p-4">
          <div className="mb-3 flex items-center gap-2">
            <FlaskConicalIcon className="size-3.5 text-muted-foreground" aria-hidden />
            <h2 id="demo-heading" className="eyebrow">
              {demoAccounts.length === 1 ? "Try it without signing up" : "Demo accounts · dev only"}
            </h2>
          </div>
          {demoAccounts.length === 1 && (
            <p className="-mt-1 mb-3 text-xs text-muted-foreground">A shared demo customer. No real money moves.</p>
          )}
          <div className="grid grid-cols-2 gap-2">
            {demoAccounts.map((account) => (
              <button
                key={account.email}
                type="button"
                onClick={() => {
                  setEmail(account.email);
                  setPassword(account.password);
                }}
                className="rounded-lg border border-border bg-card px-3 py-2 text-left shadow-xs transition-colors duration-150 hover:border-line-strong hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              >
                <div className="text-sm font-medium">{account.label}</div>
                <div className="text-xs text-muted-foreground">{account.role}</div>
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
