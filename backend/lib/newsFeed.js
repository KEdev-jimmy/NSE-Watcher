const { loadRssSources, sourceRegistry } = require('./newsSources');

const BASE_URL = process.env.MYSTOCKS_BASE_URL || 'https://mystocks.africa/api/v1/partner';
const API_KEY = process.env.MYSTOCKS_API_KEY;
const HISTORY_DAYS = 90;

const NSE_COMPANIES = [
  ['SCOM', 'Safaricom'], ['KCB', 'KCB Group'], ['EQTY', 'Equity Group'],
  ['COOP', 'Co-operative Bank'], ['ABSA', 'Absa Bank Kenya'], ['EABL', 'East African Breweries'],
  ['KPLC', 'Kenya Power'], ['KEGN', 'KenGen'], ['BRIT', 'Britam Holdings'],
  ['KNRE', 'Kenya Re'], ['NSE', 'Nairobi Securities Exchange'], ['BAT', 'BAT Kenya'],
  ['BKG', 'BK Group'], ['NCBA', 'NCBA Group'], ['I&M', 'I&M Group'],
  ['DTK', 'Diamond Trust Bank'], ['SBIC', 'Stanbic Holdings'], ['JUB', 'Jubilee Holdings'],
  ['LBTY', 'Liberty Kenya'], ['CTUM', 'Centum Investment'], ['TOTL', 'TotalEnergies Marketing Kenya'],
  ['UMME', 'Umeme'], ['CARB', 'Carbacid Investments'], ['KQ', 'Kenya Airways'],
  ['KNL', 'Kenya National Assurance'], ['PORT', 'East African Portland Cement'],
  ['IMH', 'I&M Holdings'], ['SASN', 'Sasini'], ['SCAN', 'Scangroup'],
  ['BOC', 'BOC Kenya'], ['UNGA', 'Unga Group'], ['SMER', 'Sameer Africa'],
  ['ARM', 'ARM Cement'], ['HAFR', 'Home Afrika'], ['LONG', 'Longhorn Publishers'],
  ['EGAD', 'Eaagads'], ['FTGH', 'Flame Tree Group'], ['TPSE', 'TPS Eastern Africa'],
  ['CGEN', 'Car & General'], ['ILAM', 'ILAM Fahari I-REIT'],
  ['CRWN', 'Crown Paints'], ['EXPR', 'Express Kenya'], ['FAHR', 'Fahari I-REIT'],
  ['LUMI', 'Lumi Technologies'], ['MRE', 'Mumias Sugar'], ['NMG', 'Nation Media Group'],
  ['OCH', 'Olympia Capital'], ['SGL', 'Standard Group'], ['UCHM', 'Uchumi'],
  ['WPP', 'WPP Scangroup']
];

const MARKET_RELEVANCE_TERMS = [
  'nse', 'nairobi securities exchange', 'capital markets', 'stock market', 'share price',
  'shares', 'listed', 'listing', 'dividend', 'earnings', 'profit', 'loss', 'revenue',
  'results', 'financial results', 'rights issue', 'bonus issue', 'split', 'acquisition',
  'merger', 'takeover', 'ipo', 'bond', 'treasury', 'cbk', 'central bank', 'cma',
  'investor', 'investors', 'trading', 'market', 'equity', 'banking', 'broker',
  'brokerage', 'fund manager', 'reit', 'etf', 'interest rate', 'inflation', 'forex',
  'shilling', 'corporate action', 'agm', 'annual general meeting'
];

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

function arrayFrom(data, keys) {
  if (Array.isArray(data)) return data;
  for (const key of keys) if (Array.isArray(data?.[key])) return data[key];
  if (data?.data && Array.isArray(data.data)) return data.data;
  return [];
}

function first(obj, keys) {
  for (const key of keys) {
    const value = obj?.[key];
    if (value !== undefined && value !== null && String(value).trim() !== '') return value;
  }
  return '';
}

function symbolOf(item) {
  const value = first(item, ['symbol', 'ticker', 'stockSymbol', 'securitySymbol']);
  if (typeof value === 'object') return first(value, ['symbol', 'ticker']);
  return String(value || '').trim();
}

function companyOf(item) {
  const direct = first(item, ['companyName', 'company', 'issuerName', 'issuer', 'name']);
  if (typeof direct === 'object') return String(first(direct, ['name', 'companyName']));
  return String(direct || '').trim();
}

