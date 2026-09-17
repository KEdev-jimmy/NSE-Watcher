const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const OPENAI_MODEL = process.env.OPENAI_MODEL || 'gpt-5.6-luna';
const APP_BASE_URL = process.env.APP_BASE_URL || 'https://nse-watcher.vercel.app';

function json(res, status, body) {
  res.status(status).setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  res.end(JSON.stringify(body));
}

async function loadCompany(symbol) {
  const url = `${APP_BASE_URL}/api/company?action=intelligence&symbol=${encodeURIComponent(symbol)}`;
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = { error: 'Invalid company intelligence response' }; }
  if (!response.ok) {
    const error = new Error(data.error || `Company intelligence ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return data;
}

function buildEvidencePacket(data) {
  return {
    symbol: data.symbol,
    source: data.source,
    fetchedAt: data.fetchedAt,
    profile: data.profile || null,
    financialHistory: data.financialHistory || [],
    dividends: data.dividends || [],
    evidence: data.evidence || [],
    dataQuality: data.dataQuality || {},
  };
}

function systemInstructions() {
  return [
    'You are NSE Watcher Analyst, an evidence-grounded financial research assistant.',
    'Use ONLY the supplied evidence packet. Do not use unstated facts, memory, guesses, or invented explanations.',
    'Do not give BUY, SELL, HOLD, target-price, or guaranteed-return instructions.',
    'Clearly separate documented facts from interpretation.',
    'If evidence is missing or conflicting, say so explicitly.',
    'Every material factual statement must cite one or more evidence IDs such as [E1].',
    'Use this response structure: What is happening; What the evidence says; What may matter; Risks/questions to investigate; What is unknown; Sources used.',
    'For possible causes of price movement, use cautious language such as may, could, or cannot be established from the supplied evidence.',
    'Do not manufacture financial-history trends when only a current snapshot is available.',
  ].join(' ');
}

async function askOpenAI(question, packet) {
  if (!OPENAI_API_KEY) return null;

  const evidenceText = JSON.stringify(packet, null, 2);
  const input = `${systemInstructions()}\n\nUser question: ${question}\n\nEvidence packet:\n${evidenceText}`;

  const response = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${OPENAI_API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: OPENAI_MODEL,
      input,
    }),
  });

  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = { error: text }; }
  if (!response.ok) {
    const error = new Error(data.error?.message || `AI service ${response.status}`);
    error.status = response.status;
    throw error;
  }

  const answer = typeof data.output_text === 'string'
    ? data.output_text.trim()
    : Array.isArray(data.output)
      ? data.output.flatMap(item => item.content || []).map(item => item.text || '').filter(Boolean).join('\n').trim()
      : '';

  return {
    answer,
    model: OPENAI_MODEL,
    responseId: data.id || null,
  };
}

module.exports = async (req, res) => {
  if (req.method !== 'POST') return json(res, 405, { error: 'POST only' });

  const body = req.body || {};
  const symbol = String(body.symbol || '').trim().toUpperCase();
  const question = String(body.question || '').trim();

  if (!symbol) return json(res, 400, { error: 'symbol is required' });
  if (!question) return json(res, 400, { error: 'question is required' });
  if (question.length > 1200) return json(res, 400, { error: 'question is too long' });

  try {
    const company = await loadCompany(symbol);
    const packet = buildEvidencePacket(company);

    if (!OPENAI_API_KEY) {
      return json(res, 200, {
        aiAvailable: false,
        message: 'AI Analyst is not enabled yet. The evidence packet is ready for the AI layer.',
        evidencePacket: packet,
      });
    }

    const result = await askOpenAI(question, packet);
    return json(res, 200, {
      aiAvailable: true,
      symbol: company.symbol || symbol,
      fetchedAt: company.fetchedAt,
      evidenceCount: packet.evidence.length,
      ...result,
    });
  } catch (error) {
    return json(res, error.status || 502, {
      error: 'AI Analyst unavailable',
      detail: error.message,
    });
  }
};
