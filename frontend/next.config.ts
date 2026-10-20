import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  devIndicators: false,
  images: {
    unoptimized: true,
  },
  async redirects() {
    return [
      {
        source: '/api/auth/:path*',
        destination: '/auth/:path*',
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
