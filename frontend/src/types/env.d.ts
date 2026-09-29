interface ImportMetaEnv {
  /** URL base de la API. Por defecto `/api` (proxy de Vite en dev, rewrite de Vercel en prod). */
  readonly VITE_API_URL?: string;
  /** `true`/`false`: datos simulados de services/mocks.ts. Por defecto prendidos solo en `vite dev`. */
  readonly VITE_USE_MOCKS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
