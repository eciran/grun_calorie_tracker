import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const outputRoot = path.resolve("outputs/grun-brand-kit");
const svgRoot = path.join(outputRoot, "svg");

const purple = "#6512A0";

const gPath = `M 0 0
  C -10.514 -4.055 -17.527 -11.295 -21.854 -21.378
  C -22.568 6.379 -7.685 25.813 19.351 30.399
  C 37.908 32.297 53.032 29.227 71.74 35.178
  C 72.3 14.411 71 -5.781 61.727 -24.589
  C 45.305 -57.584 9.555 -68.319 -25.031 -63.026
  C -61.447 -57.452 -89.832 -29.222 -95.697 7.102
  C -102.921 51.85 -78.949 94.271 -34.485 106.481
  C -14.929 111.851 5.129 111.371 24.624 105.943
  C 39.016 101.289 51.555 94.025 61.924 82.6
  L 42.614 63.393
  C 28.781 78.118 10.223 84.991 -9.802 84.058
  C -47.306 82.31 -71.217 51.889 -67.292 15.019
  C -64.869 -7.74 -50.413 -27.005 -28.726 -34.674
  C -15.709 -39.277 -1.904 -39.036 11.209 -35.298
  C 33.154 -28.245 41.087 -10.329 46.348 11.174
  C 39.073 6.376 32.046 3.791 23.633 3.552
  C 15.693 3.268 7.883 2.438 0 0 Z`;

const rPath = `M 0 0
  C -0.022 12.533 -6.584 22.467 -17.429 27.499
  C -23.518 29.81 -30.013 31.609 -36.781 31.611
  L -81.417 31.628 L -81.382 -32.015 L -38.645 -31.991
  C -18.893 -31.98 0.041 -22.715 0 0
  M 33.508 -111.194 L 0.707 -111.482 L -37.39 -57.327
  L -81.358 -57.32 L -81.494 -111.473 L -110.198 -111.487
  L -110.208 57.095 L -34.392 57.045
  C -22.876 57.037 -12.177 54.27 -1.804 49.954
  C 7.527 45.375 15.395 39.231 20.949 30.421
  C 27.774 19.222 30.197 6.588 28.964 -6.538
  C 26.926 -28.24 12.664 -45.575 -8.349 -52.02 Z`;

const uPath = `M 0 0
  C 0.009 11.634 9.906 19.341 20.466 19.072
  L 28.616 18.865 L 28.589 -80.734
  C 28.58 -112.3 10.872 -141.718 -21.095 -149.661
  C -38.411 -153.964 -56.397 -153.93 -73.44 -148.853
  C -105.858 -139.195 -120.851 -109.739 -120.865 -77.326
  L -120.906 18.852
  C -116.844 19.011 -113.465 19.151 -109.518 18.827
  C -100.285 18.069 -92.195 10.087 -92.173 0.302
  L -92.002 -77.302
  C -91.953 -99.515 -82.178 -120.528 -59.066 -125.018
  C -37.471 -129.212 -13.761 -124.21 -4.715 -102.081
  C -1.733 -94.785 -0.066 -87.152 -0.059 -79.025 Z`;

const nPath = `M 0 0 L -25.6 -0.205 L -121.791 119.142
  L -121.921 -0.291 L -150.855 -0.157 L -150.868 168.213
  L -124.939 168.289 L -29.275 49.679 L -29.056 168.301
  L -0.014 168.28 Z`;

function wordmarkPaths(fill = "currentColor") {
  return `<g fill="${fill}">
    <path transform="translate(704.7449 541.0491) scale(1 -1)" d="${gPath}"/>
    <path transform="translate(914.9094 490.8334) scale(1 -1)" d="${rPath}"/>
    <path transform="translate(1095.3563 452.7631) scale(1 -1)" d="${uPath}"/>
    <path transform="translate(1312.2235 602.0681) scale(1 -1)" d="${nPath}"/>
  </g>`;
}

function nestedWordmark({ x, y, width, height, fill = "#FFFFFF" }) {
  return `<svg x="${x}" y="${y}" width="${width}" height="${height}" viewBox="590 420 735 245" preserveAspectRatio="xMidYMid meet">
    ${wordmarkPaths(fill)}
  </svg>`;
}

function nestedSymbol({ x, y, width, height, fill = "#FFFFFF" }) {
  return `<svg x="${x}" y="${y}" width="${width}" height="${height}" viewBox="-110 -120 190 200" preserveAspectRatio="xMidYMid meet">
    <path fill="${fill}" transform="scale(1 -1)" d="${gPath}"/>
  </svg>`;
}

