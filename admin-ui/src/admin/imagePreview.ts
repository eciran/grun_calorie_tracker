const HEIC_TYPES = new Set(["image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence"]);

export function isHeicContentType(contentType?: string | null) {
  return HEIC_TYPES.has((contentType ?? "").split(";", 1)[0].trim().toLowerCase());
}

export async function browserImageObjectUrl(blob: Blob, declaredContentType?: string | null): Promise<string> {
  if (!isHeicContentType(declaredContentType) && !isHeicContentType(blob.type) && !(await hasHeicSignature(blob))) {
    return URL.createObjectURL(blob);
  }
  const { default: heic2any } = await import("heic2any");
  const converted = await heic2any({ blob, toType: "image/jpeg", quality: 0.9 });
  const jpeg = Array.isArray(converted) ? converted[0] : converted;
  return URL.createObjectURL(jpeg);
}

async function hasHeicSignature(blob: Blob) {
  if (blob.size < 12) return false;
  const bytes = new Uint8Array(await blob.slice(0, 32).arrayBuffer());
  if (String.fromCharCode(...bytes.slice(4, 8)) !== "ftyp") return false;
  const brand = String.fromCharCode(...bytes.slice(8, 12)).toLowerCase();
  return ["heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"].includes(brand);
}
