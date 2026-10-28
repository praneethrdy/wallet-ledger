"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { CheckIcon, CreditCardIcon, LoaderIcon, PlusIcon, RefreshCwIcon, XIcon } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState, useTransition } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { Field, FormError } from "@/components/forms/field";
import { MoneyInput } from "@/components/forms/money-input";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Schemas } from "@/lib/api/client";
import { bffGet } from "@/lib/bff-client";
import type { ActionError } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import { useIdempotencyKey } from "@/lib/idempotency";
import { formatMoney, minorDigits, moneyParseMessage, parseMoney, toDecimalString } from "@/lib/money";

import { completeMockPayment, createDeposit } from "./actions";

type Wallet = Schemas["WalletResponse"];
type Deposit = Schemas["DepositResponse"];

const POLL_MS = 1500;
const POLL_ROUND_MS = 60_000; // then stop and let the user choose to keep waiting
const QUICK_AMOUNTS: Record<string, number[]> = { INR: [500, 1000, 5000], USD: [10, 50, 100], EUR: [10, 50, 100] };

export function DepositFlow({
  wallets,
  allWallets,
  initialWallet,
  initialDeposit,
  mockProvider,
}: {
  wallets: Wallet[];
  allWallets: Wallet[];
  initialWallet: string | undefined;
  initialDeposit: Deposit | undefined;
  mockProvider: boolean;
}) {
  const reduce = useReducedMotion();
  const [deposit, setDeposit] = useState(initialDeposit);
  // What the form starts with next time: the last deposit's wallet (and amount, when retrying).
  const [draft, setDraft] = useState({ walletId: initialWallet, amount: "" });

  function startOver(from: Deposit) {
    const walletId = wallets.some((w) => w.id === from.walletId) ? from.walletId : initialWallet;
    const amount = from.status === "FAILED" ? toDecimalString(from.amount, from.currency) : "";
    setDraft({ walletId, amount });
    show(undefined);
  }

  function show(next: Deposit | undefined) {
    setDeposit(next);
    // Keep the URL resumable (reload = keep watching this deposit) without a server round trip.
    window.history.replaceState(null, "", next ? `/deposit?id=${next.id}` : "/deposit");
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
        <AnimatePresence mode="wait" initial={false}>
          {deposit ? (
            <motion.div key={deposit.id} {...slide}>
              <DepositStatus
                initial={deposit}
                wallet={allWallets.find((w) => w.id === deposit.walletId)}
                mockProvider={mockProvider}
                onNew={startOver}
              />
            </motion.div>
          ) : (
            <motion.div key="form" {...slide}>
              <DepositForm wallets={wallets} initial={draft} onCreated={show} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <aside className="hidden lg:block">
        <div className="sticky top-24 space-y-3 surface p-5 text-sm">
          <p className="eyebrow">How deposits work</p>
          <p className="text-muted-foreground">
            Starting a deposit doesn’t move money. It stays <span className="text-foreground">pending</span> until the
            payment provider confirms with a webhook, signed so it can’t be forged.
          </p>
          <p className="text-muted-foreground">
            Then one balanced transaction posts: a <span className="text-foreground">credit</span> to your wallet and a{" "}
            <span className="text-foreground">debit</span> to the external funding account. A failed payment posts
            nothing.
          </p>
          <p className="text-muted-foreground">
            Duplicate webhooks are recognised and ignored, so you’re never credited twice.
          </p>
        </div>
      </aside>
    </div>
  );
}

function DepositForm({
  wallets,
  initial,
  onCreated,
}: {
  wallets: Wallet[];
  initial: { walletId: string | undefined; amount: string };
  onCreated: (deposit: Deposit) => void;
}) {
  const idempotency = useIdempotencyKey();
  const [error, setError] = useState<ActionError>();
  const [pending, startTransition] = useTransition();

  const schema = useMemo(
    () =>
      z.object({ walletId: z.string().min(1, "Choose a wallet"), amount: z.string() }).superRefine((v, ctx) => {
        const wallet = wallets.find((w) => w.id === v.walletId);
        if (!wallet) return;
        const parsed = parseMoney(v.amount, wallet.currency);
        if (!parsed.ok) {
          ctx.addIssue({ code: "custom", path: ["amount"], message: moneyParseMessage(parsed.error, wallet.currency) });
        }
      }),
    [wallets],
  );
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { walletId: initial.walletId ?? "", amount: initial.amount },
    mode: "onTouched",
  });
  const values = useWatch({ control: form.control });
  const wallet = wallets.find((w) => w.id === values.walletId);

  if (!wallet) {
    return (
      <div className="surface p-6 text-sm text-muted-foreground">
        You have no active wallet to add money to.{" "}
        <Link href="/wallets" className="text-foreground underline underline-offset-4">
          Go to wallets
        </Link>
      </div>
    );
  }

  const submit = form.handleSubmit((v) => {
    const fd = new FormData();
    fd.set("walletId", wallet.id);
    fd.set("amount", v.amount);
    fd.set("currency", wallet.currency);
    fd.set("idempotencyKey", idempotency.get());
    startTransition(async () => {
      const res = await createDeposit(fd);
      if (res.ok) {
        idempotency.reset();
        if (res.replayed) toast("Already submitted", { description: "Showing the original deposit." });
        onCreated(res.data);
        return;
      }
      setError(res.error);
      if (res.error.code === "INTERNAL_ERROR" || res.error.code === "NETWORK_ERROR") return; // same key on retry
      idempotency.reset();
      for (const [name, message] of Object.entries(res.error.fieldErrors)) {
        if (name === "walletId" || name === "amount") form.setError(name, { message });
      }
    });
  });

  return (
    <form noValidate onSubmit={submit} className="grid gap-5 surface p-6">
      <Field label="To wallet" htmlFor="walletId" error={form.formState.errors.walletId?.message}>
        <Select
          value={values.walletId}
          onValueChange={(v) => {
            form.setValue("walletId", v, { shouldValidate: form.formState.isSubmitted });
            idempotency.reset();
          }}
        >
          <SelectTrigger id="walletId" className="h-11 w-full">
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

      <Field label="Amount" htmlFor="amount" error={form.formState.errors.amount?.message}>
        <MoneyInput
          id="amount"
          currency={wallet.currency}
          aria-invalid={Boolean(form.formState.errors.amount) || undefined}
          {...form.register("amount", { onChange: idempotency.reset })}
        />
      </Field>
      <div className="-mt-2 flex flex-wrap gap-2">
        {(QUICK_AMOUNTS[wallet.currency] ?? []).map((major) => (
          <button
            key={major}
            type="button"
            onClick={() => {
              form.setValue("amount", String(major), { shouldValidate: true });
              idempotency.reset();
            }}
            className="rounded-md border border-border bg-card px-2.5 py-1 num text-xs shadow-xs transition-colors hover:border-line-strong hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
          >
            {formatMoney(major * 10 ** minorDigits(wallet.currency), wallet.currency)}
          </button>
        ))}
      </div>

      <FormError error={error && Object.keys(error.fieldErrors).length === 0 ? error : undefined} />
      <div className="flex justify-end">
        <Button type="submit" loading={pending}>
          <PlusIcon /> Continue to payment
        </Button>
      </div>
    </form>
  );
}

