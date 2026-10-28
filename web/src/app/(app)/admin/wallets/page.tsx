import type { Metadata } from "next";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";

import { adminWalletQuery, adminWalletQueryString } from "./query";
import { WalletSearch } from "./wallet-search";
import { WalletsTable } from "./wallets-table";

export const metadata: Metadata = { title: "Wallets · Admin" };

export default function AdminWalletsPage({ searchParams }: PageProps<"/admin/wallets">) {
  return (
    <>
      <PageHeader
        title="Wallets"
        description="Every customer wallet, with its owner. Freezing blocks transfers in and out; deposits still settle."
      />
      <Suspense fallback={<Skeleton className="h-[560px] rounded-xl" />}>
        <Wallets searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Wallets({ searchParams }: { searchParams: PageProps<"/admin/wallets">["searchParams"] }) {
  const q = adminWalletQuery(await searchParams);
  const firstPage = await get((await api()).GET("/api/v1/admin/wallets", { params: { query: { ...q, limit: 25 } } }));
  const query = adminWalletQueryString(q);
  return (
    <div className="grid gap-4">
      <WalletSearch key={q.email ?? ""} email={q.email} status={q.status} />
      <Card className="gap-0 overflow-hidden py-0">
        <WalletsTable key={query} query={query} firstPage={firstPage} />
      </Card>
    </div>
  );
}