const gradientDefs = `<defs>
  <linearGradient id="grun-gradient" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#C20FE7"/>
    <stop offset="0.42" stop-color="#7B1BC4"/>
    <stop offset="0.72" stop-color="#49106F"/>
    <stop offset="1" stop-color="#8F66E9"/>
  </linearGradient>
  <radialGradient id="grun-glow" cx="18%" cy="12%" r="92%">
    <stop offset="0" stop-color="#E315F2" stop-opacity=".55"/>
    <stop offset=".58" stop-color="#6B16B9" stop-opacity=".08"/>
    <stop offset="1" stop-color="#24052B" stop-opacity=".42"/>
  </radialGradient>
  <pattern id="grun-facets" width="320" height="240" patternUnits="userSpaceOnUse">
    <path d="M0 0 126 0 42 88Z" fill="#FFFFFF" opacity=".025"/>
    <path d="M126 0 250 52 42 88Z" fill="#24052B" opacity=".045"/>
    <path d="M250 52 320 0 320 142Z" fill="#FFFFFF" opacity=".02"/>
    <path d="M42 88 190 176 0 240Z" fill="#FFFFFF" opacity=".018"/>
    <path d="M250 52 320 142 190 176Z" fill="#24052B" opacity=".04"/>
    <path d="M190 176 320 142 320 240 0 240Z" fill="#FFFFFF" opacity=".018"/>
  </pattern>
</defs>`;

const files = {
  "grun-symbol.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-110 -120 190 200" color="${purple}" role="img" aria-labelledby="title">
  <title id="title">GRUN symbol</title>
  <path fill="currentColor" transform="scale(1 -1)" d="${gPath}"/>
</svg>`,

  "grun-wordmark.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="590 420 735 245" color="${purple}" role="img" aria-labelledby="title">
  <title id="title">GRUN wordmark</title>
  ${wordmarkPaths()}
</svg>`,

  "grun-app-icon.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-labelledby="title">
  <title id="title">GRUN app icon</title>
  ${gradientDefs}
  <rect width="1024" height="1024" fill="url(#grun-gradient)"/>
  <rect width="1024" height="1024" fill="url(#grun-glow)"/>
  <rect width="1024" height="1024" fill="url(#grun-facets)"/>
  ${nestedSymbol({ x: 187, y: 170, width: 650, height: 684 })}
</svg>`,

  "grun-app-icon-rounded-preview.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-labelledby="title">
  <title id="title">GRUN rounded icon preview</title>
  ${gradientDefs}
  <defs><clipPath id="icon-mask"><rect width="1024" height="1024" rx="220"/></clipPath></defs>
  <g clip-path="url(#icon-mask)">
    <rect width="1024" height="1024" fill="url(#grun-gradient)"/>
    <rect width="1024" height="1024" fill="url(#grun-glow)"/>
    <rect width="1024" height="1024" fill="url(#grun-facets)"/>
    ${nestedSymbol({ x: 187, y: 170, width: 650, height: 684 })}
  </g>
</svg>`,

  "grun-adaptive-foreground.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-labelledby="title">
  <title id="title">GRUN Android adaptive icon foreground</title>
  ${nestedSymbol({ x: 24, y: 23, width: 60, height: 62 })}
</svg>`,

  "grun-adaptive-background.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" role="img" aria-labelledby="title">
  <title id="title">GRUN Android adaptive icon background</title>
  ${gradientDefs}
  <rect width="108" height="108" fill="url(#grun-gradient)"/>
  <rect width="108" height="108" fill="url(#grun-glow)"/>
</svg>`,

  "grun-lockup-gradient.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 600" role="img" aria-labelledby="title">
  <title id="title">GRUN horizontal gradient lockup</title>
  ${gradientDefs}
  <rect width="1600" height="600" fill="url(#grun-gradient)"/>
  <rect width="1600" height="600" fill="url(#grun-glow)"/>
  <rect width="1600" height="600" fill="url(#grun-facets)"/>
  ${nestedWordmark({ x: 245, y: 115, width: 1110, height: 370 })}
</svg>`,

  "grun-gradient-background.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080" role="img" aria-labelledby="title">
  <title id="title">GRUN gradient background</title>
  ${gradientDefs}
  <rect width="1920" height="1080" fill="url(#grun-gradient)"/>
  <rect width="1920" height="1080" fill="url(#grun-glow)"/>
  <rect width="1920" height="1080" fill="url(#grun-facets)"/>
</svg>`,

  "grun-splash.svg": `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 1920" role="img" aria-labelledby="title">
  <title id="title">GRUN splash screen</title>
  ${gradientDefs}
  <rect width="1080" height="1920" fill="url(#grun-gradient)"/>
  <rect width="1080" height="1920" fill="url(#grun-glow)"/>
  <rect width="1080" height="1920" fill="url(#grun-facets)"/>
  ${nestedSymbol({ x: 380, y: 570, width: 320, height: 336 })}
  ${nestedWordmark({ x: 170, y: 950, width: 740, height: 247 })}
</svg>`,
};

