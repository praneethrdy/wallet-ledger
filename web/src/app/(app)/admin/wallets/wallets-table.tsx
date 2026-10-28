"use client";

import { useQueryClient, type InfiniteData } from "@tanstack/react-query";
import { SearchXIcon, SnowflakeIcon, SunIcon } from "lucide-react";
import Link from "next/link";
import { useState, useTransition } from "react";
import { toast } from "sonner";

import { ListFooter, useCursorList, type CursorPage } from "@/components/data/cursor-list";
import { FormError } from "@/components/forms/field";
import { Amount } from "@/components/ledger/amount";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { EmptyState } from "@/components/states/states";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Schemas } from "@/lib/api/client";
import type { ActionError } from "@/lib/errors";
import { shortId } from "@/lib/format";
import { useIdempotencyKey } from "@/lib/idempotency";

import { setWalletFrozen } from "../actions";

type Wallet = Schemas["AdminWalletView"];

export function WalletsTable({ query, firstPage }: { query: string; firstPage: CursorPage<Wallet> }) {
  const queryKey = ["admin-wallets", query] as const;
  const list = useCursorList<Wallet>(queryKey, `/bff/admin/wallets?${query}`, firstPage);
  const queryClient = useQueryClient();
  const [target, setTarget] = useState<Wallet>();

  /** Swap the changed wallet into the cached pages, so the row updates in place. */
  function updated(wallet: Wallet) {
    queryClient.setQueryData<InfiniteData<CursorPage<Wallet>>>(
      queryKey,
      (data) =>
        data && {
          ...data,
          pages: data.pages.map((p) => ({ ...p, items: p.items.map((w) => (w.id === wallet.id ? wallet : w)) })),
        },
    );
  }

  if (list.items.length === 0) {
    return (
      <EmptyState
        icon={<SearchXIcon />}
        title="No wallets match"
        description="Email search matches any part of the owner’s address."
      />
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="pl-5">Wallet</TableHead>
            <TableHead>Owner</TableHead>
            <TableHead>Status</TableHead>
            <TableHead numeric>Balance</TableHead>
            <TableHead className="pr-5 text-right">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.items.map((w) => (
            <TableRow key={w.id}>
              <TableCell className="pl-5">
                <div className="font-medium">{w.name}</div>
                <div className="flex items-center gap-1 text-xs text-muted-foreground">
                  <span className="num">{shortId(w.id)}</span>
                  <CopyButton value={w.id} label={`Copy ID of ${w.name}`} className="size-5" />
                </div>
              </TableCell>
              <TableCell>
                <Link
                  href={`/support/users/${w.ownerUserId}`}
                  className="text-sm underline-offset-4 hover:underline focus-visible:underline"
                >
                  {w.ownerEmail}
                </Link>
              </TableCell>
              <TableCell>
                <StatusBadge value={w.status} />
              </TableCell>
              <TableCell numeric>
                <Amount minor={w.balance} currency={w.currency} />
              </TableCell>
              <TableCell className="pr-5 text-right">
                {w.status !== "CLOSED" && (
                  <Button variant="outline" size="sm" onClick={() => setTarget(w)}>
                    {w.status === "FROZEN" ? (
                      <>
                        <SunIcon /> Unfreeze
                      </>
                    ) : (
                      <>
                        <SnowflakeIcon /> Freeze
                      </>
                    )}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <ListFooter list={list} />
      <FreezeDialog wallet={target} onClose={() => setTarget(undefined)} onDone={updated} />
    </>
  );
}

function FreezeDialog({
  wallet,
  onClose,
  onDone,
}: {
  wallet: Wallet | undefined;
  onClose: () => void;
  onDone: (wallet: Wallet) => void;
}) {
  const idempotency = useIdempotencyKey();
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<ActionError>();
  const freeze = wallet?.status !== "FROZEN";

  function confirm() {
    if (!wallet) return;
    const form = new FormData();
    form.set("walletId", wallet.id);
    form.set("freeze", String(freeze));
    form.set("idempotencyKey", idempotency.get());
    startTransition(async () => {
      const res = await setWalletFrozen(form);
      idempotency.reset();
      if (!res.ok) {
        setError(res.error);
        return;
      }
      onDone(res.data);
      toast.success(freeze ? `${wallet.name} frozen` : `${wallet.name} unfrozen`, { description: wallet.ownerEmail });
      setError(undefined);
      onClose();
    });
  }

  return (
    <AlertDialog
      open={Boolean(wallet)}
      onOpenChange={(open) => {
        if (!open && !pending) {
          setError(undefined);
          idempotency.reset();
          onClose();
        }
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {freeze ? "Freeze" : "Unfreeze"} {wallet?.name}?
          </AlertDialogTitle>
          <AlertDialogDescription>
            {freeze
              ? `${wallet?.ownerEmail} won’t be able to send from or receive transfers into this wallet until it’s unfrozen. Deposits already paid still settle. The change is audited.`
              : `Transfers in and out of this wallet will work again for ${wallet?.ownerEmail}. The change is audited.`}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FormError error={error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>Cancel</AlertDialogCancel>
          <Button variant={freeze ? "destructive" : "default"} onClick={confirm} loading={pending}>
            {freeze ? "Freeze wallet" : "Unfreeze wallet"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
