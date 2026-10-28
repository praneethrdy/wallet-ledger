import { ConstructionIcon } from "lucide-react";

import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";

/** Placeholder: built in step 4 of docs/frontend-brief.md. */
export default function Page() {
  return (
    <>
      <PageHeader title="Dashboard" description="Your balances, cash flow and recent activity." />
      <div className="surface">
        <EmptyState
          icon={<ConstructionIcon />}
          title="Coming in step 4"
          description="This screen is next on the build list."
        />
      </div>
    </>
  );
}
