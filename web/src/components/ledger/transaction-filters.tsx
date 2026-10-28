"use client";

import { SearchIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";

import { cn } from "cn";

import { TX_TYPE_LABEL } from "@/components/ledger/transaction-row";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Schemas } from "@/lib/api/client";
import { TX_TYPES, txFiltersQuery, type TxFilters } from "@/lib/tx-filters";

const ALL = "all"; // Radix Select can't use "" as an item value

/**
 * Filters live in the URL, so a filtered view can be shared or bookmarked and the server renders
 * the first page. Selects and dates apply immediately; typed fields apply on Enter. Customers pick
 * from their own wallets; staff (no `wallets`) paste any wallet ID instead.
 */
export function TransactionFilters({
  wallets,
  filters,
  basePath = "/transactions",
}: {
  wallets?: Schemas["WalletResponse"][];
  filters: TxFilters;
  basePath?: string;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [reference, setReference] = useState(filters.reference ?? "");
  const [walletId, setWalletId] = useState(filters.walletId ?? "");
  const active = txFiltersQuery(filters) !== "";

  function apply(next: TxFilters) {
    const q = txFiltersQuery(next);
    startTransition(() => router.replace(q ? `${basePath}?${q}` : basePath, { scroll: false }));
  }

  return (
    <form
      role="search"
      aria-label="Filter transactions"
      aria-busy={pending}
      onSubmit={(e) => {
        e.preventDefault();
        apply({
          ...filters,
          reference: reference.trim() || undefined,
          walletId: wallets ? filters.walletId : walletId.trim() || undefined,
        });
      }}
      className={cn(
        "grid grid-cols-2 gap-3 surface p-4 transition-opacity lg:grid-cols-[1.2fr_1fr_0.9fr_0.9fr_1.3fr_auto] lg:items-end",
        pending && "opacity-70",
      )}
    >
      <FilterField label="Wallet" htmlFor="f-wallet" className="col-span-2 sm:col-span-1">
        {wallets ? (
          <Select
            value={filters.walletId ?? ALL}
            onValueChange={(v) => apply({ ...filters, walletId: v === ALL ? undefined : v })}
          >
            <SelectTrigger id="f-wallet" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All wallets</SelectItem>
              {wallets.map((w) => (
                <SelectItem key={w.id} value={w.id}>
                  {w.name} · {w.currency}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <Input
            id="f-wallet"
            className="num"
            placeholder="Wallet ID"
            value={walletId}
            onChange={(e) => setWalletId(e.target.value)}
            spellCheck={false}
            autoComplete="off"
          />
        )}
      </FilterField>

      <FilterField label="Type" htmlFor="f-type" className="col-span-2 sm:col-span-1">
        <Select
          value={filters.type ?? ALL}
          onValueChange={(v) => apply({ ...filters, type: v === ALL ? undefined : (v as TxFilters["type"]) })}
        >
          <SelectTrigger id="f-type" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All types</SelectItem>
            {TX_TYPES.map((t) => (
              <SelectItem key={t} value={t}>
                {TX_TYPE_LABEL[t]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </FilterField>

      <FilterField label="From" htmlFor="f-from">
        <Input
          id="f-from"
          type="date"
          value={filters.from ?? ""}
          max={filters.to}
          onChange={(e) => apply({ ...filters, from: e.target.value || undefined })}
        />
      </FilterField>

      <FilterField label="To" htmlFor="f-to">
        <Input
          id="f-to"
          type="date"
          value={filters.to ?? ""}
          min={filters.from}
          onChange={(e) => apply({ ...filters, to: e.target.value || undefined })}
        />
      </FilterField>

      <FilterField label="Reference" htmlFor="f-reference" className="col-span-2 lg:col-span-1">
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="f-reference"
            className="pl-8 num"
            placeholder="TXN-20261028-…"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            spellCheck={false}
            autoComplete="off"
          />
          <button type="submit" className="sr-only">
            Search
          </button>
        </div>
      </FilterField>

      {active && (
        <Button
          type="button"
          variant="ghost"
          className="col-span-2 justify-self-start lg:col-span-1"
          onClick={() => {
            setReference("");
            setWalletId("");
            apply({});
          }}
        >
          <XIcon /> Clear
        </Button>
      )}
    </form>
  );
}

function FilterField({
  label,
  htmlFor,
  className,
  children,
}: {
  label: string;
  htmlFor: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={cn("grid min-w-0 gap-1.5", className)}>
      <Label htmlFor={htmlFor} className="text-xs text-muted-foreground">
        {label}
      </Label>
      {children}
    </div>
  );
}
