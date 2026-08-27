/**
 * FileInput Component - Native file picker.
 * Single mode emits the selected File plus a generated storage path via
 * onChange's event context ({ file, fileName, path }).
 * Multiple mode (multiple=true) emits arrays instead ({ files: [{file, fileName, path}],
 * paths: [path...], fileNames: [fileName...] }) — computed here rather than via a
 * {{}} expression since the JSON engine's expression evaluator has no array-mapping support.
 */

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import type { EngineComponentProps, ActionDefinition } from '@/engine/types';
import { useUIEngine, useItemContext } from '@/engine/UIEngineContext';

interface EngineFileInputProps extends EngineComponentProps {
  accept?: string;
  multiple?: boolean;
  onChange?: ActionDefinition;
  label?: string;
  name?: string;
  disabled?: boolean;
  required?: boolean;
  error?: string;
  className?: string;
}

function toStoragePath(file: File): string {
  const extension = file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.')) : '';
  return `${crypto.randomUUID()}${extension}`;
}

export function EngineFileInput({
  accept,
  multiple = false,
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
    if (!onChange) return;

    if (multiple) {
      const fileList = Array.from(e.target.files ?? []);
      if (fileList.length === 0) return;

      const files = fileList.map((file) => ({ file, fileName: file.name, path: toStoragePath(file) }));
      dispatch(onChange, {
        ...itemContext,
        event: {
          files,
          paths: files.map((f) => f.path),
          fileNames: files.map((f) => f.fileName),
        },
      });
      return;
    }

    const file = e.target.files?.[0];
    if (!file) return;

    dispatch(onChange, { ...itemContext, event: { file, fileName: file.name, path: toStoragePath(file) } });
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
        multiple={multiple}
        onChange={handleChange}
        disabled={disabled}
        required={required}
        className={cn(error && 'border-destructive')}
      />
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
