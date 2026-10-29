import { InboxIcon, TriangleAlertIcon } from "lucide-react";

import { cn } from "cn";

type StateProps = {
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
};

/**
 * Nothing to show yet: calm, with one clear next step. The icon sits in a softly glowing,
 * slowly orbiting ring (still with reduced motion); all of it is decoration.
 */
export function EmptyState({ title, description, action, icon, className }: StateProps & { icon?: React.ReactNode }) {
  return (
    <div className={cn("flex flex-col items-center px-6 py-14 text-center", className)}>
      <div className="relative mb-6 grid size-24 place-items-center" aria-hidden>
        <div className="absolute -inset-6 glow" />
        <div className="absolute inset-0 animate-orbit rounded-full border border-dashed border-line-strong motion-reduce:animate-none">
          <span className="absolute -top-1 left-1/2 size-2 -translate-x-1/2 rounded-full bg-brand shadow-[0_0_10px_2px_color-mix(in_oklab,var(--brand)_55%,transparent)]" />
          <span className="absolute bottom-3 -left-0.5 size-1.5 rounded-full bg-line-strong" />
        </div>
        <div className="relative grid size-14 place-items-center rounded-2xl border border-[color-mix(in_oklab,var(--brand)_25%,var(--line))] bg-[linear-gradient(160deg,color-mix(in_oklab,var(--brand)_14%,var(--card)),var(--card))] text-brand-text shadow-[var(--elev-sm)] [&>svg]:size-6">
          {icon ?? <InboxIcon />}
        </div>
      </div>
      <h3 className="text-base font-medium tracking-tight">{title}</h3>
      {description && <p className="mt-1.5 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

/**
 * Something went wrong. Unexpected errors show the backend's correlationId, so a user can
 * quote it and support can find every log line for that request.
 */
export function ErrorState({
  title,
  description,
  action,
  correlationId,
  className,
}: StateProps & { correlationId?: string }) {
  return (
    <div role="alert" className={cn("flex flex-col items-center px-6 py-14 text-center", className)}>
      <div className="mb-4 grid size-11 place-items-center rounded-xl bg-[color-mix(in_oklab,var(--danger)_10%,transparent)] text-danger-text [&>svg]:size-5">
        <TriangleAlertIcon aria-hidden />
      </div>
      <h3 className="text-sm font-medium">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      {correlationId && (
        <p className="mt-3 text-xs text-muted-foreground">
          Reference <span className="rounded bg-muted px-1.5 py-0.5 num text-foreground">{correlationId}</span>
        </p>
      )}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
