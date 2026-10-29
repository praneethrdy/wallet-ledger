import { PlusIcon, SendIcon } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { requireUser } from "@/lib/auth/current-user";

/** Customers move money; an admin sees its own wallets but can't transfer or deposit. */
export async function QuickActions() {
  const user = await requireUser();
  if (user.role !== "CUSTOMER") return null;
  return (
    <div className="flex flex-wrap justify-center gap-2">
      <Button variant="outline" asChild>
        <Link href="/deposit">
          <PlusIcon /> Add money
        </Link>
      </Button>
      <Button asChild>
        <Link href="/transfer">
          <SendIcon /> Send money
        </Link>
      </Button>
    </div>
  );
}
