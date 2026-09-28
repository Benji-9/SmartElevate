interface ImportMetaEnv {
  /** URL base de la API. Por defecto `/api` (proxy de Vite en dev, rewrite de Vercel en prod). */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
