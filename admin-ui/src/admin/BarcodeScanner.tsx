import { FormEvent, useEffect, useRef, useState } from "react";
import type { IScannerControls } from "@zxing/browser";
import { useAdminLocale } from "./locale";
import { normalizeBarcode, validateGtin } from "./barcode";

type DetectorResult = { rawValue?: string };
type Detector = { detect: (source: HTMLVideoElement) => Promise<DetectorResult[]> };
type DetectorConstructor = new (options?: { formats?: string[] }) => Detector;

const copy = {
  en: {
    title: "Scan or enter barcode", description: "Use a camera, a USB/Bluetooth scanner, or enter the GTIN manually.",
    label: "Barcode", placeholder: "GTIN-8, UPC-A, EAN-13 or GTIN-14", find: "Find product", finding: "Searching…",
    camera: "Scan barcode", stop: "Stop scanner", unsupported: "Live barcode scanning requires a secure HTTPS connection. Open this page over HTTPS and try again.",
    denied: "Camera could not be opened. Check browser permission or use manual entry.", length: "Enter an 8, 12, 13 or 14 digit barcode.",
    checksum: "The barcode check digit is invalid.", notFound: "No exact catalog product matched this barcode.", draft: "Create review candidate",
    hint: "Place one barcode inside the guide. It will be detected automatically; no photo is taken. Handheld scanners can also type the code and press Enter.", preview: "Live barcode scanner", lookupFailed: "The catalog search failed. Try again.", scanning: "Looking for a barcode…"
  },
  tr: {
    title: "Barkodu tara veya gir", description: "Kamera, USB/Bluetooth okuyucu veya elle GTIN girişi kullanın.",
    label: "Barkod", placeholder: "GTIN-8, UPC-A, EAN-13 veya GTIN-14", find: "Ürünü bul", finding: "Aranıyor…",
    camera: "Barkodu tara", stop: "Taramayı durdur", unsupported: "Canlı barkod tarama için güvenli bir HTTPS bağlantısı gerekir. Bu sayfayı HTTPS üzerinden açıp tekrar deneyin.",
    denied: "Kamera açılamadı. Tarayıcı iznini kontrol edin veya elle giriş kullanın.", length: "8, 12, 13 veya 14 haneli bir barkod girin.",
    checksum: "Barkod kontrol basamağı geçersiz.", notFound: "Bu barkodla tam eşleşen katalog ürünü bulunamadı.", draft: "İnceleme adayı oluştur",
    hint: "Tek bir barkodu kılavuzun içine yerleştirin. Fotoğraf çekilmeden otomatik algılanır. El okuyucusu da kodu yazıp Enter tuşuna basabilir.", preview: "Canlı barkod tarayıcı", lookupFailed: "Katalog araması başarısız oldu. Tekrar deneyin.", scanning: "Barkod aranıyor…"
  }
};

