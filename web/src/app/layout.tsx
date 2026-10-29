import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { ThemeProvider } from "@/components/theme/theme-provider";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "@/components/ui/sonner";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

const DESCRIPTION =
  "A digital wallet on a double-entry ledger: idempotent transfers, signed webhooks, reversals instead of edits and an audited staff console.";

export const metadata: Metadata = {
  // Absolute base for the social preview image URLs (LinkedIn, WhatsApp, X need absolute links).
  metadataBase: new URL(process.env.SITE_URL ?? "https://wallet-ledger-tb4t.onrender.com"),
  title: { default: "Wallet Ledger", template: "%s · Wallet Ledger" },
  description: DESCRIPTION,
  openGraph: { type: "website", siteName: "Wallet Ledger", title: "Wallet Ledger", description: DESCRIPTION },
  twitter: { card: "summary_large_image", title: "Wallet Ledger", description: DESCRIPTION },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#fafafa" },
    { media: "(prefers-color-scheme: dark)", color: "#09090b" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // suppressHydrationWarning: next-themes sets the theme class before React hydrates.
    <html lang="en" suppressHydrationWarning className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
      <body className="min-h-dvh">
        <ThemeProvider>
          <TooltipProvider delayDuration={300}>
            {children}
            <Toaster position="bottom-right" />
          </TooltipProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
