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

function json(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', CACHE_CONTROL);
  res.end(JSON.stringify(body));
}
function symbolFor(raw) {
  const s = String(raw || '').trim().toUpperCase();
  return s.includes('.') ? s : `${s}.KE`;
}
function bareSymbol(raw) { return String(raw || '').toUpperCase().replace(/\.KE$/, ''); }

async function mystocks(path) {
  if (!API_KEY) throw new Error('MYSTOCKS_API_KEY is not configured');
  const response = await fetch(`${BASE_URL}${path}`, { headers: { 'x-api-key': API_KEY, Accept: 'application/json' } });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = { raw: text }; }
  if (!response.ok) { const e = new Error(`MyStocks ${response.status}`); e.status = response.status; throw e; }
  return data;
}
async function fetchHtml(url) {
  const response = await fetch(url, {
    headers: { Accept: 'text/html,application/xhtml+xml', 'User-Agent': 'NSE-Watcher/1.1 (+https://github.com/KEdev-jimmy/NSE-Watcher)' },
  });
  const text = await response.text();
  if (!response.ok) { const e = new Error(`Source ${response.status}`); e.status = response.status; throw e; }
  return text;
}
function decodeEntities(value) {
  return String(value || '')
    .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'").replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)));
}
function cleanText(html) {
  return decodeEntities(String(html || '')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ').replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<br\s*\/?>/gi, ' ').replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ').trim();
}
function parseTables(html) {
  const tables = [];
  for (const tableMatch of String(html || '').matchAll(/<table\b[\s\S]*?<\/table>/gi)) {
    const rows = [];
    for (const rowMatch of tableMatch[0].matchAll(/<tr\b[\s\S]*?<\/tr>/gi)) {
      const cells = [];
      for (const cellMatch of rowMatch[0].matchAll(/<(?:th|td)\b[^>]*>([\s\S]*?)<\/(?:th|td)>/gi)) cells.push(cleanText(cellMatch[1]));
      if (cells.length) rows.push(cells);
    }
    if (rows.length) tables.push(rows);
  }
  return tables;
}
function findRow(tables, patterns) {
  const regexes = patterns.map(p => p instanceof RegExp ? p : new RegExp(`^${p}$`, 'i'));
  for (const table of tables) for (const row of table) {
    const label = String(row[0] || '').replace(/\s+/g, ' ').trim();
    if (regexes.some(re => re.test(label))) return row;
  }
  return null;
}
function valueFromRow(row, index = 1) { return row ? String(row[index] || '').trim() : ''; }
function normalizePeriod(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  return text && !/^-$/i.test(text) ? text : '';
}
function pctGrowth(current, previous) {
  const a = Number(String(current || '').replace(/,/g, '').replace(/[^0-9.-]/g, ''));
  const b = Number(String(previous || '').replace(/,/g, '').replace(/[^0-9.-]/g, ''));
  if (!Number.isFinite(a) || !Number.isFinite(b) || b === 0) return '';
  return `${(((a - b) / Math.abs(b)) * 100).toFixed(2)}%`;
}

