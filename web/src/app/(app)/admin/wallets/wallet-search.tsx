"use client";

import { SearchIcon, XIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";

import { cn } from "cn";

import { StatusBadge } from "@/components/ledger/status-badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

import { adminWalletQueryString, WALLET_STATUSES } from "./query";

const ALL = "all";

/** Owner email (contains, applies on Enter) and status; both live in the URL. */
export function WalletSearch({ email, status }: { email?: string; status?: (typeof WALLET_STATUSES)[number] }) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [text, setText] = useState(email ?? "");

  function apply(next: { email?: string; status?: (typeof WALLET_STATUSES)[number] }) {
    const q = adminWalletQueryString(next);
    startTransition(() => router.replace(q ? `/admin/wallets?${q}` : "/admin/wallets", { scroll: false }));
  }

  return (
    <form
      role="search"
      aria-label="Search wallets"
      aria-busy={pending}
      onSubmit={(e) => {
        e.preventDefault();
        apply({ email: text.trim() || undefined, status });
      }}
      className={cn(
        "grid gap-3 surface p-4 transition-opacity sm:grid-cols-[1fr_200px_auto] sm:items-end",
        pending && "opacity-70",
      )}
    >
      <div className="grid gap-1.5">
        <Label htmlFor="w-email" className="text-xs text-muted-foreground">
          Owner email
        </Label>
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            id="w-email"
            type="search"
            className="pl-8"
            placeholder="alice@…"
            value={text}
            onChange={(e) => setText(e.target.value)}
            autoComplete="off"
          />
        </div>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="w-status" className="text-xs text-muted-foreground">
          Status
        </Label>
        <Select
          value={status ?? ALL}
          onValueChange={(v) =>
            apply({ email, status: v === ALL ? undefined : (v as (typeof WALLET_STATUSES)[number]) })
          }
        >
          <SelectTrigger id="w-status" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Any status</SelectItem>
            {WALLET_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                <StatusBadge value={s} />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex gap-2">
        <Button type="submit" variant="outline">
          Search
        </Button>
        {(email || status) && (
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setText("");
              apply({});
            }}
          >
            <XIcon /> Clear
          </Button>
        )}
      </div>
    </form>
  );
}
