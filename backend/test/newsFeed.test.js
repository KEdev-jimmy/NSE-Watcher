const assert = require('node:assert/strict');
const test = require('node:test');

const handleNews = require('../lib/newsFeed');
const { canonicalUrl, dedupe, normalizeItem, withinWindow, newsFreshnessMode } = handleNews._newsTest;

test('news URLs are canonicalized without tracking parameters', () => {
  assert.equal(canonicalUrl('https://Example.com/article/?utm_source=rss&fbclid=123#section'), 'https://example.com/article');
});

test('duplicate syndicated stories are removed across different sources', () => {
  const items = [
    { title: 'Safaricom reports higher earnings', publishedAt: '2026-09-18T08:00:00Z', url: 'https://a.example/story?utm_source=rss', sourceId: 'source-a' },
    { title: 'Safaricom reports higher earnings', publishedAt: '2026-09-18T08:00:00Z', url: 'https://b.example/story', sourceId: 'source-b' },
    { title: 'Safaricom reports higher earnings', publishedAt: '2026-09-19T08:00:00Z', url: 'https://c.example/story', sourceId: 'source-c' },
  ];
  const result = dedupe(items);
  assert.equal(result.length, 2);
  assert.equal(result[0].sourceId, 'source-a');
  assert.equal(result[1].sourceId, 'source-c');
});

test('malformed news items are rejected during normalization', () => {
  assert.equal(normalizeItem(null), null);
  assert.equal(normalizeItem({ url: 'https://example.com/story' }), null);
  assert.equal(normalizeItem({ title: 'Story', url: 'http://example.com/story' }).url, '');
});

test('publication timestamps are preserved and freshness is explicit', () => {
  const publishedAt = '2026-09-18T10:30:00Z';
  const item = normalizeItem({ title: 'NSE trading update', summary: 'Listed shares traded actively.', publishedAt, url: 'https://example.com/story', source: 'Example', sourceId: 'example', sourceKind: 'rss' });
  assert.equal(item.publishedAt, publishedAt);
  assert.equal(item.freshnessMode, 'WITHIN_WINDOW');
  assert.equal(newsFreshnessMode('not-a-date'), 'UNKNOWN');
  assert.equal(withinWindow({ publishedAt: 'not-a-date' }), true);
});

test('duplicate URLs are removed even when their source IDs differ', () => {
  const result = dedupe([
    { title: 'One', publishedAt: '2026-09-18T08:00:00Z', url: 'https://example.com/story?utm_source=x', sourceId: 'a' },
    { title: 'Two', publishedAt: '2026-09-18T09:00:00Z', url: 'https://example.com/story', sourceId: 'b' },
  ]);
  assert.equal(result.length, 1);
});