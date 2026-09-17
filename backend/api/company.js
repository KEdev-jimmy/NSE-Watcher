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

function symbolFor(raw) {
  const upper = String(raw || '').trim().toUpperCase();
  return upper.includes('.') ? upper : `${upper}.KE`;
}

function firstObject(value) {
  if (!value || typeof value !== 'object') return null;
  if (Array.isArray(value)) return value.find(item => item && typeof item === 'object') || null;
  return value;
}

function scalar(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (typeof value === 'string') return value.trim();
  return '';
}

function normalizedKey(value) {
  return String(value || '').replace(/[^a-z0-9]/gi, '').toLowerCase();
}

function pick(obj, keys) {
  if (!obj || typeof obj !== 'object') return '';
  const wanted = new Set(keys.map(normalizedKey));
  for (const [key, value] of Object.entries(obj)) {
    if (wanted.has(normalizedKey(key))) {
      const text = scalar(value);
      if (text) return text;
    }
  }
  return '';
}

function findFinancialArrays(value, found = []) {
  if (!value || typeof value !== 'object' || found.length >= 3) return found;
  if (Array.isArray(value)) {
    const objects = value.filter(item => item && typeof item === 'object' && !Array.isArray(item));
    if (objects.length >= 2) {
      const hasPeriod = objects.some(item => pick(item, ['period', 'fiscalPeriod', 'fiscalYear', 'year', 'date', 'asOf', 'reportDate']));
      const hasMetric = objects.some(item => pick(item, [
        'revenue', 'totalRevenue', 'profit', 'netIncome', 'netProfit',
        'eps', 'earningsPerShare', 'roe', 'returnOnEquity', 'margin', 'profitMargin',
        'debtToEquity', 'pe', 'peRatio', 'pb', 'pbRatio'
      ]));
      if (hasPeriod && hasMetric) found.push(objects);
    }
    for (const item of value) findFinancialArrays(item, found);
    return found;
  }
  for (const child of Object.values(value)) findFinancialArrays(child, found);
  return found;
}

function normalizeFinancialHistory(profile) {
  const arrays = findFinancialArrays(profile);
  const rows = arrays.flat().map((item) => {
    const period = pick(item, ['period', 'fiscalPeriod', 'fiscalYear', 'year', 'date', 'asOf', 'reportDate']);
    return {
      period,
      revenue: pick(item, ['revenue', 'totalRevenue']),
      profit: pick(item, ['profit', 'netIncome', 'netProfit', 'profitAfterTax']),
      eps: pick(item, ['eps', 'earningsPerShare']),
      roe: pick(item, ['roe', 'returnOnEquity']),
      margin: pick(item, ['netMargin', 'profitMargin', 'margin']),
      debtToEquity: pick(item, ['debtToEquity', 'debtEquity', 'debtToEquityRatio']),
      pe: pick(item, ['pe', 'peRatio', 'priceEarnings', 'priceToEarnings']),
      pb: pick(item, ['pb', 'pbRatio', 'priceBook', 'priceToBook']),
      source: 'MyStocks Africa',
    };
  }).filter(row => row.period || row.revenue || row.profit || row.eps);

  const unique = new Map();
  for (const row of rows) {
    const key = `${row.period}|${row.revenue}|${row.profit}|${row.eps}`;
    if (!unique.has(key)) unique.set(key, row);
  }
  return Array.from(unique.values()).slice(-12);
}

