const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_API_KEY;
const STOCK_ANALYSIS_BASE = 'https://stockanalysis.com/quote/nase';
const CACHE_CONTROL = 's-maxage=600, stale-while-revalidate=1800';

const OFFICIAL_IR = {
  SCOM: 'https://www.safaricom.co.ke/investor-relations-landing/meetings',
  EQTY: 'https://equitygroupholdings.com/investor-relations/',
  KCB: 'https://www.kcbgroup.com/investor-relations',
  ABSA: 'https://www.absabank.co.ke/investor-relations/',
  EABL: 'https://www.eabl.com/investors/announcements',
  CIC: 'https://www.cicinsurancegroup.com/investor-relations/',
};

const EXTERNAL_SOURCE = 'StockAnalysis / S&P Global Market Intelligence';

function json(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', CACHE_CONTROL);
  res.end(JSON.stringify(body));
}

function symbolFor(raw) {
  const symbol = String(raw || '').trim().toUpperCase();
  return symbol.includes('.') ? symbol : `${symbol}.KE`;
}

function bareSymbol(raw) {
  return String(raw || '').trim().toUpperCase().replace(/\.KE$/, '');
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

async function fetchHtml(url) {
  const response = await fetch(url, {
    headers: {
      Accept: 'text/html,application/xhtml+xml',
      'User-Agent': 'NSE-Watcher/1.2 (+https://github.com/KEdev-jimmy/NSE-Watcher)',
    },
  });
  const text = await response.text();
  if (!response.ok) {
    const error = new Error(`Source ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return text;
}

function decodeEntities(value) {
  return String(value || '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)));
}

function cleanText(html) {
  return decodeEntities(String(html || '')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim();
}

function parseTables(html) {
  const tables = [];
  for (const tableMatch of String(html || '').matchAll(/<table\b[\s\S]*?<\/table>/gi)) {
    const rows = [];
    for (const rowMatch of tableMatch[0].matchAll(/<tr\b[\s\S]*?<\/tr>/gi)) {
      const cells = [];
      for (const cellMatch of rowMatch[0].matchAll(/<(?:th|td)\b[^>]*>([\s\S]*?)<\/(?:th|td)>/gi)) {
        cells.push(cleanText(cellMatch[1]));
      }
      if (cells.length) rows.push(cells);
    }
    if (rows.length) tables.push(rows);
  }
  return tables;
}

function findRow(tables, patterns) {
  const regexes = patterns.map(pattern => pattern instanceof RegExp
    ? pattern
    : new RegExp(`^${pattern}$`, 'i'));
  for (const table of tables) {
    for (const row of table) {
      const label = String(row[0] || '').replace(/\s+/g, ' ').trim();
      if (regexes.some(regex => regex.test(label))) return row;
    }
  }
  return null;
}

function valueFromRow(row, index = 1) {
  return row ? String(row[index] || '').trim() : '';
}

function normalizePeriod(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  return text && !/^-$/i.test(text) ? text : '';
}

function numericValue(value) {
  const number = Number(String(value || '').replace(/,/g, '').replace(/[^0-9.-]/g, ''));
  return Number.isFinite(number) ? number : null;
}

function pctGrowth(current, previous) {
  const a = numericValue(current);
  const b = numericValue(previous);
  if (a === null || b === null || b === 0) return '';
  return `${(((a - b) / Math.abs(b)) * 100).toFixed(2)}%`;
}

function normalizeMarketCap(value) {
  const text = String(value || '').trim();
  if (!text) return '';
  const numeric = numericValue(text);
  if (numeric === null) return text;
  const upper = text.toUpperCase();
  if (/\bTRILLION\b|\bT\b/.test(upper)) return String(Math.round(numeric * 1e12));
  if (/\bBILLION\b|\bB\b/.test(upper)) return String(Math.round(numeric * 1e9));
  if (/\bMILLION\b|\bM\b/.test(upper)) return String(Math.round(numeric * 1e6));
  // StockAnalysis NASE ratios report market capitalization in millions of KES.
  return String(Math.round(numeric * 1e6));
}

function parseProviderDate(html, label) {
  const match = cleanText(html).match(new RegExp(`\\b${label}\\s*:\\s*([A-Z][a-z]{2} \\d{1,2}, \\d{4})\\b`, 'i'));
  if (!match) return '';
  const parsed = new Date(match[1]);
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString().slice(0, 10);
}

function parseFinancials(html) {
  const tables = parseTables(html);
  const providerUpdatedAt = parseProviderDate(html, 'Last updated');
  const pageCheckedAt = parseProviderDate(html, 'Last checked');
  const revenue = findRow(tables, [/^Revenue(?:\s+Revenue Growth)?$/i, /^Revenue$/i]);
  const revenueGrowth = findRow(tables, [/^Revenue Growth$/i]);
  const netIncome = findRow(tables, [/^Net Income(?:\s+Net Income Growth)?$/i, /^Net Income$/i, /^Net Profit$/i]);
  const netIncomeGrowth = findRow(tables, [/^Net Income Growth$/i]);
  const eps = findRow(tables, [/^Earnings Per Share(?:\s+EPS Growth)?$/i, /^EPS(?:\s+EPS Growth)?$/i]);
  const epsGrowth = findRow(tables, [/^EPS Growth$/i]);
  const margin = findRow(tables, [/^Profit Margin$/i, /^Net Margin$/i]);
  const periods = findRow(tables, [/^Period Ending$/i]);
  const fiscalYears = findRow(tables, [/^Fiscal Year$/i]);

  // StockAnalysis puts TTM before FY columns on some pages. The Company Intelligence
  // screen is explicitly an annual/FY view, so select the first column labelled FY
  // instead of assuming column 1 or 2.
  const fiscalYearIndex = (fiscalYears || []).findIndex((value, index) =>
    index > 0 && /^FY\s+\d{4}$/i.test(String(value || '').trim())
  );
  const annualIndex = fiscalYearIndex > 0
    ? fiscalYearIndex
    : Math.max(1, (periods || []).length > 1 ? 1 : 0);

  const fiscalYearLabel = normalizePeriod(valueFromRow(fiscalYears, annualIndex));
  const periodEndingLabel = normalizePeriod(valueFromRow(periods, annualIndex));
  const latestPeriod = [fiscalYearLabel, periodEndingLabel]
    .filter(Boolean)
    .join(' • ') || 'Latest reported annual period';
  const latestRevenue = valueFromRow(revenue, annualIndex) || valueFromRow(revenue);
  const latestProfit = valueFromRow(netIncome, annualIndex) || valueFromRow(netIncome);
  const latestEps = valueFromRow(eps, annualIndex) || valueFromRow(eps);
  const previousRevenue = valueFromRow(revenue, annualIndex + 1);
  const previousProfit = valueFromRow(netIncome, annualIndex + 1);
  const previousEps = valueFromRow(eps, annualIndex + 1);

  // Growth is deliberately calculated from the annual FY values. This avoids
  // accidentally exposing TTM growth when the profile is showing FY data.
  const latest = {
    period: latestPeriod || 'Latest reported',
    revenue: latestRevenue,
    profit: latestProfit,
    eps: latestEps,
    margin: valueFromRow(margin, annualIndex) || valueFromRow(margin),
    revenueGrowth: valueFromRow(revenueGrowth, annualIndex) || pctGrowth(latestRevenue, previousRevenue),
    profitGrowth: valueFromRow(netIncomeGrowth, annualIndex) || pctGrowth(latestProfit, previousProfit),
    epsGrowth: valueFromRow(epsGrowth, annualIndex) || pctGrowth(latestEps, previousEps),
    source: EXTERNAL_SOURCE,
    providerUpdatedAt,
    pageCheckedAt,
  };

  const history = [];
  const max = Math.max(
    revenue?.length || 0,
    netIncome?.length || 0,
    eps?.length || 0,
    periods?.length || 0,
    fiscalYears?.length || 0
  );

  for (let i = annualIndex; i < max; i += 1) {
    const fiscalYear = normalizePeriod(valueFromRow(fiscalYears, i));
    const periodEnding = normalizePeriod(valueFromRow(periods, i));
    const period = [fiscalYear, periodEnding].filter(Boolean).join(' • ');
    if (!period) continue;
    const previous = i + 1;
    history.push({
      period,
      revenue: valueFromRow(revenue, i),
      profit: valueFromRow(netIncome, i),
      eps: valueFromRow(eps, i),
      margin: valueFromRow(margin, i),
      revenueGrowth: pctGrowth(valueFromRow(revenue, i), valueFromRow(revenue, previous)),
      profitGrowth: pctGrowth(valueFromRow(netIncome, i), valueFromRow(netIncome, previous)),
      epsGrowth: pctGrowth(valueFromRow(eps, i), valueFromRow(eps, previous)),
      source: EXTERNAL_SOURCE,
      providerUpdatedAt,
      pageCheckedAt,
    });
  }

  return {
    profile: {
      revenue: latest.revenue,
      profit: latest.profit,
      eps: latest.eps,
      margin: latest.margin,
      financialUnit: 'Millions KES',
      financialPeriod: latest.period,
      revenueGrowth: latest.revenueGrowth,
      profitGrowth: latest.profitGrowth,
      epsGrowth: latest.epsGrowth,
      financialProviderUpdatedAt: providerUpdatedAt,
      financialPageCheckedAt: pageCheckedAt,
    },
    financialHistory: [latest, ...history]
      .filter((row, index, all) => row.period && all.findIndex(item => item.period === row.period) === index)
      .slice(0, 8)
      .reverse(),
  };
}

function parseRatios(html) {
  const tables = parseTables(html);
  const providerUpdatedAt = parseProviderDate(html, 'Last updated');
  const pageCheckedAt = parseProviderDate(html, 'Last checked');
  const fiscalYears = findRow(tables, [/^Fiscal Year$/i]);
  const periods = findRow(tables, [/^Period Ending$/i]);
  const currentIndex = (fiscalYears || []).findIndex((value, index) =>
    index > 0 && /^Current$/i.test(String(value || '').trim())
  );

  // StockAnalysis ratio tables explicitly separate the Current snapshot from
  // historical FY columns. These ratios are used as current market/ratio
  // metrics in Company Intelligence, so never select a historical FY column
  // merely because it happens to be first.
  if (currentIndex <= 0) {
    return {
      marketCap: '',
      pe: '',
      pb: '',
      debtToEquity: '',
      roe: '',
      dividendYield: '',
      ratioBasis: 'UNKNOWN',
      ratioPeriod: '',
      ratioProviderUpdatedAt: providerUpdatedAt,
      ratioPageCheckedAt: pageCheckedAt,
    };
  }

  const marketCap = findRow(tables, [/^Market Capitalization$/i]);
  const pe = findRow(tables, [/^PE Ratio$/i]);
  const pb = findRow(tables, [/^PB Ratio$/i]);
  const debt = findRow(tables, [/^Debt \/ Equity Ratio$/i, /^Debt\/Equity Ratio$/i]);
  const roe = findRow(tables, [/^Return on Equity \(ROE\)$/i, /^ROE$/i]);
  const dividendYield = findRow(tables, [/^Dividend Yield$/i]);
  return {
    marketCap: normalizeMarketCap(valueFromRow(marketCap, currentIndex)),
    pe: valueFromRow(pe, currentIndex),
    pb: valueFromRow(pb, currentIndex),
    debtToEquity: valueFromRow(debt, currentIndex),
    roe: valueFromRow(roe, currentIndex),
    dividendYield: valueFromRow(dividendYield, currentIndex),
    ratioBasis: 'Current',
    ratioPeriod: normalizePeriod(valueFromRow(periods, currentIndex)),
    ratioProviderUpdatedAt: providerUpdatedAt,
    ratioPageCheckedAt: pageCheckedAt,
  };
}

function parseDividends(html) {
  const tables = parseTables(html);
  let best = [];

  for (const table of tables) {
    const headerIndex = table.findIndex(row => {
      const headers = row.map(value => value.toLowerCase().replace(/\s+/g, ' ').trim());
      const hasExDate = headers.some(value => /ex[- ]?(?:dividend|div)[ -]?date/.test(value) || value === 'ex-date');
      const hasAmount = headers.some(value => /^(amount|dividend|dividend amount|dividend \(kes\)|cash dividend)/.test(value));
      return hasExDate && hasAmount;
    });
    if (headerIndex < 0) continue;

    const header = table[headerIndex].map(value => value.toLowerCase().replace(/\s+/g, ' ').trim());
    const exIndex = header.findIndex(value => /ex[- ]?(?:dividend|div)[ -]?date/.test(value) || value === 'ex-date');
    const amountIndex = header.findIndex(value => /^(amount|dividend|dividend amount|dividend \(kes\)|cash dividend)/.test(value));
    const payIndex = header.findIndex(value => value.includes('pay date') || value.includes('payment date'));

    const rows = table.slice(headerIndex + 1)
      .map(row => ({
        amount: String(row[amountIndex] || '').trim(),
        exDate: String(row[exIndex] || '').trim(),
        paymentDate: payIndex >= 0 ? String(row[payIndex] || '').trim() : '',
      }))
      .filter(row => row.amount && row.exDate && numericValue(row.amount) !== null);

    if (rows.length > best.length) best = rows;
  }

  return best.slice(0, 12).map(row => ({
    amount: row.amount,
    exDate: row.exDate,
    paymentDate: row.paymentDate,
    declaredDate: '',
    type: 'Dividend',
    status: 'Historical',
    source: EXTERNAL_SOURCE,
  }));
}

function normalizeMyStocksProfile(value) {
  if (!value || typeof value !== 'object') return {};
  if (Array.isArray(value)) return value.find(item => item && typeof item === 'object') || {};
  if (value.data && typeof value.data === 'object' && !Array.isArray(value.data)) return value.data;
  return value;
}

function normalizeWebsite(value) {
  const text = String(value || '').trim();
  if (!text) return '';
  const candidate = /^https?:\/\//i.test(text) ? text : `https://${text}`;
  try {
    const url = new URL(candidate);
    if (!/^https?:$/i.test(url.protocol)) return '';
    return url.toString().replace(/\/$/, '');
  } catch {
    return '';
  }
}

function normalizeLocation(value) {
  return String(value || '')
    .replace(/\s+/g, ' ')
    .replace(/^\s*(?:headquarters?|hq|location)\s*[:\-]\s*/i, '')
    .trim();
}

function pick(obj, keys) {
  const wanted = new Set(keys.map(key => String(key).replace(/[^a-z0-9]/gi, '').toLowerCase()));
  function walk(value) {
    if (!value || typeof value !== 'object') return '';
    if (Array.isArray(value)) {
      for (const item of value) {
        const found = walk(item);
        if (found) return found;
      }
      return '';
    }
    for (const [key, child] of Object.entries(value)) {
      const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
      if (wanted.has(normalized) && (typeof child === 'string' || typeof child === 'number')) return String(child);
      const found = walk(child);
      if (found) return found;
    }
    return '';
  }
  return walk(obj);
}

async function loadMyStocks(symbol) {
  const encoded = encodeURIComponent(symbol);
  const [profileResult, dividendsResult, newsResult] = await Promise.allSettled([
    mystocks(`/companies/${encoded}`),
    mystocks(`/dividends/${encoded}/history?limit=12`),
    mystocks(`/companies/${encoded}/news?limit=20`),
  ]);

  const profileRaw = profileResult.status === 'fulfilled' ? normalizeMyStocksProfile(profileResult.value) : {};
  const dividendsRaw = dividendsResult.status === 'fulfilled' ? dividendsResult.value : {};
  const newsRaw = newsResult.status === 'fulfilled' ? newsResult.value : {};

  return {
    profile: {
      description: pick(profileRaw, ['description', 'businessDescription', 'companyDescription']),
      sector: pick(profileRaw, ['sector', 'industry']),
      headquarters: normalizeLocation(pick(profileRaw, ['headquarters', 'hq', 'location'])),
      website: normalizeWebsite(pick(profileRaw, ['website', 'websiteUrl', 'url'])),
      marketCap: pick(profileRaw, ['marketCap', 'marketCapitalisation', 'marketCapitalization']),
      revenue: pick(profileRaw, ['revenue', 'totalRevenue']),
      profit: pick(profileRaw, ['profit', 'netIncome', 'netProfit', 'profitAfterTax']),
      eps: pick(profileRaw, ['eps', 'earningsPerShare']),
      roe: pick(profileRaw, ['roe', 'returnOnEquity']),
      debtToEquity: pick(profileRaw, ['debtToEquity', 'debtEquity', 'debtToEquityRatio']),
      margin: pick(profileRaw, ['netMargin', 'profitMargin', 'margin']),
      revenueGrowth: pick(profileRaw, ['revenueGrowth', 'revenueGrowthRate']),
      profitGrowth: pick(profileRaw, ['profitGrowth', 'netIncomeGrowth', 'profitGrowthRate']),
      pe: pick(profileRaw, ['pe', 'peRatio', 'priceEarnings', 'priceToEarnings']),
      pb: pick(profileRaw, ['pb', 'pbRatio', 'priceBook', 'priceToBook']),
      dividendYield: pick(profileRaw, ['dividendYield', 'yield']),
      ratioBasis: pick(profileRaw, ['ratioBasis']),
      ratioPeriod: pick(profileRaw, ['ratioPeriod']),
    },
    dividends: Array.isArray(dividendsRaw.history)
      ? dividendsRaw.history
      : (Array.isArray(dividendsRaw.data) ? dividendsRaw.data : (Array.isArray(dividendsRaw.dividends) ? dividendsRaw.dividends : [])),
    news: Array.isArray(newsRaw.items)
      ? newsRaw.items
      : (Array.isArray(newsRaw.news) ? newsRaw.news : (Array.isArray(newsRaw.data) ? newsRaw.data : [])),
    providerStatus: {
      profile: profileResult.status,
      dividends: dividendsResult.status,
      news: newsResult.status,
    },
  };
}

async function loadStockAnalysis(symbol) {
  const bare = bareSymbol(symbol);
  const urls = {
    financials: `${STOCK_ANALYSIS_BASE}/${bare}/financials/`,
    ratios: `${STOCK_ANALYSIS_BASE}/${bare}/financials/ratios/`,
    dividends: `${STOCK_ANALYSIS_BASE}/${bare}/dividend/`,
  };

  const [financialsResult, ratiosResult, dividendsResult] = await Promise.allSettled([
    fetchHtml(urls.financials),
    fetchHtml(urls.ratios),
    fetchHtml(urls.dividends),
  ]);

  const financials = financialsResult.status === 'fulfilled'
    ? parseFinancials(financialsResult.value)
    : { profile: {}, financialHistory: [] };
  const ratios = ratiosResult.status === 'fulfilled' ? parseRatios(ratiosResult.value) : {};
  const dividends = dividendsResult.status === 'fulfilled' ? parseDividends(dividendsResult.value) : [];

  return {
    profile: { ...financials.profile, ...ratios },
    financialHistory: financials.financialHistory,
    dividends,
    urls,
    providerStatus: {
      financials: financialsResult.status,
      ratios: ratiosResult.status,
      dividends: dividendsResult.status,
    },
  };
}

function sourceUrls(symbol) {
  const bare = bareSymbol(symbol);
  return {
    officialUrl: OFFICIAL_IR[bare] || '',
    financialsUrl: `${STOCK_ANALYSIS_BASE}/${bare}/financials/`,
    ratiosUrl: `${STOCK_ANALYSIS_BASE}/${bare}/financials/ratios/`,
    dividendsUrl: `${STOCK_ANALYSIS_BASE}/${bare}/dividend/`,
  };
}

function mergeProfile(primary, external) {
  const output = { ...(primary || {}) };
  const keys = [
    'marketCap', 'revenue', 'profit', 'eps', 'financialUnit', 'financialPeriod', 'roe', 'debtToEquity', 'margin',
    'revenueGrowth', 'profitGrowth', 'pe', 'pb', 'dividendYield', 'ratioBasis', 'ratioPeriod',
    'financialProviderUpdatedAt', 'financialPageCheckedAt', 'ratioProviderUpdatedAt', 'ratioPageCheckedAt',
  ];
  for (const key of keys) {
    if (String(external?.[key] || '').trim()) output[key] = external[key];
  }
  return output;
}

function normalizedComparable(value) {
  const text = String(value || '').trim();
  if (!text) return '';
  const numeric = numericValue(text);
  if (numeric !== null) return String(numeric);
  return text.toLowerCase().replace(/\s+/g, ' ');
}

function buildFieldQuality(primary, external) {
  const fields = [
    'marketCap', 'revenue', 'profit', 'eps', 'roe', 'debtToEquity', 'margin',
    'revenueGrowth', 'profitGrowth', 'pe', 'pb', 'dividendYield'
  ];
  const fieldSources = {};
  const fieldQuality = {};
  const conflicts = {};

  for (const field of fields) {
    const primaryValue = String(primary?.[field] || '').trim();
    const externalValue = String(external?.[field] || '').trim();
    const sources = [];
    if (primaryValue) sources.push('MyStocks Africa');
    if (externalValue) sources.push(EXTERNAL_SOURCE);
    const conflict = Boolean(primaryValue && externalValue &&
      normalizedComparable(primaryValue) !== normalizedComparable(externalValue));

    fieldSources[field] = sources;
    fieldQuality[field] = conflict ? 'CONFLICT' : (sources.length ? 'AVAILABLE' : 'UNAVAILABLE');
    if (conflict) {
      conflicts[field] = {
        status: 'CONFLICT',
        values: [
          { source: 'MyStocks Africa', value: primaryValue },
          { source: EXTERNAL_SOURCE, value: externalValue }
        ]
      };
    }
  }
  return { fieldSources, fieldQuality, conflicts };
}

function evidenceFor(profile, primaryProfile, externalProfile, financialHistory, dividends, sourceInfo, fetchedAt, symbol) {
  const evidence = [];
  const add = (claim, value, source, endpoint, url, metadata = {}) => {
    if (String(value || '').trim()) evidence.push({
      claim, value: String(value), source, endpoint, url, symbol, fetchedAt, ...metadata,
    });
  };

  const fieldEvidence = [
    ['Revenue', 'revenue', 'financials', sourceInfo.financialsUrl],
    ['Profit', 'profit', 'financials', sourceInfo.financialsUrl],
    ['EPS', 'eps', 'financials', sourceInfo.financialsUrl],
    ['Revenue growth', 'revenueGrowth', 'financials', sourceInfo.financialsUrl],
    ['Profit growth', 'profitGrowth', 'financials', sourceInfo.financialsUrl],
    ['EPS growth', 'epsGrowth', 'financials', sourceInfo.financialsUrl],
    ['Net margin', 'margin', 'financials', sourceInfo.financialsUrl],
    ['P/E', 'pe', 'ratios', sourceInfo.ratiosUrl],
    ['P/B', 'pb', 'ratios', sourceInfo.ratiosUrl],
    ['ROE', 'roe', 'ratios', sourceInfo.ratiosUrl],
    ['Debt / equity', 'debtToEquity', 'ratios', sourceInfo.ratiosUrl],
    ['Dividend yield', 'dividendYield', 'ratios', sourceInfo.ratiosUrl],
    ['Market capitalization', 'marketCap', 'ratios', sourceInfo.ratiosUrl],
  ];
  fieldEvidence.forEach(([claim, key, endpoint, url]) => {
    const primaryValue = String(primaryProfile?.[key] || '').trim();
    const externalValue = String(externalProfile?.[key] || '').trim();
    if (primaryValue && externalValue && normalizedComparable(primaryValue) !== normalizedComparable(externalValue)) {
      add(claim, `MyStocks Africa: ${primaryValue} | ${EXTERNAL_SOURCE}: ${externalValue}`, 'CONFLICT', endpoint, url, {
        providerUpdatedAt: externalProfile?.[endpoint === 'ratios' ? 'ratioProviderUpdatedAt' : 'financialProviderUpdatedAt'] || '',
        providerCheckedAt: externalProfile?.[endpoint === 'ratios' ? 'ratioPageCheckedAt' : 'financialPageCheckedAt'] || '',
      });
    } else if (externalValue) {
      add(claim, externalValue, EXTERNAL_SOURCE, endpoint, url, {
        providerUpdatedAt: externalProfile?.[endpoint === 'ratios' ? 'ratioProviderUpdatedAt' : 'financialProviderUpdatedAt'] || '',
        providerCheckedAt: externalProfile?.[endpoint === 'ratios' ? 'ratioPageCheckedAt' : 'financialPageCheckedAt'] || '',
        period: endpoint === 'ratios' ? (externalProfile?.ratioPeriod || '') : (externalProfile?.financialPeriod || ''),
      });
    } else if (primaryValue) {
      add(claim, primaryValue, 'MyStocks Africa', endpoint, '');
    }
  });

  // Financial history is stored oldest -> latest. Keep the latest eight rows in evidence.
  financialHistory.slice(-8).forEach(row => {
    const metadata = {
      providerUpdatedAt: row.providerUpdatedAt || externalProfile?.financialProviderUpdatedAt || '',
      providerCheckedAt: row.pageCheckedAt || externalProfile?.financialPageCheckedAt || '',
      period: row.period || '',
    };
    add(`Revenue ${row.period}`, row.revenue, EXTERNAL_SOURCE, 'financials', sourceInfo.financialsUrl, metadata);
    add(`Profit ${row.period}`, row.profit, EXTERNAL_SOURCE, 'financials', sourceInfo.financialsUrl, metadata);
    add(`EPS ${row.period}`, row.eps, EXTERNAL_SOURCE, 'financials', sourceInfo.financialsUrl, metadata);
  });

  dividends.slice(0, 12).forEach((dividend, index) => {
    add(`Dividend ${index + 1}`,
      `${dividend.amount || ''} • ${dividend.exDate || ''}${dividend.paymentDate ? ` • paid ${dividend.paymentDate}` : ''}`,
      dividend.source || EXTERNAL_SOURCE, 'dividend-history', sourceInfo.dividendsUrl);
  });

  if (sourceInfo.officialUrl) {
    add('Official investor relations source', sourceInfo.officialUrl, 'Company investor relations', 'investor-relations', sourceInfo.officialUrl);
  }
  return evidence;
}

async function buildIntelligence(rawSymbol) {
  const symbol = symbolFor(rawSymbol);
  const fetchedAt = new Date().toISOString();
  const [myStocksResult, stockAnalysisResult] = await Promise.allSettled([
    loadMyStocks(symbol),
    loadStockAnalysis(symbol),
  ]);

  const myStocks = myStocksResult.status === 'fulfilled'
    ? myStocksResult.value
    : { profile: {}, dividends: [], news: [], providerStatus: { error: myStocksResult.reason?.message || 'failed' } };
  const external = stockAnalysisResult.status === 'fulfilled'
    ? stockAnalysisResult.value
    : { profile: {}, financialHistory: [], dividends: [], urls: sourceUrls(symbol), providerStatus: { error: stockAnalysisResult.reason?.message || 'failed' } };

  const mergedProfile = mergeProfile(myStocks.profile, external.profile);
  const fieldQuality = buildFieldQuality(myStocks.profile, external.profile);
  const dividends = myStocks.dividends.length ? myStocks.dividends : external.dividends;
  const financialHistory = external.financialHistory;
  const urls = sourceUrls(symbol);
  const evidence = evidenceFor(mergedProfile, myStocks.profile, external.profile, financialHistory, dividends, urls, fetchedAt, symbol);
  const errors = [];

  if (myStocksResult.status === 'rejected') errors.push(`MyStocks: ${myStocksResult.reason?.message || 'request failed'}`);
  if (stockAnalysisResult.status === 'rejected') errors.push(`StockAnalysis: ${stockAnalysisResult.reason?.message || 'request failed'}`);

  return {
    source: 'NSE Watcher multi-source company intelligence',
    symbol,
    fetchedAt,
    profile: mergedProfile,
    dividends,
    financialHistory,
    news: myStocks.news,
    evidence,
    sources: {
      primary: { name: 'MyStocks Africa', type: 'market-data-api' },
      fundamentals: {
        name: EXTERNAL_SOURCE,
        type: 'structured-financial-data',
        urls: external.urls || urls,
      },
      issuer: urls.officialUrl
        ? { name: 'Company investor relations', type: 'official-issuer-source', url: urls.officialUrl }
        : null,
    },
    dataQuality: {
      profileAvailable: Object.values(mergedProfile).some(value => String(value || '').trim()),
      fieldSources: fieldQuality.fieldSources,
      fieldQuality: fieldQuality.fieldQuality,
      conflicts: fieldQuality.conflicts,
      conflictCount: Object.keys(fieldQuality.conflicts).length,
      dividendHistoryAvailable: dividends.length > 0,
      financialHistoryAvailable: financialHistory.length > 0,
      newsAvailable: myStocks.news.length > 0,
      valuationAvailable: Boolean(mergedProfile.pe || mergedProfile.pb || mergedProfile.marketCap),
      evidenceCount: evidence.length,
    },
    providerStatus: {
      myStocks: myStocks.providerStatus,
      stockAnalysis: external.providerStatus,
    },
    partial: errors.length > 0 || !financialHistory.length,
    providerErrors: errors,
  };
}

async function handle(req, res) {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });
  const rawSymbol = String(req.query.symbol || '').trim().toUpperCase();
  if (!rawSymbol) return json(res, 400, { error: 'symbol is required' });
  const action = String(req.query.action || 'intelligence').trim().toLowerCase();

  try {
    const result = await buildIntelligence(rawSymbol);
    if (action === 'profile') return json(res, 200, result);
    if (action === 'dividends') {
      return json(res, 200, {
        source: result.source,
        symbol: result.symbol,
        fetchedAt: result.fetchedAt,
        dividends: result.dividends,
        sources: result.sources,
      });
    }
    return json(res, 200, result);
  } catch (error) {
    return json(res, error.status || 502, {
      error: 'Company intelligence unavailable',
      detail: error.message,
      symbol: symbolFor(rawSymbol),
      fetchedAt: new Date().toISOString(),
    });
  }
}

module.exports = handle;
module.exports.normalizedComparable = normalizedComparable;
module.exports.buildFieldQuality = buildFieldQuality;
module.exports.evidenceFor = evidenceFor;
module.exports.mergeProfile = mergeProfile;
module.exports.normalizeWebsite = normalizeWebsite;
module.exports.normalizeLocation = normalizeLocation;
module.exports.parseFinancials = parseFinancials;
module.exports.parseRatios = parseRatios;
