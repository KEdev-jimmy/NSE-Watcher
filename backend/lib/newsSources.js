const RSS_SOURCES = [
  { id: 'business-daily', name: 'Business Daily Africa', kind: 'rss', feedUrl: 'https://www.businessdailyafrica.com/bd/rss.xml', homepage: 'https://www.businessdailyafrica.com/', maxItems: 20 },
  { id: 'standard-business', name: 'The Standard Business', kind: 'rss', feedUrl: 'https://www.standardmedia.co.ke/rss/business.php', homepage: 'https://www.standardmedia.co.ke/', maxItems: 20 },
  { id: 'kbc', name: 'KBC Digital', kind: 'rss', feedUrl: 'https://www.kbc.co.ke/feed/', homepage: 'https://www.kbc.co.ke/', maxItems: 20 },
  { id: 'capital-fm', name: 'Capital FM Kenya', kind: 'rss', feedUrl: 'https://www.capitalfm.co.ke/news/feed/', homepage: 'https://www.capitalfm.co.ke/news/', maxItems: 20 },
];

const WEB_SOURCES = [
  { id: 'citizen-digital-business', name: 'Citizen Digital', kind: 'web', pageUrl: 'https://citizen.digital/business/', homepage: 'https://citizen.digital/', maxItems: 20, linkPatterns: [/^\/article\//i, /^\/business\//i] },
  { id: 'the-star-business', name: 'The Star Kenya', kind: 'web', pageUrl: 'https://www.the-star.co.ke/business/', homepage: 'https://www.the-star.co.ke/', maxItems: 20, linkPatterns: [/^\/business\//i] },
  { id: 'capital-fm-nse', name: 'Capital FM - NSE', kind: 'web', pageUrl: 'https://www.capitalfm.co.ke/business/nse/', homepage: 'https://www.capitalfm.co.ke/business/', maxItems: 15, linkPatterns: [/^\/business\//i] },
  { id: 'cma-kenya', name: 'Capital Markets Authority Kenya', kind: 'web', pageUrl: 'https://www.cma.or.ke/', homepage: 'https://www.cma.or.ke/', maxItems: 20, linkPatterns: [/\/news/i, /\/press/i, /\/public/i, /\/notice/i, /\.pdf(?:$|\?)/i], titlePatterns: [/announcement/i, /notice/i, /market/i, /capital/i, /fund/i, /licen[cs]/i, /etf/i, /reit/i, /intermediary/i, /invest/i] },
  { id: 'nse-kenya', name: 'Nairobi Securities Exchange', kind: 'web', pageUrl: 'https://www.nse.co.ke/', homepage: 'https://www.nse.co.ke/', maxItems: 30, linkPatterns: [/\/wp-content\/uploads\//i, /announcement/i, /notice/i, /listed/i, /corporate/i, /market/i, /issuer/i], titlePatterns: [/announcement/i, /notice/i, /financial/i, /results/i, /dividend/i, /agm/i, /corporate action/i, /listing/i, /rights/i, /bond/i, /circular/i, /issuer/i] },
  { id: 'safaricom-ir', name: 'Safaricom Investor Relations', kind: 'company-ir', pageUrl: 'https://www.safaricom.co.ke/investor-relations-landing/meetings', homepage: 'https://www.safaricom.co.ke/investor-relations-landing/', maxItems: 15, linkPatterns: [/investor-relations/i, /reports/i, /meetings/i, /\.pdf(?:$|\?)/i], titlePatterns: [/notice/i, /dividend/i, /results/i, /report/i, /board/i, /agm/i, /shareholder/i, /investor/i], symbol: 'SCOM', companyName: 'Safaricom' },
  { id: 'equity-ir', name: 'Equity Group Investor Relations', kind: 'company-ir', pageUrl: 'https://equitygroupholdings.com/investor-relations/', homepage: 'https://equitygroupholdings.com/', maxItems: 15, linkPatterns: [/investor/i, /financial/i, /annual/i, /\.pdf(?:$|\?)/i], titlePatterns: [/results/i, /financial/i, /report/i, /dividend/i, /agm/i, /notice/i, /investor/i], symbol: 'EQTY', companyName: 'Equity Group' },
  { id: 'kcb-ir', name: 'KCB Group Investor Relations', kind: 'company-ir', pageUrl: 'https://www.kcbgroup.com/investor-relations', homepage: 'https://www.kcbgroup.com/', maxItems: 15, linkPatterns: [/investor/i, /financial/i, /report/i, /\.pdf(?:$|\?)/i], titlePatterns: [/results/i, /financial/i, /report/i, /dividend/i, /shareholder/i, /agm/i, /notice/i, /investor/i], symbol: 'KCB', companyName: 'KCB Group' },
  { id: 'absa-ir', name: 'Absa Bank Kenya Investor Relations', kind: 'company-ir', pageUrl: 'https://www.absabank.co.ke/investor-relations/', homepage: 'https://www.absabank.co.ke/', maxItems: 15, linkPatterns: [/investor/i, /annual/i, /financial/i, /\.pdf(?:$|\?)/i], titlePatterns: [/results/i, /financial/i, /report/i, /dividend/i, /agm/i, /announcement/i, /notice/i], symbol: 'ABSA', companyName: 'Absa Bank Kenya' },
  { id: 'eabl-ir', name: 'EABL Investor Relations', kind: 'company-ir', pageUrl: 'https://www.eabl.com/investors/announcements', homepage: 'https://www.eabl.com/investors', maxItems: 15, linkPatterns: [/investors/i, /announcement/i, /press/i, /financial/i, /\.pdf(?:$|\?)/i], titlePatterns: [/results/i, /financial/i, /report/i, /dividend/i, /board/i, /announcement/i, /agm/i], symbol: 'EABL', companyName: 'East African Breweries' },
];

// Reuters supplies authenticated RSS/API delivery to licensed clients. Do not
// scrape Reuters or invent an unauthorised endpoint. A licensed feed can be
// enabled in Vercel with REUTERS_RSS_URL.
const REUTERS_RSS_URL = String(process.env.REUTERS_RSS_URL || '').trim();
if (REUTERS_RSS_URL) {
  RSS_SOURCES.push({ id: 'reuters-licensed', name: 'Reuters', kind: 'rss-licensed', feedUrl: REUTERS_RSS_URL, homepage: 'https://www.reuters.com/', maxItems: 20 });
}

function decodeXml(value) {
  return String(value || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/gi, '$1')
    .replace(/&#(\d+);/g, (_, code) => { try { return String.fromCodePoint(Number(code)); } catch (_) { return ''; } })
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => { try { return String.fromCodePoint(parseInt(code, 16)); } catch (_) { return ''; } })
    .replace(/&quot;/gi, '"').replace(/&apos;/gi, "'").replace(/&amp;/gi, '&').replace(/&lt;/gi, '<').replace(/&gt;/gi, '>');
}

function cleanText(value) {
  return decodeXml(String(value || '')).replace(/<br\s*\/?>(?=\s*)/gi, '\n').replace(/<\/p>/gi, '\n').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
}

function tagValue(block, tag) {
  const escaped = tag.replace(':', '\\:');
  const match = block.match(new RegExp(`<${escaped}(?:\\s[^>]*)?>([\\s\\S]*?)</${escaped}>`, 'i'));
  return match ? decodeXml(match[1]).trim() : '';
}

function linkValue(block) {
  const direct = tagValue(block, 'link');
  if (direct) return cleanText(direct);
  const attr = block.match(/<link\b[^>]*\bhref=["']([^"']+)["'][^>]*\/?>(?:<\/link>)?/i);
  return attr ? decodeXml(attr[1]).trim() : '';
}

function imageValue(block) {
  const media = block.match(/<(?:media:content|media:thumbnail|enclosure)\b[^>]*\burl=["']([^"']+)["'][^>]*\/?>(?:<\/[^>]+>)?/i);
  return media ? decodeXml(media[1]).trim() : '';
}

function parseRss(xml, source) {
  const blocks = [];
  const itemRe = /<(item|entry)\b[^>]*>([\s\S]*?)<\/\1>/gi;
  let match;
  while ((match = itemRe.exec(xml))) blocks.push(match[2]);
  return blocks.map(block => ({
    title: cleanText(tagValue(block, 'title')),
    summary: cleanText(tagValue(block, 'description') || tagValue(block, 'summary') || tagValue(block, 'content:encoded') || tagValue(block, 'content')).slice(0, 1000),
    publishedAt: (tagValue(block, 'pubDate') || tagValue(block, 'published') || tagValue(block, 'updated') || tagValue(block, 'dc:date')).trim(),
    url: linkValue(block), imageUrl: imageValue(block), source: source.name, sourceId: source.id, sourceKind: source.kind,
  })).filter(item => item.title && item.url);
}

function absoluteUrl(href, baseUrl) {
  try { return new URL(href, baseUrl).toString(); } catch (_) { return ''; }
}

function parseHtmlLinks(html, source) {
  const results = [];
  const anchorRe = /<a\b([^>]*?)href=["']([^"']+)["']([^>]*)>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = anchorRe.exec(html))) {
    const href = absoluteUrl(match[2], source.pageUrl);
    const title = cleanText(match[4]);
    if (!href || !title || title.length < 12) continue;
    let relative = '';
    try { relative = new URL(href).pathname; } catch (_) {}
    if (source.linkPatterns && !source.linkPatterns.some(pattern => pattern.test(relative) || pattern.test(href))) continue;
    if (source.titlePatterns && !source.titlePatterns.some(pattern => pattern.test(title))) continue;
    if (href === source.pageUrl || href === source.homepage) continue;
    results.push({ title, summary: '', publishedAt: '', url: href, imageUrl: '', source: source.name, sourceId: source.id, sourceKind: source.kind, symbol: source.symbol || '', companyName: source.companyName || '' });
  }
  return results.slice(0, source.maxItems);
}

async function fetchText(url, timeoutMs = 8_000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { headers: { Accept: 'application/rss+xml, application/atom+xml, application/xml, text/html;q=0.9, */*;q=0.8', 'User-Agent': 'NSE-Watcher/1.0 (+https://nse-watcher.vercel.app)' }, signal: controller.signal });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.text();
  } finally { clearTimeout(timer); }
}

async function fetchRssSource(source) { return parseRss(await fetchText(source.feedUrl), source).slice(0, source.maxItems); }
async function fetchWebSource(source) { return parseHtmlLinks(await fetchText(source.pageUrl), source); }

async function loadNewsSources() {
  const jobs = [
    ...RSS_SOURCES.map(source => ({ source, run: () => fetchRssSource(source) })),
    ...WEB_SOURCES.map(source => ({ source, run: () => fetchWebSource(source) })),
  ];
  const results = await Promise.allSettled(jobs.map(job => job.run()));
  const items = [];
  const errors = [];
  results.forEach((result, index) => {
    const source = jobs[index].source;
    if (result.status === 'fulfilled') items.push(...result.value);
    else errors.push(`${source.name}: ${result.reason?.message || 'request failed'}`);
  });
  return { items, errors };
}

function sourceRegistry() {
  return [...RSS_SOURCES, ...WEB_SOURCES].map(source => ({ id: source.id, name: source.name, kind: source.kind, feedUrl: source.feedUrl || null, pageUrl: source.pageUrl || null, homepage: source.homepage, configured: source.kind !== 'rss-licensed' || Boolean(REUTERS_RSS_URL) }));
}

module.exports = { RSS_SOURCES, WEB_SOURCES, loadNewsSources, loadRssSources: loadNewsSources, sourceRegistry };
