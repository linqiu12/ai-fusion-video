import type { NextConfig } from "next";

const developmentBackendUrl = process.env.DEV_BACKEND_URL?.trim().replace(/\/+$/, "");

if (process.env.NODE_ENV === "development" && !developmentBackendUrl) {
  throw new Error(
    "本地开发必须配置 DEV_BACKEND_URL，请检查 ai-fusion-video-web/.env.development",
  );
}

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // 允许并行启动隔离的 E2E 实例，避免占用开发者现有 .next 锁。
  distDir: process.env.NEXT_DIST_DIR || ".next",
  output: "standalone",
  // Development API rewrites proxy SSE through Next.js. Disable compression
  // there so incremental events are flushed instead of buffered by gzip.
  compress: process.env.NODE_ENV !== "development",
  async rewrites() {
    if (process.env.NODE_ENV !== "development") {
      return [];
    }

    return [
      {
        source: "/api/:path*",
        destination: `${developmentBackendUrl!}/api/:path*`,
      },
      {
        source: "/media/:path*",
        destination: `${developmentBackendUrl!}/media/:path*`,
      },
    ];
  },
  async headers() {
    return [
      {
        source: "/runtime-config.js",
        headers: [
          {
            key: "Cache-Control",
            value: "no-store, max-age=0",
          },
        ],
      },
    ];
  },
};

export default nextConfig;
