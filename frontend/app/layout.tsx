import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { AppHeader } from "@/components/layout/AppHeader";
import { AppFooter } from "@/components/layout/AppFooter";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "YUKIRA — Institutional-Oriented Investment Intelligence & Verification Platform",
  description: "Deterministic quantitative analysis, point-in-time empirical verification, and cryptographic provenance for Indian mutual funds. Before you commit capital, ask one more question.",
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
      <body className="min-h-full flex flex-col bg-background text-text-primary selection:bg-primary/20 selection:text-primary-foreground">
        <ThemeProvider>
          <AppHeader />
          <main className="flex-1 flex flex-col">{children}</main>
          <AppFooter />
        </ThemeProvider>
      </body>
    </html>
  );
}
