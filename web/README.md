# Wallet Ledger: web

Next.js 16 frontend for the wallet backend. Built step by step from [`docs/frontend-brief.md`](../docs/frontend-brief.md).

> **Status:** step 1 done: design system, re-themed components, and the `/design` style guide. Screens come next.

## Requirements

- **Node.js ≥ 20.9** (22 LTS recommended, see `.nvmrc`). On this machine nvm-windows still has Node 18 active, so use Node 22 explicitly, or run `nvm install 22 && nvm use 22`.

## Scripts

| Command                                                       | What it does                                                                                       |
| ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `npm run dev`                                                 | dev server on http://localhost:3000 (`/` currently redirects to `/design`)                         |
| `npm run build` / `npm start`                                 | production build / serve                                                                           |
| `npm run lint` · `npm run typecheck` · `npm run format:check` | quality gates                                                                                      |
| `npm run gen:api`                                             | regenerate `src/lib/api/schema.d.ts` from `../backend/openapi.json` (the API contract)             |
| `node scripts/screenshot-design.mjs`                          | screenshot `/design` (light, dark, mobile) into `docs/screenshots/` (needs `npm start -- -p 3100`) |

## Design system

- **Tokens:** CSS variables in `src/app/globals.css`, with light and dark both first-class.
  - The brand palette is mapped onto shadcn's semantic tokens, so every component draws from one palette.
- **Utilities:**
  - `num`: Geist Mono with tabular figures, for money, IDs and references;
  - `eyebrow`: small uppercase labels;
  - `surface`: a hairline card;
  - `glass`: the blurred top bar;
  - `bg-dots`, `bg-grid`, `glow`: auth backdrops;
  - `shimmer`: skeleton loading.
- **Money:** `src/lib/money.ts` formats integer minor units exactly (no floating point).
- **Ledger components** in `src/components/ledger/`: `Amount`, `StatusBadge`, `StatCard`, `DoubleEntryPreview`, `TAccount`.
