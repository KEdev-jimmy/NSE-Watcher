const marketHandler = require('../backend/api/market.js');

function runMarketHandler(request) {
  const url = new URL(request.url);
  const headers = new Headers();

  return new Promise(async (resolve) => {
    let statusCode = 200;
    let body = '';

    const response = {
      status(code) {
        statusCode = code;
        return response;
      },
      setHeader(name, value) {
        headers.set(name, String(value));
        return response;
      },
      end(value = '') {
        body = String(value);
        resolve(new Response(body, { status: statusCode, headers }));
      },
    };

    const req = {
      method: request.method,
      query: Object.fromEntries(url.searchParams.entries()),
      url: url.pathname + url.search,
      headers: Object.fromEntries(request.headers.entries()),
    };

    try {
      await marketHandler(req, response);
    } catch (error) {
      resolve(new Response(JSON.stringify({
        error: 'Worker request failed',
        detail: error?.message || String(error),
      }), {
        status: 502,
        headers: { 'Content-Type': 'application/json' },
      }));
    }
  });
}

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Keep the Worker root useful for a deployment smoke test.
    if (url.pathname === '/' || url.pathname === '/health') {
      return Response.json({
        service: 'NSE Watcher API',
        runtime: 'Cloudflare Workers',
        status: 'ok',
      });
    }

    // Reuse the existing, tested market API contract under /api/market.
    if (url.pathname === '/api/market' || url.pathname === '/api/market/') {
      return runMarketHandler(request);
    }

    return Response.json({
      error: 'Not found',
      expected: '/api/market?action=status',
    }, { status: 404 });
  },
};
