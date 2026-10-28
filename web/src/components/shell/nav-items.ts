import type { SessionUser } from "@/lib/session/data";

export type NavItem = { href: string; label: string };

/** What each role sees in the top bar. Hiding is UX only; the backend enforces access. */
export function navFor(role: SessionUser["role"]): NavItem[] {
  switch (role) {
    case "CUSTOMER":
      return [
        { href: "/dashboard", label: "Dashboard" },
        { href: "/wallets", label: "Wallets" },
        { href: "/transfer", label: "Transfer" },
        { href: "/deposit", label: "Add money" },
        { href: "/transactions", label: "Transactions" },
      ];
    case "ADMIN":
      return [
        { href: "/admin", label: "Overview" },
        { href: "/admin/wallets", label: "Wallets" },
        { href: "/support/users", label: "Users" },
        { href: "/support/transactions", label: "Transactions" },
        { href: "/support/webhook-events", label: "Webhooks" },
        { href: "/admin/reconciliation", label: "Reconciliation" },
      ];
    case "SUPPORT":
      return [
        { href: "/support/users", label: "Users" },
        { href: "/support/transactions", label: "Transactions" },
        { href: "/support/webhook-events", label: "Webhooks" },
        { href: "/admin/reconciliation", label: "Reconciliation" },
      ];
  }
}
