import { useId, useState, type ChangeEvent, type DragEvent } from 'react';
import type { PriorityUploadRules } from '../../types/pending';

const typeNames = new Intl.ListFormat('es', { type: 'disjunction' });
const megabytes = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

/** `"PDF, JPG o PNG, hasta 5 MB"` a partir de lo que informa el servidor. */
function describeRules({ acceptedTypes, maxSizeBytes }: PriorityUploadRules) {
  const types = acceptedTypes.map((type) =>
    (type.split('/')[1] ?? type).toUpperCase().replace('JPEG', 'JPG'),
  );
  return `${typeNames.format(types)}, hasta ${megabytes.format(maxSizeBytes / 1024 / 1024)} MB`;
}

type UploadDropzoneProps = {
  rules: PriorityUploadRules;
  onSelect: (file: File) => void;
};

/**
 * Elegir el certificado: un `<input type="file">` real (teclado y lector de pantalla) y, además,
 * se puede soltar un archivo arriba. Valida tipo y tamaño antes de subir; la validación real
 * (magic bytes) la hace el servidor.
 */
export function UploadDropzone({ rules, onSelect }: UploadDropzoneProps) {
  const id = useId();
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const summary = describeRules(rules);

  function pick(file: File | undefined) {
    if (!file) return;
    if (!rules.acceptedTypes.includes(file.type)) {
      setError(`Ese tipo de archivo no sirve. Subí un ${summary}.`);
    } else if (file.size > rules.maxSizeBytes) {
      setError(`El archivo es muy grande. Subí un ${summary}.`);
    } else {
      setError(null);
      onSelect(file);
    }
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    pick(event.target.files?.[0]);
    // Permite volver a elegir el mismo archivo después de quitarlo.
    event.target.value = '';
  }

  function handleDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    pick(event.dataTransfer.files[0]);
  }

  return (
    <div>
      {/* Antes del label: el CSS muestra el foco del input en la zona (`input:focus-visible + label`). */}
      <input
        id={id}
        type="file"
        className="visually-hidden"
        accept={rules.acceptedTypes.join(',')}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={handleChange}
      />
      <label
        htmlFor={id}
        className={['upload-dropzone', dragging && 'upload-dropzone--dragging']
          .filter(Boolean)
          .join(' ')}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
      >
        <span className="upload-dropzone__title">Elegí tu certificado</span>
        <span className="upload-dropzone__hint">{summary}</span>
      </label>
      {error && (
        <p id={`${id}-error`} role="alert" className="priority-error">
          {error}
        </p>
      )}
    </div>
  );
}
