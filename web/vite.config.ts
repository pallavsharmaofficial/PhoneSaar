import { defineConfig } from "vitest/config";

export default defineConfig({
  base: "/PhoneSaar/app/",
  // The shared rules live one level above web/.
  server: { fs: { allow: [".."] } },
  build: { target: "es2022" },
  test: { include: ["src/**/*.test.ts"] },
});
