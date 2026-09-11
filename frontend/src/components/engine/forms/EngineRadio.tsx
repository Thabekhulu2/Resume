/**
 * Radio Component - Single choice within a named group
 */

import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import type { EngineComponentProps, ActionDefinition } from '@/engine/types';
import { useUIEngine, useItemContext } from '@/engine/UIEngineContext';

interface EngineRadioProps extends EngineComponentProps {
  checked?: boolean;
  onChange?: ActionDefinition;
  label?: string;
  name: string;
  value: string;
  disabled?: boolean;
  className?: string;
}

export function EngineRadio({
  checked = false,
  onChange,
  label,
  name,
  value,
  disabled = false,
  className,
}: EngineRadioProps) {
  const { dispatch } = useUIEngine();
  const itemContext = useItemContext();

  const handleChange = () => {
    if (onChange) {
      dispatch(onChange, {
        ...itemContext,
        event: { target: { checked: true, value } },
      });
    }
  };

  const radioId = `radio-${name}-${value}`.replace(/\s+/g, '-');

  return (
    <div className={cn('flex items-center space-x-2', className)}>
      <input
        type="radio"
        id={radioId}
        name={name}
        value={value}
        checked={checked}
        onChange={handleChange}
        disabled={disabled}
        className="h-4 w-4 border-input text-primary focus:outline-none focus:ring-2 focus:ring-ring"
      />
      {label && (
        <Label
          htmlFor={radioId}
          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
        >
          {label}
        </Label>
      )}
    </div>
  );
}
