import { istDay } from "@/lib/format";

/**
 * Questions per user per day (IST), so a public demo can't exhaust the model's free quota.
 * In memory, per server process: fine for the single-instance deployment; it resets on restart.
 */
export function createDailyLimiter(limit: number, now: () => number = Date.now) {
  const counts = new Map<string, { day: string; used: number }>();
  return {
    /** Records one question; false when the user is already at today's limit. */
    take(userId: string): boolean {
      const day = istDay(new Date(now()).toISOString());
      const entry = counts.get(userId);
      const used = entry?.day === day ? entry.used : 0;
      if (used >= limit) return false;
      counts.set(userId, { day, used: used + 1 });
      return true;
    },
  };
}
