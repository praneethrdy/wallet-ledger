import { SearchXIcon } from "lucide-react";
import Link from "next/link";

import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";

/** "Not found" also covers "not yours": the backend deliberately doesn't distinguish (ADR-009). */
export default function NotFound() {
  return (
    <div className="mx-auto max-w-lg surface">
      <EmptyState
        icon={<SearchXIcon />}
        title="We couldn’t find that"
        description="It may not exist, or it may not be yours to see."
        action={
          <Button variant="outline" asChild>
            <Link href="/">Back to home</Link>
          </Button>
        }
      />
    </div>
  );
}
