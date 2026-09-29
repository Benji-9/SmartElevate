/** Lee un QR del cuadro actual del video: el texto si hay uno, `null` si no. */
export type ReadQr = (video: HTMLVideoElement) => Promise<string | null>;

// `BarcodeDetector` todavía no está en los tipos del DOM de TypeScript.
type BarcodeDetectorClass = {
  new (options: { formats: string[] }): {
    detect: (source: HTMLVideoElement) => Promise<{ rawValue: string }[]>;
  };
  getSupportedFormats: () => Promise<string[]>;
};

/**
 * Usa `BarcodeDetector` nativo si lee QR (Chrome Android, Safari macOS…). Si no (iOS Safari,
 * Firefox), carga jsQR con `import()` para no sumarlo al bundle principal.
 */
export async function createQrReader(): Promise<ReadQr> {
  const Native = (globalThis as { BarcodeDetector?: BarcodeDetectorClass }).BarcodeDetector;
  if (Native && (await Native.getSupportedFormats()).includes('qr_code')) {
    const detector = new Native({ formats: ['qr_code'] });
    return async (video) =>
      video.readyState < 2 ? null : ((await detector.detect(video))[0]?.rawValue ?? null);
  }

  const { default: jsQR } = await import('jsqr');
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { willReadFrequently: true });
  return async (video) => {
    if (!context || video.readyState < 2) return null;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0);
    const image = context.getImageData(0, 0, canvas.width, canvas.height);
    return jsQR(image.data, image.width, image.height)?.data ?? null;
  };
}
