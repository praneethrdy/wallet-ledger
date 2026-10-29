"use client";

import { ArrowUpIcon, ShieldCheckIcon, SparklesIcon } from "lucide-react";
import { useEffect, useRef, useState, useTransition } from "react";

import { cn } from "cn";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

import { askAssistant } from "./actions";

type Message = { role: "user" | "assistant"; content: string; error?: boolean };

const SUGGESTIONS = [
  "How much did I spend this month?",
  "What are my wallet balances?",
  "Show my last 5 transactions",
  "How much did I deposit in the last 30 days?",
];

export function AssistantChat({ provider, dailyLimit }: { provider: string; dailyLimit: number }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [pending, startTransition] = useTransition();
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => {
    end.current?.scrollIntoView({ block: "end", behavior: "smooth" });
  }, [messages, pending]);

  function ask(question: string) {
    const q = question.trim();
    if (!q || pending) return;
    // The last few successful turns give follow-ups context ("and last month?").
    const history = messages
      .filter((m) => !m.error)
      .slice(-8)
      .map(({ role, content }) => ({ role, content }));
    setMessages((m) => [...m, { role: "user", content: q }]);
    setDraft("");
    startTransition(async () => {
      const res = await askAssistant({ question: q, history });
      setMessages((m) => [
        ...m,
        res.ok ? { role: "assistant", content: res.answer } : { role: "assistant", content: res.error, error: true },
      ]);
    });
  }

  return (
    <div className="grid max-w-3xl gap-4">
      <div className="min-h-80 surface p-4 sm:p-6" aria-live="polite">
        {messages.length === 0 ? (
          <div className="flex flex-col items-center py-8 text-center">
            <div className="mb-4 grid size-11 place-items-center rounded-xl bg-[color-mix(in_oklab,var(--brand)_12%,transparent)] text-brand-text">
              <SparklesIcon className="size-5" aria-hidden />
            </div>
            <p className="text-sm font-medium">Ask anything about your wallets</p>
            <p className="mt-1 max-w-sm text-sm text-muted-foreground">
              Totals are calculated exactly from your ledger, not guessed by the AI.
            </p>
            <div className="mt-5 flex flex-wrap justify-center gap-2">
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => ask(s)}
                  className="rounded-full border border-border bg-card px-3 py-1.5 text-xs shadow-xs transition-colors hover:border-line-strong hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <ol className="grid gap-4">
            {messages.map((m, i) => (
              <li key={i} className={cn("flex", m.role === "user" ? "justify-end" : "justify-start")}>
                <div
                  className={cn(
                    "max-w-[85%] rounded-2xl px-4 py-2.5 text-sm whitespace-pre-wrap",
                    m.role === "user"
                      ? "bg-primary text-primary-foreground"
                      : m.error
                        ? "border border-[color-mix(in_oklab,var(--danger)_30%,transparent)] text-danger-text"
                        : "border border-border bg-card",
                  )}
                >
                  {/* Plain text on purpose: the answer is never rendered as HTML or markdown links. */}
                  {m.content}
                </div>
              </li>
            ))}
            {pending && (
              <li className="flex justify-start" aria-label="Thinking">
                <div className="flex gap-1 rounded-2xl border border-border bg-card px-4 py-3">
                  {[0, 1, 2].map((d) => (
                    <span
                      key={d}
                      className="size-1.5 animate-pulse rounded-full bg-muted-foreground motion-reduce:animate-none"
                      style={{ animationDelay: `${d * 150}ms` }}
                    />
                  ))}
                </div>
              </li>
            )}
          </ol>
        )}
        <div ref={end} />
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          ask(draft);
        }}
        className="flex items-end gap-2 surface p-2"
      >
        <label htmlFor="question" className="sr-only">
          Your question
        </label>
        <Textarea
          id="question"
          rows={1}
          maxLength={500}
          value={draft}
          placeholder="e.g. How much did I send to others last week?"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              ask(draft);
            }
          }}
          className="max-h-32 min-h-10 resize-none border-0 shadow-none focus-visible:ring-0"
        />
        <Button type="submit" size="icon" aria-label="Ask" loading={pending} disabled={!draft.trim()}>
          <ArrowUpIcon />
        </Button>
      </form>

      <p className="flex items-start gap-2 px-1 text-xs text-muted-foreground">
        <ShieldCheckIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        <span>
          Read-only: it can look at your wallets and transactions, never move money. Your question and the matching
          transaction data are sent to {provider} to write the answer. AI can make mistakes; amounts come from your
          ledger. {dailyLimit} questions a day.
        </span>
      </p>
    </div>
  );
}
