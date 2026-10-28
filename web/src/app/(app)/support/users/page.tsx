import { SearchIcon, SearchXIcon } from "lucide-react";
import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { StatusBadge } from "@/components/ledger/status-badge";
import { PageHeader } from "@/components/shell/page-header";
import { EmptyState } from "@/components/states/states";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { api, get } from "@/lib/api/client";
import { formatDate } from "@/lib/format";

export const metadata: Metadata = { title: "Users · Support" };

export default function SupportUsersPage({ searchParams }: PageProps<"/support/users">) {
  return (
    <>
      <PageHeader title="Users" description="Find a customer by email to see their account and wallet balances." />
      <Suspense fallback={<Skeleton className="h-[480px] rounded-xl" />}>
        <Users searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function Users({ searchParams }: { searchParams: PageProps<"/support/users">["searchParams"] }) {
  const { email: raw } = await searchParams;
  const email = typeof raw === "string" ? raw.trim().slice(0, 254) : "";
  const users = await get(
    (await api()).GET("/api/v1/support/users", { params: { query: { email: email || undefined, limit: 50 } } }),
  );

  return (
    <div className="grid gap-4">
      {/* A plain GET form: the search lands in the URL with no client JS needed. */}
      <form role="search" action="/support/users" className="flex flex-wrap items-end gap-3 surface p-4">
        <div className="grid min-w-0 flex-1 gap-1.5">
          <Label htmlFor="u-email" className="text-xs text-muted-foreground">
            Email contains
          </Label>
          <div className="relative">
            <SearchIcon className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="u-email"
              name="email"
              type="search"
              defaultValue={email}
              placeholder="alice@…"
              className="pl-8"
              autoComplete="off"
            />
          </div>
        </div>
        <Button type="submit" variant="outline">
          Search
        </Button>
      </form>

      <Card className="gap-0 overflow-hidden py-0">
        {users.length === 0 ? (
          <EmptyState
            icon={<SearchXIcon />}
            title="No users match"
            description="Search matches any part of the email address, ignoring case."
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="pl-5">Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="pr-5 text-right">Joined</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((u) => (
                <TableRow key={u.id} className="relative">
                  <TableCell className="pl-5 font-medium">
                    {/* The link's ::after covers the row, so the whole row is clickable but it's one tab stop. */}
                    <Link
                      href={`/support/users/${u.id}`}
                      className="after:absolute after:inset-0 focus-visible:underline focus-visible:outline-none"
                    >
                      {u.email}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <StatusBadge value={u.role} />
                  </TableCell>
                  <TableCell>
                    <StatusBadge value={u.status} />
                  </TableCell>
                  <TableCell className="pr-5 text-right text-muted-foreground">{formatDate(u.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
      {users.length === 50 && (
        <p className="text-center text-xs text-muted-foreground">
          Showing the first 50. Narrow the search to see more.
        </p>
      )}
    </div>
  );
}
