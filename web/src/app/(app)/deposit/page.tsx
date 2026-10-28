import { WalletIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get, unwrap, type Schemas } from "@/lib/api/client";
import { env } from "@/lib/env";
import { ApiError } from "@/lib/errors";
import { isUuid } from "@/lib/format";

import { DepositFlow } from "./deposit-flow";

export const metadata: Metadata = { title: "Add money" };

export default function DepositPage({ searchParams }: PageProps<"/deposit">) {
  return (
    <>
      <PageHeader
        title="Add money"
        description="Top up a wallet. It’s credited only after the payment provider confirms with a signed webhook."
      />
      <Suspense fallback={<Skeleton className="h-[380px] max-w-xl rounded-xl" />}>
        <Deposit searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Deposit({ searchParams }: { searchParams: PageProps<"/deposit">["searchParams"] }) {
  const { wallet, id } = await searchParams;
  const client = await api();
  const [wallets, deposit] = await Promise.all([
    get(client.GET("/api/v1/wallets")),
    // ?id= resumes a deposit after a reload; a foreign or unknown id just starts over.
    typeof id === "string" && isUuid(id)
      ? unwrap(client.GET("/api/v1/deposits/{id}", { params: { path: { id } } })).then(
          (r): Schemas["DepositResponse"] | undefined => r.data,
          (err) => {
            if (err instanceof ApiError && err.status === 404) return undefined;
            throw err;
          },
        )
      : undefined,
  ]);
  const usable = wallets.filter((w) => w.status === "ACTIVE");

  if (usable.length === 0 && !deposit) {
    return (
      <div className="max-w-xl surface">
        <EmptyState
          icon={<WalletIcon />}
          title={wallets.length ? "No active wallets" : "You don’t have a wallet yet"}
          description={
            wallets.length ? "All your wallets are frozen, so they can’t take deposits." : "Create a wallet first."
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
    <DepositFlow
      wallets={usable}
      allWallets={wallets}
      initialWallet={typeof wallet === "string" && usable.some((w) => w.id === wallet) ? wallet : usable[0]?.id}
      initialDeposit={deposit}
      mockProvider={env.mockProviderEnabled}
    />
  );
}
