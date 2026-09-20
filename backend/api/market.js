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
    case '3d': start.setDate(start.getDate() - 3); interval = '1d'; break;
    case '1d': start.setDate(start.getDate() - 2); interval = '15m'; break;
    case '1w': start.setDate(start.getDate() - 7); interval = '1d'; break;
    case '1m': start.setMonth(start.getMonth() - 1); interval = '1d'; break;
    case '3m': start.setMonth(start.getMonth() - 3); interval = '1d'; break;
    case '6m': start.setMonth(start.getMonth() - 6); interval = '1d'; break;
    case '1y': start.setFullYear(start.getFullYear() - 1); interval = '1w'; break;
    case '3y': start.setFullYear(start.getFullYear() - 3); interval = '1mo'; break;
    case '5y': start.setFullYear(start.getFullYear() - 5); interval = '1mo'; break;
    default: start.setFullYear(start.getFullYear() - 1); interval = '1w';
  }
  return { interval, from: start.toISOString().slice(0, 10), to: end.toISOString().slice(0, 10) };
}

function candleArray(raw) {
  if (Array.isArray(raw?.candles)) return raw.candles;
  if (Array.isArray(raw?.data?.candles)) return raw.data.candles;
  return [];
}

function candleTimestamp(candle) {
  const raw = candle?.timestamp || candle?.date;
  const value = raw ? new Date(raw) : null;
  return value && Number.isFinite(value.getTime()) ? value : null;
}

function chronologicallyOrderedCandles(candles) {
  const entries = candles.map((candle, index) => ({
    candle,
    index,
    timestamp: candleTimestamp(candle),
  }));
  if (entries.length < 2 || entries.some((entry) => !entry.timestamp)) return candles;
  return entries
    .sort((a, b) => a.timestamp - b.timestamp || a.index - b.index)
    .map((entry) => entry.candle);
}

