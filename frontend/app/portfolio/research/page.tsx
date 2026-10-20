'use client';

import React from 'react';
import { PageContainer } from '@/components/layout/PageContainer';
import { ResearchPortfolioView } from '@/components/portfolio/ResearchPortfolioView';

export default function ResearchPortfolioPage() {
  return (
    <PageContainer
      title="My Research Portfolio"
      subtitle="Assemble multi-fund portfolios, inspect consolidated underlying security and sector exposures, identify overlap and concentration, and evaluate candidate fund additions."
    >
      <ResearchPortfolioView />
    </PageContainer>
  );
}