function buildEvidence(profile, dividends, financialHistory, fetchedAt, symbol) {
  const evidence = [];
  const add = (claim, value, endpoint) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      evidence.push({
        claim,
        value: String(value),
        source: 'MyStocks Africa',
        endpoint,
        symbol,
        fetchedAt,
      });
    }
  };

  add('Company description', pick(profile, ['description', 'businessDescription', 'companyDescription']), `/companies/${symbol}`);
  add('Sector', pick(profile, ['sector', 'industry']), `/companies/${symbol}`);
  add('Revenue', pick(profile, ['revenue', 'totalRevenue']), `/companies/${symbol}`);
  add('Profit', pick(profile, ['profit', 'netIncome', 'netProfit', 'profitAfterTax']), `/companies/${symbol}`);
  add('EPS', pick(profile, ['eps', 'earningsPerShare']), `/companies/${symbol}`);
  add('ROE', pick(profile, ['roe', 'returnOnEquity']), `/companies/${symbol}`);
  add('Debt / equity', pick(profile, ['debtToEquity', 'debtEquity', 'debtToEquityRatio']), `/companies/${symbol}`);
  add('Net margin', pick(profile, ['netMargin', 'profitMargin', 'margin']), `/companies/${symbol}`);
  add('P/E', pick(profile, ['pe', 'peRatio', 'priceEarnings', 'priceToEarnings']), `/companies/${symbol}`);
  add('P/B', pick(profile, ['pb', 'pbRatio', 'priceBook', 'priceToBook']), `/companies/${symbol}`);

  financialHistory.forEach((row) => {
    const period = row.period || 'reported period';
    add(`Revenue for ${period}`, row.revenue, `/companies/${symbol}`);
    add(`Profit for ${period}`, row.profit, `/companies/${symbol}`);
    add(`EPS for ${period}`, row.eps, `/companies/${symbol}`);
    add(`ROE for ${period}`, row.roe, `/companies/${symbol}`);
  });

  dividends.slice(0, 12).forEach((dividend, index) => {
    const amount = pick(dividend, ['amount', 'dividend', 'dividendAmount', 'value']);
    const date = pick(dividend, ['paymentDate', 'payDate', 'exDate', 'exDividendDate', 'bookClosureDate']);
    add(`Dividend ${index + 1}`, [amount, date].filter(Boolean).join(' • '), `/dividends/${symbol}/history`);
  });

  return evidence;
}

module.exports = async (req, res) => {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });

  const action = String(req.query.action || 'intelligence');
  const rawSymbol = String(req.query.symbol || '').trim().toUpperCase();
  if (!rawSymbol) return json(res, 400, { error: 'symbol is required' });

  const symbol = symbolFor(rawSymbol);

  try {
    if (action === 'profile') {
      const profile = await mystocks(`/companies/${encodeURIComponent(symbol)}`);
      return json(res, 200, {
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt: new Date().toISOString(),
        profile,
        financialHistory: normalizeFinancialHistory(profile),
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

    const profile = profileResponse.status === 'fulfilled' ? firstObject(profileResponse.value) : null;
    const dividends = dividendResponse.status === 'fulfilled'
      ? (dividendResponse.value.history || dividendResponse.value.data || [])
      : [];
    const fetchedAt = new Date().toISOString();
    const financialHistory = profile ? normalizeFinancialHistory(profile) : [];
    const evidence = buildEvidence(profile || {}, Array.isArray(dividends) ? dividends : [], financialHistory, fetchedAt, symbol);

    if (!profile && !dividends.length) {
      const firstError = profileResponse.status === 'rejected' ? profileResponse.reason : dividendResponse.reason;
      return json(res, firstError?.status || 502, {
        error: 'Company intelligence unavailable',
        detail: firstError?.message || 'No company data returned',
        source: 'MyStocks Africa',
        delayMinutes: 15,
        fetchedAt,
      });
    }

    return json(res, 200, {
      source: 'MyStocks Africa',
      delayMinutes: 15,
      fetchedAt,
      symbol,
      profile,
      dividends,
      financialHistory,
      evidence,
      dataQuality: {
        profileAvailable: Boolean(profile),
        dividendHistoryAvailable: Array.isArray(dividends) && dividends.length > 0,
        financialHistoryAvailable: financialHistory.length > 0,
        evidenceCount: evidence.length,
      },
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
