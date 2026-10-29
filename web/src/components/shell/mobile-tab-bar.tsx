"use client";

import { HomeIcon, PlusIcon, ReceiptTextIcon, SendIcon, WalletIcon } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { cn } from "cn";

import { isActive } from "./nav-links";
import type { NavItem } from "./nav-items";

type Tab = NavItem & { icon: React.ComponentType<{ className?: string }> };

const TABS: Tab[] = [
  { href: "/dashboard", label: "Home", icon: HomeIcon },
  { href: "/wallets", label: "Wallets", icon: WalletIcon },
  { href: "/transfer", label: "Send", icon: SendIcon },
  { href: "/deposit", label: "Add money", icon: PlusIcon },
  { href: "/transactions", label: "Activity", icon: ReceiptTextIcon },
];

/**
 * Customers on phones: the everyday destinations within thumb reach, with Send raised in the
 * middle as the main action. The active tab's pill glides between tabs (instantly with reduced
 * motion). Everything else (Ask, theme) stays in the top bar's menu.
 */
export function MobileTabBar() {
  const pathname = usePathname();
  const reduce = useReducedMotion();

  return (
    <nav
      aria-label="Quick"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border pb-[env(safe-area-inset-bottom)] glass md:hidden"
    >
      <ul className="mx-auto grid h-16 max-w-md grid-cols-5 items-center px-2">
        {TABS.map((tab) => {
          const active = isActive(pathname, tab, TABS);
          const Icon = tab.icon;
          const send = tab.href === "/transfer";
          return (
            <li key={tab.href} className="flex justify-center">
              <Link
                href={tab.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "group relative flex flex-col items-center gap-1 rounded-xl px-2 py-1.5 text-[11px] transition-colors",
                  "focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                  active ? "font-medium text-foreground" : "text-muted-foreground",
                )}
              >
                {send ? (
                  <span
                    className={cn(
                      "-mt-7 mb-2 grid size-12 place-items-center rounded-full bg-primary text-primary-foreground",
                      "shadow-[0_8px_20px_-6px_color-mix(in_oklab,var(--brand)_70%,transparent)] ring-4 ring-background",
                      "transition-transform group-active:scale-95",
                    )}
                  >
                    <Icon className="size-5" />
                  </span>
                ) : (
                  <span className="relative grid h-7 w-12 place-items-center">
                    {active && (
                      <motion.span
                        layoutId="tab-pill"
                        transition={reduce ? { duration: 0 } : { type: "spring", stiffness: 500, damping: 36 }}
                        className="absolute inset-0 rounded-full bg-[color-mix(in_oklab,var(--brand)_14%,transparent)]"
                      />
                    )}
                    <Icon className={cn("relative size-[18px]", active && "text-brand-text")} />
                  </span>
                )}
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
