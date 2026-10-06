import cssnano from "cssnano";

export default {
  plugins: [cssnano({ preset: ["default", { normalizeUrl: false }] })]
};
