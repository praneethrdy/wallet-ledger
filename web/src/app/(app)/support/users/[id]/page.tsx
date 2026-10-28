import { ChevronRightIcon, WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Amount } from "@/components/ledger/amount";
import { CopyButton } from "@/components/ledger/copy-button";
import { StatusBadge } from "@/components/ledger/status-badge";
import { FadeIn } from "@/components/motion/fade-in";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, get } from "@/lib/api/client";
import { requireUser } from "@/lib/auth/current-user";
import { ApiError } from "@/lib/errors";
import { formatDate, isUuid, shortId } from "@/lib/format";

export const metadata: Metadata = { title: "User · Support" };

export default function SupportUserPage({ params }: PageProps<"/support/users/[id]">) {
  return (
    <Suspense
      fallback={
        <div className="space-y-6">
          <Skeleton className="h-20 rounded-xl" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      }
    >
      <User params={params} />
    </Suspense>
  );
}

async function User({ params }: { params: PageProps<"/support/users/[id]">["params"] }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();

  const client = await api();
  const viewer = await requireUser();
  let user, wallets;
  try {
    [user, wallets] = await Promise.all([
      get(client.GET("/api/v1/support/users/{id}", { params: { path: { id } } })),
      get(client.GET("/api/v1/support/users/{id}/wallets", { params: { path: { id } } })),
    ]);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) notFound();
    throw err;
  }

  return (
    <>
      <FadeIn className="mb-8">
        <nav aria-label="Breadcrumb" className="mb-3 flex items-center gap-1 text-sm text-muted-foreground">
          <Link href="/support/users" className="hover:text-foreground">
            Users
          </Link>
          <ChevronRightIcon className="size-3.5" aria-hidden />
          <span className="truncate text-foreground">{user.email}</span>
        </nav>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 className="truncate text-2xl font-semibold tracking-tight">{user.email}</h1>
              <StatusBadge value={user.role} />
              <StatusBadge value={user.status} />
            </div>
            <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
              <span className="num">{user.id}</span>
              <CopyButton value={user.id} label="Copy user ID" className="size-6" />
              <span aria-hidden>·</span> Joined {formatDate(user.createdAt)}
            </p>
          </div>
          {viewer.role === "ADMIN" && wallets.length > 0 && (
            <Button variant="outline" asChild>
              <Link href={`/admin/wallets?email=${encodeURIComponent(user.email)}`}>Manage wallets</Link>
            </Button>
          )}
        </div>
      </FadeIn>

      <Card className="gap-0 overflow-hidden pb-0">
        <CardHeader className="pb-4">
          <CardTitle>Wallets</CardTitle>
        </CardHeader>
        {wallets.length === 0 ? (
          <EmptyState icon={<WalletIcon />} title="No wallets" description="This user hasn’t created a wallet." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-5">Wallet</TableHead>
                <TableHead>Status</TableHead>
                <TableHead numeric>Balance</TableHead>
                <TableHead className="pr-5 text-right">
                  <span className="sr-only">Links</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {wallets.map((w) => (
                <TableRow key={w.id}>
                  <TableCell className="pl-5">
                    <div className="font-medium">{w.name}</div>
                    <div className="flex items-center gap-1 text-xs text-muted-foreground">
                      <span className="num">{shortId(w.id)}</span>
                      <CopyButton value={w.id} label={`Copy ID of ${w.name}`} className="size-5" />
                      <span aria-hidden>·</span> {w.currency}
                    </div>
                  </TableCell>
                  <TableCell>
                    <StatusBadge value={w.status} />
                  </TableCell>
                  <TableCell numeric className="font-medium">
                    <Amount minor={w.balance} currency={w.currency} />
                  </TableCell>
                  <TableCell className="pr-5 text-right">
                    <Link
                      href={`/support/transactions?walletId=${w.id}`}
                      className="text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                    >
                      Transactions
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </>
  );
}
