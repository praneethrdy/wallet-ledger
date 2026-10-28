import { MonitorSmartphoneIcon } from "lucide-react";
import type { Metadata } from "next";
import { Suspense } from "react";

import { logoutEverywhere } from "@/app/(auth)/actions";
import { StatusBadge } from "@/components/ledger/status-badge";
import { PageHeader } from "@/components/shell/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { api, get } from "@/lib/api/client";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" description="Your profile and sessions." />
      <div className="grid max-w-2xl gap-4">
        <Suspense fallback={<Skeleton className="h-40 w-full rounded-xl" />}>
          <Profile />
        </Suspense>
        <Card>
          <CardHeader>
            <CardTitle>Sessions</CardTitle>
            <CardDescription>
              Signs you out on every device and browser. Access already granted expires within 15 minutes.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form action={logoutEverywhere}>
              <Button type="submit" variant="outline">
                <MonitorSmartphoneIcon /> Sign out everywhere
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  );
}

async function Profile() {
  const me = await get((await api()).GET("/api/v1/me"));
  return (
    <Card>
      <CardHeader>
        <CardTitle>Profile</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <dt className="pt-0.5 eyebrow">Email</dt>
          <dd className="text-sm">{me.email}</dd>
          <dt className="pt-0.5 eyebrow">Role</dt>
          <dd>
            <StatusBadge value={me.role} />
          </dd>
          <dt className="pt-0.5 eyebrow">Status</dt>
          <dd>
            <StatusBadge value={me.status} />
          </dd>
          <dt className="pt-0.5 eyebrow">User ID</dt>
          <dd className="num text-xs break-all text-muted-foreground">{me.id}</dd>
        </dl>
      </CardContent>
    </Card>
  );
}
