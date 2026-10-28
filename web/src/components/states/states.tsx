import { InboxIcon, TriangleAlertIcon } from "lucide-react";

import { cn } from "cn";

type StateProps = {
  title: string;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
};

/** Nothing to show yet: calm, with one clear next step. */
export function EmptyState({ title, description, action, icon, className }: StateProps & { icon?: React.ReactNode }) {
  return (
    <div className={cn("flex flex-col items-center px-6 py-14 text-center", className)}>
      <div className="mb-4 grid size-11 place-items-center rounded-xl border border-border bg-card text-muted-foreground shadow-xs [&>svg]:size-5">
        {icon ?? <InboxIcon aria-hidden />}
      </div>
      <h3 className="text-sm font-medium">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
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
