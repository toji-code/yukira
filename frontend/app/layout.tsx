import type { Metadata, Viewport } from "next";
import { Manrope, Inter, Geist_Mono } from "next/font/google";
import "./globals.css";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { AppHeader } from "@/components/layout/AppHeader";
import { AppFooter } from "@/components/layout/AppFooter";
import { Auth0Provider } from "@auth0/nextjs-auth0/client";

const manrope = Manrope({
  variable: "--font-manrope",
  subsets: ["latin"],
  display: "swap",
});

const inter = Inter({
  variable: "--font-inter",
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
    { media: "(prefers-color-scheme: dark)", color: "#131314" },
    { media: "(prefers-color-scheme: light)", color: "#FFFFFF" },
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
      className={`${manrope.variable} ${inter.variable} ${geistMono.variable} h-full antialiased`}
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  var stored = localStorage.getItem('yukira-theme-preference');
                  var theme = (stored === 'light' || stored === 'dark') ? stored :
                    (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'dark');
                  var root = document.documentElement;
                  root.classList.remove('light', 'dark');
                  root.classList.add(theme);
                  root.setAttribute('data-theme', theme);
                  root.style.colorScheme = theme;
                } catch (e) {}
              })();
            `,
          }}
        />
      </head>
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