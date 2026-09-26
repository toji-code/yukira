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
            <ol className="flex items-center space-x-2 text-xs text-text-muted font-mono">
              {breadcrumbs.map((crumb, idx) => (
                <li key={idx} className="flex items-center space-x-2">
                  {idx > 0 && <span>/</span>}
                  {crumb.href ? (
                    <a
                      href={crumb.href}
                      className="hover:text-text-primary transition-colors focus:outline-none focus-visible:underline"
                    >
                      {crumb.label}
                    </a>
                  ) : (
                    <span className="text-text-secondary font-medium">{crumb.label}</span>
                  )}
                </li>
              ))}
            </ol>
          </nav>
        )}

        {(title || action) && (
          <div className="mb-8 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
            <div>
              {title && (
                <h1 className="text-2xl font-bold tracking-tight text-text-primary">
                  {title}
                </h1>
              )}
              {subtitle && (
                <p className="mt-1 text-sm text-text-secondary">
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
