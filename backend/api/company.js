const handleCompany = require('../lib/companyIntelligence');

function numeric(value) {
  const number = Number(String(value || '').replace(/,/g, '').replace(/[^0-9.-]/g, ''));
  return Number.isFinite(number) ? number : null;
}

function growth(current, previous) {
  const a = numeric(current);
  const b = numeric(previous);
  if (a === null || b === null || b === 0) return '';
  return `${(((a - b) / Math.abs(b)) * 100).toFixed(2)}%`;
}

function hasGrowth(value) {
  const text = String(value || '').trim();
  return Boolean(text && text !== '-' && !/^n\/a$/i.test(text));
}

function repairAnnualGrowth(result) {
  if (!result || !Array.isArray(result.financialHistory)) return result;

  const sourceProfile = result.profile || {};
  const history = result.financialHistory.map((row, index, all) => {
    const previous = all[index - 1];
    const calculatedRevenueGrowth = previous ? growth(row.revenue, previous.revenue) : '';
    const calculatedProfitGrowth = previous ? growth(row.profit, previous.profit) : '';
    const calculatedEpsGrowth = previous ? growth(row.eps, previous.eps) : '';

    return {
      ...row,
      // Provider-supplied growth is authoritative. Calculated growth is only a
      // fallback for a row where the provider did not supply a usable value.
      revenueGrowth: hasGrowth(row.revenueGrowth) ? row.revenueGrowth : calculatedRevenueGrowth,
      profitGrowth: hasGrowth(row.profitGrowth) ? row.profitGrowth : calculatedProfitGrowth,
      epsGrowth: hasGrowth(row.epsGrowth) ? row.epsGrowth : calculatedEpsGrowth,
    };
  });

  const latest = history[history.length - 1];
  if (latest) {
    result.profile = {
      ...sourceProfile,
      revenueGrowth: hasGrowth(latest.revenueGrowth)
        ? latest.revenueGrowth
        : (hasGrowth(sourceProfile.revenueGrowth) ? sourceProfile.revenueGrowth : ''),
      profitGrowth: hasGrowth(latest.profitGrowth)
        ? latest.profitGrowth
        : (hasGrowth(sourceProfile.profitGrowth) ? sourceProfile.profitGrowth : ''),
      epsGrowth: hasGrowth(latest.epsGrowth)
        ? latest.epsGrowth
        : (hasGrowth(sourceProfile.epsGrowth) ? sourceProfile.epsGrowth : ''),
    };
  }
  result.financialHistory = history;
  return result;
}

