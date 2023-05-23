import { createProxyMiddleware } from 'http-proxy-middleware';

export default function(app) {
  app.use(
    '/api',
    createProxyMiddleware({
      target: 'http://lvh.me:4010',
      changeOrigin: true,
    })
  );
};