function parseFinancials(html) {
  const tables = parseTables(html);
  const revenue = findRow(tables, [/^Revenue(?:\s+Revenue Growth)?$/i, /^Revenue$/i]);
  const revenueGrowth = findRow(tables, [/^Revenue Growth$/i]);
  const netIncome = findRow(tables, [/^Net Income(?:\s+Net Income Growth)?$/i, /^Net Income$/i, /^Net Profit$/i]);
  const netIncomeGrowth = findRow(tables, [/^Net Income Growth$/i]);
  const eps = findRow(tables, [/^Earnings Per Share(?:\s+EPS Growth)?$/i, /^EPS(?:\s+EPS Growth)?$/i]);
  const epsGrowth = findRow(tables, [/^EPS Growth$/i]);
  const margin = findRow(tables, [/^Profit Margin$/i, /^Net Margin$/i]);
  const periods = findRow(tables, [/^Period Ending$/i]);
  const fiscalYears = findRow(tables, [/^Fiscal Year$/i]);

  const periodRow = periods || fiscalYears || [];
  const hasTtm = String(periodRow[1] || '').toUpperCase() === 'TTM';
  const annualIndex = hasTtm ? 2 : 1;
  const latestPeriod = normalizePeriod(valueFromRow(periods, annualIndex) || valueFromRow(fiscalYears, annualIndex) || valueFromRow(periods) || valueFromRow(fiscalYears));
  const latestRevenue = valueFromRow(revenue, annualIndex) || valueFromRow(revenue);
  const latestProfit = valueFromRow(netIncome, annualIndex) || valueFromRow(netIncome);
  const latestEps = valueFromRow(eps, annualIndex) || valueFromRow(eps);
  const previousRevenue = valueFromRow(revenue, annualIndex + 1);
  const previousProfit = valueFromRow(netIncome, annualIndex + 1);
  const previousEps = valueFromRow(eps, annualIndex + 1);

  const latest = {
    period: latestPeriod || 'Latest reported', revenue: latestRevenue, profit: latestProfit, eps: latestEps,
    margin: valueFromRow(margin, annualIndex) || valueFromRow(margin),
    revenueGrowth: valueFromRow(revenueGrowth, annualIndex) || pctGrowth(latestRevenue, previousRevenue),
    profitGrowth: valueFromRow(netIncomeGrowth, annualIndex) || pctGrowth(latestProfit, previousProfit),
    epsGrowth: valueFromRow(epsGrowth, annualIndex) || pctGrowth(latestEps, previousEps),
    source: 'StockAnalysis / S&P Global Market Intelligence',
  };

  const history = [];
  const max = Math.max(revenue?.length || 0, netIncome?.length || 0, eps?.length || 0, periods?.length || 0, fiscalYears?.length || 0);
  for (let i = annualIndex; i < max; i += 1) {
    const period = normalizePeriod(valueFromRow(periods, i) || valueFromRow(fiscalYears, i));
    if (!period) continue;
    history.push({ period, revenue: valueFromRow(revenue, i), profit: valueFromRow(netIncome, i), eps: valueFromRow(eps, i), margin: valueFromRow(margin, i), source: 'StockAnalysis / S&P Global Market Intelligence' });
  }
  return {
    profile: { revenue: latest.revenue, profit: latest.profit, eps: latest.eps, margin: latest.margin, revenueGrowth: latest.revenueGrowth, profitGrowth: latest.profitGrowth, epsGrowth: latest.epsGrowth },
    financialHistory: [latest, ...history].filter((row, i, all) => row.period && all.findIndex(x => x.period === row.period) === i).slice(0, 8).reverse(),
  };
}
function parseRatios(html) {
  const tables = parseTables(html);
  const marketCap = findRow(tables, [/^Market Capitalization$/i]);
  const pe = findRow(tables, [/^PE Ratio$/i]);
  const pb = findRow(tables, [/^PB Ratio$/i]);
  const debt = findRow(tables, [/^Debt \/ Equity Ratio$/i, /^Debt\/Equity Ratio$/i]);
  const roe = findRow(tables, [/^Return on Equity \(ROE\)$/i, /^ROE$/i]);
  const dividendYield = findRow(tables, [/^Dividend Yield$/i]);
  return { marketCap: valueFromRow(marketCap), pe: valueFromRow(pe), pb: valueFromRow(pb), debtToEquity: valueFromRow(debt), roe: valueFromRow(roe), dividendYield: valueFromRow(dividendYield) };
}
function parseDividends(html) {
  const tables = parseTables(html);
  let best = [];
  for (const table of tables) {
    const headerIndex = table.findIndex(row => {
      const h = row.map(v => v.toLowerCase());
      return h.some(v => /ex[- ]?div(?:idend)? date/.test(v)) && h.some(v => v.includes('amount'));
    });
    if (headerIndex < 0) continue;
    const header = table[headerIndex].map(v => v.toLowerCase());
    const exIndex = header.findIndex(v => /ex[- ]?div(?:idend)? date/.test(v));
    const amountIndex = header.findIndex(v => v.includes('amount'));
    const recordIndex = header.findIndex(v => v.includes('record date'));
    const payIndex = header.findIndex(v => v.includes('pay date') || v.includes('payment date'));
    const rows = table.slice(headerIndex + 1).map(row => ({ amount: String(row[amountIndex] || '').trim(), exDate: String(row[exIndex] || '').trim(), paymentDate: payIndex >= 0 ? String(row[payIndex] || '').trim() : '' })).filter(r => r.amount && r.exDate);
    if (rows.length > best.length) best = rows;
  }
  return best.slice(0, 12).map(r => ({ amount: r.amount, exDate: r.exDate, paymentDate: r.paymentDate, declaredDate: '', type: 'Dividend', status: 'Historical', source: 'StockAnalysis / S&P Global Market Intelligence' }));
}
function normalizeMyStocksProfile(value) {
  if (!value || typeof value !== 'object') return {};
  if (Array.isArray(value)) return value.find(x => x && typeof x === 'object') || {};
  if (value.data && typeof value.data === 'object' && !Array.isArray(value.data)) return value.data;
  return value;
}
function pick(obj, keys) {
  const wanted = new Set(keys.map(k => k.replace(/[^a-z0-9]/gi, '').toLowerCase()));
  function walk(value) {
    if (!value || typeof value !== 'object') return '';
    if (Array.isArray(value)) { for (const item of value) { const found = walk(item); if (found) return found; } return ''; }
    for (const [key, child] of Object.entries(value)) {
      const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase();
      if (wanted.has(normalized) && (typeof child === 'string' || typeof child === 'number')) return String(child);
      const found = walk(child); if (found) return found;
    }
    return '';
  }
  return walk(obj);
}
async function loadMyStocks(symbol) {
  const encoded = encodeURIComponent(symbol);
  const [profileResult, dividendsResult, newsResult] = await Promise.allSettled([mystocks(`/companies/${encoded}`), mystocks(`/dividends/${encoded}/history?limit=12`), mystocks(`/companies/${encoded}/news?limit=20`)]);
  const profileRaw = profileResult.status === 'fulfilled' ? normalizeMyStocksProfile(profileResult.value) : {};
  const d = dividendsResult.status === 'fulfilled' ? dividendsResult.value : {};
  const n = newsResult.status === 'fulfilled' ? newsResult.value : {};
  return {
    profile: {
      description: pick(profileRaw, ['description','businessDescription','companyDescription']), sector: pick(profileRaw, ['sector','industry']), headquarters: pick(profileRaw, ['headquarters','hq','location']), website: pick(profileRaw, ['website','websiteUrl','url']), marketCap: pick(profileRaw, ['marketCap','marketCapitalisation','marketCapitalization']),
      revenue: pick(profileRaw, ['revenue','totalRevenue']), profit: pick(profileRaw, ['profit','netIncome','netProfit','profitAfterTax']), eps: pick(profileRaw, ['eps','earningsPerShare']), roe: pick(profileRaw, ['roe','returnOnEquity']), debtToEquity: pick(profileRaw, ['debtToEquity','debtEquity','debtToEquityRatio']), margin: pick(profileRaw, ['netMargin','profitMargin','margin']), revenueGrowth: pick(profileRaw, ['revenueGrowth','revenueGrowthRate']), profitGrowth: pick(profileRaw, ['profitGrowth','netIncomeGrowth','profitGrowthRate']), pe: pick(profileRaw, ['pe','peRatio','priceEarnings','priceToEarnings']), pb: pick(profileRaw, ['pb','pbRatio','priceBook','priceToBook']), dividendYield: pick(profileRaw, ['dividendYield','yield']),
    },
    dividends: Array.isArray(d.history) ? d.history : (Array.isArray(d.data) ? d.data : (Array.isArray(d.dividends) ? d.dividends : [])),
    news: Array.isArray(n.items) ? n.items : (Array.isArray(n.news) ? n.news : (Array.isArray(n.data) ? n.data : [])),
    providerStatus: { profile: profileResult.status, dividends: dividendsResult.status, news: newsResult.status },
  };
}
async function loadStockAnalysis(symbol) {
  const bare = bareSymbol(symbol);
  const urls = { financials: `${STOCK_ANALYSIS_BASE}/${bare}/financials/`, ratios: `${STOCK_ANALYSIS_BASE}/${bare}/financials/ratios/`, dividends: `${STOCK_ANALYSIS_BASE}/${bare}/dividend/` };
  const [f, r, d] = await Promise.allSettled([fetchHtml(urls.financials), fetchHtml(urls.ratios), fetchHtml(urls.dividends)]);
  const financials = f.status === 'fulfilled' ? parseFinancials(f.value) : { profile: {}, financialHistory: [] };
  return { profile: { ...financials.profile, ...(r.status === 'fulfilled' ? parseRatios(r.value) : {}) }, financialHistory: financials.financialHistory, dividends: d.status === 'fulfilled' ? parseDividends(d.value) : [], urls, providerStatus: { financials: f.status, ratios: r.status, dividends: d.status } };
}
function sourceUrls(symbol) {
  const bare = bareSymbol(symbol);
  return { officialUrl: OFFICIAL_IR[bare] || '', financialsUrl: `${STOCK_ANALYSIS_BASE}/${bare}/financials/`, ratiosUrl: `${STOCK_ANALYSIS_BASE}/${bare}/financials/ratios/`, dividendsUrl: `${STOCK_ANALYSIS_BASE}/${bare}/dividend/` };
}
function mergeProfile(primary, external) {
  const output = { ...(primary || {}) };
  const keys = ['marketCap','revenue','profit','eps','roe','debtToEquity','margin','revenueGrowth','profitGrowth','pe','pb','dividendYield'];
  for (const key of keys) if (String(external?.[key] || '').trim()) output[key] = external[key];
  return output;
}
function evidenceFor(profile, history, dividends, urls, fetchedAt, symbol) {
  const out = [];
  const add = (claim, value, source, endpoint, url) => { if (String(value || '').trim()) out.push({ claim, value: String(value), source, endpoint, url, symbol, fetchedAt }); };
  const external = 'StockAnalysis / S&P Global Market Intelligence';
  add('Revenue', profile.revenue, external, 'financials', urls.financialsUrl); add('Profit', profile.profit, external, 'financials', urls.financialsUrl); add('EPS', profile.eps, external, 'financials', urls.financialsUrl); add('Revenue growth', profile.revenueGrowth, external, 'financials', urls.financialsUrl); add('Profit growth', profile.profitGrowth, external, 'financials', urls.financialsUrl); add('Net margin', profile.margin, external, 'financials', urls.financialsUrl);
  add('P/E', profile.pe, external, 'ratios', urls.ratiosUrl); add('P/B', profile.pb, external, 'ratios', urls.ratiosUrl); add('ROE', profile.roe, external, 'ratios', urls.ratiosUrl); add('Debt / equity', profile.debtToEquity, external, 'ratios', urls.ratiosUrl); add('Dividend yield', profile.dividendYield, external, 'ratios', urls.ratiosUrl); add('Market capitalization', profile.marketCap, external, 'ratios', urls.ratiosUrl);
  history.slice(0,8).forEach(row => { add(`Revenue ${row.period}`, row.revenue, external, 'financials', urls.financialsUrl); add(`Profit ${row.period}`, row.profit, external, 'financials', urls.financialsUrl); add(`EPS ${row.period}`, row.eps, external, 'financials', urls.financialsUrl); });
  dividends.slice(0,12).forEach((d,i) => add(`Dividend ${i+1}`, `${d.amount || ''} • ${d.exDate || ''}${d.paymentDate ? ` • paid ${d.paymentDate}` : ''}`, external, 'dividend-history', urls.dividendsUrl));
  if (urls.officialUrl) add('Official investor relations source', urls.officialUrl, 'Company investor relations', 'investor-relations', urls.officialUrl);
  return out;
}
async function buildIntelligence(rawSymbol) {
  const symbol = symbolFor(rawSymbol), fetchedAt = new Date().toISOString();
  const [m, s] = await Promise.allSettled([loadMyStocks(symbol), loadStockAnalysis(symbol)]);
  const my = m.status === 'fulfilled' ? m.value : { profile:{}, dividends:[], news:[], providerStatus:{ error:m.reason?.message || 'failed' } };
  const ext = s.status === 'fulfilled' ? s.value : { profile:{}, financialHistory:[], dividends:[], urls:sourceUrls(symbol), providerStatus:{ error:s.reason?.message || 'failed' } };
  const profile = mergeProfile(my.profile, ext.profile);
  const dividends = my.dividends.length ? my.dividends : ext.dividends;
  const history = ext.financialHistory;
  const urls = sourceUrls(symbol);
  const errors = [];
  if (m.status === 'rejected') errors.push(`MyStocks: ${m.reason?.message || 'request failed'}`);
  if (s.status === 'rejected') errors.push(`StockAnalysis: ${s.reason?.message || 'request failed'}`);
  const evidence = evidenceFor(profile, history, dividends, urls, fetchedAt, symbol);
  return {
    source:'NSE Watcher multi-source company intelligence', symbol, delayMinutes:15, fetchedAt, profile, dividends, financialHistory:history, news:my.news, evidence,
    sources:{ primary:{name:'MyStocks Africa',type:'market-data-api'}, fundamentals:{name:'StockAnalysis / S&P Global Market Intelligence',type:'structured-financial-data',urls:ext.urls || urls}, issuer:urls.officialUrl ? {name:'Company investor relations',type:'official-issuer-source',url:urls.officialUrl}:null },
    dataQuality:{ profileAvailable:Object.values(profile).some(v=>String(v||'').trim()), dividendHistoryAvailable:dividends.length>0, financialHistoryAvailable:history.length>0, newsAvailable:my.news.length>0, valuationAvailable:Boolean(profile.pe||profile.pb||profile.marketCap), evidenceCount:evidence.length },
    providerStatus:{ myStocks:my.providerStatus, stockAnalysis:ext.providerStatus }, partial:errors.length>0 || !history.length, providerErrors:errors
  };
}
async function handle(req,res) {
  if (req.method !== 'GET') return json(res,405,{error:'GET only'});
  const raw = String(req.query.symbol || '').trim().toUpperCase();
  if (!raw) return json(res,400,{error:'symbol is required'});
  try {
    const result = await buildIntelligence(raw), action = String(req.query.action || 'intelligence').toLowerCase();
    if (action === 'profile') return json(res,200,result);
    if (action === 'dividends') return json(res,200,{source:result.source,symbol:result.symbol,fetchedAt:result.fetchedAt,dividends:result.dividends,sources:result.sources});
    return json(res,200,result);
  } catch (error) {
    return json(res,error.status || 502,{error:'Company intelligence unavailable',detail:error.message,symbol:symbolFor(raw),fetchedAt:new Date().toISOString()});
  }
}
module.exports = handle;
