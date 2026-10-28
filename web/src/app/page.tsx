import { redirect } from "next/navigation";

/**
 * The proxy routes "/" before this renders: signed-in users go to their home area, everyone
 * else to /login (a marketing landing page may replace this later, step 11).
 */
export default function Home() {
  redirect("/login");
}
