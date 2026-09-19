import React from 'react';

interface PageContainerProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  action?: React.ReactNode;
  breadcrumbs?: Array<{ label: string; href?: string }>;
}

export function PageContainer({
  children,
  title,
  subtitle,
  action,
  breadcrumbs,
}: PageContainerProps) {
  return (
    <main className="flex-1 py-8" role="main">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav aria-label="Breadcrumb" className="mb-4">
            <ol className="flex items-center space-x-2 text-xs text-zinc-500 font-mono">
              {breadcrumbs.map((crumb, idx) => (
                <li key={idx} className="flex items-center space-x-2">
                  {idx > 0 && <span>/</span>}
                  {crumb.href ? (
                    <a
                      href={crumb.href}
                      className="hover:text-zinc-900 dark:hover:text-zinc-100 transition-colors focus:outline-none focus-visible:underline"
                    >
                      {crumb.label}
                    </a>
                  ) : (
                    <span className="text-zinc-800 dark:text-zinc-200 font-medium">{crumb.label}</span>
                  )}
                </li>
              ))}
            </ol>
          </nav>
        )}

        {(title || action) && (
          <div className="mb-8 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-zinc-200 dark:border-zinc-800 pb-6">
            <div>
              {title && (
                <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
                  {title}
                </h1>
              )}
              {subtitle && (
                <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
                  {subtitle}
                </p>
              )}
            </div>
            {action && <div className="flex items-center gap-3">{action}</div>}
          </div>
        )}

        {children}
      </div>
    </main>
  );
}