function cleanCell(value) {
  return String(value || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseDividendRows(html) {
  const rows = [];
  const datePattern = /^[A-Z][a-z]{2} \d{1,2}, \d{4}$/;
  const amountPattern = /^\d+(?:\.\d+)?(?:\s+KES)?$/i;

  for (const match of String(html || '').matchAll(/<tr\b[\s\S]*?<\/tr>/gi)) {
    const cells = [...match[0].matchAll(/<(?:th|td)\b[^>]*>([\s\S]*?)<\/(?:th|td)>/gi)]
      .map(cell => cleanCell(cell[1]));
    if (cells.length < 4) continue;

    const exIndex = cells.findIndex(cell => datePattern.test(cell));
    if (exIndex < 0 || exIndex + 1 >= cells.length) continue;
    const amount = cells[exIndex + 1];
    if (!amountPattern.test(amount)) continue;

    const recordDate = cells[exIndex + 2] || '';
    const paymentDate = cells[exIndex + 3] || '';
    if (!datePattern.test(recordDate) || !datePattern.test(paymentDate)) continue;

    rows.push({
      amount: amount.replace(/\s+KES$/i, '').trim() + ' KES',
      exDate: cells[exIndex],
      paymentDate,
      declaredDate: '',
      type: 'Dividend',
      status: 'Historical',
      source: 'StockAnalysis / S&P Global Market Intelligence',
    });
  }

  return rows.slice(0, 12);
}

function parseDividendText(html) {
  const text = cleanCell(String(html || '').replace(/<br\s*\/?>/gi, ' | '));
  const rows = [];
  const pattern = /([A-Z][a-z]{2} \d{1,2}, \d{4})\s+([0-9]+(?:\.[0-9]+)?)\s+KES\s+([A-Z][a-z]{2} \d{1,2}, \d{4})\s+([A-Z][a-z]{2} \d{1,2}, \d{4})/g;
  for (const match of text.matchAll(pattern)) {
    rows.push({
      amount: `${match[2]} KES`,
      exDate: match[1],
      paymentDate: match[4],
      declaredDate: '',
      type: 'Dividend',
      status: 'Historical',
      source: 'StockAnalysis / S&P Global Market Intelligence',
    });
  }
  return rows.slice(0, 12);
}

function dividendsFromNews(news) {
  if (!Array.isArray(news)) return [];
  const rows = [];
  const seen = new Set();
  for (const item of news) {
    const title = String(item?.title || '').replace(/\s+/g, ' ').trim();
    const createdAt = String(item?.createdAt || '').trim();
    let match = title.match(/(?:final|interim) dividend of\s+KSh\s*([0-9]+(?:\.[0-9]+)?)\s+per share/i);
    if (!match) continue;

    const amount = `${match[1]} KES`;
    const payMatch = title.match(/payable on\s+([A-Z][a-z]+ \d{1,2}, \d{4})/i);
    const paidMatch = title.match(/paid on\s+([A-Z][a-z]+ \d{1,2}, \d{4})/i);
    const paidMonthMatch = title.match(/paid in\s+([A-Z][a-z]+ \d{4})/i);
    const recordMatch = title.match(/register as at\s+([A-Z][a-z]+ \d{1,2}, \d{4})/i);
    const key = `${amount}|${payMatch?.[1] || paidMatch?.[1] || paidMonthMatch?.[1] || ''}`;
    if (seen.has(key)) continue;
    seen.add(key);

    rows.push({
      amount,
      exDate: '',
      paymentDate: payMatch?.[1] || paidMatch?.[1] || paidMonthMatch?.[1] || '',
      declaredDate: createdAt ? createdAt.slice(0, 10) : '',
      recordDate: recordMatch?.[1] || '',
      type: /interim/i.test(title) ? 'Interim Dividend' : 'Final Dividend',
      status: 'Historical',
      source: 'MyStocks Africa / company news',
    });
  }
  return rows.slice(0, 12);
}

async function loadDividendFallback(symbol) {
  const bare = String(symbol || '').replace(/\.KE$/i, '').toUpperCase();
  if (!bare) return [];
  const url = `https://stockanalysis.com/quote/nase/${encodeURIComponent(bare)}/dividend/`;
  try {
    const response = await fetch(url, {
      headers: {
        Accept: 'text/html,application/xhtml+xml',
        'User-Agent': 'NSE-Watcher/1.4 (+https://github.com/KEdev-jimmy/NSE-Watcher)',
      },
    });
    if (!response.ok) return [];
    const html = await response.text();
    const rows = parseDividendRows(html);
    return rows.length ? rows : parseDividendText(html);
  } catch {
    return [];
  }
}

function repairEvidence(result) {
  if (!result || !Array.isArray(result.evidence)) return result;
  const values = result.profile || {};
  const replacements = new Map([
    ['Revenue growth', values.revenueGrowth],
    ['Profit growth', values.profitGrowth],
    ['EPS growth', values.epsGrowth],
  ]);

  for (const evidence of result.evidence) {
    if (replacements.has(evidence.claim)) {
      const value = replacements.get(evidence.claim);
      if (value) evidence.value = value;
    }
  }

  if (values.epsGrowth && !result.evidence.some(item => item.claim === 'EPS growth')) {
    result.evidence.push({
      claim: 'EPS growth',
      value: values.epsGrowth,
      source: 'StockAnalysis / S&P Global Market Intelligence',
      endpoint: 'financials',
      url: `https://stockanalysis.com/quote/nase/${String(result.symbol || '').replace(/\.KE$/i, '').toUpperCase()}/financials/`,
      symbol: result.symbol,
      fetchedAt: result.fetchedAt,
    });
  }
  if (result.dataQuality) result.dataQuality.evidenceCount = result.evidence.length;
  return result;
}

async function handleWithRepair(req, res) {
  let capturedStatus = 200;
  let capturedBody = null;
  const capturedResponse = {
    status(code) {
      capturedStatus = code;
      return this;
    },
    setHeader() {
      return this;
    },
    end(body) {
      try {
        capturedBody = JSON.parse(body);
      } catch {
        capturedBody = body;
      }
    },
  };

  await handleCompany(req, capturedResponse);

  if (!capturedBody || typeof capturedBody !== 'object' || capturedStatus >= 400) {
    return res.status(capturedStatus).setHeader('Content-Type', 'application/json').end(
      typeof capturedBody === 'string' ? capturedBody : JSON.stringify(capturedBody || { error: 'Company intelligence unavailable' })
    );
  }

  const action = String(req.query?.action || 'intelligence').trim().toLowerCase();
  if (action === 'intelligence' || action === 'profile' || action === 'dividends') {
    repairAnnualGrowth(capturedBody);

    if (Array.isArray(capturedBody.dividends) && capturedBody.dividends.length === 0) {
      const fallback = await loadDividendFallback(capturedBody.symbol);
      const newsFallback = dividendsFromNews(capturedBody.news);
      const dividends = fallback.length ? fallback : newsFallback;
      if (dividends.length) {
        capturedBody.dividends = dividends;
        if (capturedBody.dataQuality) capturedBody.dataQuality.dividendHistoryAvailable = true;
        if (Array.isArray(capturedBody.evidence)) {
          dividends.forEach((dividend, index) => {
            capturedBody.evidence.push({
              claim: `Dividend ${index + 1}`,
              value: `${dividend.amount} • ${dividend.exDate || 'date not supplied'} • paid ${dividend.paymentDate || 'date not supplied'}`,
              source: dividend.source,
              endpoint: dividend.source.startsWith('MyStocks') ? 'company-news' : 'dividend-history',
              url: dividend.source.startsWith('MyStocks')
                ? ''
                : `https://stockanalysis.com/quote/nase/${String(capturedBody.symbol || '').replace(/\.KE$/i, '').toUpperCase()}/dividend/`,
              symbol: capturedBody.symbol,
              fetchedAt: capturedBody.fetchedAt,
            });
          });
        }
      }
    }

    repairEvidence(capturedBody);
    if (capturedBody.dataQuality) {
      capturedBody.dataQuality.evidenceCount = Array.isArray(capturedBody.evidence)
        ? capturedBody.evidence.length
        : 0;
      capturedBody.dataQuality.dividendHistoryAvailable = Array.isArray(capturedBody.dividends)
        ? capturedBody.dividends.length > 0
        : false;
    }
  }

  res.status(capturedStatus).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 's-maxage=600, stale-while-revalidate=1800');
  return res.end(JSON.stringify(capturedBody));
}

module.exports = handleWithRepair;
