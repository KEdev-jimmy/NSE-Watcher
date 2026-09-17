const RSS_SOURCES = [
  {
    id: 'business-daily',
    name: 'Business Daily Africa',
    kind: 'rss',
    feedUrl: 'https://www.businessdailyafrica.com/bd/rss.xml',
    homepage: 'https://www.businessdailyafrica.com/',
    maxItems: 20,
  },
  {
    id: 'standard-business',
    name: 'The Standard Business',
    kind: 'rss',
    feedUrl: 'https://www.standardmedia.co.ke/rss/business.php',
    homepage: 'https://www.standardmedia.co.ke/',
    maxItems: 20,
  },
];

function decodeXml(value) {
  return String(value || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/gi, '$1')
    .replace(/&#(\d+);/g, (_, code) => {
      try { return String.fromCodePoint(Number(code)); } catch (_) { return ''; }
    })
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => {
      try { return String.fromCodePoint(parseInt(code, 16)); } catch (_) { return ''; }
    })
    .replace(/&quot;/gi, '"')
    .replace(/&apos;/gi, "'")
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>');
}

function cleanText(value) {
  return decodeXml(String(value || ''))
    .replace(/<br\s*\/?>(?=\s*)/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
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

  return blocks.map((block) => {
    const title = cleanText(tagValue(block, 'title'));
    const summary = cleanText(
      tagValue(block, 'description') ||
      tagValue(block, 'summary') ||
      tagValue(block, 'content:encoded') ||
      tagValue(block, 'content')
    );
    const publishedAt = (
      tagValue(block, 'pubDate') ||
      tagValue(block, 'published') ||
      tagValue(block, 'updated') ||
      tagValue(block, 'dc:date')
    ).trim();

    return {
      title,
      summary: summary.slice(0, 1000),
      publishedAt,
      url: linkValue(block),
      imageUrl: imageValue(block),
      source: source.name,
      sourceId: source.id,
      sourceKind: source.kind,
    };
  }).filter((item) => item.title && item.url);
}

async function fetchRssSource(source) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8_000);

  try {
    const response = await fetch(source.feedUrl, {
      headers: {
        Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8',
        'User-Agent': 'NSE-Watcher/1.0 (+https://nse-watcher.vercel.app)',
      },
      signal: controller.signal,
    });

    if (!response.ok) throw new Error(`${source.name} HTTP ${response.status}`);
    const xml = await response.text();
    return parseRss(xml, source).slice(0, source.maxItems);
  } finally {
    clearTimeout(timer);
  }
}

async function loadRssSources() {
  const results = await Promise.allSettled(RSS_SOURCES.map(fetchRssSource));
  const items = [];
  const errors = [];

  results.forEach((result, index) => {
    const source = RSS_SOURCES[index];
    if (result.status === 'fulfilled') {
      items.push(...result.value);
    } else {
      errors.push(`${source.name}: ${result.reason?.message || 'request failed'}`);
    }
  });

  return { items, errors };
}

function sourceRegistry() {
  return RSS_SOURCES.map(({ id, name, kind, feedUrl, homepage }) => ({
    id, name, kind, feedUrl, homepage,
  }));
}

module.exports = { RSS_SOURCES, loadRssSources, sourceRegistry };
