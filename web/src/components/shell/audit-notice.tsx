import { EyeIcon } from "lucide-react";

/** Staff console reminder. True by construction: the backend writes an audit row for every support read. */
export function AuditNotice() {
  return (
    <p className="mb-6 inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs text-muted-foreground shadow-xs">
      <EyeIcon className="size-3.5" aria-hidden />
      Your lookups are recorded in the audit log
    </p>
  );
}
