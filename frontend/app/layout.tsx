import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { AppHeader } from "@/components/layout/AppHeader";
import { AppFooter } from "@/components/layout/AppFooter";
import { Auth0Provider } from "@auth0/nextjs-auth0/client";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
  display: "swap",
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "YUKIRA — Institutional Investment Intelligence & Verification",
  description:
    "Deterministic quantitative analysis, point-in-time empirical verification, and cryptographic provenance for Indian mutual funds. Before you commit capital, ask one more question.",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#0A0C0E" },
    { media: "(prefers-color-scheme: light)", color: "#FBFCFD" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex h-full min-h-full flex-col">
        <Auth0Provider>
          <ThemeProvider>
            <a
              href="#main"
              className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-sm focus:bg-surface focus:px-3 focus:py-2 focus:text-[13px] focus:text-text-primary focus:ring-1 focus:ring-accent"
            >
              Skip to content
            </a>
            <AppHeader />
            <main id="main" className="flex flex-1 flex-col">
              {children}
            </main>
            <AppFooter />
          </ThemeProvider>
        </Auth0Provider>
      </body>
    </html>
  );
}