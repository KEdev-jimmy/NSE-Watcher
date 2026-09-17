# NSE Watcher Company Intelligence

The existing Android Company Intelligence screen is kept intact. The backend now enriches the existing UI contract instead of adding a second company-data screen.

## Evidence layers

1. **MyStocks Africa** — primary company profile, delayed market context, company news/corporate actions and dividend history.
2. **StockAnalysis / S&P Global Market Intelligence** — fallback structured financials, comparable financial history, valuation ratios, profitability/leverage ratios and dividend history when the primary provider does not return those fields.
3. **Issuer investor relations** — official company source links are attached for supported issuers so the evidence chain can be checked against company disclosures. Current mappings include Safaricom, Equity Group, KCB Group, Absa Bank Kenya, EABL and CIC Group.

MyStocks' documented Partner API exposes company profiles, company news, dividends and market data. The company profile endpoint is documented as returning key financials, but it does not expose a dedicated financial-history endpoint in the documented market-data surface, so the enrichment layer does not invent historical series when the primary provider omits them.

## Existing Android sections now backed by the enrichment

- Business
- Financial health
- Growth
- Valuation
- Dividends
- Market behaviour
- What changed?
- Risks to investigate
- Intelligence signals
- Balanced evidence perspective

No Android layout redesign is required for the enrichment.

## Reliability rules

- A secondary value is only used when the primary value is blank.
- Source names and source URLs are retained in the backend evidence objects.
- Financial history is returned oldest-to-newest so the deterministic intelligence engine can compare the latest two periods correctly.
- Missing data remains missing; no synthetic financial figures are generated.
- Provider failures are isolated and exposed through `providerStatus` / `providerErrors`.
- The response is cached for 10 minutes with stale-while-revalidate for 30 minutes.

## Remaining expansion

The next Company Intelligence phase can add a wider issuer-IR registry, official NSE/CMA filing linkage per company, cross-source event matching, explicit filing/document timelines, and a deterministic `Why is this stock moving?` evidence chain combining price, company events, news, sector and market movement.