function dateOf(item) {
  return String(first(item, [
    'publishedAt', 'published_at', 'date', 'createdAt', 'created_at', 'exDate', 'ex_date'
  ]) || '').trim();
}

function normalizeSymbol(value) {
  return String(value || '').trim().toUpperCase().replace(/\.KE$/, '');
}

function resolveKnownCompany(symbol, companyName) {
  const normalized = normalizeSymbol(symbol);
  const bySymbol = NSE_COMPANIES.find(([ticker]) => ticker === normalized);
  if (bySymbol) return { symbol: bySymbol[0], companyName: bySymbol[1] };

  const name = String(companyName || '').trim();
  if (!name || name.toLowerCase() === 'mystocks africa') {
    return { symbol: normalized, companyName: '' };
  }

  const lower = name.toLowerCase();
  const byName = NSE_COMPANIES.find(([, company]) =>
    lower === company.toLowerCase() || lower.includes(company.toLowerCase())
  );

  return byName
    ? { symbol: byName[0], companyName: byName[1] }
    : { symbol: normalized, companyName: name };
}

function resolveFromProvider(item) {
  const nestedCompany = item?.company || item?.issuer || item?.security;
  const providerSymbol = symbolOf(item) || (nestedCompany && symbolOf(nestedCompany));
  const providerCompany = companyOf(item) || (nestedCompany && companyOf(nestedCompany));
  return resolveKnownCompany(providerSymbol, providerCompany);
}

function resolveFromTitle(item) {
  const title = String(first(item, ['title', 'headline', 'name']) || '').toLowerCase();
  if (!title) return { symbol: '', companyName: '' };

  const match = NSE_COMPANIES.find(([symbol, company]) =>
    title.includes(company.toLowerCase()) || title.includes(symbol.toLowerCase())
  );

  return match
    ? { symbol: match[0], companyName: match[1] }
    : { symbol: '', companyName: '' };
}

function categoryOf(item, fallback = 'Market') {
  const type = String(first(item, [
    'type', 'itemType', 'actionType', 'category'
  ]) || '').toLowerCase();

  if (type.includes('dividend')) return 'Dividends';
  if (
    type.includes('corporate') || type.includes('action') ||
    type.includes('rights') || type.includes('bonus') || type.includes('split')
  ) return 'Corporate Actions';
  if (
    type.includes('analysis') || type.includes('earnings') || type.includes('outlook')
  ) return 'Analysis';
  if (type.includes('company') || type.includes('stock') || type.includes('news')) {
    return 'Company News';
  }

  return fallback;
}

function normalizedId(item, prefix = 'news') {
  return String(first(item, [
    'id', 'articleId', 'documentId', 'slug', 'url'
  ]) || `${prefix}-${dateOf(item)}-${symbolOf(item)}-${String(
    first(item, ['title', 'headline']) || ''
  ).slice(0, 60)}`);
}

function normalizeItem(item, forcedCategory) {
  if (!item || typeof item !== 'object') return null;

  const title = String(first(item, ['title', 'headline', 'name']) || '').trim();
  if (!title) return null;

  const resolved = resolveFromProvider(item);
  const titleResolved = resolveFromTitle(item);
  const symbol = normalizeSymbol(resolved.symbol || titleResolved.symbol);
  const companyName = resolved.companyName || titleResolved.companyName;
  const category = forcedCategory || categoryOf(item);
  const summary = String(first(item, [
    'summary', 'excerpt', 'description', 'dek'
  ]) || '').trim();
  const body = String(first(item, [
    'body', 'content', 'articleBody', 'text'
  ]) || '').trim();
  const source = String(first(item, [
    'source', 'publisher', 'publication'
  ]) || 'MyStocks Africa').trim();
  const publishedAt = dateOf(item);
  const url = String(first(item, [
    'url', 'link', 'sourceUrl', 'articleUrl'
  ]) || '').trim();
  const imageUrl = String(first(item, [
    'imageUrl', 'image', 'thumbnail', 'coverImage'
  ]) || '').trim();
  const dividendAmount = String(first(item, [
    'dividendAmount', 'amountPerShare', 'amount', 'dividend'
  ]) || '').trim();
  const exDate = String(first(item, [
    'exDate', 'ex_date', 'bookClosureDate'
  ]) || '').trim();
  const paymentDate = String(first(item, [
    'paymentDate', 'payment_date', 'payDate'
  ]) || '').trim();

  return {
    id: normalizedId(item),
    title,
    summary,
    body,
    source,
    publishedAt,
    category,
    symbol,
    companyName,
    imageUrl,
    url,
    dividendAmount,
    exDate,
    paymentDate,
    sourceId: String(item.sourceId || '').trim(),
    sourceKind: String(item.sourceKind || '').trim(),
    verification: item.sourceKind === 'rss' && url
      ? 'source-linked'
      : url
        ? 'provider-linked'
        : 'provider-only'
  };
}

