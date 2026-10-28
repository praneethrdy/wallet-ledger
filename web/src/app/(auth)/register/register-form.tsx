"use client";

import { ArrowRightIcon } from "lucide-react";
import Link from "next/link";
import { useActionState, useState } from "react";

import { Field, FormError } from "@/components/forms/field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { register, type AuthFormState } from "../actions";

export function RegisterForm() {
  const [state, action, pending] = useActionState<AuthFormState, FormData>(register, undefined);
  const [password, setPassword] = useState("");
  const longEnough = password.length >= 10;

  return (
    <div className="mt-8 space-y-6">
      <form action={action} className="space-y-5" noValidate>
        <FormError error={state?.error?.code === "VALIDATION_FAILED" ? undefined : state?.error} />
        <Field label="Email" htmlFor="email" error={state?.error?.fieldErrors.email}>
          <Input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            required
            defaultValue={state?.email}
            aria-invalid={Boolean(state?.error?.fieldErrors.email) || undefined}
          />
        </Field>
        <Field
          label="Password"
          htmlFor="password"
          error={state?.error?.fieldErrors.password}
          help={
            <span className={longEnough ? "text-credit-text" : undefined}>
              {longEnough ? "✓ " : ""}At least 10 characters
            </span>
          }
        >
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            maxLength={128}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(state?.error?.fieldErrors.password) || undefined}
          />
        </Field>
        <Button type="submit" className="w-full" loading={pending}>
          Create account <ArrowRightIcon />
        </Button>
      </form>
      <p className="text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link href="/login" className="font-medium text-foreground underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
