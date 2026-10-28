import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "cn";
import { Slot } from "radix-ui";

/** Status badges: a 10% tint of the colour with solid text. */
const badgeVariants = cva(
  "inline-flex h-[22px] w-fit shrink-0 items-center gap-1.5 rounded-md px-2 text-xs font-medium whitespace-nowrap [&>svg]:size-3 [&>svg]:pointer-events-none",
  {
    variants: {
      variant: {
        neutral: "bg-[color-mix(in_oklab,var(--text)_7%,transparent)] text-foreground",
        brand: "bg-[color-mix(in_oklab,var(--brand)_12%,transparent)] text-brand",
        success: "bg-[color-mix(in_oklab,var(--credit)_12%,transparent)] text-credit-text",
        warning: "bg-[color-mix(in_oklab,var(--warning)_14%,transparent)] text-warning-text",
        danger: "bg-[color-mix(in_oklab,var(--danger)_12%,transparent)] text-danger-text",
        outline: "border border-border text-muted-foreground",
      },
      dot: {
        true: "before:size-1.5 before:rounded-full before:bg-current before:content-['']",
        false: "",
      },
    },
    defaultVariants: {
      variant: "neutral",
      dot: false,
    },
  },
);

function Badge({
  className,
  variant,
  dot,
  asChild = false,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span";
  return <Comp data-slot="badge" className={cn(badgeVariants({ variant, dot }), className)} {...props} />;
}

export { Badge, badgeVariants };