const previewCards = [
  ["Themeable symbol", "grun-symbol.svg"],
  ["Themeable wordmark", "grun-wordmark.svg"],
  ["Store icon", "grun-app-icon-rounded-preview.svg"],
  ["Gradient lockup", "grun-lockup-gradient.svg"],
  ["Splash screen", "grun-splash.svg"],
];

const preview = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1400 1280">
  <rect width="1400" height="1280" fill="#F6F3F8"/>
  <text x="70" y="82" font-family="Arial, sans-serif" font-size="38" font-weight="700" fill="#24152E">GRUN brand kit</text>
  <text x="70" y="122" font-family="Arial, sans-serif" font-size="20" fill="#725E7E">Application-ready SVG assets</text>
  <g font-family="Arial, sans-serif" font-size="18" font-weight="600" fill="#24152E">
    <rect x="70" y="170" width="600" height="280" rx="24" fill="#FFFFFF"/>
    <text x="100" y="215">${previewCards[0][0]}</text>
    <image href="svg/${previewCards[0][1]}" x="245" y="238" width="250" height="180"/>
    <rect x="730" y="170" width="600" height="280" rx="24" fill="#FFFFFF"/>
    <text x="760" y="215">${previewCards[1][0]}</text>
    <image href="svg/${previewCards[1][1]}" x="790" y="260" width="480" height="150"/>
    <rect x="70" y="490" width="390" height="390" rx="24" fill="#FFFFFF"/>
    <text x="100" y="535">${previewCards[2][0]}</text>
    <image href="svg/${previewCards[2][1]}" x="115" y="570" width="300" height="260"/>
    <rect x="500" y="490" width="830" height="390" rx="24" fill="#FFFFFF"/>
    <text x="530" y="535">${previewCards[3][0]}</text>
    <image href="svg/${previewCards[3][1]}" x="550" y="585" width="730" height="274"/>
    <rect x="70" y="920" width="1260" height="280" rx="24" fill="#FFFFFF"/>
    <text x="100" y="965">${previewCards[4][0]} (cropped preview)</text>
    <image href="svg/${previewCards[4][1]}" x="570" y="980" width="260" height="205" preserveAspectRatio="xMidYMin slice"/>
  </g>
</svg>`;

const readme = `# GRUN brand kit

This package converts the reusable vector structures in GRUN.pdf into application-ready SVG assets.

## Assets

- \`grun-symbol.svg\`: themeable G/leaf symbol. Uses \`currentColor\`; defaults to GRUN purple.
- \`grun-wordmark.svg\`: themeable full GRUN wordmark. Uses \`currentColor\`.
- \`grun-app-icon.svg\`: 1024x1024 full-bleed master icon. Do not add rounded corners before store upload.
- \`grun-app-icon-rounded-preview.svg\`: rounded visual preview only.
- \`grun-adaptive-foreground.svg\`: Android adaptive icon foreground, with safe-zone spacing.
- \`grun-adaptive-background.svg\`: Android adaptive icon background layer.
- \`grun-lockup-gradient.svg\`: horizontal white wordmark on the GRUN gradient.
- \`grun-gradient-background.svg\`: reusable 16:9 brand background.
- \`grun-splash.svg\`: 9:16 splash-screen master.

## Brand tokens

- Primary purple: \`#6512A0\`
- White: \`#FFFFFF\`
- Gradient highlight: \`#C20FE7\`
- Gradient shadow: \`#24052B\`
- Gradient lavender: \`#8F66E9\`

## Usage

The symbol and wordmark are monochrome and can be recolored with CSS or the platform tint/color API. Keep the leaf cutout clear, do not stretch the artwork, and retain at least 12.5% clear space around standalone marks.

The phone mockup on page 1 of the PDF is presentation imagery rather than a reusable logo structure, so it is not included as an application asset. The gradient and white logo used by that composition are included separately.
`;

const tokens = {
  name: "GRUN",
  source: "GRUN.pdf",
  colors: {
    primary: purple,
    white: "#FFFFFF",
    gradientHighlight: "#C20FE7",
    gradientMid: "#7B1BC4",
    gradientShadow: "#24052B",
    gradientLavender: "#8F66E9",
  },
  clearSpace: "12.5% of mark width",
  minimumDigitalSize: { symbolPx: 24, wordmarkWidthPx: 96 },
};

await mkdir(svgRoot, { recursive: true });
await Promise.all(
  Object.entries(files).map(([name, content]) =>
    writeFile(path.join(svgRoot, name), `${content}\n`, "utf8"),
  ),
);
await writeFile(path.join(outputRoot, "preview.svg"), `${preview}\n`, "utf8");
await writeFile(path.join(outputRoot, "README.md"), readme, "utf8");
await writeFile(path.join(outputRoot, "brand-tokens.json"), `${JSON.stringify(tokens, null, 2)}\n`, "utf8");

console.log(`Generated ${Object.keys(files).length} SVG assets in ${svgRoot}`);

