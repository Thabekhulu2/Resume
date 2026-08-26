/**
 * FileInput Component - Native file picker; emits the selected File plus a
 * generated storage path via onChange's event context ({ file, fileName, path })
 */

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import type { EngineComponentProps, ActionDefinition } from '@/engine/types';
import { useUIEngine, useItemContext } from '@/engine/UIEngineContext';

interface EngineFileInputProps extends EngineComponentProps {
  accept?: string;
  onChange?: ActionDefinition;
  label?: string;
  name?: string;
  disabled?: boolean;
  required?: boolean;
  error?: string;
  className?: string;
}

export function EngineFileInput({
  accept,
  onChange,
  label,
  name,
  disabled = false,
  required = false,
  error,
  className,
}: EngineFileInputProps) {
  const { dispatch } = useUIEngine();
  const itemContext = useItemContext();

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !onChange) return;

    const extension = file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.')) : '';
    const path = `${crypto.randomUUID()}${extension}`;

    dispatch(onChange, { ...itemContext, event: { file, fileName: file.name, path } });
  };

  const inputId = name || `file-input-${Math.random().toString(36).slice(2, 9)}`;

  return (
    <div className={cn('space-y-2', className)}>
      {label && (
        <Label htmlFor={inputId}>
          {label}
          {required && <span className="text-destructive ml-1">*</span>}
        </Label>
      )}
      <Input
        id={inputId}
        type="file"
        name={name}
        accept={accept}
        onChange={handleChange}
        disabled={disabled}
        required={required}
        className={cn(error && 'border-destructive')}
      />
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
