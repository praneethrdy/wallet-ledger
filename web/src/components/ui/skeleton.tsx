import { cn } from "cn";

/** Loading placeholder with a soft shimmer (static when reduced motion is preferred). */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="skeleton" aria-hidden className={cn("shimmer rounded-md", className)} {...props} />;
}

export { Skeleton };
