"use client";

import { MenuIcon } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

import { cn } from "cn";

import { Logo } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";

import type { NavItem } from "./nav-items";

/** Length of the longest prefix of this item that matches the path, or -1. */
function matchLength(pathname: string, item: NavItem): number {
  const prefixes = [item.href, ...(item.also ?? [])];
  return Math.max(-1, ...prefixes.filter((p) => pathname === p || pathname.startsWith(`${p}/`)).map((p) => p.length));
}

/** The item with the most specific match wins: "/admin" must not light up on "/admin/wallets". */
function isActive(pathname: string, item: NavItem, all: NavItem[]) {
  const mine = matchLength(pathname, item);
  return mine >= 0 && all.every((other) => other === item || matchLength(pathname, other) < mine);
}

/** Desktop: inline links. Mobile: a menu button opening a sheet. Active item uses the accent. */
export function NavLinks({ items }: { items: NavItem[] }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  return (
    <>
      <nav aria-label="Main" className="hidden items-center gap-0.5 md:flex">
        {items.map((item) => {
          const active = isActive(pathname, item, items);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "relative rounded-md px-3 py-1.5 text-sm transition-colors duration-150",
                "focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                active ? "text-foreground" : "text-muted-foreground hover:text-foreground",
              )}
            >
              {item.label}
              {active && <span className="absolute inset-x-3 -bottom-[13px] h-0.5 rounded-full bg-brand" />}
            </Link>
          );
        })}
      </nav>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger asChild>
          <Button variant="ghost" size="icon-sm" className="md:hidden" aria-label="Open menu">
            <MenuIcon />
          </Button>
        </SheetTrigger>
        <SheetContent side="left" className="w-72">
          <SheetHeader>
            <SheetTitle>
              <Logo />
            </SheetTitle>
          </SheetHeader>
          <nav aria-label="Main" className="grid gap-1 px-4">
            {items.map((item) => {
              const active = isActive(pathname, item, items);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setOpen(false)}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "rounded-lg px-3 py-2 text-sm transition-colors",
                    active ? "bg-muted font-medium text-foreground" : "text-muted-foreground hover:bg-muted",
                  )}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </SheetContent>
      </Sheet>
    </>
  );
}
