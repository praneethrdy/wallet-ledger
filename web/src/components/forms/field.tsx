import { TriangleAlertIcon } from "lucide-react";

import { cn } from "cn";

import { Label } from "@/components/ui/label";
import type { ActionError } from "@/lib/errors";

/** Label above, control, then help text or (in rose) the field's error. */
export function Field({
  label,
  htmlFor,
  help,
  error,
  className,
  children,
}: {
  label: string;
  htmlFor: string;
  help?: React.ReactNode;
  error?: string;
  className?: string;
  children: React.ReactNode;
}) {
  const messageId = `${htmlFor}-message`;
  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={htmlFor} className="text-[13px] font-medium">
        {label}
      </Label>
      {children}
      {error ? (
        <p id={messageId} className="text-xs text-danger-text">
          {error}
        </p>
      ) : (
        help && (
          <p id={messageId} className="text-xs text-muted-foreground">
            {help}
          </p>
        )
      )}
    </div>
  );
}

/** Form-level error banner. Shows the support reference only for unexpected errors. */
export function FormError({ error }: { error?: ActionError }) {
  if (!error) return null;
  return (
    <div
      role="alert"
      className="flex gap-2.5 rounded-lg border border-[color-mix(in_oklab,var(--danger)_25%,transparent)] bg-[color-mix(in_oklab,var(--danger)_7%,transparent)] px-3.5 py-3 text-sm text-danger-text"
    >
      <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>
        <p>{error.message}</p>
        {error.correlationId && (
          <p className="mt-1 text-xs opacity-80">
            Reference <span className="num">{error.correlationId}</span>
          </p>
        )}
      </div>
    </div>
  );
}
