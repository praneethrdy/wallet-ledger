import { redirect } from "next/navigation";

/**
 * Step 1: only the design system exists, so "/" opens the style guide.
 * Step 3 changes this: logged-out visitors go to /login (later the landing page), logged-in
 * users to /dashboard (docs/frontend-brief.md, "Routing of /").
 */
export default function Home() {
  redirect("/design");
}