function withinWindow(item) {
  if (!item?.publishedAt) return true;
  const time = Date.parse(item.publishedAt);
  if (!Number.isFinite(time)) return true;
  return time >= Date.now() - HISTORY_DAYS * 24 * 60 * 60 * 1000;
}

function isRelevantExternalNews(item) {
  const haystack = `${item.title} ${item.summary}`.toLowerCase();

  if (NSE_COMPANIES.some(([symbol, company]) =>
    haystack.includes(company.toLowerCase()) ||
    new RegExp(`\\b${symbol.replace(/[&.]/g, '\\$&')}\\b`, 'i').test(haystack)
  )) return true;

  return MARKET_RELEVANCE_TERMS.some(term => haystack.includes(term));
}

function normalizeExternalNews(items) {
  return items
    .filter(item => item?.title && item?.url)
    .filter(isRelevantExternalNews)
    .map(item => normalizeItem(item, categoryOf(item, 'Market')))
    .filter(Boolean)
    .filter(withinWindow);
}

function sortNewest(items) {
  return items.sort((a, b) => {
    const at = Date.parse(a.publishedAt || '') || 0;
    const bt = Date.parse(b.publishedAt || '') || 0;
    return bt - at;
  });
}

function dedupe(items) {
  const seen = new Set();

  return items.filter(item => {
    const normalizedTitle = String(item.title || '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();

    const sourceKey = String(item.sourceId || item.source || '').toLowerCase();
    const key = item.url
      ? `${sourceKey}|url|${item.url}`
      : `${sourceKey}|title|${normalizedTitle}|${item.publishedAt || ''}`;

    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

async function enrichMissingCompanyMetadata(items) {
  const candidates = items
    .filter(item => !item.symbol || !item.companyName)
    .slice(0, 30);

  for (const item of candidates) {
    try {
      const detail = await mystocks(`/market-intel/${encodeURIComponent(item.id)}`);
      const raw = detail?.article || detail?.item || detail?.data || detail;
      const resolved = resolveFromProvider(raw);
      const titleResolved = resolveFromTitle(raw || item);

      if (!item.symbol) {
        item.symbol = normalizeSymbol(resolved.symbol || titleResolved.symbol);
      }
      if (!item.companyName) {
        item.companyName = resolved.companyName || titleResolved.companyName;
      }
      if (!item.body) {
        item.body = String(first(raw, [
          'body', 'content', 'articleBody', 'text'
        ]) || '').trim();
      }
      if (!item.summary) {
        item.summary = String(first(raw, [
          'summary', 'excerpt', 'description', 'dek'
        ]) || '').trim();
      }
      if (!item.url) {
        item.url = String(first(raw, [
          'url', 'link', 'sourceUrl', 'articleUrl'
        ]) || '').trim();
      }
      if (item.url && item.verification === 'provider-only') {
        item.verification = 'provider-linked';
      }
    } catch (_) {
      // Missing provider metadata is allowed; title/company matching remains available.
    }
  }

  return items;
}

async function loadMyStocksFeed() {
  const errors = [];
  const [intelResult, dividendResult] = await Promise.allSettled([
    mystocks('/market-intel?exchange=NSE&limit=50'),
    mystocks('/dividends/calendar?all=true')
  ]);

  let intel = [];
  let dividends = [];

  if (intelResult.status === 'fulfilled') {
    intel = arrayFrom(intelResult.value, ['articles', 'items'])
      .map(item => normalizeItem(item))
      .filter(Boolean);
  } else {
    errors.push(`MyStocks market-intel: ${intelResult.reason?.message || 'request failed'}`);
  }

  if (dividendResult.status === 'fulfilled') {
    dividends = arrayFrom(dividendResult.value, [
      'declarations', 'dividends', 'items'
    ]).map(item => normalizeItem(item, 'Dividends')).filter(Boolean);
  } else {
    errors.push(`MyStocks dividends: ${dividendResult.reason?.message || 'request failed'}`);
  }

  return {
    items: [...intel, ...dividends].filter(withinWindow),
    errors,
  };
}

async function loadFeed() {
  const [myStocksResult, rssResult] = await Promise.all([
    loadMyStocksFeed().catch(error => ({
      items: [],
      errors: [`MyStocks: ${error?.message || 'request failed'}`],
    })),
    loadRssSources(),
  ]);

  const externalItems = normalizeExternalNews(rssResult.items);
  const items = sortNewest(dedupe([
    ...myStocksResult.items,
    ...externalItems,
  ]));

  const providerErrors = [
    ...myStocksResult.errors,
    ...rssResult.errors,
  ];

  if (items.length) {
    await enrichMissingCompanyMetadata(
      items.filter(item => !item.sourceKind || item.sourceKind === 'api')
    );
  }

  return {
    items,
    providerErrors,
    partial: providerErrors.length > 0,
  };
}

async function loadDetail(id) {
  const data = await mystocks(`/market-intel/${encodeURIComponent(id)}`);
  const raw = data?.article || data?.item || data?.data || data;
  return normalizeItem(raw, categoryOf(raw));
}

async function loadCompany(rawSymbol) {
  const symbol = normalizeSymbol(rawSymbol);
  const data = await mystocks(`/companies/${encodeURIComponent(`${symbol}.KE`)}/news?limit=50`);
  const rawItems = arrayFrom(data, ['items', 'articles', 'news']);

  return sortNewest(
    dedupe(
      rawItems
        .map(item => normalizeItem(item))
        .filter(Boolean)
        .filter(withinWindow)
    )
  );
}

async function handle(req, res) {
  if (req.method !== 'GET') return json(res, 405, { error: 'GET only' });

  const action = String(req.query.action || 'feed').trim().toLowerCase();

  try {
    if (action === 'feed') {
      const result = await loadFeed();

      if (!result.items.length && result.providerErrors.length) {
        return json(res, 502, {
          error: 'News data unavailable',
          source: 'NSE Watcher news aggregation',
          historyDays: HISTORY_DAYS,
          partial: true,
          providerErrors: result.providerErrors,
          sources: {
            primary: {
              id: 'mystocks',
              name: 'MyStocks Africa',
              kind: 'market-intelligence-api',
              homepage: 'https://mystocks.africa/',
            },
            secondary: sourceRegistry(),
          },
          items: [],
        });
      }

      return json(res, 200, {
        source: 'NSE Watcher news aggregation',
        fetchedAt: new Date().toISOString(),
        historyDays: HISTORY_DAYS,
        windowStart: new Date(Date.now() - HISTORY_DAYS * 86400000).toISOString(),
        partial: result.partial,
        providerErrors: result.providerErrors,
        sources: {
          primary: {
            id: 'mystocks',
            name: 'MyStocks Africa',
            kind: 'market-intelligence-api',
            homepage: 'https://mystocks.africa/',
          },
          secondary: sourceRegistry(),
        },
        items: result.items,
      });
    }

    if (action === 'detail') {
      const id = String(req.query.id || '').trim();
      if (!id) return json(res, 400, { error: 'id is required' });

      const item = await loadDetail(id);
      return json(res, 200, {
        source: 'MyStocks Africa',
        item,
      });
    }

    if (action === 'company') {
      const rawSymbol = String(req.query.symbol || '').trim();
      if (!rawSymbol) return json(res, 400, { error: 'symbol is required' });

      const items = await loadCompany(rawSymbol);
      return json(res, 200, {
        source: 'MyStocks Africa',
        historyDays: HISTORY_DAYS,
        symbol: normalizeSymbol(rawSymbol),
        items,
      });
    }

    return json(res, 400, {
      error: 'Unknown action',
      supportedActions: ['feed', 'detail', 'company'],
    });
  } catch (error) {
    return json(res, error.status || 502, {
      error: 'News data unavailable',
      detail: error.message,
      source: error.data ? 'MyStocks Africa' : 'NSE Watcher news aggregation',
      provider: error.data || null,
      items: [],
    });
  }
}

module.exports = handle;
