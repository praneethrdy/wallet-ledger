import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  // JSX in component tests (React 19 automatic runtime), without the React Vite plugin.
  oxc: { jsx: { runtime: "automatic" } },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "node", // component tests opt into jsdom with a file comment
    restoreMocks: true,
  },
});
