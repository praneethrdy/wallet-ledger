import { SearchXIcon } from "lucide-react";
import Link from "next/link";

import { Logo } from "@/components/brand/logo";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";

/** Unknown URLs outside the signed-in app. */
export default function RootNotFound() {
  return (
    <main className="grid min-h-dvh place-items-center bg-dots px-4">
      <div className="w-full max-w-md">
        <div className="mb-6 flex justify-center">
          <Logo />
        </div>
        <div className="surface">
          <EmptyState
            icon={<SearchXIcon />}
            title="Page not found"
            description="The link may be broken, or the page may have moved."
            action={
              <Button asChild>
                <Link href="/">Go to Wallet Ledger</Link>
              </Button>
            }
          />
        </div>
      </div>
    </main>
  );
}
