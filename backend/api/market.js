const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_API_KEY;

function json(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 's-maxage=60, stale-while-revalidate=300');
  res.end(JSON.stringify(body));
}

async function mystocks(path) {
  if (!API_KEY) throw new Error('MYSTOCKS_API_KEY is not configured');
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { 'x-api-key': API_KEY, Accept: 'application/json' },
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = { raw: text }; }
  if (!response.ok) {
    const error = new Error(`MyStocks ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

module.exports = async (req, res) => {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });
  const action = String(req.query.action || 'snapshot');
  try {
    if (action === 'status') {
      const data = await mystocks('/market/status?exchange=NSE');
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(), data });
    }
    if (action === 'stocks') {
      const data = await mystocks('/stocks?exchange=NSE&limit=50');
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(), data });
    }
    if (action === 'movers') {
      const gainers = await mystocks('/market/movers?exchange=NSE&direction=gainers&limit=10');
      const losers = await mystocks('/market/movers?exchange=NSE&direction=losers&limit=10');
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(), gainers, losers });
    }
    const symbols = String(req.query.symbols || '').trim();
    const path = symbols
      ? `/market/snapshot?symbols=${encodeURIComponent(symbols)}`
      : '/market/snapshot?symbols=SCOM.KE,EQTY.KE,KCB.KE,ABSA.KE,COOP.KE,SBIC.KE';
    const data = await mystocks(path);
    return json(res, 200, { source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(), data });
  } catch (error) {
    return json(res, error.status || 502, {
      error: 'Market data unavailable',
      detail: error.message,
      source: 'MyStocks Africa',
      delayMinutes: 15,
      fetchedAt: new Date().toISOString(),
      provider: error.data || null,
    });
  }
};
