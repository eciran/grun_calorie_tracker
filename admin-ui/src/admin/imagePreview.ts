const HEIC_TYPES = new Set(["image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence"]);

export function isHeicContentType(contentType?: string | null) {
  return HEIC_TYPES.has((contentType ?? "").split(";", 1)[0].trim().toLowerCase());
}

export async function browserImageObjectUrl(blob: Blob, declaredContentType?: string | null): Promise<string> {
  if (!isHeicContentType(declaredContentType) && !isHeicContentType(blob.type) && !(await hasHeicSignature(blob))) {
    return URL.createObjectURL(blob);
  }
  const jpeg = await convertHeic(blob);
  return URL.createObjectURL(jpeg);
}

let conversionQueue: Promise<unknown> = Promise.resolve();

function convertHeic(blob: Blob): Promise<Blob> {
  if (blob.size > 25 * 1024 * 1024) return Promise.reject(new Error("HEIC file exceeds preview limits."));
  // Avoid simultaneous decoder heaps when several evidence images are opened.
  const conversion = conversionQueue.then(() => new Promise<Blob>((resolve, reject) => {
    const worker = new Worker(new URL("./heicPreview.worker.ts", import.meta.url), { type: "module" });
    const finish = () => { clearTimeout(timeout); worker.terminate(); };
    const timeout = setTimeout(() => { finish(); reject(new Error("HEIC preview timed out.")); }, 60_000);
    worker.onmessage = ({ data }: MessageEvent<{ blob?: Blob; error?: string }>) => {
      finish();
      if (data.blob instanceof Blob) resolve(data.blob);
      else reject(new Error(data.error || "HEIC preview failed."));
    };
    const fail = () => { finish(); reject(new Error("HEIC preview failed.")); };
    worker.onerror = fail;
    worker.onmessageerror = fail;
    worker.postMessage(blob);
  }));
  conversionQueue = conversion.catch(() => undefined);
  return conversion;
}

async function hasHeicSignature(blob: Blob) {
  if (blob.size < 12) return false;
  const bytes = new Uint8Array(await blob.slice(0, 32).arrayBuffer());
  if (String.fromCharCode(...bytes.slice(4, 8)) !== "ftyp") return false;
  const brand = String.fromCharCode(...bytes.slice(8, 12)).toLowerCase();
  return ["heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"].includes(brand);
}
