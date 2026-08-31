/**
 * Icon Component - Renders a lucide icon, optionally inside a colored circle
 */

import { Target, TrendingUp, TrendingDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { EngineComponentProps } from '@/engine/types';

const iconMap = {
  target: Target,
  'trending-up': TrendingUp,
  'trending-down': TrendingDown,
};

interface EngineIconProps extends EngineComponentProps {
  name: keyof typeof iconMap;
  /** Classes for the circular background (size, color) */
  className?: string;
  /** Classes for the icon itself (size, color) */
  iconClassName?: string;
}

export function EngineIcon({ name, className, iconClassName }: EngineIconProps) {
  const LucideIcon = iconMap[name];
  if (!LucideIcon) return null;

  return (
    <div className={cn('flex shrink-0 items-center justify-center rounded-full', className)}>
      <LucideIcon className={cn('text-white', iconClassName)} />
    </div>
  );
}
