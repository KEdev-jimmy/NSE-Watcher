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

function periodConfig(period) {
  const end = new Date();
  const start = new Date(end);
  let interval = '1d';
  switch (String(period || '1y').toLowerCase()) {
    case '3d':
      start.setDate(start.getDate() - 3);
      interval = '1d';
      break;
    case '1d':
      // Intraday chart: NSE observations are 15-minute delayed.
      // Include yesterday as the query boundary so the provider can return
      // the latest trading-session observations when the market is closed.
      start.setDate(start.getDate() - 2);
      interval = '15m';
      break;
    case '1w':
      start.setDate(start.getDate() - 7);
      interval = '1d';
      break;
    case '1m':
      start.setMonth(start.getMonth() - 1);
      interval = '1d';
      break;
    case '3m':
      start.setMonth(start.getMonth() - 3);
      interval = '1d';
      break;
    case '6m':
      start.setMonth(start.getMonth() - 6);
      interval = '1d';
      break;
    case '1y':
      start.setFullYear(start.getFullYear() - 1);
      interval = '1w';
      break;
    case '3y':
      start.setFullYear(start.getFullYear() - 3);
      interval = '1mo';
      break;
    case '5y':
      start.setFullYear(start.getFullYear() - 5);
      interval = '1mo';
      break;
    default:
      start.setFullYear(start.getFullYear() - 1);
      interval = '1w';
  }
  return {
    interval,
    from: start.toISOString().slice(0, 10),
    to: end.toISOString().slice(0, 10),
  };
}

function previousCloseFromStock(raw) {
  const data = raw?.data && !Array.isArray(raw.data) ? raw.data : raw;
  const value = Number(String(data?.previousClose ?? '').replace(/,/g, ''));
  return Number.isFinite(value) && value > 0 ? value : null;
}

function candleArray(raw) {
  if (Array.isArray(raw?.candles)) return raw.candles;
  if (Array.isArray(raw?.data?.candles)) return raw.data.candles;
  return [];
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
    if (action === 'chart') {
      const symbol = String(req.query.symbol || '').trim();
      if (!symbol) return json(res, 400, { error: 'symbol is required' });
      const period = String(req.query.period || '1y').toLowerCase();
      const cfg = periodConfig(period);
      const data = await mystocks(`/stocks/${encodeURIComponent(symbol)}/candles?interval=${encodeURIComponent(cfg.interval)}&from=${cfg.from}&to=${cfg.to}`);

      // For 1D, make the chart's first point the previous trading close when
      // the provider supplies it. This makes the displayed 1D return mean
      // "previous close -> latest delayed intraday observation", matching the
      // NSE day-change convention instead of "first intraday candle -> last".
      if (period === '1d') {
        try {
          const stock = await mystocks(`/stocks/${encodeURIComponent(symbol)}`);
          const previousClose = previousCloseFromStock(stock);
          const candles = candleArray(data);
          if (previousClose !== null && candles.length) {
            const first = candles[0];
            const existingFirst = Number(first?.close);
            if (!Number.isFinite(existingFirst) || Math.abs(existingFirst - previousClose) > 0.000001) {
              candles.unshift({
                date: first?.date || first?.timestamp || cfg.from,
                close: previousClose,
                synthetic: true,
                label: 'Previous close',
              });
            }
          }
        } catch (_) {
          // Keep the provider candles if the optional previous-close lookup fails.
        }
      }

      return json(res, 200, {
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt: new Date().toISOString(),
        symbol,
        period,
        interval: cfg.interval,
        asOf: new Date().toISOString(),
        data,
      });
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
