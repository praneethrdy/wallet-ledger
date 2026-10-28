"use client";

import {
  ArrowDownLeftIcon,
  ArrowUpRightIcon,
  CopyIcon,
  MoreHorizontalIcon,
  PlusIcon,
  RefreshCwIcon,
  SendIcon,
  WalletIcon,
} from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { cn } from "cn";

import { Logo, LogoMark } from "@/components/brand/logo";
import { Amount } from "@/components/ledger/amount";
import { DoubleEntryPreview } from "@/components/ledger/double-entry-preview";
import { StatCard } from "@/components/ledger/stat-card";
import { StatusBadge } from "@/components/ledger/status-badge";
import { TAccount } from "@/components/ledger/t-account";
import { AnimatedNumber } from "@/components/motion/animated-number";
import { FadeIn } from "@/components/motion/fade-in";
import { EmptyState, ErrorState } from "@/components/states/states";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatMoney } from "@/lib/money";

/* ---------------------------------------------------------------------------------------- */

const SECTIONS = [
  ["colour", "Colour"],
  ["type", "Typography"],
  ["shape", "Shape & elevation"],
  ["buttons", "Buttons"],
  ["inputs", "Inputs"],
  ["badges", "Badges"],
  ["money", "Money"],
  ["cards", "Cards"],
  ["table", "Table"],
  ["states", "States"],
  ["overlays", "Overlays"],
  ["signature", "Signature"],
] as const;

const SWATCHES: { name: string; token: string; light: string; dark: string; note?: string }[] = [
  { name: "Background", token: "--bg", light: "#FAFAFA", dark: "#09090B" },
  { name: "Surface", token: "--surface", light: "#FFFFFF", dark: "#111113" },
  { name: "Muted", token: "--surface-2", light: "#F4F4F5", dark: "#18181B" },
  { name: "Border", token: "--line", light: "#E4E4E7", dark: "#27272A" },
  { name: "Text", token: "--text", light: "#09090B", dark: "#FAFAFA" },
  { name: "Muted text", token: "--text-muted", light: "#71717A", dark: "#A1A1AA" },
  { name: "Accent", token: "--brand", light: "#5B5BD6", dark: "#7C7CF0", note: "sparingly" },
  { name: "Credit", token: "--credit", light: "#10B981", dark: "#10B981", note: "money in" },
  { name: "Warning", token: "--warning", light: "#F59E0B", dark: "#F59E0B" },
  { name: "Error", token: "--danger", light: "#F43F5E", dark: "#F43F5E", note: "errors only" },
];

const ENTRIES = [
  {
    ref: "TXN-20261028-8F3K2QXW",
    type: "TRANSFER",
    desc: "Rent share",
    dir: "DEBIT",
    amount: 1_850_000,
    balance: 4_126_200,
    at: "28 Oct, 10:42",
  },
  {
    ref: "TXN-20261027-Q2M9ZD4A",
    type: "DEPOSIT",
    desc: "Deposit mock_fb9da3",
    dir: "CREDIT",
    amount: 5_000_000,
    balance: 5_976_200,
    at: "27 Oct, 18:05",
  },
  {
    ref: "TXN-20261026-H7T1KX0P",
    type: "TRANSFER",
    desc: "Groceries",
    dir: "DEBIT",
    amount: 248_500,
    balance: 976_200,
    at: "26 Oct, 09:13",
  },
  {
    ref: "TXN-20261025-B3WE5RJN",
    type: "REVERSAL",
    desc: "Reversal of TXN-…-Y8VQ",
    dir: "CREDIT",
    amount: 120_000,
    balance: 1_224_700,
    at: "25 Oct, 16:30",
  },
] as const;

/* ---------------------------------------------------------------------------------------- */

