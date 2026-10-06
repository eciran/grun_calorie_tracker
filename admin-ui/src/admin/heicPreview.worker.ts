import createModule from "libheif-js/libheif-wasm/libheif.js";
import wasmUrl from "libheif-js/libheif-wasm/libheif.wasm?url";

// A worker is short-lived: terminating it also releases the codec's native heap.
self.onmessage = async ({ data }: MessageEvent<Blob>) => {
  try {
    if (!(data instanceof Blob) || data.size > 25 * 1024 * 1024) throw new Error("HEIC file exceeds preview limits.");
    const response = await fetch(wasmUrl);
    if (!response.ok) throw new Error("HEIC decoder could not be loaded.");
    const wasmBinary = await response.arrayBuffer();
    const codec = await new Promise<ReturnType<typeof createModule>>((resolve, reject) => {
      const module = createModule({ wasmBinary,
        onRuntimeInitialized: () => queueMicrotask(() => resolve(module)),
        onAbort: reject
      });
    });
    const images = new codec.HeifDecoder().decode(new Uint8Array(await data.arrayBuffer()));
    try {
      const image = images[0];
      if (!image) throw new Error("No image found in HEIC file.");
      const width = image.get_width();
      const height = image.get_height();
      if (width <= 0 || height <= 0 || width * height > 50_000_000) throw new Error("HEIC dimensions exceed preview limits.");
      const pixels = new ImageData(width, height);
      await new Promise<void>((resolve, reject) => image.display(pixels, decoded => {
        if (!decoded) reject(new Error("HEIC image could not be decoded."));
        else resolve();
      }));
      const canvas = new OffscreenCanvas(width, height);
      const context = canvas.getContext("2d");
      if (!context) throw new Error("Image preview is not supported by this browser.");
      context.putImageData(pixels, 0, 0);
      self.postMessage({ blob: await canvas.convertToBlob({ type: "image/jpeg", quality: 0.9 }) });
    } finally {
      images.forEach(image => image.free());
    }
  } catch {
    self.postMessage({ error: "HEIC preview could not be created." });
  }
};
