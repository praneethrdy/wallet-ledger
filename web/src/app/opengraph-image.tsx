import { ImageResponse } from "next/og";

/** The card shown when the link is shared (LinkedIn, WhatsApp, X…). Rendered once, at build time. */
export const alt = "Wallet Ledger: money that always adds up";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const INK = "#09090b";
const MUTED = "#71717a";
const LINE = "#e4e4e7";
const BRAND = "#5b5bd6";
const CREDIT = "#047857";

export default function OpenGraphImage() {
  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        background: "#fafafa",
        backgroundImage:
          "radial-gradient(circle at 78% 30%, rgba(91,91,214,0.18), transparent 45%), radial-gradient(#e4e4e7 1px, transparent 1px)",
        backgroundSize: "100% 100%, 24px 24px",
        padding: "72px 80px",
        fontFamily: "sans-serif",
        color: INK,
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", justifyContent: "space-between", width: 600 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <Mark />
          <div style={{ fontSize: 30, fontWeight: 600, letterSpacing: -0.5 }}>Wallet Ledger</div>
        </div>
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 68, fontWeight: 700, letterSpacing: -2, lineHeight: 1.04 }}>
            Money that always adds up.
          </div>
          <div style={{ marginTop: 24, fontSize: 26, color: MUTED, lineHeight: 1.35 }}>
            Double-entry ledger · idempotent transfers · signed webhooks · audited staff console
          </div>
        </div>
        <div style={{ display: "flex", fontSize: 22, color: MUTED }}>Java 21 · Spring Boot · PostgreSQL · Next.js</div>
      </div>

      <div style={{ display: "flex", alignItems: "center", marginLeft: "auto" }}>
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            width: 380,
            background: "white",
            border: `1px solid ${LINE}`,
            borderRadius: 20,
            boxShadow: "0 24px 48px -12px rgba(9,9,11,0.18)",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              padding: "16px 24px",
              borderBottom: `1px solid ${LINE}`,
              fontSize: 15,
              letterSpacing: 2,
              color: MUTED,
            }}
          >
            <span>DEBITS</span>
            <span>CREDITS</span>
          </div>
          <div style={{ display: "flex" }}>
            <Leg name="Your wallet" amount="₹1,250.00" />
            <Leg name="Their wallet" amount="₹1,250.00" credit />
          </div>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              padding: "16px 24px",
              borderTop: `1px solid ${LINE}`,
              fontSize: 16,
              color: MUTED,
            }}
          >
            <span>1 transaction · 2 legs</span>
            <span
              style={{
                display: "flex",
                padding: "6px 14px",
                borderRadius: 999,
                background: "#ecfdf5",
                color: CREDIT,
                fontWeight: 600,
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" style={{ marginRight: 8 }}>
                <path
                  d="M5 12.5l4.5 4.5L19 7.5"
                  fill="none"
                  stroke={CREDIT}
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              Balanced
            </span>
          </div>
        </div>
      </div>
    </div>,
    size,
  );
}

function Leg({ name, amount, credit = false }: { name: string; amount: string; credit?: boolean }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: credit ? "flex-end" : "flex-start",
        width: "50%",
        padding: "22px 24px",
        borderLeft: credit ? `1px solid ${LINE}` : "none",
      }}
    >
      <span style={{ fontSize: 18 }}>{name}</span>
      <span style={{ marginTop: 6, fontSize: 26, fontWeight: 600, color: credit ? CREDIT : INK }}>{amount}</span>
    </div>
  );
}

function Mark() {
  return (
    <svg width="52" height="52" viewBox="0 0 32 32">
      <rect width="32" height="32" rx="9" fill={BRAND} />
      <rect x="8" y="9" width="10" height="4" rx="2" fill="#fff" opacity="0.95" />
      <rect x="14" y="14" width="10" height="4" rx="2" fill="#fff" opacity="0.7" />
      <rect x="8" y="19" width="16" height="4" rx="2" fill="#fff" opacity="0.95" />
    </svg>
  );
}
