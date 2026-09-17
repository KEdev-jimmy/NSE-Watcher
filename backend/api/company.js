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

function repairAnnualGrowth(result) {
  if (!result || !Array.isArray(result.financialHistory)) return result;

  const history = result.financialHistory.map((row, index, all) => {
    const previous = all[index - 1];
    return {
      ...row,
      revenueGrowth: previous ? growth(row.revenue, previous.revenue) : '',
      profitGrowth: previous ? growth(row.profit, previous.profit) : '',
      epsGrowth: previous ? growth(row.eps, previous.eps) : '',
    };
  });

  const latest = history[history.length - 1];
  if (latest) {
    result.profile = {
      ...result.profile,
      revenueGrowth: latest.revenueGrowth || result.profile?.revenueGrowth || '',
      profitGrowth: latest.profitGrowth || result.profile?.profitGrowth || '',
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

async function loadDividendFallback(symbol) {
  const bare = String(symbol || '').replace(/\.KE$/i, '').toUpperCase();
  if (!bare) return [];
  const url = `https://stockanalysis.com/quote/nase/${encodeURIComponent(bare)}/dividend/`;
  try {
    const response = await fetch(url, {
      headers: {
        Accept: 'text/html,application/xhtml+xml',
        'User-Agent': 'NSE-Watcher/1.3 (+https://github.com/KEdev-jimmy/NSE-Watcher)',
      },
    });
    if (!response.ok) return [];
    return parseDividendRows(await response.text());
  } catch {
    return [];
  }
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
      if (fallback.length) {
        capturedBody.dividends = fallback;
        if (capturedBody.dataQuality) capturedBody.dataQuality.dividendHistoryAvailable = true;
        if (Array.isArray(capturedBody.evidence)) {
          fallback.forEach((dividend, index) => {
            capturedBody.evidence.push({
              claim: `Dividend ${index + 1}`,
              value: `${dividend.amount} • ${dividend.exDate} • paid ${dividend.paymentDate}`,
              source: dividend.source,
              endpoint: 'dividend-history',
              url: `https://stockanalysis.com/quote/nase/${String(capturedBody.symbol || '').replace(/\.KE$/i, '').toUpperCase()}/dividend/`,
              symbol: capturedBody.symbol,
              fetchedAt: capturedBody.fetchedAt,
            });
          });
          capturedBody.dataQuality.evidenceCount = capturedBody.evidence.length;
        }
      }
    }
  }

  res.status(capturedStatus).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 's-maxage=600, stale-while-revalidate=1800');
  return res.end(JSON.stringify(capturedBody));
}

module.exports = handleWithRepair;
