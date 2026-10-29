"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeftIcon, ArrowRightIcon, CheckIcon, SendIcon } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState, useTransition } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { Field, FormError } from "@/components/forms/field";
import { MoneyInput } from "@/components/forms/money-input";
import { CopyButton } from "@/components/ledger/copy-button";
import { DoubleEntryPreview } from "@/components/ledger/double-entry-preview";
import { TAccount } from "@/components/ledger/t-account";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { Schemas } from "@/lib/api/client";
import type { ActionError } from "@/lib/errors";
import { isUuid, shortId } from "@/lib/format";
import { useIdempotencyKey } from "@/lib/idempotency";
import { formatMoney, moneyParseMessage, parseMoney } from "@/lib/money";

import { transfer } from "./actions";

type Wallet = Schemas["WalletResponse"];
type Step = "edit" | "review" | "done";

/** Errors where the outcome is unknown (or still running): retrying with the SAME key is safe. */
const RETRY_WITH_SAME_KEY = new Set(["INTERNAL_ERROR", "NETWORK_ERROR", "IDEMPOTENCY_REQUEST_IN_PROGRESS"]);

export function TransferFlow({
  wallets,
  allWallets,
  initialFrom,
  initialTo,
}: {
  wallets: Wallet[];
  allWallets: Wallet[];
  initialFrom: string;
  initialTo: string;
}) {
  const router = useRouter();
  const reduce = useReducedMotion();
  const idempotency = useIdempotencyKey();
  const [step, setStep] = useState<Step>("edit");
  const [error, setError] = useState<ActionError>();
  const [result, setResult] = useState<Schemas["TransactionDetail"]>();
  const [pending, startTransition] = useTransition();

  const schema = useMemo(
    () =>
      z
        .object({
          fromWalletId: z.string().min(1, "Choose a wallet"),
          toWalletId: z.string().trim().refine(isUuid, "Paste a wallet ID like 01a181c2-42c4-7ade-97e5-4557181301dc"),
          amount: z.string(),
          note: z.string().max(140, "Use at most 140 characters"),
        })
        .superRefine((v, ctx) => {
          const from = wallets.find((w) => w.id === v.fromWalletId);
          if (!from) return;
          if (v.toWalletId.trim() === from.id) {
            ctx.addIssue({ code: "custom", path: ["toWalletId"], message: "Choose a different wallet to send to" });
          }
          const parsed = parseMoney(v.amount, from.currency);
          if (!parsed.ok) {
            ctx.addIssue({ code: "custom", path: ["amount"], message: moneyParseMessage(parsed.error, from.currency) });
          } else if (parsed.minor > from.balance) {
            ctx.addIssue({
              code: "custom",
              path: ["amount"],
              message: `More than this wallet’s balance (${formatMoney(from.balance, from.currency)})`,
            });
          }
        }),
    [wallets],
  );

  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { fromWalletId: initialFrom, toWalletId: initialTo, amount: "", note: "" },
    mode: "onTouched",
  });
  const values = useWatch({ control: form.control });
  const from = wallets.find((w) => w.id === values.fromWalletId) ?? wallets[0];
  const parsedAmount = parseMoney(values.amount ?? "", from.currency);
  const ownTarget = allWallets.find((w) => w.id === values.toWalletId?.trim());
  const otherOwn = allWallets.filter((w) => w.id !== from.id && w.currency === from.currency && w.status === "ACTIVE");

  function edit() {
    idempotency.reset(); // changing the request = a new intent
    setError(undefined);
    setStep("edit");
  }

  function confirm() {
    const fd = new FormData();
    fd.set("fromWalletId", from.id);
    fd.set("toWalletId", (values.toWalletId ?? "").trim());
    fd.set("amount", values.amount ?? "");
    fd.set("currency", from.currency);
    fd.set("note", values.note ?? "");
    fd.set("idempotencyKey", idempotency.get());

    startTransition(async () => {
      const res = await transfer(fd);
      if (res.ok) {
        idempotency.reset();
        setResult(res.data);
        setError(undefined);
        setStep("done");
        if (res.replayed) toast("Already submitted", { description: "Showing the original result." });
        router.refresh(); // balances elsewhere in the app are now stale
        return;
      }
      setError(res.error);
      if (RETRY_WITH_SAME_KEY.has(res.error.code)) return; // stay on review; "Try again" reuses the key
      idempotency.reset();
      const fields = Object.entries(res.error.fieldErrors);
      if (fields.length) {
        for (const [name, message] of fields) {
          form.setError(name as keyof z.infer<typeof schema>, { message });
        }
        setStep("edit");
      }
    });
  }

  const slide = {
    initial: { opacity: 0, x: reduce ? 0 : 12 },
    animate: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: reduce ? 0 : -12 },
    transition: { duration: 0.18, ease: "easeOut" as const },
  };

  return (
    <div className="grid max-w-5xl gap-8 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="min-w-0">
        <Steps step={step} />
        <AnimatePresence mode="wait" initial={false}>
          {step === "edit" && (
            <motion.form
              key="edit"
              {...slide}
              noValidate
              onSubmit={form.handleSubmit(() => {
                setError(undefined);
                setStep("review");
              })}
              className="mt-6 grid gap-5 surface p-6"
            >
              <Field label="From" htmlFor="fromWalletId" error={form.formState.errors.fromWalletId?.message}>
                <Select
                  value={values.fromWalletId}
                  onValueChange={(v) => {
                    form.setValue("fromWalletId", v, { shouldValidate: form.formState.isSubmitted });
                    idempotency.reset();
                  }}
                >
                  <SelectTrigger id="fromWalletId" className="h-11 w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {wallets.map((w) => (
                      <SelectItem key={w.id} value={w.id}>
                        <span className="flex w-full items-center justify-between gap-6">
                          <span>{w.name}</span>
                          <span className="num text-muted-foreground">{formatMoney(w.balance, w.currency)}</span>
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field
                label="To wallet ID"
                htmlFor="toWalletId"
                error={form.formState.errors.toWalletId?.message}
                help={
                  ownTarget ? (
                    <span className="text-credit-text">Your wallet · {ownTarget.name}</span>
                  ) : (
                    "Ask the recipient for their wallet ID (they can copy it from Wallets)."
                  )
                }
              >
                <Input
                  id="toWalletId"
                  className="num"
                  placeholder="01a181c2-42c4-7ade-97e5-4557181301dc"
                  autoComplete="off"
                  spellCheck={false}
                  aria-invalid={Boolean(form.formState.errors.toWalletId) || undefined}
                  {...form.register("toWalletId", { onChange: idempotency.reset })}
                />
              </Field>
              {otherOwn.length > 0 && (
                <div className="-mt-2 flex flex-wrap items-center gap-2">
                  <span className="text-xs text-muted-foreground">Your wallets:</span>
                  {otherOwn.map((w) => (
                    <button
                      key={w.id}
                      type="button"
                      onClick={() => {
                        form.setValue("toWalletId", w.id, { shouldValidate: true });
                        idempotency.reset();
                      }}
                      className="rounded-md border border-border bg-card px-2 py-1 text-xs shadow-xs transition-colors hover:border-line-strong hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                    >
                      {w.name}
                    </button>
                  ))}
                </div>
              )}

              <Field
                label="Amount"
                htmlFor="amount"
                error={form.formState.errors.amount?.message}
                help={`Available ${formatMoney(from.balance, from.currency)}`}
              >
                <MoneyInput
                  id="amount"
                  currency={from.currency}
                  aria-invalid={Boolean(form.formState.errors.amount) || undefined}
                  {...form.register("amount", { onChange: idempotency.reset })}
                />
              </Field>

              <Field
                label="Note"
                htmlFor="note"
                error={form.formState.errors.note?.message}
                help={`Optional · ${(values.note ?? "").length}/140`}
              >
                <Textarea
                  id="note"
                  rows={2}
                  maxLength={140}
                  placeholder="What’s it for?"
                  {...form.register("note", { onChange: idempotency.reset })}
                />
              </Field>

              <div className="flex justify-end">
                <Button type="submit">
                  Review <ArrowRightIcon />
                </Button>
              </div>
            </motion.form>
          )}

          {step === "review" && parsedAmount.ok && (
            <motion.div key="review" {...slide} className="mt-6 grid gap-5">
              <DoubleEntryPreview
                from={{ label: `Your wallet · ${from.name}`, detail: from.id }}
                to={{
                  label: ownTarget ? `Your wallet · ${ownTarget.name}` : "Recipient",
                  detail: (values.toWalletId ?? "").trim(),
                }}
                amount={parsedAmount.minor}
                currency={from.currency}
              />
              {values.note && (
                <p className="px-1 text-sm text-muted-foreground">
                  Note: <span className="text-foreground">{values.note}</span>
                </p>
              )}
              <FormError error={error} />
              <div className="flex flex-wrap justify-between gap-3">
                <Button variant="ghost" onClick={edit} disabled={pending}>
                  <ArrowLeftIcon /> Edit
                </Button>
                <Button onClick={confirm} loading={pending}>
                  <SendIcon /> {error && RETRY_WITH_SAME_KEY.has(error.code) ? "Try again" : "Confirm transfer"}
                </Button>
              </div>
            </motion.div>
          )}

          {step === "done" && result && (
            <motion.div key="done" {...slide} className="mt-6 grid gap-5">
              <div className="flex items-start gap-4 surface p-6">
                <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[color-mix(in_oklab,var(--credit)_14%,transparent)] text-credit-text">
                  <CheckIcon className="size-5" />
                </span>
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold tracking-tight">
                    {formatMoney(result.legs.find((l) => l.direction === "DEBIT")?.amount ?? 0, from.currency)} sent
                  </h2>
                  <p className="mt-0.5 flex items-center gap-1 text-sm text-muted-foreground">
                    Reference <span className="num text-foreground">{result.reference}</span>
                    <CopyButton value={result.reference} label="Copy reference" className="size-6" />
                  </p>
                </div>
              </div>
              <TAccount legs={result.legs} />
              <div className="flex flex-wrap justify-end gap-3">
                <Button variant="outline" asChild>
                  <Link href={`/transactions/${result.id}`}>View transaction</Link>
                </Button>
                <Button
                  onClick={() => {
                    form.reset({ fromWalletId: from.id, toWalletId: "", amount: "", note: "" });
                    setResult(undefined);
                    setStep("edit");
                  }}
                >
                  Send another
                </Button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <aside className="hidden lg:block">
        <div className="sticky top-24 space-y-3 surface p-5 text-sm">
          <p className="eyebrow">How transfers work</p>
          <p className="text-muted-foreground">
            A transfer is one ledger transaction with two legs: a <span className="text-foreground">debit</span> from
            your wallet and a <span className="text-foreground">credit</span> to theirs. They post together, or not at
            all.
          </p>
          <p className="text-muted-foreground">
            Pressing confirm twice (or a flaky connection) can’t send twice: each transfer carries a one-time key, and
            repeats return the original result.
          </p>
          <p className="text-muted-foreground">
            Sending from <span className="text-foreground">{from.name}</span> · {shortId(from.id)}
          </p>
        </div>
      </aside>
    </div>
  );
}

function Steps({ step }: { step: Step }) {
  const steps: { id: Step; label: string }[] = [
    { id: "edit", label: "Details" },
    { id: "review", label: "Review" },
    { id: "done", label: "Done" },
  ];
  const current = steps.findIndex((s) => s.id === step);
  return (
    <ol className="flex items-center gap-3 text-sm" aria-label="Progress">
      {steps.map((s, i) => (
        <li key={s.id} className="flex items-center gap-3" aria-current={i === current ? "step" : undefined}>
          <span
            className={
              i <= current
                ? "grid size-6 place-items-center rounded-full bg-primary text-xs font-medium text-primary-foreground"
                : "grid size-6 place-items-center rounded-full border border-border text-xs text-muted-foreground"
            }
          >
            {i < current ? <CheckIcon className="size-3.5" /> : i + 1}
          </span>
          <span className={i === current ? "font-medium" : "text-muted-foreground"}>{s.label}</span>
          {i < steps.length - 1 && <span className="h-px w-8 bg-border" aria-hidden />}
        </li>
      ))}
    </ol>
  );
}