export function StyleGuide() {
  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-40 glass">
        <div className="mx-auto flex h-14 max-w-[1280px] items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <Logo />
            <span className="hidden text-line-strong sm:inline">/</span>
            <span className="hidden text-sm text-muted-foreground sm:inline">Design system</span>
          </div>
          <ThemeToggle />
        </div>
      </header>

      <div className="mx-auto grid max-w-[1280px] grid-cols-1 gap-10 px-4 py-10 sm:px-6 lg:grid-cols-[180px_1fr]">
        <nav aria-label="Sections" className="hidden lg:block">
          <ul className="sticky top-24 space-y-1 text-sm">
            {SECTIONS.map(([id, label]) => (
              <li key={id}>
                <a
                  href={`#${id}`}
                  className="block rounded-md px-2 py-1 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
                >
                  {label}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <main className="min-w-0 space-y-16">
          <FadeIn>
            <p className="eyebrow">Wallet Ledger · v1</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight">Design system</h1>
            <p className="mt-2 max-w-2xl text-[15px] leading-relaxed text-muted-foreground">
              Calm, precise, trustworthy. Hairline borders and soft layered shadows for depth; the indigo accent only
              where it matters; money in Geist Mono with tabular figures. Toggle the theme at the top right: every token
              and component below follows it.
            </p>
          </FadeIn>

          <ColourSection />
          <TypeSection />
          <ShapeSection />
          <ButtonSection />
          <InputSection />
          <BadgeSection />
          <MoneySection />
          <CardSection />
          <TableSection />
          <StatesSection />
          <OverlaySection />
          <SignatureSection />
        </main>
      </div>
    </div>
  );
}

/* ---------------------------------------------------------------------------------------- */

function Section({
  id,
  title,
  description,
  children,
}: {
  id: string;
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-24">
      <div className="mb-5 border-b border-border pb-3">
        <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {children}
    </section>
  );
}

/** A labelled cell in a demo grid. */
function Specimen({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-col gap-2.5", className)}>
      <span className="eyebrow">{label}</span>
      <div className="flex flex-wrap items-center gap-3">{children}</div>
    </div>
  );
}

/* ---------------------------------------------------------------------------------------- */

function ColourSection() {
  return (
    <Section
      id="colour"
      title="Colour"
      description="Tokens as CSS variables. Both themes are shown side by side; the page itself follows the toggle."
    >
      <div className="grid gap-4 md:grid-cols-2">
        {(["light", "dark"] as const).map((mode) => (
          <div
            key={mode}
            className={cn(mode, "rounded-xl border border-border bg-background p-4 text-foreground shadow-sm")}
          >
            <div className="mb-3 flex items-center justify-between">
              <span className="eyebrow">{mode}</span>
              <Badge variant="brand">{mode === "light" ? "default" : "first-class"}</Badge>
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
              {SWATCHES.map((s) => (
                <div key={s.token} className="overflow-hidden rounded-lg border border-border bg-card">
                  <div className="h-10 border-b border-border" style={{ background: `var(${s.token})` }} />
                  <div className="px-2 py-1.5">
                    <div className="truncate text-[11px] font-medium">{s.name}</div>
                    <div className="num text-[10px] text-muted-foreground">{mode === "light" ? s.light : s.dark}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
      <p className="mt-3 text-sm text-muted-foreground">
        Money out is shown in the default text colour with a “−”, never red: red means something went wrong.
      </p>
    </Section>
  );
}

function TypeSection() {
  return (
    <Section
      id="type"
      title="Typography"
      description="Geist Sans for the interface, Geist Mono for money, IDs, references and JSON."
    >
      <div className="divide-y divide-border surface">
        {[
          [
            "Display · 30/600",
            <span key="d" className="text-3xl font-semibold tracking-tight">
              Every rupee, accounted for twice
            </span>,
          ],
          [
            "Heading · 18/600",
            <span key="h" className="text-lg font-semibold tracking-tight">
              Recent transactions
            </span>,
          ],
          [
            "Body · 14/400",
            <span key="b" className="text-sm">
              Transfers between your own wallets count as neither money in nor money out.
            </span>,
          ],
          [
            "Muted · 13/400",
            <span key="m" className="text-[13px] text-muted-foreground">
              Posted atomically: both legs or neither.
            </span>,
          ],
          [
            "Eyebrow · 11/500",
            <span key="e" className="eyebrow">
              Available balance
            </span>,
          ],
          [
            "Mono · money",
            <span key="n" className="num text-2xl font-medium">
              {formatMoney(5_976_200, "INR")}
            </span>,
          ],
          [
            "Mono · reference",
            <span key="r" className="num text-sm">
              TXN-20261028-8F3K2QXW
            </span>,
          ],
        ].map(([label, node]) => (
          <div
            key={label as string}
            className="grid grid-cols-1 items-baseline gap-2 px-5 py-4 sm:grid-cols-[180px_1fr]"
          >
            <span className="eyebrow">{label}</span>
            {node}
          </div>
        ))}
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div className="surface p-5">
          <span className="eyebrow">Tabular figures · aligned</span>
          <div className="mt-3 space-y-1 text-right">
            {[1_850_000, 5_000_000, 248_500, 1_111_100].map((v) => (
              <div key={v} className="num text-sm">
                {formatMoney(v, "INR")}
              </div>
            ))}
          </div>
        </div>
        <div className="surface p-5">
          <span className="eyebrow">Proportional · misaligned (avoid)</span>
          <div className="mt-3 space-y-1 text-right font-sans">
            {[1_850_000, 5_000_000, 248_500, 1_111_100].map((v) => (
              <div key={v} className="text-sm [font-variant-numeric:proportional-nums]">
                {formatMoney(v, "INR")}
              </div>
            ))}
          </div>
        </div>
      </div>
    </Section>
  );
}

function ShapeSection() {
  return (
    <Section
      id="shape"
      title="Shape & elevation"
      description="Radius 14px for cards, 10px for inputs and buttons. Depth = 1px hairline + soft layered shadows. 4px spacing grid; content max-width 1280px."
    >
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Full class names on purpose: Tailwind only generates classes it can see verbatim. */}
        {(
          [
            ["xs", "shadow-xs"],
            ["sm", "shadow-sm"],
            ["md", "shadow-md"],
            ["lg", "shadow-lg"],
          ] as const
        ).map(([s, shadow]) => (
          <div key={s} className={cn("grid h-24 place-items-center rounded-xl border border-border bg-card", shadow)}>
            <span className="num text-sm text-muted-foreground">shadow-{s}</span>
          </div>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap items-end gap-6">
        <div className="grid size-24 place-items-center rounded-xl border border-border bg-card text-xs text-muted-foreground shadow-sm">
          14px card
        </div>
        <div className="grid h-10 w-36 place-items-center rounded-lg border border-border bg-card text-xs text-muted-foreground shadow-xs">
          10px control
        </div>
        <div className="flex items-end gap-1">
          {[1, 2, 3, 4, 6, 8, 12].map((n) => (
            <div key={n} className="flex flex-col items-center gap-1">
              <div className="w-4 rounded-sm bg-brand/80" style={{ height: n * 4 }} />
              <span className="num text-[10px] text-muted-foreground">{n * 4}</span>
            </div>
          ))}
        </div>
      </div>
    </Section>
  );
}

function ButtonSection() {
  const [loading, setLoading] = useState(false);
  return (
    <Section
      id="buttons"
      title="Buttons"
      description="One primary (accent) action per view. Every variant has hover, focus-visible (2px accent ring), disabled and loading states."
    >
      <div className="grid gap-7 surface p-6">
        <Specimen label="Variants">
          <Button>
            <SendIcon /> Send money
          </Button>
          <Button variant="outline">
            <PlusIcon /> New wallet
          </Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="destructive">Freeze wallet</Button>
          <Button variant="link">View all</Button>
        </Specimen>
        <Specimen label="States (primary)">
          <Button>Default</Button>
          <Button className="bg-brand-hover">Hover</Button>
          <Button className="ring-2 ring-ring ring-offset-2 ring-offset-background">Focus-visible</Button>
          <Button disabled>Disabled</Button>
          <Button loading>Loading</Button>
        </Specimen>
        <Specimen label="States (outline)">
          <Button variant="outline">Default</Button>
          <Button variant="outline" className="border-line-strong bg-muted">
            Hover
          </Button>
          <Button variant="outline" className="ring-2 ring-ring ring-offset-2 ring-offset-background">
            Focus-visible
          </Button>
          <Button variant="outline" disabled>
            Disabled
          </Button>
          <Button variant="outline" loading>
            Loading
          </Button>
        </Specimen>
        <Specimen label="Sizes & icon buttons">
          <Button size="sm">Small</Button>
          <Button>Default</Button>
          <Button size="lg">Large</Button>
          <Button size="icon" variant="outline" aria-label="Copy">
            <CopyIcon />
          </Button>
          <Button size="icon-sm" variant="ghost" aria-label="More">
            <MoreHorizontalIcon />
          </Button>
        </Specimen>
        <Specimen label="Try it">
          <Button
            loading={loading}
            onClick={() => {
              setLoading(true);
              setTimeout(() => setLoading(false), 1400);
            }}
          >
            Confirm transfer
          </Button>
          <span className="text-sm text-muted-foreground">Keeps its width while loading; can’t be double-clicked.</span>
        </Specimen>
      </div>
    </Section>
  );
}

function InputSection() {
  return (
    <Section
      id="inputs"
      title="Inputs"
      description="Labels above, help or error text below. Amounts use Geist Mono with a currency prefix."
    >
      <div className="grid gap-6 surface p-6 md:grid-cols-2">
        <Field label="Email" htmlFor="i1" help="We never show whether an email is registered.">
          <Input id="i1" type="email" placeholder="you@example.com" />
        </Field>
        <Field label="Focus" htmlFor="i2">
          <Input id="i2" defaultValue="alice@wallet.local" className="border-ring ring-2 ring-ring/25" />
        </Field>
        <Field label="Amount" htmlFor="i3" help="Up to 2 decimal places.">
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-3 grid place-items-center text-sm text-muted-foreground">
              ₹
            </span>
            <Input id="i3" inputMode="decimal" defaultValue="500.00" className="pl-7 text-right num" />
          </div>
        </Field>
        <Field label="Invalid" htmlFor="i4" error="Not enough balance in this wallet">
          <Input id="i4" aria-invalid defaultValue="9,99,999.00" className="text-right num" />
        </Field>
        <Field label="Disabled" htmlFor="i5">
          <Input id="i5" disabled defaultValue="01a181c2-42c4-7ade-97e5-4557181301dc" className="num" />
        </Field>
        <Field label="Wallet" htmlFor="i6">
          <Select defaultValue="main">
            <SelectTrigger id="i6" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="main">Main · ₹59,762.00</SelectItem>
              <SelectItem value="savings">Savings · ₹12,910.00</SelectItem>
            </SelectContent>
          </Select>
        </Field>
        <Field label="Note" htmlFor="i7" help="Optional, up to 140 characters." className="md:col-span-2">
          <Textarea id="i7" placeholder="What’s it for?" />
        </Field>
        <div className="flex items-center gap-3">
          <Switch id="i8" defaultChecked />
          <Label htmlFor="i8">Email me a receipt</Label>
        </div>
      </div>
    </Section>
  );
}

function Field({
  label,
  htmlFor,
  help,
  error,
  className,
  children,
}: {
  label: string;
  htmlFor: string;
  help?: string;
  error?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={htmlFor} className="text-[13px] font-medium">
        {label}
      </Label>
      {children}
      {error ? (
        <p className="text-xs text-danger-text">{error}</p>
      ) : (
        help && <p className="text-xs text-muted-foreground">{help}</p>
      )}
    </div>
  );
}

function BadgeSection() {
  return (
    <Section
      id="badges"
      title="Badges"
      description="10% tinted background, solid text. One mapping for every backend status, type and role."
    >
      <div className="grid gap-6 surface p-6">
        <Specimen label="Tones">
          <Badge>Neutral</Badge>
          <Badge variant="brand">Brand</Badge>
          <Badge variant="success">Success</Badge>
          <Badge variant="warning">Warning</Badge>
          <Badge variant="danger">Danger</Badge>
          <Badge variant="outline">Outline</Badge>
        </Specimen>
        <Specimen label="Wallet · deposit · reconciliation · webhook">
          {["ACTIVE", "FROZEN", "PENDING", "SUCCEEDED", "FAILED", "PASSED", "RUNNING", "PROCESSED", "REJECTED"].map(
            (s) => (
              <StatusBadge key={s} value={s} />
            ),
          )}
        </Specimen>
        <Specimen label="Transaction types · roles">
          {["DEPOSIT", "TRANSFER", "REVERSAL", "CUSTOMER", "SUPPORT", "ADMIN"].map((s) => (
            <StatusBadge key={s} value={s} />
          ))}
        </Specimen>
      </div>
    </Section>
  );
}

function MoneySection() {
  return (
    <Section
      id="money"
      title="Money"
      description="Integers in minor units, formatted exactly (no floating point). Credits in emerald with “+”; debits in the default colour with “−”."
    >
      <div className="divide-y divide-border surface">
        {[
          ["Credit", <Amount key="1" minor={5_000_000} currency="INR" tone="credit" />],
          ["Debit", <Amount key="2" minor={1_850_000} currency="INR" tone="debit" />],
          ["Signed (net 0)", <Amount key="3" minor={0} currency="INR" tone="signed" />],
          ["Balance", <Amount key="4" minor={5_976_200} currency="INR" className="text-xl font-medium" />],
          ["Large (Indian grouping)", <Amount key="5" minor={1_234_567_890} currency="INR" />],
          ["Smallest unit", <Amount key="6" minor={5} currency="INR" />],
          [
            "USD / EUR",
            <span key="7" className="flex gap-4">
              <Amount minor={123_456} currency="USD" />
              <Amount minor={99} currency="EUR" />
            </span>,
          ],
        ].map(([label, node]) => (
          <div key={label as string} className="flex items-center justify-between px-5 py-3">
            <span className="text-sm text-muted-foreground">{label}</span>
            {node}
          </div>
        ))}
      </div>
    </Section>
  );
}

function CardSection() {
  const [balance, setBalance] = useState(5_976_200);
  return (
    <Section
      id="cards"
      title="Cards"
      description="Hairline border, soft shadow, 14px radius. Balances count up when they change."
    >
      <div className="grid gap-4 md:grid-cols-3">
        <StatCard
          label="Total balance"
          icon={<WalletIcon />}
          value={<AnimatedNumber value={balance} format={(v) => formatMoney(v, "INR")} />}
          hint="Across 2 INR wallets"
        />
        <StatCard
          label="In this month"
          icon={<ArrowDownLeftIcon />}
          value={<span className="text-credit-text">{formatMoney(4_668_000, "INR", "always")}</span>}
          hint="Deposits and transfers received"
        />
        <StatCard
          label="Out this month"
          icon={<ArrowUpRightIcon />}
          value={`−${formatMoney(3_862_000, "INR")}`}
          hint="Transfers sent"
        />
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Main wallet</CardTitle>
            <CardDescription className="num">01a181c2-42c4-7ade-97e5-4557181301dc</CardDescription>
          </CardHeader>
          <CardContent className="flex items-end justify-between">
            <Amount minor={balance} currency="INR" className="text-2xl font-medium tracking-tight" />
            <StatusBadge value="ACTIVE" />
          </CardContent>
        </Card>
        <Card className="justify-center">
          <CardContent className="flex flex-wrap items-center gap-3">
            <Button variant="outline" onClick={() => setBalance((b) => b + 500_000)}>
              <RefreshCwIcon /> Receive ₹5,000
            </Button>
            <Button variant="outline" onClick={() => setBalance((b) => Math.max(0, b - 248_500))}>
              Spend ₹2,485
            </Button>
            <span className="text-sm text-muted-foreground">Watch the balance count.</span>
          </CardContent>
        </Card>
      </div>
    </Section>
  );
}

function TableSection() {
  return (
    <Section
      id="table"
      title="Table"
      description="Eyebrow headers, hairline rows, money right-aligned with tabular figures, running balance per row."
    >
      <div className="overflow-hidden surface">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead>Date</TableHead>
              <TableHead>Description</TableHead>
              <TableHead>Reference</TableHead>
              <TableHead numeric>Amount</TableHead>
              <TableHead numeric>Balance</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ENTRIES.map((e) => (
              <TableRow key={e.ref}>
                <TableCell className="text-muted-foreground">{e.at}</TableCell>
                <TableCell>
                  <div className="flex items-center gap-2.5">
                    <StatusBadge value={e.type} />
                    <span>{e.desc}</span>
                  </div>
                </TableCell>
                <TableCell className="num text-xs text-muted-foreground">{e.ref}</TableCell>
                <TableCell numeric>
                  <Amount minor={e.amount} currency="INR" tone={e.dir === "CREDIT" ? "credit" : "debit"} />
                </TableCell>
                <TableCell numeric className="text-muted-foreground">
                  {formatMoney(e.balance, "INR")}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </Section>
  );
}

function StatesSection() {
  return (
    <Section id="states" title="States" description="Every data view has a designed loading, empty and error state.">
      <div className="grid gap-4 lg:grid-cols-3">
        <div className="surface p-5">
          <span className="eyebrow">Loading</span>
          <div className="mt-4 space-y-3">
            <Skeleton className="h-8 w-40" />
            <Skeleton className="h-4 w-24" />
            <div className="space-y-2 pt-2">
              {[0, 1, 2].map((i) => (
                <div key={i} className="flex items-center justify-between">
                  <Skeleton className="h-4 w-36" />
                  <Skeleton className="h-4 w-20" />
                </div>
              ))}
            </div>
          </div>
        </div>
        <div className="surface">
          <EmptyState
            title="No transactions yet"
            description="Add money to this wallet to see entries here."
            action={
              <Button variant="outline" size="sm">
                <PlusIcon /> Add money
              </Button>
            }
          />
        </div>
        <div className="surface">
          <ErrorState
            title="Couldn’t load your wallets"
            description="Something went wrong on our side. Try again in a moment."
            correlationId="7c1e9a52-3b0f-4d1e-9f58-2a6c0e4b8d11"
            action={
              <Button variant="outline" size="sm">
                <RefreshCwIcon /> Retry
              </Button>
            }
          />
        </div>
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <Alert>
          <AlertTitle>This wallet is frozen</AlertTitle>
          <AlertDescription>
            Transfers in and out are blocked. Contact support if you think this is a mistake.
          </AlertDescription>
        </Alert>
        <div className="flex flex-wrap items-center gap-3 surface p-5">
          <span className="w-full eyebrow">Toasts</span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => toast.success("Transfer sent", { description: "TXN-20261028-8F3K2QXW" })}
          >
            Success
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => toast("Already submitted", { description: "Showing the original result." })}
          >
            Replayed
          </Button>
          <Button variant="outline" size="sm" onClick={() => toast.error("Not enough balance in this wallet")}>
            Error
          </Button>
        </div>
      </div>
    </Section>
  );
}

function OverlaySection() {
  return (
    <Section
      id="overlays"
      title="Overlays"
      description="Dialogs, menus, tooltips and tabs share the surface, hairline and large soft shadow."
    >
      <div className="flex flex-wrap items-center gap-3 surface p-6">
        <Dialog>
          <DialogTrigger asChild>
            <Button variant="outline">Open dialog</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>New wallet</DialogTitle>
              <DialogDescription>Wallets hold one currency. You can have several.</DialogDescription>
            </DialogHeader>
            <div className="grid gap-1.5">
              <Label htmlFor="d1">Name</Label>
              <Input id="d1" placeholder="Holiday fund" />
            </div>
            <DialogFooter>
              <Button>Create wallet</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="outline">Confirm reversal</Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Reverse TXN-20261028-8F3K2QXW?</AlertDialogTitle>
              <AlertDialogDescription>
                This posts a new REVERSAL transaction with mirrored legs. History is not changed, and it can’t be
                undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>Post reversal</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">Menu</Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuLabel>alice@wallet.local</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Settings</DropdownMenuItem>
            <DropdownMenuItem>Log out everywhere</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Log out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="outline" size="icon" aria-label="Copy wallet ID">
              <CopyIcon />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Copy wallet ID</TooltipContent>
        </Tooltip>

        <Tabs defaultValue="all" className="ml-auto">
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="in">In</TabsTrigger>
            <TabsTrigger value="out">Out</TabsTrigger>
          </TabsList>
          <TabsContent value="all" />
        </Tabs>
      </div>
    </Section>
  );
}

function SignatureSection() {
  return (
    <Section id="signature" title="Signature components" description="The screens that make the ledger visible.">
      <div className="grid gap-6 lg:grid-cols-2">
        <div>
          <span className="eyebrow">Transfer review · double-entry preview</span>
          <div className="mt-3">
            <DoubleEntryPreview
              from={{ label: "Your wallet · Main", detail: "01a181c2-42c4-7ade-…" }}
              to={{ label: "Recipient", detail: "01a181c4-2199-7dea-…" }}
              amount={50_000}
              currency="INR"
            />
          </div>
        </div>
        <div>
          <span className="eyebrow">Transaction detail · T-account</span>
          <div className="mt-3">
            <TAccount
              legs={[
                { accountName: "Main", direction: "DEBIT", amount: 50_000, currency: "INR" },
                { accountName: "Another user’s wallet", direction: "CREDIT", amount: 50_000, currency: "INR" },
              ]}
            />
          </div>
        </div>
      </div>

      <div className="mt-6">
        <span className="eyebrow">Auth brand panel · dot pattern, radial glow, floating balance card</span>
        <div className="relative mt-3 h-80 overflow-hidden rounded-xl border border-border bg-background">
          <div className="absolute inset-0 bg-dots [mask-image:radial-gradient(ellipse_at_center,black,transparent_75%)]" />
          <div className="absolute top-1/2 left-1/2 size-[520px] -translate-x-1/2 -translate-y-1/2 glow" />
          <div className="relative flex h-full flex-col justify-between p-8">
            <Logo />
            <div className="mx-auto w-72 rotate-[-2deg] surface p-5 shadow-lg">
              <div className="flex items-center justify-between">
                <span className="eyebrow">Available balance</span>
                <LogoMark className="size-5" />
              </div>
              <div className="mt-2 num text-2xl font-medium tracking-tight">{formatMoney(5_976_200, "INR")}</div>
              <div className="mt-4 flex items-center justify-between text-xs">
                <span className="text-muted-foreground">This month</span>
                <span className="num text-credit-text">{formatMoney(806_000, "INR", "always")}</span>
              </div>
            </div>
            <p className="max-w-sm text-sm text-muted-foreground">
              Every movement is a balanced pair of entries. Nothing is ever edited, only corrected.
            </p>
          </div>
        </div>
      </div>
    </Section>
  );
}
