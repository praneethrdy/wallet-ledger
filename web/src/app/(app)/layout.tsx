import Link from "next/link";
import { Suspense } from "react";

import { Logo } from "@/components/brand/logo";
import { QueryProvider } from "@/components/providers/query-provider";
import { navFor } from "@/components/shell/nav-items";
import { NavLinks } from "@/components/shell/nav-links";
import { UserMenu } from "@/components/shell/user-menu";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Skeleton } from "@/components/ui/skeleton";
import { requireUser } from "@/lib/auth/current-user";
import { env } from "@/lib/env";

/**
 * Authenticated shell. The chrome is static; everything that depends on the session (role-aware
 * nav, user menu) streams in behind its own <Suspense>, as Cache Components requires.
 */
export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-40 glass">
        <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-6 px-4 sm:px-6">
          <Link href="/" className="rounded-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
            <Logo />
          </Link>
          <Suspense fallback={<NavSkeleton />}>
            <RoleNav />
          </Suspense>
          <div className="ml-auto flex items-center gap-3">
            <ThemeToggle className="hidden sm:inline-flex" />
            <Suspense fallback={<Skeleton className="size-8 rounded-full" />}>
              <AccountMenu />
            </Suspense>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-[1280px] px-4 py-8 sm:px-6 sm:py-10">
        <QueryProvider>{children}</QueryProvider>
      </main>
    </div>
  );
}

async function RoleNav() {
  const user = await requireUser();
  return <NavLinks items={navFor(user.role, { assistant: Boolean(env.assistant) })} />;
}

async function AccountMenu() {
  const user = await requireUser();
  return <UserMenu user={user} />;
}

function NavSkeleton() {
  return (
    <div className="hidden items-center gap-4 md:flex">
      {[64, 56, 60, 72].map((w) => (
        <Skeleton key={w} className="h-4" style={{ width: w }} />
      ))}
    </div>
  );
}