export function BarcodeScanner({ onLookup }: { onLookup: (barcode: string) => Promise<boolean> }) {
  const { locale } = useAdminLocale();
  const text = copy[locale];
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const scannerControlsRef = useRef<IScannerControls | null>(null);
  const scanningRef = useRef(false);

  function stopCamera() {
    scanningRef.current = false;
    scannerControlsRef.current?.stop();
    scannerControlsRef.current = null;
    streamRef.current?.getTracks().forEach(track => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCameraOpen(false);
  }

  useEffect(() => () => stopCamera(), []);

  async function lookup(raw: string) {
    const result = validateGtin(raw);
    setValue(result.barcode);
    setNotFound(null);
    if (!result.valid) {
      setError(result.reason === "checksum" ? text.checksum : text.length);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const found = await onLookup(result.barcode);
      if (!found) setNotFound(result.barcode);
    } catch {
      setError(text.lookupFailed);
    } finally {
      setBusy(false);
    }
  }

  async function startCamera() {
    const DetectorClass = (window as unknown as { BarcodeDetector?: DetectorConstructor }).BarcodeDetector;
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setError(text.unsupported);
      return;
    }
    setError(null);
    setNotFound(null);
    scanningRef.current = true;
    setCameraOpen(true);
    try {
      const video = await waitForVideoElement(videoRef);
      if (!video || !scanningRef.current) throw new Error("Scanner preview was not mounted.");
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        audio: false
      });
      if (!scanningRef.current) { stream.getTracks().forEach(track => track.stop()); return; }
      streamRef.current = stream;
      video.srcObject = stream;
      await waitForVideoMetadata(video);
      await video.play();
      if (DetectorClass) {
        try {
          const detector = new DetectorClass({ formats: ["ean_8", "ean_13", "upc_a", "itf"] });
          while (scanningRef.current) {
            const results = await detector.detect(video);
            const detected = normalizeBarcode(results[0]?.rawValue ?? "");
            if (detected) { stopCamera(); await lookup(detected); return; }
            await new Promise<void>(resolve => window.setTimeout(resolve, 220));
          }
          return;
        } catch {
          // Some Safari versions expose BarcodeDetector but fail for live video. Keep the stream and use ZXing.
        }
      }
      if (scanningRef.current) await startZxingScanner(video);
    } catch {
      stopCamera();
      setError(text.denied);
    }
  }

  async function startZxingScanner(video: HTMLVideoElement) {
    const { BrowserMultiFormatReader } = await import("@zxing/browser");
    const reader = new BrowserMultiFormatReader(undefined, { delayBetweenScanAttempts: 180, delayBetweenScanSuccess: 400 });
    scannerControlsRef.current = await reader.decodeFromVideoElement(video, (result) => {
      const detected = normalizeBarcode(result?.getText() ?? "");
      if (!detected || !scanningRef.current) return;
      stopCamera();
      void lookup(detected);
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void lookup(value);
  }

  return <section className="barcode-scanner" aria-labelledby="barcode-scanner-title">
    <div className="barcode-scanner-copy"><span>GTIN</span><h3 id="barcode-scanner-title">{text.title}</h3><p>{text.description}</p></div>
    <form className="barcode-scanner-form" onSubmit={submit}>
      <label>{text.label}<input autoComplete="off" inputMode="numeric" maxLength={32} placeholder={text.placeholder} value={value} onChange={event => { setValue(normalizeBarcode(event.target.value)); setError(null); setNotFound(null); }} /></label>
      <button className="primary-button" disabled={busy} type="submit">{busy ? text.finding : text.find}</button>
      <button className="ghost-button" disabled={busy} type="button" onClick={() => cameraOpen ? stopCamera() : void startCamera()}>{cameraOpen ? text.stop : text.camera}</button>
    </form>
    <small className="barcode-scanner-hint">{text.hint}</small>
    {cameraOpen && <div className="barcode-camera"><video ref={videoRef} aria-label={text.preview} autoPlay muted playsInline /><div className="barcode-scan-guide" aria-hidden="true"><i /><i /><i /><i /></div><span className="barcode-scan-status">{text.scanning}</span></div>}
    {error && <div className="inline-error" role="alert">{error}</div>}
    {notFound && <div className="barcode-not-found" role="status"><span>{text.notFound}</span><a className="ghost-button" href={`/admin/products/contributions?barcode=${encodeURIComponent(notFound)}`}>{text.draft}</a></div>}
  </section>;
}

async function waitForVideoElement(ref: { current: HTMLVideoElement | null }): Promise<HTMLVideoElement | null> {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (ref.current) return ref.current;
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  }
  return null;
}

function waitForVideoMetadata(video: HTMLVideoElement): Promise<void> {
  if (video.readyState >= HTMLMediaElement.HAVE_METADATA) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => { cleanup(); reject(new Error("Camera metadata timed out.")); }, 5000);
    const loaded = () => { cleanup(); resolve(); };
    const failed = () => { cleanup(); reject(new Error("Camera preview failed.")); };
    const cleanup = () => { window.clearTimeout(timeout); video.removeEventListener("loadedmetadata", loaded); video.removeEventListener("error", failed); };
    video.addEventListener("loadedmetadata", loaded, { once: true });
    video.addEventListener("error", failed, { once: true });
  });
}
