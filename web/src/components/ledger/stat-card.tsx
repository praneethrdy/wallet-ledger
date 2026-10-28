import { cn } from "cn";

import { Card } from "@/components/ui/card";

type StatCardProps = {
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  icon?: React.ReactNode;
  className?: string;
};

/** Dashboard metric: eyebrow label, large tabular value, quiet hint line. */
export function StatCard({ label, value, hint, icon, className }: StatCardProps) {
  return (
    <Card className={cn("gap-3 px-5", className)}>
      <div className="flex items-center justify-between">
        <span className="eyebrow">{label}</span>
        {icon && <span className="text-muted-foreground [&>svg]:size-4">{icon}</span>}
      </div>
      <div className="num text-[28px] leading-none font-medium tracking-tight">{value}</div>
      {hint && <div className="text-xs text-muted-foreground">{hint}</div>}
    </Card>
  );
}
