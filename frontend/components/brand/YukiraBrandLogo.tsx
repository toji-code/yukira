'use client';

import React from 'react';
import Image from 'next/image';
import { useTheme } from '@/components/theme/ThemeProvider';

interface YukiraBrandLogoProps {
  /**
   * 'emblem': Square emblem for headers, avatars, and compact spaces
   * 'card': Full widescreen brand card with Tree of Life + Lion + YUKIRA wordmark
   */
  variant?: 'emblem' | 'card';
  className?: string;
  priority?: boolean;
}

export function YukiraBrandLogo({
  variant = 'emblem',
  className = '',
  priority = false,
}: YukiraBrandLogoProps) {
  const { resolvedTheme, mounted } = useTheme();
  const isLight = mounted && resolvedTheme === 'light';
  const logoSrc = isLight ? '/yukira-logo-light.png' : '/yukira-logo.png';

  if (variant === 'card') {
    return (
      <div className={`relative flex items-center justify-center ${className}`}>
        <Image
          src={logoSrc}
          alt="YUKIRA — Investment Intelligence"
          width={2160}
          height={2160}
          unoptimized
          className={`h-full w-full object-contain filter ${
            isLight
              ? 'drop-shadow-[0_4px_16px_rgba(184,142,40,0.16)]'
              : 'drop-shadow-[0_4px_16px_rgba(212,175,55,0.18)]'
          }`}
          priority={priority}
        />
      </div>
    );
  }

  // Emblem / header / hero logo display
  return (
    <div
      className={`relative flex items-center justify-center shrink-0 transition-transform duration-300 group-hover:scale-105 ${
        className || 'h-[48px] w-[48px]'
      }`}
    >
      <Image
        src={logoSrc}
        alt="YUKIRA"
        width={2160}
        height={2160}
        unoptimized
        className={`h-full w-full object-contain filter ${
          isLight
            ? 'drop-shadow-[0_2px_10px_rgba(184,142,40,0.18)]'
            : 'drop-shadow-[0_2px_10px_rgba(212,175,55,0.22)]'
        }`}
        priority={priority}
      />
    </div>
  );
}
