"use client";

import { LogOutIcon, MonitorSmartphoneIcon, SettingsIcon } from "lucide-react";
import Link from "next/link";

import { logout, logoutEverywhere } from "@/app/(auth)/actions";
import { StatusBadge } from "@/components/ledger/status-badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { SessionUser } from "@/lib/session/data";

export function UserMenu({ user }: { user: SessionUser }) {
  const initial = user.email[0]?.toUpperCase() ?? "?";
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label="Account menu"
        className="grid size-8 place-items-center rounded-full border border-border bg-card text-xs font-semibold shadow-xs transition-colors hover:border-line-strong focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background focus-visible:outline-none"
      >
        {initial}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuLabel className="flex flex-col gap-1.5 py-2">
          <span className="truncate text-sm font-medium">{user.email}</span>
          <StatusBadge value={user.role} />
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/settings">
            <SettingsIcon /> Settings
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void logoutEverywhere()}>
          <MonitorSmartphoneIcon /> Sign out everywhere
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void logout()}>
          <LogOutIcon /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
