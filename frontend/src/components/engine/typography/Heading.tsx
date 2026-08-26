/**
 * Heading Component - h1-h6 headings
 */

import { cn } from '@/lib/utils';
import type { EngineComponentProps } from '@/engine/types';

interface HeadingProps extends EngineComponentProps {
  level?: 1 | 2 | 3 | 4 | 5 | 6;
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' | '4xl';
  className?: string;
}

const defaultSizeByLevel: Record<number, string> = {
  1: 'text-4xl font-bold tracking-tight',
  2: 'text-3xl font-bold tracking-tight',
  3: 'text-2xl font-bold',
  4: 'text-xl font-bold',
  5: 'text-lg font-medium',
  6: 'text-base font-medium',
};

const sizeMap: Record<string, string> = {
  sm: 'text-sm font-medium',
  md: 'text-base font-medium',
  lg: 'text-lg font-bold',
  xl: 'text-xl font-bold',
  '2xl': 'text-2xl font-bold',
  '3xl': 'text-3xl font-bold tracking-tight',
  '4xl': 'text-4xl font-bold tracking-tight',
};

export function Heading({
  level = 1,
  size,
  className,
  children,
}: HeadingProps) {
  const Component = `h${level}` as 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
  const sizeClass = size ? sizeMap[size] : defaultSizeByLevel[level];

  return (
    <Component className={cn(sizeClass, className)}>{children}</Component>
  );
}
