import "server-only";

import { redirect } from "next/navigation";

import { getSession } from "@/lib/session/session";
import type { SessionUser } from "@/lib/session/data";

/**
 * The signed-in user, or a redirect to /login. Read inside a <Suspense> boundary.
 * This is a convenience for rendering (names, role-aware UI); authorization is the backend's job.
 */
export async function requireUser(): Promise<SessionUser> {
  const session = await getSession();
  if (!session) redirect("/login");
  return session.user;
}

export async function requireRole(...roles: SessionUser["role"][]): Promise<SessionUser> {
  const user = await requireUser();
  if (!roles.includes(user.role)) redirect("/");
  return user;
}
