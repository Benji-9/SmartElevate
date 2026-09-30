const sizeFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

const formatSize = (bytes: number) =>
  bytes < 1024 * 1024
    ? `${sizeFormat.format(bytes / 1024)} KB`
    : `${sizeFormat.format(bytes / 1024 / 1024)} MB`;

type UploadedFileProps = {
  file: File;
  onRemove: () => void;
  disabled?: boolean;
};

/** Archivo elegido que todavía no se envió. Se puede quitar para elegir otro. */
export function UploadedFile({ file, onRemove, disabled }: UploadedFileProps) {
  return (
    <div className="uploaded-file">
      <svg
        className="uploaded-file__icon"
        viewBox="0 0 24 24"
        width="20"
        height="20"
        aria-hidden="true"
      >
        <path
          d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8l-5-5zm0 0v5h5"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <p>
        <span className="uploaded-file__name">{file.name}</span>
        <span className="uploaded-file__size">{formatSize(file.size)}</span>
      </p>
      <button
        type="button"
        className="uploaded-file__remove"
        onClick={onRemove}
        disabled={disabled}
        aria-label={`Quitar ${file.name}`}
      >
        Quitar
      </button>
    </div>
  );
}
