const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_DATA_KEY || process.env.MYSTOCKS_API_KEY;
const MARKET_DATA_DELAY_MINUTES = 15;
const MARKET_DATA_REFRESH_SECONDS = 900;

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

async function loadAllNseCompanies() {
  const pageSize = 200;
  const companies = [];
  let cursor = '';
  const seenCursors = new Set();

  while (true) {
    const suffix = cursor ? `&cursor=${encodeURIComponent(cursor)}` : '';
    const page = await mystocks(`/companies?exchange=NSE&limit=${pageSize}${suffix}`);
    const pageCompanies = Array.isArray(page?.companies) ? page.companies : [];
    companies.push(...pageCompanies);

    if (!page?.hasMore || !page?.nextCursor || seenCursors.has(page.nextCursor)) {
      return { ...page, companies, count: companies.length, hasMore: false, nextCursor: null };
    }

    seenCursors.add(page.nextCursor);
    cursor = page.nextCursor;
  }
}

async function loadAllNseStocks() {
  const pageSize = 200;
  const stocks = [];
  let cursor = '';
  const seenCursors = new Set();

  while (true) {
    const suffix = cursor ? `&cursor=${encodeURIComponent(cursor)}` : '';
    const page = await mystocks(`/stocks?exchange=NSE&limit=${pageSize}${suffix}`);
    const pageStocks = Array.isArray(page?.stocks) ? page.stocks : [];
    stocks.push(...pageStocks);

    if (!page?.hasMore || !page?.nextCursor || seenCursors.has(page.nextCursor)) {
      return { ...page, stocks, count: stocks.length, hasMore: false, nextCursor: null };
    }

    seenCursors.add(page.nextCursor);
    cursor = page.nextCursor;
  }
}

