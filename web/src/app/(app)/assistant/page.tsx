import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { connection } from "next/server";
import { Suspense } from "react";

import { PageHeader } from "@/components/shell/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { env } from "@/lib/env";

import { AssistantChat } from "./assistant-chat";

export const metadata: Metadata = { title: "Ask your wallet" };

export default function AssistantPage() {
  return (
    <>
      <PageHeader
        title="Ask your wallet"
        description="Questions about your own money, answered from your transactions. It can read, never move money."
      />
      <Suspense fallback={<Skeleton className="h-[480px] max-w-3xl rounded-xl" />}>
        <Assistant />
      </Suspense>
    </>
  );
}

async function Assistant() {
  // Decide at request time, not at build time: the key may be set only on the running server.
  await connection();
  if (!env.assistant) notFound(); // hidden unless an LLM key is configured
  const { providerName, fallback, dailyLimit } = env.assistant;
  return (
    <AssistantChat
      provider={fallback ? `${providerName} (or ${fallback.providerName} as a backup)` : providerName}
      dailyLimit={dailyLimit}
    />
  );
}
