import { ConstructionIcon } from "lucide-react";

import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";

/** Placeholder: built in step 8 of docs/frontend-brief.md. */
export default function Page() {
  return (
    <>
      <PageHeader title="Users" description="Find a customer and see their wallets. Every lookup is audited." />
      <div className="surface">
        <EmptyState
          icon={<ConstructionIcon />}
          title="Coming in step 8"
          description="This screen is next on the build list."
        />
      </div>
    </>
  );
}
