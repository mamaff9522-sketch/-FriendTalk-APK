import express from 'express';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'friendtalk-4e623';
const API_KEY = process.env.FIREBASE_WEB_API_KEY;
if (!API_KEY) { console.error('FIREBASE_WEB_API_KEY missing'); process.exit(1); }
initializeApp({ credential: applicationDefault(), projectId: PROJECT_ID });
const auth = getAuth();

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', false);
app.use(express.json({ limit: '8kb' }));

// Basic in-memory per-IP rate limit (per instance)
const WINDOW_MS = 60_000, MAX_REQ = Number(process.env.RATE_LIMIT_PER_MIN || 20);
const hits = new Map();
setInterval(() => { const now = Date.now(); for (const [k, v] of hits) if (now - v.start > WINDOW_MS) hits.delete(k); }, WINDOW_MS).unref();
// Cloud Run appends the real caller IP as the LAST X-Forwarded-For entry
// (earlier entries are client-supplied and spoofable). IPv6 is bucketed per /64
// because many carriers/VPNs rotate addresses inside one prefix.
function clientIp(req) {
  const parts = String(req.get('x-forwarded-for') || '').split(',').map(s => s.trim()).filter(Boolean);
  const ip = parts.length ? parts[parts.length - 1] : (req.socket.remoteAddress || 'unknown');
  if (ip.includes(':')) {
    const [head, tail = ''] = ip.split('::');
    const full = [...head.split(':').filter(Boolean)];
    if (ip.includes('::')) { const t = tail.split(':').filter(Boolean); while (full.length + t.length < 8) full.push('0'); full.push(...t); }
    return 'v6:' + full.slice(0, 4).join(':');
  }
  return ip;
}
function rateLimit(req, res, next) {
  const ip = clientIp(req), now = Date.now();
  let e = hits.get(ip);
  if (!e || now - e.start > WINDOW_MS) { e = { start: now, count: 0 }; hits.set(ip, e); }
  e.count++;
  res.set('X-RateLimit-Remaining', String(Math.max(0, MAX_REQ - e.count)));
  if (e.count > MAX_REQ) { res.set('Retry-After', String(Math.ceil((e.start + WINDOW_MS - now) / 1000))); return res.status(429).json({ error: 'RATE_LIMITED' }); }
  next();
}

const ERR = { EMAIL_EXISTS: 409, EMAIL_NOT_FOUND: 401, INVALID_PASSWORD: 401, INVALID_LOGIN_CREDENTIALS: 401, USER_DISABLED: 403,
  TOKEN_EXPIRED: 401, INVALID_REFRESH_TOKEN: 401, USER_NOT_FOUND: 401, INVALID_EMAIL: 400, MISSING_PASSWORD: 400,
  TOO_MANY_ATTEMPTS_TRY_LATER: 429, OPERATION_NOT_ALLOWED: 403 };
async function callGoogle(url, body, form = false) {
  const r = await fetch(url, { method: 'POST',
    headers: { 'content-type': form ? 'application/x-www-form-urlencoded' : 'application/json' },
    body: form ? new URLSearchParams(body).toString() : JSON.stringify(body) });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) {
    const code = String(data?.error?.message || 'AUTH_ERROR').split(' ')[0].split(':')[0];
    const e = new Error(code); e.status = ERR[code] || (code.startsWith('WEAK_PASSWORD') ? 400 : 400); throw e;
  }
  return data;
}
const IDT = 'https://identitytoolkit.googleapis.com/v1/accounts';
function creds(req) {
  const { email, password } = req.body || {};
  if (typeof email !== 'string' || typeof password !== 'string' || !email || !password || email.length > 254 || password.length > 128) return null;
  return { email: email.trim(), password };
}
const wrap = fn => async (req, res) => { try { await fn(req, res); } catch (e) { res.status(e.status || 500).json({ error: e.status ? e.message : 'INTERNAL' }); if (!e.status) console.error('error', e.message); } };

app.get('/health', (_req, res) => res.json({ ok: true, service: 'friendtalk-auth' }));

app.post('/auth/signup', rateLimit, wrap(async (req, res) => {
  const c = creds(req); if (!c) return res.status(400).json({ error: 'EMAIL_AND_PASSWORD_REQUIRED' });
  const d = await callGoogle(`${IDT}:signUp?key=${API_KEY}`, { ...c, returnSecureToken: true });
  res.status(201).json({ uid: d.localId, email: d.email, idToken: d.idToken, refreshToken: d.refreshToken, expiresIn: Number(d.expiresIn) });
}));

app.post('/auth/login', rateLimit, wrap(async (req, res) => {
  const c = creds(req); if (!c) return res.status(400).json({ error: 'EMAIL_AND_PASSWORD_REQUIRED' });
  const d = await callGoogle(`${IDT}:signInWithPassword?key=${API_KEY}`, { ...c, returnSecureToken: true });
  res.json({ uid: d.localId, email: d.email, idToken: d.idToken, refreshToken: d.refreshToken, expiresIn: Number(d.expiresIn) });
}));

app.post('/auth/refresh', rateLimit, wrap(async (req, res) => {
  const rt = req.body?.refreshToken;
  if (typeof rt !== 'string' || !rt || rt.length > 2048) return res.status(400).json({ error: 'REFRESH_TOKEN_REQUIRED' });
  const d = await callGoogle(`https://securetoken.googleapis.com/v1/token?key=${API_KEY}`, { grant_type: 'refresh_token', refresh_token: rt }, true);
  res.json({ uid: d.user_id, idToken: d.id_token, refreshToken: d.refresh_token, expiresIn: Number(d.expires_in) });
}));

app.post('/auth/logout', rateLimit, wrap(async (req, res) => {
  const m = /^Bearer (.+)$/.exec(req.get('authorization') || '');
  if (!m) return res.status(401).json({ error: 'MISSING_BEARER_TOKEN' });
  let decoded;
  try { decoded = await auth.verifyIdToken(m[1]); } catch { return res.status(401).json({ error: 'INVALID_TOKEN' }); }
  await auth.revokeRefreshTokens(decoded.uid);
  res.json({ ok: true, uid: decoded.uid, revoked: true });
}));

app.use((_req, res) => res.status(404).json({ error: 'NOT_FOUND' }));
const port = Number(process.env.PORT || 8080);
app.listen(port, () => console.log(`friendtalk-auth listening on ${port}`));
