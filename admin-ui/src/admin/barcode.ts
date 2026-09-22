export const SUPPORTED_GTIN_LENGTHS = [8, 12, 13, 14] as const;

export function normalizeBarcode(value: string): string {
  return value.replace(/[^0-9]/g, "");
}

export function validateGtin(value: string): { valid: boolean; barcode: string; reason?: "length" | "checksum" } {
  const barcode = normalizeBarcode(value);
  if (!SUPPORTED_GTIN_LENGTHS.includes(barcode.length as (typeof SUPPORTED_GTIN_LENGTHS)[number])) {
    return { valid: false, barcode, reason: "length" };
  }
  const digits = [...barcode].map(Number);
  const expected = digits.pop()!;
  const sum = digits.reverse().reduce((total, digit, index) => total + digit * (index % 2 === 0 ? 3 : 1), 0);
  return { valid: (10 - (sum % 10)) % 10 === expected, barcode, reason: (10 - (sum % 10)) % 10 === expected ? undefined : "checksum" };
}
