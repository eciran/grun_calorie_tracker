declare module "libheif-js/libheif-wasm/libheif.js" {
  type HeifImage = {
    get_width(): number;
    get_height(): number;
    display(image: ImageData, callback: (decoded: ImageData | null) => void): void;
    free(): void;
  };
  type HeifModule = {
    HeifDecoder: new () => { decode(bytes: Uint8Array): HeifImage[] };
  };
  export default function createModule(options: {
    wasmBinary: ArrayBuffer;
    onRuntimeInitialized(): void;
    onAbort(reason: unknown): void;
  }): HeifModule;
}

declare module "*.wasm?url" {
  const url: string;
  export default url;
}
