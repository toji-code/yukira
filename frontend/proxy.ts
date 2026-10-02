import { auth0 } from "./lib/auth0";
import { NextRequest } from "next/server";

/**
 * Next.js 16 renamed the `middleware` file convention to `proxy`
 * (`middleware` is deprecated). This file must export a function named
 * `proxy` (or a default export) or the build fails with
 * "Proxy is missing expected function export name".
 *
 * `auth0.middleware` is the Auth0 SDK's own API and keeps its name — only
 * this wrapper's export name follows the Next.js convention.
 */
export async function proxy(request: NextRequest) {
  return await auth0.middleware(request);
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt).*)"
  ]
};
