"use client";

import { ArrowRightIcon, Undo2Icon } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { Field, FormError } from "@/components/forms/field";
import { TAccount } from "@/components/ledger/t-account";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Schemas } from "@/lib/api/client";
import type { ActionError } from "@/lib/errors";
import { useIdempotencyKey } from "@/lib/idempotency";

import { reverseTransaction } from "../../../actions";

const MAX = 500;
/** Refusals that mean "this can never be reversed now": stop offering the button. */
const FINAL = new Set(["ALREADY_REVERSED", "REVERSAL_NOT_ALLOWED"]);

export function ReverseForm({ tx }: { tx: Schemas["TransactionDetail"] }) {
  const router = useRouter();
  const idempotency = useIdempotencyKey();
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<ActionError>();
  const [pending, startTransition] = useTransition();

  if (tx.reversedByTransactionId || tx.type === "REVERSAL") {
    return (
      <Alert>
        <Undo2Icon />
        <AlertTitle>{tx.type === "REVERSAL" ? "A reversal can’t be reversed" : "Already reversed"}</AlertTitle>
        <AlertDescription>
          <p>
            {tx.type === "REVERSAL"
              ? "To undo a reversal, make a new transfer instead. Reversals only ever point back to an original."
              : "This transaction has a reversal already, so its net effect is zero."}
          </p>
          <Link
            href={`/support/transactions/${tx.reversedByTransactionId ?? tx.reversalOfTransactionId}`}
            className="mt-1 inline-flex items-center gap-1 font-medium text-foreground underline-offset-4 hover:underline"
          >
            {tx.type === "REVERSAL" ? "View the original" : "View the reversal"} <ArrowRightIcon className="size-3.5" />
          </Link>
        </AlertDescription>
      </Alert>
    );
  }

  const trimmed = reason.trim();
  const reasonError =
    touched && !trimmed
      ? "Say why this is being reversed"
      : reason.length > MAX
        ? `Use at most ${MAX} characters`
        : undefined;
  const flipped = tx.legs.map((l) => ({
    ...l,
    direction: l.direction === "DEBIT" ? ("CREDIT" as const) : ("DEBIT" as const),
  }));

  function confirm() {
    const form = new FormData();
    form.set("transactionId", tx.id);
    form.set("reason", trimmed);
    form.set("idempotencyKey", idempotency.get());
    startTransition(async () => {
      const res = await reverseTransaction(form);
      if (res.ok) {
        idempotency.reset();
        toast.success(res.replayed ? "Already reversed by this request" : "Reversal posted", {
          description: res.data.reference,
        });
        router.push(`/support/transactions/${res.data.id}`);
        return;
      }
      if (res.error.code !== "INTERNAL_ERROR" && res.error.code !== "NETWORK_ERROR") idempotency.reset();
      setError(res.error);
      setOpen(false);
      if (FINAL.has(res.error.code)) router.refresh(); // show the current state (e.g. the existing reversal)
    });
  }

  return (
    <form
      noValidate
      className="grid gap-5 surface p-6"
      onSubmit={(e) => {
        e.preventDefault();
        setTouched(true);
        if (trimmed && reason.length <= MAX) {
          setError(undefined);
          setOpen(true);
        }
      }}
    >
      <Field
        label="Reason"
        htmlFor="reason"
        error={reasonError ?? error?.fieldErrors.reason}
        help={`Required · recorded in the audit log · ${reason.length}/${MAX}`}
      >
        <Textarea
          id="reason"
          rows={3}
          value={reason}
          placeholder="e.g. Customer sent to the wrong wallet; confirmed by ticket #1234"
          onChange={(e) => {
            setReason(e.target.value);
            idempotency.reset();
          }}
          onBlur={() => setTouched(true)}
          aria-invalid={Boolean(reasonError) || undefined}
        />
      </Field>
      <FormError error={error && !error.fieldErrors.reason ? error : undefined} />
      <div className="flex justify-end">
        <Button type="submit" variant="destructive">
          <Undo2Icon /> Reverse transaction…
        </Button>
      </div>

      <AlertDialog open={open} onOpenChange={(o) => !pending && setOpen(o)}>
        <AlertDialogContent className="sm:max-w-xl">
          <AlertDialogHeader>
            <AlertDialogTitle>Post a reversal of {tx.reference}?</AlertDialogTitle>
            <AlertDialogDescription>
              This posts a new REVERSAL transaction; history is not changed. The money moves back as shown below, and it
              can’t be reversed again.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="text-sm">
            <TAccount legs={flipped} showIds />
            <p className="mt-3 text-muted-foreground">
              Reason: <span className="text-foreground">{trimmed}</span>
            </p>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>Cancel</AlertDialogCancel>
            <Button variant="destructive" onClick={confirm} loading={pending}>
              Post reversal
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </form>
  );
}
