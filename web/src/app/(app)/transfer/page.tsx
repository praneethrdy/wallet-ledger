import { WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";

import { TransferFlow } from "./transfer-flow";

export const metadata: Metadata = { title: "Send money" };

export default function TransferPage({ searchParams }: PageProps<"/transfer">) {
  return (
    <>
      <PageHeader
        title="Send money"
        description="Move money to any wallet by its ID. Both sides post together, or not at all."
      />
      <Suspense fallback={<Skeleton className="h-[460px] max-w-xl rounded-xl" />}>
        <Transfer searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Transfer({ searchParams }: { searchParams: PageProps<"/transfer">["searchParams"] }) {
  const [{ from, to }, wallets] = await Promise.all([
    searchParams,
    (async () => get((await api()).GET("/api/v1/wallets")))(),
  ]);
  const usable = wallets.filter((w) => w.status === "ACTIVE");

  if (usable.length === 0) {
    return (
      <div className="max-w-xl surface">
        <EmptyState
          icon={<WalletIcon />}
          title={wallets.length ? "No active wallets" : "You don’t have a wallet yet"}
          description={
            wallets.length
              ? "All your wallets are frozen, so they can’t send money."
              : "Create a wallet and add money first."
          }
          action={
            <Button variant="outline" asChild>
              <Link href="/wallets">Go to wallets</Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <TransferFlow
      wallets={usable}
      allWallets={wallets}
      initialFrom={typeof from === "string" && usable.some((w) => w.id === from) ? from : usable[0].id}
      initialTo={typeof to === "string" ? to : ""}
    />
  );
}
