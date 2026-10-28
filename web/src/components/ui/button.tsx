import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "cn";
import { Slot } from "radix-ui";
import { Loader2Icon } from "lucide-react";

const buttonVariants = cva(
  [
    "group/button relative inline-flex shrink-0 items-center justify-center gap-2 rounded-lg border text-sm font-medium whitespace-nowrap select-none",
    "transition-[background-color,border-color,color,box-shadow,transform] duration-150 ease-out outline-none",
    "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
    "active:not-aria-[haspopup]:translate-y-px disabled:pointer-events-none disabled:opacity-45",
    "aria-invalid:border-destructive [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  ].join(" "),
  {
    variants: {
      variant: {
        // Accent, used sparingly: the one primary action on a screen.
        default: [
          "border-transparent bg-primary text-primary-foreground hover:bg-brand-hover",
          "shadow-[inset_0_1px_0_rgb(255_255_255/0.16),var(--elev-xs)]",
        ].join(" "),
        outline:
          "border-border bg-card text-foreground shadow-xs hover:border-line-strong hover:bg-muted aria-expanded:bg-muted",
        secondary:
          "border-transparent bg-muted text-foreground hover:bg-[color-mix(in_oklab,var(--surface-2),var(--text)_6%)]",
        ghost: "border-transparent text-muted-foreground hover:bg-muted hover:text-foreground aria-expanded:bg-muted",
        // Red is for real errors / destructive actions only.
        destructive: [
          "border-transparent bg-danger text-white hover:bg-[color-mix(in_oklab,var(--danger),black_10%)]",
          "shadow-[inset_0_1px_0_rgb(255_255_255/0.16),var(--elev-xs)] focus-visible:ring-danger",
        ].join(" "),
        link: "border-transparent px-0 text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-3.5",
        sm: "h-8 px-3 text-[13px]",
        lg: "h-10 px-5",
        icon: "size-9",
        "icon-sm": "size-8",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

type ButtonProps = React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
    /** Shows a spinner, keeps the button's width, and blocks clicks. */
    loading?: boolean;
  };

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  loading = false,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot.Root : "button";

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      data-loading={loading || undefined}
      aria-busy={loading || undefined}
      disabled={disabled || loading}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    >
      {asChild ? (
        children
      ) : (
        <>
          {loading && (
            <span className="absolute inset-0 grid place-items-center">
              <Loader2Icon className="animate-spin" aria-hidden />
            </span>
          )}
          <span className={cn("inline-flex items-center gap-2", loading && "invisible")}>{children}</span>
        </>
      )}
    </Comp>
  );
}

export { Button, buttonVariants };