function periodConfig(period) {
  const end = new Date();
  const start = new Date(end);
  let interval = '1d';
  switch (String(period || '1y').toLowerCase()) {
    case '3d': start.setDate(start.getDate() - 3); interval = '1d'; break;
    case '1d': start.setDate(start.getDate() - 7); interval = '15m'; break;
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

function technicalHistoryConfig(now = new Date(), lookbackDays = 400) {
  const parsed = Number(lookbackDays);
  const boundedDays = Number.isFinite(parsed)
    ? Math.min(730, Math.max(260, Math.trunc(parsed)))
    : 400;
  const to = new Date(now);
  const from = new Date(now);
  from.setUTCDate(from.getUTCDate() - boundedDays);
  return {
    interval: '1d',
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
    lookbackDays: boundedDays,
  };
}

function positiveNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function normalizeTechnicalCandles(rawCandles) {
  return chronologicallyOrderedCandles(Array.isArray(rawCandles) ? rawCandles : [])
    .map((candle) => {
      const close = positiveNumber(candle?.close);
      if (close === null) return null;

      const rawVolume = Number(candle?.volume);
      const explicitlyUnavailable = candle?.volumeAvailable === false;
      const volumeAvailable = !explicitlyUnavailable && Number.isFinite(rawVolume) && rawVolume >= 0;
      const rawDate = String(candle?.date || candle?.timestamp || '').trim();
      const timestamp = candleTimestamp(candle);

      return {
        date: rawDate,
        timestamp: timestamp ? timestamp.toISOString() : null,
        open: positiveNumber(candle?.open),
        high: positiveNumber(candle?.high),
        low: positiveNumber(candle?.low),
        close,
        volume: volumeAvailable ? rawVolume : null,
        volumeAvailable,
      };
    })
    .filter(Boolean);
}

function percentage(part, total) {
  if (!total) return 0;
  return Math.round((part / total) * 10000) / 100;
}

function technicalDataQuality(candles, meta = null) {
  const rows = Array.isArray(candles) ? candles : [];
  const completeOhlcCount = rows.filter((candle) =>
    positiveNumber(candle?.open) !== null &&
    positiveNumber(candle?.high) !== null &&
    positiveNumber(candle?.low) !== null &&
    positiveNumber(candle?.close) !== null
  ).length;
  const volumeAvailableCount = rows.filter((candle) =>
    candle?.volumeAvailable === true &&
    Number.isFinite(Number(candle?.volume)) &&
    Number(candle.volume) >= 0
  ).length;
  const closeOnlyCount = rows.filter((candle) =>
    positiveNumber(candle?.close) !== null &&
    positiveNumber(candle?.open) === null &&
    positiveNumber(candle?.high) === null &&
    positiveNumber(candle?.low) === null
  ).length;

  const trailingHasOhlc = (count) => {
    const tail = rows.slice(-count);
    return tail.length >= count && tail.every((candle) =>
      positiveNumber(candle?.high) !== null &&
      positiveNumber(candle?.low) !== null &&
      positiveNumber(candle?.close) !== null
    );
  };
  const trailingHasVolume = (count) => {
    const tail = rows.slice(-count);
    return tail.length >= count && tail.every((candle) =>
      candle?.volumeAvailable === true &&
      Number.isFinite(Number(candle?.volume)) &&
      Number(candle.volume) >= 0
    );
  };

  return {
    qualityStatus: meta?.qualityStatus || null,
    qualityIssues: Array.isArray(meta?.qualityIssues) ? meta.qualityIssues : [],
    recommendedChartType: meta?.recommendedChartType || null,
    sandbox: meta?.sandbox === true,
    coverageStartsAt: meta?.coverageStartsAt || null,
    coverageEndsAt: meta?.coverageEndsAt || null,
    candleCount: rows.length,
    completeOhlcCount,
    volumeAvailableCount,
    closeOnlyCount,
    ohlcCoveragePct: percentage(completeOhlcCount, rows.length),
    volumeCoveragePct: percentage(volumeAvailableCount, rows.length),
    firstObservationAt: rows[0]?.timestamp || rows[0]?.date || null,
    lastObservationAt: rows[rows.length - 1]?.timestamp || rows[rows.length - 1]?.date || null,
    indicatorReadiness: {
      sma20: rows.length >= 20,
      sma50: rows.length >= 50,
      sma100: rows.length >= 100,
      sma200: rows.length >= 200,
      rsi14: rows.length >= 15,
      macd: rows.length >= 35,
      stochastic14: trailingHasOhlc(14),
      volume20: trailingHasVolume(20),
    },
  };
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

  const dayKeys = [...new Set(valid.map((entry) =>
    entry.timestamp.toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' })
  ))].sort().reverse();
  const latestDayKey = dayKeys[0];
  const previousDayKey = dayKeys[1] || null;

  const session = valid
    .filter((entry) => entry.timestamp.toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' }) === latestDayKey)
    .sort((a, b) => a.timestamp - b.timestamp)
    .map((entry) => entry.candle);

  if (!session.length) return null;

  const previousSession = previousDayKey
    ? valid
        .filter((entry) => entry.timestamp.toLocaleDateString('en-CA', { timeZone: 'Africa/Nairobi' }) === previousDayKey)
        .sort((a, b) => a.timestamp - b.timestamp)
        .map((entry) => entry.candle)
    : [];
  const previousClose = Number.isFinite(Number(previousSession[previousSession.length - 1]?.close)) && Number(previousSession[previousSession.length - 1].close) > 0
    ? Number(previousSession[previousSession.length - 1].close)
    : null;
  const previousCloseAt = candleTimestamp(previousSession[previousSession.length - 1])?.toISOString() || null;
  const close = Number.isFinite(Number(session[session.length - 1]?.close)) && Number(session[session.length - 1].close) > 0
    ? Number(session[session.length - 1].close)
    : null;

  return {
    candles: session,
    open: Number.isFinite(Number(session[0]?.open)) && Number(session[0].open) > 0
      ? Number(session[0].open)
      : null,
    close,
    openAt: candleTimestamp(session[0])?.toISOString() || null,
    closeAt: candleTimestamp(session[session.length - 1])?.toISOString() || null,
    previousClose,
    previousCloseAt,
  };
}

function providerAsOf(raw) {
  return raw?.asOf || raw?.meta?.asOf || raw?.data?.asOf || raw?.data?.meta?.asOf || null;
}

function providerMeta(raw) {
  return raw?.meta || raw?.data?.meta || null;
}

function normalizeMarketStatus(raw) {
  // /market/status returns an exchange map:
  // { anyOpen, checkedAt, serverTime, exchanges: { NSE: { isOpen, status, nextOpen, nextClose, ... } } }.
  // Keep compatibility with a direct exchange object as well.
  const exchange = raw?.exchanges?.NSE ?? raw?.data?.exchanges?.NSE ?? raw?.NSE ?? raw?.data?.NSE ?? raw;
  const rawStatus = String(
    exchange?.status ??
    exchange?.marketStatus ??
    exchange?.state ??
    ''
  ).trim().toUpperCase();

  const hasExplicitIsOpen = typeof exchange?.isOpen === 'boolean';
  const explicitIsOpen = hasExplicitIsOpen ? exchange.isOpen : null;
  const statusOpen = rawStatus === 'OPEN' || rawStatus === 'TRADING';
  const statusClosed = rawStatus === 'CLOSED' || rawStatus === 'NOT_TRADING';

  // The provider's explicit boolean and status are both authoritative when
  // present. If they conflict, surface UNKNOWN rather than inventing a state.
  if (hasExplicitIsOpen && (statusOpen || statusClosed)) {
    if ((explicitIsOpen && statusClosed) || (!explicitIsOpen && statusOpen)) {
      return { isOpen: false, status: 'UNKNOWN', isKnown: false };
    }
    return {
      isOpen: explicitIsOpen,
      status: explicitIsOpen ? 'OPEN' : 'CLOSED',
      isKnown: true,
      nextOpen: exchange?.nextOpen || null,
      nextClose: exchange?.nextClose || null,
    };
  }

  if (hasExplicitIsOpen) {
    return {
      isOpen: explicitIsOpen,
      status: explicitIsOpen ? 'OPEN' : 'CLOSED',
      isKnown: true,
      nextOpen: exchange?.nextOpen || null,
      nextClose: exchange?.nextClose || null,
    };
  }

  if (statusOpen) return { isOpen: true, status: 'OPEN', isKnown: true };
  if (statusClosed) return { isOpen: false, status: 'CLOSED', isKnown: true };

  return { isOpen: false, status: 'UNKNOWN', isKnown: false };
}

module.exports = async (req, res) => {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });
  const action = String(req.query.action || 'snapshot');
  try {
    if (action === 'status') {
      const data = await mystocks('/market/status');
      const normalized = normalizeMarketStatus(data);
      const exchangeData = data?.exchanges?.NSE ?? data?.data?.exchanges?.NSE ?? data?.NSE ?? data?.data?.NSE ?? data;
      return json(res, 200, {
        source: 'MyStocks Africa', delayMinutes: null, fetchedAt: new Date().toISOString(),
        isOpen: normalized.isOpen,
        status: normalized.status,
        isKnown: normalized.isKnown,
        nextOpen: exchangeData?.nextOpen || exchangeData?.nextSessionOpen || null,
        nextClose: exchangeData?.nextClose || exchangeData?.nextSessionClose || null,
        data,
      });
    }
    if (action === 'companies') {
      const data = await loadAllNseCompanies();
      return json(res, 200, {
        source: 'MyStocks Africa',
        fetchedAt: new Date().toISOString(),
        data
      });
    }
    if (action === 'stocks') {
      const data = await loadAllNseStocks();
      const observations = (data?.stocks || []).map(stock => stock?.lastPriceUpdate || stock?.asOf).filter(Boolean);
      const dates = observations.map(value => new Date(value)).filter(date => Number.isFinite(date.getTime()));
      const newestObservationAt = dates.length ? new Date(Math.max(...dates.map(date => date.getTime()))).toISOString() : null;
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: MARKET_DATA_DELAY_MINUTES, refreshIntervalSeconds: MARKET_DATA_REFRESH_SECONDS, fetchedAt: new Date().toISOString(), newestObservationAt, data });
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
      return json(res, 200, { source: 'MyStocks Africa', delayMinutes: MARKET_DATA_DELAY_MINUTES, refreshIntervalSeconds: MARKET_DATA_REFRESH_SECONDS, fetchedAt: new Date().toISOString(), gainers, losers });
    }
    if (action === 'technical-history') {
      const symbol = String(req.query.symbol || '').trim();
      if (!symbol) return json(res, 400, { error: 'symbol is required' });

      const cfg = technicalHistoryConfig(new Date(), req.query.lookbackDays);
      const data = await mystocks(
        `/stocks/${encodeURIComponent(symbol)}/candles?interval=${cfg.interval}&from=${cfg.from}&to=${cfg.to}`
      );
      const candles = normalizeTechnicalCandles(candleArray(data));
      const meta = providerMeta(data);
      const latest = candles[candles.length - 1];
      const latestObservationAt = meta?.asOf || latest?.timestamp || latest?.date || providerAsOf(data) || null;

      return json(res, 200, {
        source: 'MyStocks Africa',
        delayMinutes: MARKET_DATA_DELAY_MINUTES,
        refreshIntervalSeconds: MARKET_DATA_REFRESH_SECONDS,
        fetchedAt: new Date().toISOString(),
        symbol,
        purpose: 'technical-analysis',
        interval: cfg.interval,
        from: cfg.from,
        to: cfg.to,
        lookbackDays: cfg.lookbackDays,
        latestObservationAt,
        dataQuality: technicalDataQuality(candles, meta),
        candles,
      });
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
      let previousSessionClose = null;
      let previousSessionCloseAt = null;

      if (period === '1d') {
        try {
          const session = latestTradingSession(candleArray(data));
          if (session) {
            sessionOpen = session.open;
            sessionClose = session.close;
            sessionOpenAt = session.openAt;
            sessionCloseAt = session.closeAt;
            previousSessionClose = session.previousClose;
            previousSessionCloseAt = session.previousCloseAt;

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
        source: 'MyStocks Africa', delayMinutes: MARKET_DATA_DELAY_MINUTES, refreshIntervalSeconds: MARKET_DATA_REFRESH_SECONDS, fetchedAt: new Date().toISOString(),
        symbol, period, interval: cfg.interval, asOf: chartAsOf, latestObservationAt,
        session: period === '1d' ? {
          open: Number.isFinite(sessionOpen) ? sessionOpen : null,
          close: Number.isFinite(sessionClose) ? sessionClose : null,
          changePct: Number.isFinite(sessionChangePct) ? sessionChangePct : null,
          openAt: sessionOpenAt,
          closeAt: sessionCloseAt,
          previousClose: Number.isFinite(previousSessionClose) ? previousSessionClose : null,
          previousCloseAt: previousSessionCloseAt,
          dailyChangePct: Number.isFinite(previousSessionClose) && previousSessionClose > 0 && Number.isFinite(sessionClose)
            ? ((sessionClose - previousSessionClose) / previousSessionClose) * 100
            : null,
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
    return json(res, 200, { source: 'MyStocks Africa', delayMinutes: MARKET_DATA_DELAY_MINUTES, refreshIntervalSeconds: MARKET_DATA_REFRESH_SECONDS, fetchedAt: new Date().toISOString(), data });
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
module.exports.technicalHistoryConfig = technicalHistoryConfig;
module.exports.normalizeTechnicalCandles = normalizeTechnicalCandles;
module.exports.technicalDataQuality = technicalDataQuality;
