import React from 'react';
import Link from 'next/link';

type ContainerVariant = 'app' | 'wide' | 'prose';

export interface SectionRef {
  /** Zero-padded ordinal rendered as a mono eyebrow, e.g. "01". */
  ordinal: string;
  title: string;
  description?: string;
}

interface PageContainerProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  /** Mono uppercase eyebrow above the title. */
  eyebrow?: string;
  action?: React.ReactNode;
  breadcrumbs?: Array<{ label: string; href?: string }>;
  /** `wide` for analytical routes, `prose` for governance narrative. */
  width?: ContainerVariant;
}

const CONTAINER_CLASS: Record<ContainerVariant, string> = {
  app: 'container-app',
  wide: 'container-wide',
  prose: 'container-prose',
};

export function PageContainer({
  children,
  title,
  subtitle,
  eyebrow,
  action,
  breadcrumbs,
  width = 'app',
}: PageContainerProps) {
  return (
    <div className={`${CONTAINER_CLASS[width]} py-6 md:py-8`}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav aria-label="Breadcrumb" className="mb-4">
          <ol className="flex flex-wrap items-center gap-2">
            {breadcrumbs.map((crumb, idx) => (
              <li key={`${crumb.label}-${idx}`} className="flex items-center gap-2">
                {idx > 0 && <span className="text-text-disabled" aria-hidden>/</span>}
                {crumb.href ? (
                  <Link href={crumb.href} className="text-link">
                    {crumb.label}
                  </Link>
                ) : (
                  <span className="mono-meta">{crumb.label}</span>
                )}
              </li>
            ))}
          </ol>
        </nav>
      )}

      {(title || action || eyebrow) && (
        <div className="flex flex-col gap-4 border-b border-border pb-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            {eyebrow && <p className="eyebrow">{eyebrow}</p>}
            {title && (
              <h1 className="mt-1.5 text-2xl font-bold tracking-tight text-text-primary sm:text-3xl lg:text-4xl">
                {title}
              </h1>
            )}
            {subtitle && (
              <p className="mt-2 max-w-[68ch] text-[13px] leading-[1.55] text-text-secondary">
                {subtitle}
              </p>
            )}
          </div>
          {action && <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div>}
        </div>
      )}

      {children}
    </div>
  );
}

/**
 * Numbered section heading with a persistent mono ordinal and an optional
 * one-line description that stays visible above the panel, so disclosure depth
 * is self-explanatory.
 */
export function SectionHeading({
  ordinal,
  title,
  description,
  action,
}: SectionRef & { action?: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-baseline sm:justify-between">
      <div className="flex items-baseline gap-3">
        {ordinal && <span className="tab-ordinal">{ordinal}</span>}
        <h2 className="text-h2 text-text-primary">
          {title}
        </h2>
      </div>
      <div className="flex flex-1 items-baseline justify-between gap-4 sm:pl-4">
        {description && (
          <p className="min-w-0 flex-1 text-[13px] leading-[1.4] text-text-tertiary">
            {description}
          </p>
        )}
        {action}
      </div>
    </div>
  );
}

/** Metric tile row. Tile count responds to width, not to a breakpoint ladder. */
export function MetricGrid({
  children,
  min = 300,
  className = '',
}: {
  children: React.ReactNode;
  min?: number;
  className?: string;
}) {
  return (
    <div
      className={`grid gap-4 ${className}`.trim()}
      style={{ gridTemplateColumns: `repeat(auto-fit, minmax(min(100%, ${min}px), 1fr))` }}
    >
      {children}
    </div>
  );
}