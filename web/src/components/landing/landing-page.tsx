import {
  ArrowRightIcon,
  CopyCheckIcon,
  FileClockIcon,
  GitBranchIcon,
  LockKeyholeIcon,
  ScaleIcon,
  ShieldCheckIcon,
  Undo2Icon,
  UsersIcon,
} from "lucide-react";
import Link from "next/link";

import { Logo } from "@/components/brand/logo";
import { CopyButton } from "@/components/ledger/copy-button";
import { TAccount } from "@/components/ledger/t-account";
import { FadeIn } from "@/components/motion/fade-in";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Button } from "@/components/ui/button";

import { ProductPreview } from "./product-preview";
import { ProofRibbon } from "./proof-ribbon";
import { Reveal } from "./reveal";

const REPO = "https://github.com/praneethrdy/wallet-ledger";

/** Public home page for signed-out visitors. Static: no session, no backend calls. */
export function LandingPage() {
  return (
    <div className="min-h-dvh overflow-x-clip">
      <TopBar />
      <main>
        <Hero />
        <ProofRibbon />
        <Features />
        <HowMoneyMoves />
        <ApiExample />
        <Stack />
        <FinalCta />
      </main>
      <Footer />
    </div>
  );
}

function TopBar() {
  return (
    <header className="sticky top-0 z-40 glass">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4 sm:px-6">
        <Link href="/" className="rounded-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
          <Logo />
        </Link>
        <nav aria-label="Sections" className="hidden items-center gap-6 text-sm text-muted-foreground md:flex">
          <a href="#features" className="transition-colors hover:text-foreground">
            Features
          </a>
          <a href="#how" className="transition-colors hover:text-foreground">
            How it works
          </a>
          <a href="#api" className="transition-colors hover:text-foreground">
            API
          </a>
          <a href={REPO} className="transition-colors hover:text-foreground">
            GitHub
          </a>
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle className="hidden sm:inline-flex" />
          <Button variant="ghost" asChild className="hidden sm:inline-flex">
            <Link href="/login">Sign in</Link>
          </Button>
          <Button asChild>
            <Link href="/login">
              Try the demo <ArrowRightIcon />
            </Link>
          </Button>
        </div>
      </div>
    </header>
  );
}

function Hero() {
  return (
    <section className="relative">
      <div
        className="absolute inset-0 -z-10 bg-grid [mask-image:radial-gradient(ellipse_at_top,black,transparent_70%)]"
        aria-hidden
      />
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 pt-16 pb-20 sm:px-6 lg:grid-cols-[1fr_1.15fr] lg:pt-24 lg:pb-28 [&>*]:min-w-0">
        <FadeIn>
          <p className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1 text-xs text-muted-foreground shadow-xs">
            <span className="size-1.5 rounded-full bg-credit" aria-hidden />
            Double-entry ledger · Spring Boot · Next.js
          </p>
          <h1 className="mt-6 text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-[56px] lg:leading-[1.05]">
            Money that always adds&nbsp;up.
          </h1>
          <p className="mt-5 max-w-xl text-base text-pretty text-muted-foreground sm:text-lg">
            A digital wallet built like a bank&rsquo;s books: every movement is a balanced pair of ledger entries,
            transfers can&rsquo;t be sent twice, deposits settle only on signed webhooks, and every staff action is
            audited.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Button size="lg" asChild>
              <Link href="/login">
                Try the live demo <ArrowRightIcon />
              </Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <a href={REPO}>
                <GitBranchIcon /> View the code
              </a>
            </Button>
          </div>
          <p className="mt-4 text-xs text-muted-foreground">
            Demo login on the sign-in page · no real money · free hosting, so the first load may take a minute.
          </p>
        </FadeIn>
        <FadeIn delay={0.08}>
          <ProductPreview />
        </FadeIn>
      </div>
    </section>
  );
}

