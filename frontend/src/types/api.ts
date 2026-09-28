export interface PingResponse {
  status: string;
}

/** Formato de error que devuelve el backend (GlobalExceptionHandler). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  violations?: { field: string; message: string }[];
}
