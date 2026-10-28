import { Badge } from "@/components/ui/badge";

type Variant = "neutral" | "brand" | "success" | "warning" | "danger" | "outline";

/** One place that decides how every backend status/type/role looks. */
const STYLES: Record<string, { variant: Variant; label: string; dot?: boolean }> = {
  // wallet / user status
  ACTIVE: { variant: "success", label: "Active", dot: true },
  FROZEN: { variant: "warning", label: "Frozen", dot: true },
  CLOSED: { variant: "neutral", label: "Closed", dot: true },
  // deposit status
  PENDING: { variant: "warning", label: "Pending", dot: true },
  SUCCEEDED: { variant: "success", label: "Succeeded", dot: true },
  FAILED: { variant: "danger", label: "Failed", dot: true },
  // transaction types
  DEPOSIT: { variant: "success", label: "Deposit" },
  TRANSFER: { variant: "neutral", label: "Transfer" },
  REVERSAL: { variant: "brand", label: "Reversal" },
  REFUND: { variant: "neutral", label: "Refund" },
  FX_CONVERSION: { variant: "neutral", label: "FX" },
  // reconciliation
  PASSED: { variant: "success", label: "Passed", dot: true },
  RUNNING: { variant: "brand", label: "Running", dot: true },
  // webhook events
  RECEIVED: { variant: "neutral", label: "Received", dot: true },
  PROCESSED: { variant: "success", label: "Processed", dot: true },
  REJECTED: { variant: "danger", label: "Rejected", dot: true },
  // roles
  CUSTOMER: { variant: "outline", label: "Customer" },
  SUPPORT: { variant: "brand", label: "Support" },
  ADMIN: { variant: "brand", label: "Admin" },
};

export function StatusBadge({ value, className }: { value: string; className?: string }) {
  const style = STYLES[value] ?? { variant: "neutral" as const, label: value };
  return (
    <Badge variant={style.variant} dot={style.dot} className={className}>
      {style.label}
    </Badge>
  );
}