function latestTradingSession(candles) {
  const valid = candles
    .filter((candle) => Number.isFinite(Number(candle?.close)) && Number(candle.close) > 0)
    .map((candle, index) => ({ candle, index, timestamp: candleTimestamp(candle) }))
    .filter((entry) => entry.timestamp);

  if (!valid.length) return null;

  const latestDay = valid.reduce(
    (latest, entry) => entry.timestamp > latest ? entry.timestamp : latest,
    valid[0].timestamp
  ).toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' });

  const session = valid
    .filter((entry) => entry.timestamp.toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' }) === latestDay)
    .sort((a, b) => a.timestamp - b.timestamp)
    .map((entry) => entry.candle);

  if (!session.length) return null;

  return {
    candles: session,
    open: Number.isFinite(Number(session[0]?.open)) && Number(session[0].open) > 0
      ? Number(session[0].open)
      : null,
    close: Number.isFinite(Number(session[session.length - 1]?.close)) && Number(session[session.length - 1].close) > 0
      ? Number(session[session.length - 1].close)
      : null,
    openAt: candleTimestamp(session[0])?.toISOString() || null,
    closeAt: candleTimestamp(session[session.length - 1])?.toISOString() || null,
  };
}

function providerAsOf(raw) {
  return raw?.asOf || raw?.meta?.asOf || raw?.data?.asOf || raw?.data?.meta?.asOf || null;
}

function providerMeta(raw) {
  return raw?.meta || raw?.data?.meta || null;
}

function normalizeMarketStatus(providerData) {
  const rawStatus = String(
    providerData?.status ??
    providerData?.marketStatus ??
    providerData?.state ??
    ''
  ).trim();
  const normalizedStatus = rawStatus.toLowerCase();
  const statusOpen = normalizedStatus === 'open' || normalizedStatus === 'trading';
  const statusClosed = normalizedStatus === 'closed' || normalizedStatus === 'not_trading';

  const hasExplicitIsOpen = typeof providerData?.isOpen === 'boolean';
  const explicitIsOpen = hasExplicitIsOpen ? providerData.isOpen : null;

  // A provider must not be treated as CLOSED merely because it omitted status.
  // If explicit boolean and status disagree, preserve the uncertainty instead
  // of silently choosing one source of truth.
  if (hasExplicitIsOpen && (statusOpen || statusClosed)) {
    if ((explicitIsOpen && statusClosed) || (!explicitIsOpen && statusOpen)) {
      return { isOpen: false, status: 'UNKNOWN', isKnown: false };
    }
    return {
      isOpen: explicitIsOpen,
      status: explicitIsOpen ? 'OPEN' : 'CLOSED',
      isKnown: true,
    };
  }

  if (hasExplicitIsOpen) {
    return {
      isOpen: explicitIsOpen,
      status: explicitIsOpen ? 'OPEN' : 'CLOSED',
      isKnown: true,
    };
  }

  if (statusOpen) return { isOpen: true, status: 'OPEN', isKnown: true };
  if (statusClosed) return { isOpen: false, status: 'CLOSED', isKnown: true };

  return { isOpen: false, status: 'UNKNOWN', isKnown: false };
}

function unwrapProviderData(raw) {
  return raw?.data && !Array.isArray(raw.data) ? raw.data : raw;
}

module.exports = async (req, res) => {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });
  const action = String(req.query.action || 'snapshot');
  try {
    if (action === 'status') {
      const data = await mystocks('/market/status?exchange=NSE');
      const providerData = unwrapProviderData(data);
      const normalized = normalizeMarketStatus(providerData);
      return json(res, 200, {
        source: 'MyStocks Africa', delayMinutes: null, fetchedAt: new Date().toISOString(),
        isOpen: normalized.isOpen,
        status: normalized.status,
        isKnown: normalized.isKnown,
        nextOpen: providerData?.nextOpen || providerData?.nextSessionOpen || null,
        nextClose: providerData?.nextClose || providerData?.nextSessionClose || null,
        data,
      });
    }
    if (action === 'stocks') {
      const data = await mystocks('/stocks?exchange=NSE&limit=50');
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(), data });
    }
    if (action === 'indices') {
      // MyStocks' documented market-quote surface does not provide a verified
      // official NSE index contract. Do not send guessed index symbols to the
      // provider: that produces a misleading provider error instead of an
      // honest unavailable state.
      return json(res, 200, {
        source: null,
        delayMinutes: null,
        fetchedAt: new Date().toISOString(),
        indices: [],
        requested: ['NASI', 'NSE 20 Share Index', 'NSE 25 Share Index'],
        availability: 'unavailable',
        reason: 'No verified official NSE index data source is configured.',
      });
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
      // Historical calculations and chart rendering require chronological observations.
      // If the provider omits timestamps, preserve its original order rather than guessing.
      const orderedCandles = chronologicallyOrderedCandles(candleArray(data));
      if (data?.candles) data.candles = orderedCandles;
      else if (data?.data?.candles) data.data.candles = orderedCandles;

      let chartAsOf = providerAsOf(data);
      let sessionOpen = null;
      let sessionClose = null;
      let sessionOpenAt = null;
      let sessionCloseAt = null;

      if (period === '1d') {
        try {
          const session = latestTradingSession(candleArray(data));
          if (session) {
            sessionOpen = session.open;
            sessionClose = session.close;
            sessionOpenAt = session.openAt;
            sessionCloseAt = session.closeAt;

            // Keep the chart timeline strictly observational and chronological:
            // return only actual candles from the latest Nairobi trading session.
            if (data?.candles) data.candles = session.candles;
            else if (data?.data?.candles) data.data.candles = session.candles;
          }
          chartAsOf = providerAsOf(data) || chartAsOf;
        } catch (_) {
          // Keep provider candles if optional session shaping fails.
        }
      }

      const candles = candleArray(data);
      const latestCandle = candles[candles.length - 1];
      const latestCandleTimestamp = candleTimestamp(latestCandle);
      chartAsOf = providerAsOf(data) || latestCandleTimestamp?.toISOString() || chartAsOf;
      const meta = providerMeta(data);
      const latestObservationAt = meta?.asOf || latestCandleTimestamp?.toISOString() || chartAsOf || null;
      if (sessionClose === null && Number.isFinite(Number(latestCandle?.close))) sessionClose = Number(latestCandle.close);
      const sessionChangePct = Number.isFinite(sessionOpen) && sessionOpen > 0 && Number.isFinite(sessionClose)
        ? ((sessionClose - sessionOpen) / sessionOpen) * 100
        : null;

      return json(res, 200, {
        source: 'MyStocks Africa', delayMinutes: 15, fetchedAt: new Date().toISOString(),
        symbol, period, interval: cfg.interval, asOf: chartAsOf, latestObservationAt,
        session: period === '1d' ? {
          open: Number.isFinite(sessionOpen) ? sessionOpen : null,
          close: Number.isFinite(sessionClose) ? sessionClose : null,
          changePct: Number.isFinite(sessionChangePct) ? sessionChangePct : null,
          openAt: sessionOpenAt,
          closeAt: sessionCloseAt,
        } : null,
        dataQuality: meta ? {
          qualityStatus: meta.qualityStatus || null,
          qualityIssues: Array.isArray(meta.qualityIssues) ? meta.qualityIssues : [],
          recommendedChartType: meta.recommendedChartType || null,
          sandbox: meta.sandbox === true,
        } : null,
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
      error: 'Market data unavailable', detail: error.message,
      source: 'MyStocks Africa', delayMinutes: null, fetchedAt: new Date().toISOString(), provider: error.data || null,
    });
  }
};

module.exports.normalizeMarketStatus = normalizeMarketStatus;
module.exports.latestTradingSession = latestTradingSession;
module.exports.chronologicallyOrderedCandles = chronologicallyOrderedCandles;
