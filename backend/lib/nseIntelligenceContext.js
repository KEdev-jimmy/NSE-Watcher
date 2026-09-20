function text(value) {
  return String(value ?? '').trim();
}

function finiteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function compactProfile(profile) {
  if (!profile || typeof profile !== 'object') return {};
  const fields = [
    'sector', 'industry', 'description', 'headquarters', 'website',
    'marketCap', 'revenue', 'profit', 'eps', 'roe', 'debtToEquity',
    'margin', 'revenueGrowth', 'profitGrowth', 'epsGrowth', 'pe', 'pb',
    'dividendYield', 'financialUnit', 'financialPeriod', 'ratioBasis', 'ratioPeriod',
    'financialProviderUpdatedAt', 'financialPageCheckedAt',
    'ratioProviderUpdatedAt', 'ratioPageCheckedAt',
  ];
  return Object.fromEntries(fields
    .filter(key => profile[key] !== undefined && profile[key] !== null && text(profile[key]) !== '')
    .map(key => [key, profile[key]]));
}

function compactFinancialHistory(history) {
  if (!Array.isArray(history)) return [];
  return history.slice(-8).map(row => ({
    period: text(row?.period),
    revenue: row?.revenue ?? '',
    profit: row?.profit ?? '',
    eps: row?.eps ?? '',
    margin: row?.margin ?? '',
    revenueGrowth: row?.revenueGrowth ?? '',
    profitGrowth: row?.profitGrowth ?? '',
    epsGrowth: row?.epsGrowth ?? '',
    source: text(row?.source),
    providerUpdatedAt: text(row?.providerUpdatedAt),
    pageCheckedAt: text(row?.pageCheckedAt),
  })).filter(row => row.period);
}

function compactDividends(dividends) {
  if (!Array.isArray(dividends)) return [];
  return dividends.slice(0, 12).map(row => ({
    amount: row?.amount ?? '',
    exDate: text(row?.exDate),
    paymentDate: text(row?.paymentDate),
    declaredDate: text(row?.declaredDate),
    type: text(row?.type),
    status: text(row?.status),
    source: text(row?.source),
  }));
}

function movementEvidence(movement) {
  if (!Array.isArray(movement?.evidence)) return [];
  return movement.evidence.slice(0, 10).map((item, index) => ({
    id: text(item?.id) || `M${index + 1}`,
    claim: text(item?.title),
    value: text(item?.description),
    source: text(item?.source),
    url: text(item?.sourceUrl),
    period: text(item?.date),
    relationship: text(item?.relationship),
    daysFromMove: item?.daysFromMove ?? null,
  }));
}

function companyEvidence(company) {
  if (!Array.isArray(company?.evidence)) return [];
  return company.evidence.slice(0, 40).map((item, index) => ({
    id: text(item?.id) || `C${index + 1}`,
    claim: text(item?.claim),
    value: item?.value ?? '',
    source: text(item?.source),
    url: text(item?.url),
    period: text(item?.period),
  }));
}

function buildNseIntelligenceContext({ company = {}, movement = null, market = null } = {}) {
  const symbol = text(company?.symbol || movement?.symbol);
  const companyRecords = companyEvidence(company);
  const movementRecords = movementEvidence(movement);

  const evidence = [
    ...companyRecords.map(item => ({ ...item, kind: 'company' })),
    ...movementRecords
      .filter(item => !companyRecords.some(existing => existing.id === item.id))
      .map(item => ({ ...item, kind: 'movement' })),
  ];

  const move = movement?.move || null;
  const moves = movement?.moves && typeof movement.moves === 'object'
    ? Object.fromEntries(Object.entries(movement.moves).map(([period, value]) => [
      period,
      value ? {
        from: text(value.from),
        to: text(value.to),
        priceBefore: finiteNumber(value.priceBefore),
        priceAfter: finiteNumber(value.priceAfter),
        changePct: finiteNumber(value.changePct),
      } : null,
    ]))
    : {};

  const context = {
    contextVersion: 1,
    sourceOfTruth: 'NSE Watcher verified provider data and app calculations',
    symbol,
    company: {
      profile: compactProfile(company?.profile),
      financialHistory: compactFinancialHistory(company?.financialHistory),
      dividends: compactDividends(company?.dividends),
    },
    movement: {
      latest: move ? {
        from: text(move.from),
        to: text(move.to),
        priceBefore: finiteNumber(move.priceBefore),
        priceAfter: finiteNumber(move.priceAfter),
        changePct: finiteNumber(move.changePct),
      } : null,
      periods: moves,
      summary: text(movement?.summary),
      limitations: Array.isArray(movement?.limitations) ? movement.limitations.slice(0, 6).map(text).filter(Boolean) : [],
    },
    market: market && typeof market === 'object' ? market : null,
    dataQuality: company?.dataQuality && typeof company.dataQuality === 'object' ? company.dataQuality : {},
    freshness: {
      fetchedAt: text(company?.fetchedAt || movement?.fetchedAt),
      companySource: text(company?.source),
      movementSource: text(movement?.source),
    },
    evidence,
  };

  return context;
}

function validateNseIntelligenceContext(context) {
  const evidence = Array.isArray(context?.evidence) ? context.evidence : [];
  const ids = evidence.map(item => text(item?.id)).filter(Boolean);
  const uniqueIds = new Set(ids);
  const missingIds = evidence.filter(item => !text(item?.id)).length;
  return {
    valid: missingIds === 0 && ids.length === uniqueIds.size,
    evidenceCount: evidence.length,
    duplicateIds: ids.filter((id, index) => ids.indexOf(id) !== index).filter((id, index, all) => all.indexOf(id) === index),
    missingIds,
  };
}

module.exports = {
  buildNseIntelligenceContext,
  validateNseIntelligenceContext,
};
