"use client";

/** Last resort when the root layout itself fails: no app styles are guaranteed, so keep it plain. */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="en">
      <body
        style={{
          fontFamily: "system-ui, sans-serif",
          display: "grid",
          placeItems: "center",
          minHeight: "100dvh",
          margin: 0,
          padding: 16,
          background: "#fafafa",
          color: "#18181b",
        }}
      >
        <main style={{ maxWidth: 420, textAlign: "center" }}>
          <h1 style={{ fontSize: 20, fontWeight: 600 }}>Wallet Ledger is having trouble</h1>
          <p style={{ color: "#52525b", fontSize: 14 }}>Please try again in a moment.</p>
          {error.digest && <p style={{ color: "#71717a", fontSize: 12 }}>Reference {error.digest}</p>}
          <button
            onClick={reset}
            style={{
              marginTop: 12,
              padding: "8px 16px",
              borderRadius: 10,
              border: 0,
              background: "#5b5bd6",
              color: "white",
              fontSize: 14,
              cursor: "pointer",
            }}
          >
            Try again
          </button>
        </main>
      </body>
    </html>
  );
}
