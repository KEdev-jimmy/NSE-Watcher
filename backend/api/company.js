const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_API_KEY;

function json(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=900');
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

  const action = String(req.query.action || 'intelligence');
  const rawSymbol = String(req.query.symbol || '').trim().toUpperCase();
  if (!rawSymbol) return json(res, 400, { error: 'symbol is required' });

  const symbol = rawSymbol.includes('.') ? rawSymbol : `${rawSymbol}.KE`;

  try {
    if (action === 'profile') {
      const profile = await mystocks(`/companies/${encodeURIComponent(symbol)}`);
      return json(res, 200, {
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt: new Date().toISOString(),
        profile,
      });
    }

    if (action === 'dividends') {
      const dividends = await mystocks(`/dividends/${encodeURIComponent(symbol)}/history?limit=12`);
      return json(res, 200, {
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt: new Date().toISOString(),
        dividends: dividends.history || dividends.data || [],
      });
    }

    const [profileResponse, dividendResponse] = await Promise.allSettled([
      mystocks(`/companies/${encodeURIComponent(symbol)}`),
      mystocks(`/dividends/${encodeURIComponent(symbol)}/history?limit=12`),
    ]);

    const profile = profileResponse.status === 'fulfilled' ? profileResponse.value : null;
    const dividends = dividendResponse.status === 'fulfilled'
      ? (dividendResponse.value.history || dividendResponse.value.data || [])
      : [];

    if (!profile && !dividends.length) {
      const firstError = profileResponse.status === 'rejected' ? profileResponse.reason : dividendResponse.reason;
      return json(res, firstError?.status || 502, {
        error: 'Company intelligence unavailable',
        detail: firstError?.message || 'No company data returned',
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt: new Date().toISOString(),
      });
    }

    return json(res, 200, {
      source: 'MyStocks Africa',
      delayMinutes: 15,
      fetchedAt: new Date().toISOString(),
      symbol,
      profile,
      dividends,
      partial: !profile || dividendResponse.status !== 'fulfilled',
    });
  } catch (error) {
    return json(res, error.status || 502, {
      error: 'Company intelligence unavailable',
      detail: error.message,
      source: 'MyStocks Africa',
      delayMinutes: 15,
      fetchedAt: new Date().toISOString(),
      provider: error.data || null,
    });
  }
};