function DepositStatus({
  initial,
  wallet,
  mockProvider,
  onNew,
}: {
  initial: Deposit;
  wallet: Wallet | undefined;
  mockProvider: boolean;
  onNew: (from: Deposit) => void;
}) {
  const router = useRouter();
  const [roundStart, setRoundStart] = useState(() => Date.now());
  const [paying, startPaying] = useTransition();
  const [payError, setPayError] = useState<ActionError>();

  const query = useQuery({
    queryKey: ["deposit", initial.id],
    queryFn: () => bffGet<Deposit>(`/bff/deposits/${initial.id}`),
    initialData: initial,
    initialDataUpdatedAt: 0, // poll right away: the server-rendered copy may already be stale
    refetchInterval: (q) =>
      q.state.data?.status === "PENDING" && q.state.dataUpdatedAt - roundStart < POLL_ROUND_MS ? POLL_MS : false,
    refetchIntervalInBackground: false,
  });
  const deposit = query.data;
  const settled = deposit.status !== "PENDING";
  const timedOut = !settled && query.dataUpdatedAt - roundStart >= POLL_ROUND_MS;
  const amount = formatMoney(deposit.amount, deposit.currency);

  // Balances shown elsewhere (header, dashboard) are stale once the deposit lands.
  const refreshed = useRef(false);
  useEffect(() => {
    if (deposit.status === "SUCCEEDED" && !refreshed.current) {
      refreshed.current = true;
      router.refresh();
    }
  }, [deposit.status, router]);

  function pay(outcome: "success" | "failure") {
    setPayError(undefined);
    startPaying(async () => {
      const res = await completeMockPayment(deposit.id, outcome);
      if (!res.ok) setPayError(res.error);
      await query.refetch();
    });
  }

  return (
    <div className="grid gap-5">
      <div className="flex items-start justify-between gap-4 surface p-6">
        <div className="min-w-0">
          <p className="eyebrow">Deposit to {wallet?.name ?? "wallet"}</p>
          <p className="mt-1.5 num text-3xl font-semibold tracking-tight">{amount}</p>
          <p className="mt-1 flex flex-wrap items-center gap-x-1 text-sm text-muted-foreground">
            <span className="whitespace-nowrap">Provider ref</span>
            <span className="min-w-0 num break-all text-foreground">{deposit.providerReference}</span>
            <CopyButton value={deposit.providerReference} label="Copy provider reference" className="size-6" />
          </p>
        </div>
        <StatusBadge value={deposit.status} />
      </div>

      <ol className="grid gap-0 surface p-6" aria-label="Deposit progress" aria-live="polite">
        <TimelineStep state="done" title="Deposit created" detail={formatDateTime(deposit.createdAt)} />
        <TimelineStep
          state={deposit.status === "SUCCEEDED" ? "done" : deposit.status === "FAILED" ? "failed" : "active"}
          title={
            deposit.status === "FAILED"
              ? "Payment failed"
              : deposit.status === "SUCCEEDED"
                ? "Payment confirmed by provider"
                : "Waiting for the payment provider"
          }
          detail={
            deposit.status === "FAILED"
              ? "The provider reported a failed payment. No money moved."
              : deposit.status === "SUCCEEDED"
                ? `Signed webhook verified · ${formatDateTime(deposit.updatedAt)}`
                : timedOut
                  ? "No answer yet. Payments can take a while; you can keep waiting or come back later."
                  : "Checking every couple of seconds…"
          }
        />
        <TimelineStep
          last
          state={deposit.status === "SUCCEEDED" ? "done" : deposit.status === "FAILED" ? "skipped" : "todo"}
          title={`Credited to ${wallet?.name ?? "your wallet"}`}
          detail={
            deposit.status === "SUCCEEDED"
              ? `+${amount} posted to the ledger`
              : deposit.status === "FAILED"
                ? "Nothing was posted to the ledger"
                : "Posts as soon as the payment is confirmed"
          }
        />
      </ol>

      {!settled && mockProvider && (
        <div className="relative overflow-hidden rounded-xl border border-dashed border-line-strong p-6">
          <p className="flex items-center gap-1.5 eyebrow">
            <CreditCardIcon className="size-3.5" /> Test payment provider · dev only
          </p>
          <p className="mt-2 text-sm text-muted-foreground">
            Stands in for the provider’s checkout. Your choice is sent to the backend as a signed webhook, exactly like
            the real thing.
          </p>
          <FormError error={payError} />
          <div className="mt-4 flex flex-wrap gap-3">
            <Button onClick={() => pay("success")} loading={paying}>
              Pay {amount}
            </Button>
            <Button variant="outline" onClick={() => pay("failure")} disabled={paying}>
              Fail payment
            </Button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap justify-end gap-3">
        {timedOut && (
          <Button
            variant="outline"
            onClick={() => {
              setRoundStart(Date.now());
              void query.refetch();
            }}
          >
            <RefreshCwIcon /> Keep waiting
          </Button>
        )}
        {deposit.status === "SUCCEEDED" && deposit.transactionId && (
          <Button variant="outline" asChild>
            <Link href={`/transactions/${deposit.transactionId}`}>View transaction</Link>
          </Button>
        )}
        {(settled || timedOut) && (
          <Button onClick={() => onNew(deposit)}>{deposit.status === "FAILED" ? "Try again" : "Add more money"}</Button>
        )}
      </div>
    </div>
  );
}

function TimelineStep({
  state,
  title,
  detail,
  last,
}: {
  state: "done" | "active" | "failed" | "todo" | "skipped";
  title: string;
  detail: string;
  last?: boolean;
}) {
  const icon = {
    done: (
      <span className="grid size-7 place-items-center rounded-full bg-[color-mix(in_oklab,var(--credit)_14%,transparent)] text-credit-text">
        <CheckIcon className="size-4" />
      </span>
    ),
    active: (
      <span className="grid size-7 place-items-center rounded-full bg-[color-mix(in_oklab,var(--warning)_14%,transparent)] text-warning-text">
        <LoaderIcon className="size-4 animate-spin motion-reduce:animate-none" />
      </span>
    ),
    failed: (
      <span className="grid size-7 place-items-center rounded-full bg-[color-mix(in_oklab,var(--danger)_12%,transparent)] text-danger-text">
        <XIcon className="size-4" />
      </span>
    ),
    todo: <span className="grid size-7 place-items-center rounded-full border border-border" />,
    skipped: <span className="grid size-7 place-items-center rounded-full border border-dashed border-border" />,
  }[state];

  return (
    <li className="flex gap-4" aria-current={state === "active" ? "step" : undefined}>
      <div className="flex flex-col items-center">
        {icon}
        {!last && <span className="my-1 w-px flex-1 bg-border" aria-hidden />}
      </div>
      <div className={last ? "pt-1" : "pt-1 pb-5"}>
        <p
          className={state === "todo" || state === "skipped" ? "text-sm text-muted-foreground" : "text-sm font-medium"}
        >
          {title}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">{detail}</p>
      </div>
    </li>
  );
}