const FEATURES = [
  {
    icon: ScaleIcon,
    title: "Double-entry, enforced twice",
    text: "Every transaction's debits equal its credits, checked in Java and again by a Postgres constraint trigger at commit.",
  },
  {
    icon: CopyCheckIcon,
    title: "No double spend",
    text: "Idempotency keys make retries and double-clicks safe: a repeated request replays the original result instead of moving money again.",
  },
  {
    icon: LockKeyholeIcon,
    title: "Safe under concurrency",
    text: "Row locks taken in a fixed order: no overspend and no deadlocks with a hundred transfers racing the same wallet.",
  },
  {
    icon: ShieldCheckIcon,
    title: "Signed webhooks",
    text: "Deposits settle only on HMAC-signed provider events, verified on the raw bytes, with replay protection and secret rotation.",
  },
  {
    icon: Undo2Icon,
    title: "Reversals, never edits",
    text: "The ledger is append-only. Mistakes are fixed by posting the opposite legs, and a nightly reconciliation proves it all balances.",
  },
  {
    icon: UsersIcon,
    title: "An audited staff console",
    text: "Support and admin roles can look up users, freeze wallets and reverse transfers. Every lookup lands in the audit log.",
  },
];

function Features() {
  return (
    <section id="features" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-20 sm:px-6 lg:py-28">
      <Reveal className="max-w-2xl">
        <p className="eyebrow">Built for correctness</p>
        <h2 className="mt-3 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
          The guarantees a payments system needs, made visible.
        </h2>
      </Reveal>
      <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((f, i) => (
          <Reveal key={f.title} delay={(i % 3) * 0.05}>
            <article className="group h-full surface p-6 transition-shadow duration-200 hover:shadow-[var(--elev-md)]">
              <span className="grid size-10 place-items-center rounded-xl border border-border bg-background text-brand-text shadow-xs transition-transform duration-200 group-hover:-translate-y-0.5">
                <f.icon className="size-5" aria-hidden />
              </span>
              <h3 className="mt-5 font-medium tracking-tight">{f.title}</h3>
              <p className="mt-2 text-sm text-muted-foreground">{f.text}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </section>
  );
}

function HowMoneyMoves() {
  return (
    <section id="how" className="scroll-mt-20 border-y border-border bg-card/40">
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-20 sm:px-6 lg:grid-cols-2 lg:py-28 [&>*]:min-w-0">
        <Reveal>
          <p className="eyebrow">How money moves</p>
          <h2 className="mt-3 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
            One transfer, two legs, zero drift.
          </h2>
          <p className="mt-5 text-muted-foreground">
            Sending {"₹"}1,250 isn&rsquo;t &ldquo;subtract here, add there&rdquo;. It&rsquo;s one transaction with a
            debit from your wallet and a credit to theirs, posted together or not at all. Balances are derived from
            these entries, never stored by hand, so they can&rsquo;t disagree with history.
          </p>
          <ul className="mt-6 grid gap-3 text-sm">
            {[
              "Debits equal credits in every transaction, per currency",
              "Entries are append-only: the database refuses UPDATE and DELETE",
              "Every wallet balance = credits − debits, recomputable at any time",
            ].map((item) => (
              <li key={item} className="flex gap-3">
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-brand" aria-hidden />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </Reveal>
        <Reveal delay={0.06}>
          <TAccount
            legs={[
              { accountName: "Your wallet · Main", direction: "DEBIT", amount: 1_250_00, currency: "INR" },
              { accountName: "Their wallet · Main", direction: "CREDIT", amount: 1_250_00, currency: "INR" },
            ]}
          />
        </Reveal>
      </div>
    </section>
  );
}

const CURL = `curl -X POST https://<api>/api/v1/transfers \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Idempotency-Key: 7f3c9a2e-5d1b-4c8e-9a6f-1b2c3d4e5f60" \\
  -H "Content-Type: application/json" \\
  -d '{"fromWalletId":"…","toWalletId":"…","amount":125000,"currency":"INR"}'`;

function ApiExample() {
  return (
    <section
      id="api"
      className="mx-auto grid max-w-6xl scroll-mt-20 items-center gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[1fr_1.2fr] lg:py-28 [&>*]:min-w-0"
    >
      <Reveal>
        <p className="eyebrow">Retry-safe by design</p>
        <h2 className="mt-3 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">
          Send it twice. It happens once.
        </h2>
        <p className="mt-5 text-muted-foreground">
          Every money-moving request carries an <span className="num text-foreground">Idempotency-Key</span>. The key is
          reserved before any state changes, and the response is stored in the same database transaction as the money
          movement. Repeat the request, after a timeout or a double-click, and you get the original answer back, marked{" "}
          <span className="num text-foreground">Idempotent-Replayed: true</span>.
        </p>
        <p className="mt-4 text-sm text-muted-foreground">
          Amounts are integers in minor units (paise): {"₹"}1,250.00 is <span className="num">125000</span>. No floats
          anywhere near money.
        </p>
      </Reveal>
      <Reveal delay={0.06}>
        <div className="overflow-hidden rounded-xl border border-border bg-[#0b0b10] text-[#e4e4ea] shadow-[var(--elev-lg)]">
          <div className="flex items-center justify-between border-b border-white/10 px-4 py-2">
            <span className="text-xs text-white/60">Create a transfer</span>
            <CopyButton value={CURL} label="Copy curl command" className="size-7 text-white/60 hover:text-white" />
          </div>
          {/* Focusable so keyboard users can scroll it sideways. */}
          <pre
            tabIndex={0}
            aria-label="curl example"
            className="overflow-x-auto p-4 num text-[12.5px] leading-relaxed focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none focus-visible:ring-inset"
          >
            <code>{CURL}</code>
          </pre>
          <div className="border-t border-white/10 px-4 py-3 num text-[12.5px] leading-relaxed">
            <span className="text-white/50"># same request again →</span>
            {"\n"}
            <span className="text-emerald-400">HTTP/1.1 201</span>
            {"  "}
            <span className="text-white/80">Idempotent-Replayed: true</span>
          </div>
        </div>
      </Reveal>
    </section>
  );
}

function Stack() {
  const groups: [string, string[]][] = [
    ["Backend", ["Java 21", "Spring Boot 3.5", "PostgreSQL 16", "Flyway", "Spring Security", "Testcontainers"]],
    ["Frontend", ["Next.js 16", "React 19", "TypeScript", "Tailwind CSS 4", "TanStack Query", "Playwright"]],
  ];
  return (
    <section aria-label="Tech stack" className="border-t border-border">
      <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
        <Reveal className="grid gap-8 md:grid-cols-2">
          {groups.map(([title, items]) => (
            <div key={title}>
              <p className="eyebrow">{title}</p>
              <ul className="mt-4 flex flex-wrap gap-2">
                {items.map((item) => (
                  <li
                    key={item}
                    className="rounded-full border border-border bg-card px-3 py-1 text-sm text-muted-foreground shadow-xs"
                  >
                    {item}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </Reveal>
      </div>
    </section>
  );
}

function FinalCta() {
  return (
    <section className="px-4 pb-20 sm:px-6">
      <Reveal className="relative mx-auto max-w-6xl overflow-hidden rounded-2xl border border-border bg-card px-6 py-14 text-center shadow-[var(--elev-md)] sm:px-12">
        <div
          className="absolute inset-0 -z-0 bg-dots [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)] opacity-60"
          aria-hidden
        />
        <div className="relative">
          <h2 className="text-3xl font-semibold tracking-tight text-balance">See the ledger for yourself.</h2>
          <p className="mx-auto mt-3 max-w-lg text-muted-foreground">
            Sign in with the demo account, add money, send it, and watch both legs land. Then open the code.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button size="lg" asChild>
              <Link href="/login">
                Try the live demo <ArrowRightIcon />
              </Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <a href={REPO}>
                <FileClockIcon /> Read the design decisions
              </a>
            </Button>
          </div>
        </div>
      </Reveal>
    </section>
  );
}

function Footer() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-8 text-sm text-muted-foreground sm:px-6">
        <Logo />
        <p>A portfolio project · demo data only, no real money.</p>
        <a href={REPO} className="transition-colors hover:text-foreground">
          github.com/praneethrdy/wallet-ledger
        </a>
      </div>
    </footer>
  );
}
