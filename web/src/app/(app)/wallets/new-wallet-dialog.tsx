"use client";

import { PlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Field, FormError } from "@/components/forms/field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ActionError } from "@/lib/errors";
import { useIdempotencyKey } from "@/lib/idempotency";

import { createWallet } from "./actions";

export function NewWalletDialog({ variant = "default" }: { variant?: "default" | "outline" }) {
  const router = useRouter();
  const idempotency = useIdempotencyKey();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<ActionError>();
  const [pending, startTransition] = useTransition();

  function submit(form: FormData) {
    form.set("idempotencyKey", idempotency.get());
    startTransition(async () => {
      const result = await createWallet(form);
      idempotency.reset(); // a definite answer arrived: the next attempt is a new intent
      if (!result.ok) {
        setError(result.error);
        return;
      }
      setError(undefined);
      setOpen(false);
      toast.success(result.replayed ? "Wallet already created" : "Wallet created");
      router.push(`/wallets/${result.data.id}`);
    });
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setError(undefined);
      }}
    >
      <DialogTrigger asChild>
        <Button variant={variant}>
          <PlusIcon /> New wallet
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New wallet</DialogTitle>
          <DialogDescription>Each wallet holds one currency. You can have as many as you like.</DialogDescription>
        </DialogHeader>
        <form action={submit} onChange={idempotency.reset} className="grid gap-4">
          <FormError error={error?.code === "VALIDATION_FAILED" ? undefined : error} />
          <Field label="Name" htmlFor="wallet-name" error={error?.fieldErrors.name}>
            <Input
              id="wallet-name"
              name="name"
              placeholder="Holiday fund"
              maxLength={60}
              required
              autoFocus
              aria-invalid={Boolean(error?.fieldErrors.name) || undefined}
            />
          </Field>
          <Field label="Currency" htmlFor="wallet-currency" error={error?.fieldErrors.currency}>
            <Select name="currency" defaultValue="INR">
              <SelectTrigger id="wallet-currency" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="INR">INR · Indian rupee</SelectItem>
                <SelectItem value="USD">USD · US dollar</SelectItem>
                <SelectItem value="EUR">EUR · Euro</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <DialogFooter>
            <Button type="submit" loading={pending}>
              Create wallet
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
