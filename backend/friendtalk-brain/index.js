import express from 'express';
import admin from 'firebase-admin';
import crypto from 'node:crypto';
import { fetchKeys, verifySsv } from './ssv.js';
admin.initializeApp({ databaseURL: process.env.DATABASE_URL || 'https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app' });
const dbProxy = () => admin.database();
const db = { ref: p => dbProxy().ref(p) };
const app = express();
app.disable('x-powered-by');
const CORS = (process.env.CORS_ORIGINS || '').split(',').map(x => x.trim()).filter(Boolean);
app.use((req, res, next) => {
  res.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  res.set('X-Content-Type-Options', 'nosniff');
  const o = req.headers.origin;
  if (o && CORS.includes(o)) { res.set('Access-Control-Allow-Origin', o); res.set('Vary', 'Origin'); res.set('Access-Control-Allow-Headers', 'Authorization, Content-Type'); res.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE'); }
  if (req.method === 'OPTIONS') return res.sendStatus(o && CORS.includes(o) ? 204 : 403);
  next();
});
app.use(express.json({ limit: '128kb' }));
const now = () => admin.database.ServerValue.TIMESTAMP;
app.get('/health', (_q, r) => r.json({ ok: true, service: 'friendtalk-brain' }));
async function auth(req, res, next) {
  const m = (req.headers.authorization || '').match(/^Bearer (.+)$/);
  if (!m) return res.status(401).json({ error: 'missing token' });
  try {
    const t = await admin.auth().verifyIdToken(m[1], true);
    req.uid = t.uid;
    if (req.method !== 'GET' && t.firebase?.sign_in_provider === 'password' && t.email_verified !== true) return res.status(403).json({ error: 'email not verified' });
    next();
  }
  catch { res.status(401).json({ error: 'invalid token' }); }
}
async function notBanned(req, res, next) {
  const b = (await db.ref(`bans/${req.uid}`).get()).val();
  if (b && (!b.until || b.until > Date.now())) return res.status(403).json({ error: 'banned' });
  next();
}
const PERMS = ['coins_confirm','age_approve','config_edit','ban_users','manage_content'];
async function loadRole(uid) {
  const r = (await db.ref(`roles/${uid}`).get()).val();
  if (!r) return null;
  if (typeof r === 'string') return { role: r, permissions: {} }; // legacy string role
  return { role: r.role, permissions: r.permissions || {} };
}
const can = (r, p) => !!r && (r.role === 'superadmin' || (r.role === 'admin' && (r.permissions[p] === true)));
async function isAdmin(req, res, next) {
  const r = await loadRole(req.uid);
  if (!r || !['admin', 'superadmin'].includes(r.role)) return res.status(403).json({ error: 'admin only' });
  req.role = r; next();
}
const perm = p => (req, res, next) => can(req.role, p) ? next() : res.status(403).json({ error: `missing permission ${p}` });
async function isSuper(req, res, next) {
  const r = await loadRole(req.uid);
  if (r?.role !== 'superadmin') return res.status(403).json({ error: 'superadmin only' });
  req.role = r; next();
}
const w = fn => (q, r, n) => fn(q, r, n).catch(e => { console.error(e.message); r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' }); });
app.use(['/coins', '/age', '/config', '/admin', '/superadmin', '/me', '/match', '/bot', '/ui', '/chats', '/swipe', '/posts', '/feed', '/friends', '/radar', '/shake', '/limits', '/quota', '/search'], w(auth));
app.get('/me', w(async (q, r) => {
  const role = await loadRole(q.uid);
  r.json({ uid: q.uid, role: role?.role || 'user', permissions: role?.permissions || {} });
}));
app.use('/admin', w(isAdmin));
app.use('/superadmin', w(isSuper));
app.post('/coins/purchase', w(notBanned), w(async (q, r) => {
  const { packageId, paymentRef } = q.body || {};
  if (typeof packageId !== 'string' || typeof paymentRef !== 'string') return r.status(400).json({ error: 'packageId and paymentRef required' });
  const pkg = (await db.ref(`coinPackages/${packageId}`).get()).val();
  const ref = db.ref(`coinTransactions/${q.uid}`).push();
  await ref.set({ type: 'purchase', packageId, paymentRef, coins: pkg?.coins ?? null, status: 'pending', createdAt: now() });
  r.status(202).json({ txId: ref.key, status: 'pending' }); // never credits coins
}));
app.post('/admin/coins/confirm', perm('coins_confirm'), w(notBanned), w(async (q, r) => {
  const { uid, txId } = q.body || {};
  const txRef = db.ref(`coinTransactions/${uid}/${txId}`);
  const stamp = Date.now();
  const res = await txRef.transaction(t => { if (t === null) return null; if (t.status !== 'pending') return; return { ...t, status: 'paid', confirmedBy: q.uid, confirmedAt: stamp }; });
  const v = res.snapshot.val();
  if (!res.committed || !v || v.status !== 'paid' || v.confirmedAt !== stamp) return r.status(409).json({ error: 'tx not found or not pending' });
  const coins = Number(v.coins) || 0;
  await db.ref(`wallets/${uid}/coins`).transaction(c => (c || 0) + coins);
  r.json({ status: 'paid', credited: coins });
}));
app.post('/age/verify', w(notBanned), w(async (q, r) => {
  const { birthDate, documentRef } = q.body || {};
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate || '') || typeof documentRef !== 'string') return r.status(400).json({ error: 'birthDate (YYYY-MM-DD) and documentRef required' });
  await db.ref(`kyc/${q.uid}`).set({ birthDate, documentRef, status: 'pending', submittedAt: now() });
  r.status(202).json({ status: 'pending' }); // never auto-approves
}));
app.post('/admin/age/approve', perm('age_approve'), w(notBanned), w(async (q, r) => {
  const { uid, approved } = q.body || {};
  if (!uid || typeof approved !== 'boolean') return r.status(400).json({ error: 'uid and approved required' });
  await db.ref(`kyc/${uid}`).update({ status: approved ? 'approved' : 'rejected', reviewedBy: q.uid, reviewedAt: now() });
  r.json({ status: approved ? 'approved' : 'rejected' });
}));
app.get('/config', w(async (_q, r) => r.json((await db.ref('appConfig').get()).val() || {})));
app.put('/admin/config', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, u = {};
  if (typeof b.maintenanceMode === 'boolean') u.maintenanceMode = b.maintenanceMode;
  if (typeof b.maintenanceMessage === 'string') u.maintenanceMessage = b.maintenanceMessage;
  if (typeof b.minVersion === 'string' || typeof b.minVersion === 'number') u.minVersion = b.minVersion;
  if (!Object.keys(u).length) return r.status(400).json({ error: 'nothing to update' });
  await db.ref('appConfig').update(u); r.json(u);
}));
app.get('/superadmin/admins', w(async (_q, r) => r.json((await db.ref('roles').get()).val() || {})));
app.post('/superadmin/admins', w(notBanned), w(async (q, r) => {
  let { uid, email, permissions } = q.body || {};
  if (!uid && email) uid = (await admin.auth().getUserByEmail(email).catch(() => null))?.uid;
  if (!uid) return r.status(400).json({ error: 'valid uid or email required' });
  await admin.auth().getUser(uid).catch(() => { throw Object.assign(new Error('nouser'), { code: 404 }); });
  const cur = await loadRole(uid);
  if (cur?.role === 'superadmin') return r.status(409).json({ error: 'target is superadmin; cannot change via this endpoint' });
  const p = {}; for (const k of PERMS) p[k] = permissions?.[k] === true;
  const entry = { role: 'admin', permissions: p, grantedBy: q.uid, grantedAt: Date.now() };
  await db.ref(`roles/${uid}`).set(entry); r.json({ uid, ...entry });
}));
app.delete('/superadmin/admins/:uid', w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, ref = db.ref('roles');
  let blocked = false;
  const res = await ref.transaction(all => {
    if (!all || !all[uid]) return all;
    const role = typeof all[uid] === 'string' ? all[uid] : all[uid].role;
    if (role === 'superadmin') {
      const supers = Object.values(all).filter(v => (typeof v === 'string' ? v : v?.role) === 'superadmin').length;
      if (supers <= 1) { blocked = true; return; }
    }
    delete all[uid]; return all;
  });
  if (blocked) return r.status(409).json({ error: 'cannot remove the last superadmin' });
  if (!res.snapshot.child(uid).exists()) return r.json({ revoked: uid }); r.status(409).json({ error: 'not revoked' });
}));
// ===== AI chat characters (clearly labeled AI; free; never take coins/gifts) =====
const GEMINI_MODEL = process.env.GEMINI_MODEL || 'gemini-2.5-flash-lite';
const PROJECT = process.env.GOOGLE_CLOUD_PROJECT || 'friendtalk-4e623';
const QUEUE_TTL_MS = 60_000;
const botsEnabled = async () => (await db.ref('appConfig/aiBotsEnabled').get()).val() === true;
const publicBot = (id, b) => ({ id, name: b.name, avatarUrl: b.avatarUrl, bio: b.bio, interests: b.interests || [], enabled: b.enabled === true, isAI: true });
async function gcpToken() {
  const res = await fetch('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', { headers: { 'Metadata-Flavor': 'Google' } });
  if (!res.ok) throw new Error('metadata token ' + res.status);
  return (await res.json()).access_token;
}
async function bump(path, limit) {
  let over = false;
  await db.ref(path).transaction(c => { c = c || 0; if (c >= limit) { over = true; return; } return c + 1; });
  return !over;
}
// ===== Admin-configurable limits (appConfig/limits). Values here are ONLY the initial seed. =====
const FEATURES = ['aiBotMessagesPerDay', 'radarPerDay', 'shakePerDay', 'randomMatchPerDay', 'searchPerDay', 'personalityMatchPerDay', 'chatMessagesPerDay'];
const SEED_LIMITS = {
  tiers: {
    free: { aiBotMessagesPerDay: 20, radarPerDay: 50, shakePerDay: 50, randomMatchPerDay: 50, searchPerDay: 50, personalityMatchPerDay: 50, chatMessagesPerDay: 0, maxAdsPerDay: 5 },
    vip: { aiBotMessagesPerDay: 300, radarPerDay: 500, shakePerDay: 500, randomMatchPerDay: 500, searchPerDay: 500, personalityMatchPerDay: 500, chatMessagesPerDay: 0, maxAdsPerDay: 0 }
  },
  // adBonus = extra quota granted per watched ad, per feature
  adBonus: { aiBotMessagesPerDay: 10, radarPerDay: 10, shakePerDay: 10, randomMatchPerDay: 10, searchPerDay: 10, personalityMatchPerDay: 10, chatMessagesPerDay: 0 },
  global: { botDailyLimit: parseInt(process.env.BOT_DAILY_LIMIT || '2000', 10), botHourlyPerUser: parseInt(process.env.BOT_HOURLY_PER_USER || '30', 10), adMinIntervalSec: 20 },
  ads: { adsEnabled: true, rewardEnabled: true, bannerEnabled: true, interstitialEnabled: false, interstitialEverySwipes: 10, ssvRequired: false },
  posting: { enabled: true, freeEnabled: true, vipEnabled: true, reason: '' }
};
let limitsCache = { at: 0, v: null };
async function getLimits() {
  if (limitsCache.v && Date.now() - limitsCache.at < 30000) return limitsCache.v;
  const ref = db.ref('appConfig/limits');
  let v = (await ref.get()).val();
  if (!v) { await ref.transaction(c => c || SEED_LIMITS); v = (await ref.get()).val(); }
  limitsCache = { at: Date.now(), v }; return v;
}
const dayKey = () => new Date(Date.now() + 7 * 3600 * 1000).toISOString().slice(0, 10).replace(/-/g, ''); // Bangkok day
async function tierOf(uid) {
  const v = (await db.ref(`vip/${uid}`).get()).val();
  return v && v.until > Date.now() ? { tier: 'vip', vipUntil: v.until } : { tier: 'free', vipUntil: null };
}
const num = x => (Number.isInteger(x) ? x : 0);
async function postingFor(uid, tier, L) {
  const P = { ...SEED_LIMITS.posting, ...(L.posting || {}) };
  const blk = (await db.ref(`postBlocks/${uid}`).get()).val();
  if (blk && (!blk.until || blk.until > Date.now())) return { canPost: false, reason: String(blk.reason || 'บัญชีนี้ถูกระงับการโพสต์') };
  if (!P.enabled) return { canPost: false, reason: P.reason || 'ปิดการโพสต์ชั่วคราว' };
  if (!(tier === 'vip' ? P.vipEnabled : P.freeEnabled)) return { canPost: false, reason: P.reason || 'ระดับสมาชิกของคุณยังโพสต์ไม่ได้' };
  return { canPost: true, reason: '' };
}
async function quotaInfo(uid, L) {
  const { tier, vipUntil } = await tierOf(uid);
  const t = L.tiers?.[tier] || {}, day = dayKey();
  const [used, bonus] = await Promise.all([db.ref(`usage/${uid}/${day}`).get(), db.ref(`quotaBonus/${uid}/${day}`).get()]);
  const u = used.val() || {}, b = bonus.val() || {};
  const features = {};
  for (const f of FEATURES) {
    const base = num(t[f]), extra = num(b[f]), lim = base === 0 ? 0 : base + extra;
    features[f] = { limit: lim, base, bonus: extra, used: num(u[f]), remaining: lim === 0 ? null : Math.max(0, lim - num(u[f])), unlimited: base === 0 };
  }
  const posting = await postingFor(uid, tier, L);
  return { tier, vipUntil, day, features, posting, ads: { watched: num(u.ads), max: num(t.maxAdsPerDay), perAd: L.adBonus || {}, ...(L.ads || {}), showAds: tier !== 'vip' && L.ads?.adsEnabled === true } };
}
/** Atomically consume 1 unit of `feature`; returns null if OK, else the limit hit. 0 = unlimited. */
async function consume(uid, feature) {
  const L = await getLimits();
  const { tier } = await tierOf(uid);
  const base = num(L.tiers?.[tier]?.[feature]);
  if (base === 0) return null;
  const day = dayKey();
  const lim = base + num((await db.ref(`quotaBonus/${uid}/${day}/${feature}`).get()).val());
  let over = false;
  await db.ref(`usage/${uid}/${day}/${feature}`).transaction(c => { c = c || 0; over = c >= lim; return over ? c : c + 1; });
  return over ? lim : null;
}
const quota = feature => async (q, r, n) => {
  const lim = await consume(q.uid, feature);
  if (lim !== null) return r.status(429).json({ code: 'quota', feature, limit: lim, error: 'quota exceeded' });
  n();
};
app.get('/limits', w(async (q, r) => r.json(await quotaInfo(q.uid, await getLimits()))));
app.get('/admin/limits', perm('config_edit'), w(async (_q, r) => { limitsCache.at = 0; r.json(await getLimits()); }));
app.put('/admin/limits', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, cur = await getLimits(), out = JSON.parse(JSON.stringify(cur));
  const int = (v, path) => { if (!Number.isInteger(v) || v < 0 || v > 100000) throw Object.assign(new Error(`${path} must be an integer 0..100000`), { status: 400 }); return v; };
  try {
    for (const tier of ['free', 'vip']) for (const [k, v] of Object.entries(b.tiers?.[tier] || {})) {
      if (![...FEATURES, 'maxAdsPerDay'].includes(k)) return r.status(400).json({ error: `unknown field tiers.${tier}.${k}` });
      out.tiers[tier][k] = int(v, `tiers.${tier}.${k}`);
    }
    for (const [k, v] of Object.entries(b.adBonus || {})) { if (!FEATURES.includes(k)) return r.status(400).json({ error: `unknown adBonus.${k}` }); out.adBonus[k] = int(v, `adBonus.${k}`); }
    for (const [k, v] of Object.entries(b.global || {})) { if (!['botDailyLimit', 'botHourlyPerUser', 'adMinIntervalSec'].includes(k)) return r.status(400).json({ error: `unknown global.${k}` }); out.global[k] = int(v, `global.${k}`); }
    for (const [k, v] of Object.entries(b.ads || {})) {
      if (k === 'interstitialEverySwipes') { out.ads[k] = int(v, 'ads.interstitialEverySwipes'); continue; }
      if (!['adsEnabled', 'rewardEnabled', 'bannerEnabled', 'interstitialEnabled', 'ssvRequired'].includes(k) || typeof v !== 'boolean') return r.status(400).json({ error: `bad ads.${k}` });
      out.ads[k] = v;
    }
    out.posting = { ...SEED_LIMITS.posting, ...(out.posting || {}) };
    for (const [k, v] of Object.entries(b.posting || {})) {
      if (k === 'reason') { if (typeof v !== 'string' || v.length > 200) return r.status(400).json({ error: 'bad posting.reason' }); out.posting.reason = v; continue; }
      if (!['enabled', 'freeEnabled', 'vipEnabled'].includes(k) || typeof v !== 'boolean') return r.status(400).json({ error: `bad posting.${k}` });
      out.posting[k] = v;
    }
  } catch (e) { return r.status(e.status || 400).json({ error: e.message }); }
  out.updatedAt = Date.now(); out.updatedBy = q.uid;
  await db.ref('appConfig/limits').set(out);
  limitsCache = { at: Date.now(), v: out };
  r.json(out);
}));
app.put('/admin/post-block/:uid', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  if (q.body?.blocked === false) { await db.ref(`postBlocks/${uid}`).remove(); return r.json({ uid, blocked: false }); }
  await db.ref(`postBlocks/${uid}`).set({ reason: String(q.body?.reason || '').slice(0, 200) || 'บัญชีนี้ถูกระงับการโพสต์', by: q.uid, at: Date.now() });
  r.json({ uid, blocked: true });
}));
app.put('/admin/vip/:uid', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  const until = q.body?.until;
  if (until === null) { await db.ref(`vip/${uid}`).remove(); return r.json({ uid, vip: false }); }
  if (!Number.isInteger(until) || until < Date.now()) return r.status(400).json({ error: 'until must be a future ms timestamp or null' });
  await db.ref(`vip/${uid}`).set({ until, by: q.uid, at: Date.now() });
  r.json({ uid, vip: true, until });
}));
/** Credit one ad reward (shared by client path and SSV callback). */
async function creditAd(uid, feature, txId) {
  const L = await getLimits();
  if (!(L.ads?.adsEnabled && L.ads?.rewardEnabled)) return { status: 403, body: { error: 'ad rewards disabled' } };
  if (!FEATURES.includes(feature)) return { status: 400, body: { error: 'bad feature' } };
  const { tier } = await tierOf(uid);
  const max = num(L.tiers?.[tier]?.maxAdsPerDay), per = num(L.adBonus?.[feature]), day = dayKey();
  if (txId) { const t = await db.ref(`adTx/${txId}`).transaction(c => c ? c : { uid, at: Date.now() }); if (t.snapshot.val()?.done) return { status: 200, body: { duplicate: true } }; }
  const lastRef = db.ref(`adLast/${uid}`), minGap = num(L.global?.adMinIntervalSec) * 1000;
  let tooSoon = false;
  await lastRef.transaction(c => { tooSoon = !!c && Date.now() - c < minGap; return tooSoon ? c : Date.now(); });
  if (tooSoon) return { status: 429, body: { code: 'ad_rate', error: 'wait before next ad' } };
  let over = false;
  await db.ref(`usage/${uid}/${day}/ads`).transaction(c => { c = c || 0; over = max > 0 && c >= max; return over ? c : c + 1; });
  if (max === 0 || over) return { status: 429, body: { code: 'ad_cap', limit: max, error: 'daily ad limit reached' } };
  await db.ref(`quotaBonus/${uid}/${day}/${feature}`).transaction(c => (c || 0) + per);
  if (txId) await db.ref(`adTx/${txId}/done`).set(true);
  return { status: 200, body: { credited: per, feature, ...(await quotaInfo(uid, L)) } };
}
app.post('/quota/ad-reward', w(notBanned), w(async (q, r) => {
  const L = await getLimits();
  const feature = q.body?.feature || 'aiBotMessagesPerDay';
  if (L.ads?.ssvRequired) return r.status(202).json({ pending: true, note: 'credited by AdMob SSV callback' });
  const x = await creditAd(q.uid, feature, null); r.status(x.status).json(x.body);
}));
// AdMob SSV callback (public; trust comes from Google's ECDSA signature).
app.get('/admob/ssv', w(async (q, r) => {
  const raw = q.originalUrl.split('?')[1] || '';
  if (!raw) return r.status(200).send('ok'); // AdMob console "verify URL" ping
  const v = verifySsv(raw, await fetchKeys());
  if (!v.ok) return r.status(403).json({ error: v.reason });
  const uid = v.params.user_id, feature = v.params.custom_data || 'aiBotMessagesPerDay', tx = String(v.params.transaction_id || '').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 100);
  if (!UID_RE.test(uid || '') || !tx) return r.status(400).json({ error: 'missing user_id/transaction_id' });
  const x = await creditAd(uid, feature, tx);
  r.status(x.status === 429 ? 200 : x.status).json(x.body); // 200 so AdMob does not retry a capped reward
}));
// Simple real user search (by display name prefix), quota-limited.
app.get('/search/users', w(notBanned), w(quota('searchPerDay')), w(async (q, r) => {
  const term = String(q.query.q || '').trim().slice(0, 50);
  if (term.length < 1) return r.status(400).json({ error: 'q required' });
  const s = await db.ref('users').orderByChild('displayName').startAt(term).endAt(term + '\uf8ff').limitToFirst(20).get();
  const out = []; s.forEach(c => { if (c.key !== q.uid && c.val()?.displayName) out.push(publicProfile(c.key, c.val())); });
  r.json({ users: out });
}));

// ===== Interest profile + similarity (appConfig/interests; seed only) =====
const SEED_INTERESTS = {
  threshold: 0.15, minShared: 2, maxPerCategory: 10,
  categories: {
    lookingFor: { label: 'มองหา', weight: 3, options: ['เพื่อนคุย', 'เพื่อนเที่ยว', 'แฟน', 'คู่ชีวิต', 'เพื่อนเล่นเกม', 'เพื่อนออกกำลังกาย', 'เพื่อนเรียน', 'คอนเนกชันงาน'] },
    personality: { label: 'นิสัย', weight: 3, options: ['อินโทรเวิร์ต', 'เอ็กซ์โทรเวิร์ต', 'ขี้เล่น', 'ใจเย็น', 'จริงจัง', 'ตลก', 'โรแมนติก', 'ชอบผจญภัย', 'รักสงบ', 'ช่างคุย', 'ฟังเก่ง', 'มีเหตุผล'] },
    favoriteFoods: { label: 'อาหาร', weight: 1, options: ['อาหารไทย', 'อาหารญี่ปุ่น', 'อาหารเกาหลี', 'อาหารจีน', 'อาหารอิตาเลียน', 'ปิ้งย่าง', 'ชาบู', 'สตรีทฟู้ด', 'มังสวิรัติ', 'ขนมหวาน', 'กาแฟ', 'ชานม'] },
    favoriteMovies: { label: 'หนัง', weight: 1, options: ['แอ็กชัน', 'ตลก', 'โรแมนติก', 'สยองขวัญ', 'ไซไฟ', 'แฟนตาซี', 'ดราม่า', 'สารคดี', 'อนิเมะ', 'ซีรีส์เกาหลี', 'ซีรีส์วาย', 'ระทึกขวัญ'] },
    favoriteMusic: { label: 'เพลง', weight: 1, options: ['ป๊อป', 'ร็อก', 'ลูกทุ่ง', 'หมอลำ', 'ฮิปฮอป', 'อินดี้', 'แจ๊ส', 'คลาสสิก', 'EDM', 'เคป๊อป', 'อาร์แอนด์บี', 'เพื่อชีวิต'] },
    hobbies: { label: 'งานอดิเรก', weight: 1.5, options: ['เล่นเกม', 'อ่านหนังสือ', 'ถ่ายรูป', 'ท่องเที่ยว', 'ทำอาหาร', 'วาดรูป', 'ร้องเพลง', 'เต้น', 'ปลูกต้นไม้', 'ช้อปปิ้ง', 'ดูบอล', 'ตั้งแคมป์'] },
    lifestyle: { label: 'ไลฟ์สไตล์', weight: 1, options: ['รักสุนัข', 'รักแมว', 'ไม่ดื่ม', 'ดื่มบ้าง', 'ไม่สูบบุหรี่', 'ออกกำลังกายประจำ', 'ตื่นเช้า', 'นอนดึก', 'สายปาร์ตี้', 'ติดบ้าน'] },
    languages: { label: 'ภาษา', weight: 0.5, options: ['ไทย', 'อังกฤษ', 'จีน', 'ญี่ปุ่น', 'เกาหลี', 'ลาว', 'พม่า', 'เวียดนาม'] }
  }
};
let interestCache = { at: 0, v: null };
async function getInterestCfg() {
  if (interestCache.v && Date.now() - interestCache.at < 30000) return interestCache.v;
  const ref = db.ref('appConfig/interests');
  let v = (await ref.get()).val();
  if (!v) { await ref.transaction(c => c || SEED_INTERESTS); v = (await ref.get()).val(); }
  interestCache = { at: Date.now(), v }; return v;
}
const cleanTag = t => String(t || '').replace(/[.#$\[\]\/\u0000-\u001f]/g, '').trim().slice(0, 30);
function sanitizeInterests(body, cfg) {
  const out = {};
  for (const [cat, c] of Object.entries(cfg.categories || {})) {
    const arr = Array.isArray(body?.[cat]) ? body[cat] : [];
    const seen = [];
    for (const x of arr) { const t = cleanTag(x); if (t && !seen.includes(t)) seen.push(t); if (seen.length >= (cfg.maxPerCategory || 10)) break; }
    if (seen.length) out[cat] = seen;
  }
  return out;
}
function similarity(a, b, cfg) {
  let num = 0, den = 0, sharedCount = 0; const shared = {};
  for (const [cat, c] of Object.entries(cfg.categories || {})) {
    const A = new Set((a?.[cat] || []).map(x => String(x).toLowerCase())), B = new Set((b?.[cat] || []).map(x => String(x).toLowerCase()));
    if (!A.size && !B.size) continue;
    const w = Number(c.weight) || 1, inter = [...A].filter(x => B.has(x)), uni = new Set([...A, ...B]).size;
    den += w; num += w * (uni ? inter.length / uni : 0);
    if (inter.length) { shared[cat] = (a[cat] || []).filter(x => B.has(String(x).toLowerCase())); sharedCount += inter.length; }
  }
  const score = den ? num / den : 0;
  return { score: Math.round(score * 100) / 100, percent: Math.round(score * 100), shared, sharedCount, isMatch: score >= (cfg.threshold ?? 0.15) || sharedCount >= (cfg.minShared ?? 2) };
}
const ipOf = async uid => (await db.ref(`users/${uid}/interestProfile`).get()).val() || {};
app.get('/interests/config', w(async (_q, r) => r.json(await getInterestCfg())));
app.put('/me/interests', w(notBanned), w(async (q, r) => {
  const cfg = await getInterestCfg(), ip = sanitizeInterests(q.body || {}, cfg);
  await db.ref(`users/${q.uid}/interestProfile`).set(Object.keys(ip).length ? ip : null);
  r.json({ interestProfile: ip });
}));
app.put('/admin/interests', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, cur = JSON.parse(JSON.stringify(await getInterestCfg()));
  if (b.threshold !== undefined) { if (typeof b.threshold !== 'number' || b.threshold < 0 || b.threshold > 1) return r.status(400).json({ error: 'threshold 0..1' }); cur.threshold = b.threshold; }
  if (b.minShared !== undefined) { if (!Number.isInteger(b.minShared) || b.minShared < 0 || b.minShared > 50) return r.status(400).json({ error: 'minShared 0..50' }); cur.minShared = b.minShared; }
  for (const [cat, c] of Object.entries(b.categories || {})) {
    if (!/^[A-Za-z]{2,30}$/.test(cat)) return r.status(400).json({ error: 'bad category key' });
    const o = cur.categories[cat] || { label: cat, weight: 1, options: [] };
    if (c.label !== undefined) o.label = cleanTag(c.label);
    if (c.weight !== undefined) { if (typeof c.weight !== 'number' || c.weight < 0 || c.weight > 10) return r.status(400).json({ error: 'weight 0..10' }); o.weight = c.weight; }
    if (c.options !== undefined) { if (!Array.isArray(c.options) || c.options.length > 60) return r.status(400).json({ error: 'options max 60' }); o.options = [...new Set(c.options.map(cleanTag).filter(Boolean))]; }
    cur.categories[cat] = o;
  }
  await db.ref('appConfig/interests').set(cur); interestCache = { at: Date.now(), v: cur };
  r.json(cur);
}));
// Another user's profile with interests + what the viewer shares with them.
app.get('/users/:uid/profile', w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  const u = (await db.ref(`users/${uid}`).get()).val();
  if (!u || await isBanned(uid)) return r.status(404).json({ error: 'not found' });
  const cfg = await getInterestCfg(), mine = await ipOf(q.uid);
  r.json({ ...publicProfile(uid, u), interestProfile: u.interestProfile || {}, similarity: uid === q.uid ? null : similarity(mine, u.interestProfile || {}, cfg) });
}));
app.get('/match/personality', w(notBanned), w(quota('personalityMatchPerDay')), w(async (q, r) => {
  const cfg = await getInterestCfg();
  const [users, bans] = await Promise.all([db.ref('users').get(), db.ref('bans').get()]);
  const all = users.val() || {}, b = bans.val() || {}, mine = all[q.uid]?.interestProfile || {}, now = Date.now();
  if (!Object.keys(mine).length) return r.status(409).json({ code: 'no_interests', error: 'set your interests first' });
  const out = [];
  for (const [uid, u] of Object.entries(all)) {
    if (uid === q.uid || !u?.displayName || !u.interestProfile) continue;
    const ban = b[uid]; if (ban && (!ban.until || ban.until > now)) continue;
    const sim = similarity(mine, u.interestProfile, cfg);
    if (sim.isMatch) out.push({ ...publicProfile(uid, u), interestProfile: u.interestProfile, similarity: sim });
  }
  out.sort((x, y) => y.similarity.score - x.similarity.score || y.similarity.sharedCount - x.similarity.sharedCount);
  r.json({ users: out.slice(0, 20) });
}));

app.post('/match', w(notBanned), w(quota('randomMatchPerDay')), w(async (q, r) => {
  const me = q.uid, t = Date.now(), qref = db.ref('matchQueue');
  let partner = null;
  await qref.transaction(all => {
    all = all || {}; partner = null;
    for (const [uid, v] of Object.entries(all)) if (!v || t - v.ts > QUEUE_TTL_MS) delete all[uid];
    const other = Object.keys(all).find(u => u !== me);
    if (other) { partner = other; delete all[other]; delete all[me]; }
    else all[me] = { ts: t };
    return all;
  });
  if (partner) { const { chatId } = await openDirectChat(me, partner); return r.json({ type: 'user', uid: partner, chatId }); }
  if (await botsEnabled()) {
    const all = (await db.ref('aiBots').get()).val() || {};
    const list = Object.entries(all).filter(([, b]) => b && b.enabled === true);
    if (list.length) {
      await db.ref(`matchQueue/${me}`).remove();
      const [id, b] = list[Math.floor(Math.random() * list.length)];
      return r.json({ type: 'bot', bot: publicBot(id, b) });
    }
  }
  r.json({ type: 'waiting' });
}));
app.post('/bot/chat', w(notBanned), w(async (q, r) => {
  const { botId, message } = q.body || {};
  if (typeof botId !== 'string' || !/^[A-Za-z0-9_-]{1,40}$/.test(botId) || typeof message !== 'string' || !message.trim() || message.length > 500)
    return r.status(400).json({ error: 'botId and message (1-500 chars) required' });
  if (!(await botsEnabled())) return r.status(403).json({ error: 'bots disabled' });
  const bot = (await db.ref(`aiBots/${botId}`).get()).val();
  if (!bot || bot.enabled !== true) return r.status(403).json({ error: 'bots disabled' });
  const d = new Date(), day = d.toISOString().slice(0, 10).replace(/-/g, ''), hour = day + String(d.getUTCHours()).padStart(2, '0');
  const G = (await getLimits()).global || {};
  if (!(await bump(`botRate/${q.uid}/${hour}`, num(G.botHourlyPerUser) || Infinity))) return r.status(429).json({ error: 'rate limit: try again later' });
  if (!(await bump(`botDaily/${day}`, num(G.botDailyLimit) || Infinity))) return r.status(429).json({ error: 'daily bot limit reached' });
  { const lim = await consume(q.uid, 'aiBotMessagesPerDay'); if (lim !== null) return r.status(429).json({ code: 'quota', feature: 'aiBotMessagesPerDay', limit: lim, error: 'quota exceeded' }); }
  const chatRef = db.ref(`botChats/${q.uid}/${botId}`);
  const hist = Object.values((await chatRef.orderByKey().limitToLast(10).get()).val() || {}).sort((a, b) => a.ts - b.ts);
  const system = [
    `คุณคือ "${bot.name}" ตัวละคร AI ในแอป FriendTalk บุคลิก: ${bot.persona}. ความสนใจ: ${(bot.interests || []).join(', ')}.`,
    'คุณเป็นตัวละคร AI ไม่ใช่มนุษย์ ห้ามอ้างว่าเป็นคนจริง ถ้าถูกถามว่าเป็นบอทหรือคนจริง ให้ตอบตรงๆ ว่าเป็น AI',
    'ตอบเป็นภาษาไทยแบบเป็นกันเอง สั้นๆ 1-3 ประโยค',
    'ห้ามเนื้อหาทางเพศหรือชวนสัมพันธ์เชิงชู้สาว ห้ามเนื้อหาเกี่ยวกับผู้เยาว์ ห้ามขอข้อมูลส่วนตัว (ที่อยู่ เบอร์โทร รหัสผ่าน) หรือข้อมูลการเงิน ห้ามขอเงิน เหรียญ หรือของขวัญ',
    'ถ้าผู้ใช้พูดถึงการทำร้ายตัวเอง ให้แนะนำให้ติดต่อสายด่วนสุขภาพจิต 1323'
  ].join('\n');
  const contents = [...hist.map(m => ({ role: m.from === 'bot' ? 'model' : 'user', parts: [{ text: m.text }] })), { role: 'user', parts: [{ text: message.trim() }] }];
  const res = await fetch(`https://aiplatform.googleapis.com/v1/projects/${PROJECT}/locations/global/publishers/google/models/${GEMINI_MODEL}:generateContent`, {
    method: 'POST', headers: { Authorization: `Bearer ${await gcpToken()}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: system }] }, contents,
      generationConfig: { maxOutputTokens: 150, temperature: 0.8 },
      safetySettings: ['HARM_CATEGORY_SEXUALLY_EXPLICIT', 'HARM_CATEGORY_HARASSMENT', 'HARM_CATEGORY_HATE_SPEECH', 'HARM_CATEGORY_DANGEROUS_CONTENT'].map(category => ({ category, threshold: 'BLOCK_LOW_AND_ABOVE' }))
    })
  });
  if (!res.ok) { console.error('vertex', res.status, (await res.text()).slice(0, 300)); return r.status(502).json({ error: 'ai unavailable' }); }
  const j = await res.json();
  const reply = (j.candidates?.[0]?.content?.parts || []).map(p => p.text || '').join('').trim() || 'ขอโทษนะ ตอบเรื่องนี้ไม่ได้ เปลี่ยนเรื่องคุยกันดีกว่า 😊';
  const ts = Date.now();
  await chatRef.push({ from: 'user', text: message.trim(), ts });
  await chatRef.push({ from: 'bot', text: reply, ts: ts + 1 });
  r.json({ botId, reply, isAI: true });
}));
app.delete('/bot/chat/:botId', w(async (q, r) => {
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(q.params.botId)) return r.status(400).json({ error: 'bad botId' });
  await db.ref(`botChats/${q.uid}/${q.params.botId}`).remove(); r.json({ ended: q.params.botId });
}));
app.get('/admin/bots', perm('config_edit'), w(async (_q, r) => {
  const all = (await db.ref('aiBots').get()).val() || {};
  r.json({ enabled: await botsEnabled(), bots: Object.entries(all).map(([id, b]) => ({ ...publicBot(id, b), persona: b.persona })) });
}));
app.put('/admin/bots/enabled', perm('config_edit'), w(notBanned), w(async (q, r) => {
  if (typeof q.body?.enabled !== 'boolean') return r.status(400).json({ error: 'enabled (boolean) required' });
  await db.ref('appConfig/aiBotsEnabled').set(q.body.enabled); r.json({ aiBotsEnabled: q.body.enabled });
}));
app.put('/admin/bots/:botId', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const id = q.params.botId, b = q.body || {}, u = {};
  if (!/^[A-Za-z0-9_-]{1,40}$/.test(id)) return r.status(400).json({ error: 'bad botId' });
  if (typeof b.enabled === 'boolean') u.enabled = b.enabled;
  for (const k of ['name', 'avatarUrl', 'bio', 'persona']) if (typeof b[k] === 'string' && b[k].length <= 1000) u[k] = b[k];
  if (Array.isArray(b.interests)) u.interests = b.interests.filter(x => typeof x === 'string').slice(0, 10);
  if (!Object.keys(u).length) return r.status(400).json({ error: 'nothing to update' });
  const ref = db.ref(`aiBots/${id}`);
  if (!(await ref.get()).exists() && !u.name) return r.status(404).json({ error: 'bot not found' });
  await ref.update(u); r.json({ id, ...u });
}));
// ===== Server-driven UI layouts (admin UI Builder) =====
const UI_SCREENS = ['home'];
const BLOCK_TYPES = ['image', 'frame', 'text', 'button', 'banner', 'spacer', 'section'];
const SECTIONS = ['tabs', 'banners', 'stories', 'clubs', 'sdc', 'feed'];
const HEX = /^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})?$/;
function validateLayout(screen, layout) {
  if (!layout || typeof layout !== 'object' || !Array.isArray(layout.blocks)) return 'layout.blocks array required';
  if (Buffer.byteLength(JSON.stringify(layout)) > 100 * 1024) return 'layout too large (max 100KB)';
  if (layout.blocks.length === 0 || layout.blocks.length > 100) return '1-100 blocks required';
  const ids = new Set(), out = [];
  for (const b of layout.blocks) {
    if (!b || typeof b !== 'object') return 'invalid block';
    if (typeof b.id !== 'string' || !/^[A-Za-z0-9_-]{1,40}$/.test(b.id) || ids.has(b.id)) return 'block id invalid or duplicate';
    ids.add(b.id);
    if (!BLOCK_TYPES.includes(b.type)) return `bad type ${b.type}`;
    if (b.type === 'section' && !SECTIONS.includes(b.section)) return `bad section ${b.section}`;
    for (const k of ['imageUrl', 'linkUrl']) if (b[k] && (typeof b[k] !== 'string' || !/^https:\/\/[^\s]{1,1000}$/.test(b[k]))) return `${k} must be https://`;
    for (const k of ['bgColorHex', 'textColorHex']) if (b[k] && (typeof b[k] !== 'string' || !HEX.test(b[k]))) return `${k} must be #RRGGBB`;
    if (b.text && (typeof b.text !== 'string' || b.text.length > 500)) return 'text max 500 chars';
    const num = (v, lo, hi, d) => (typeof v === 'number' && isFinite(v)) ? Math.min(hi, Math.max(lo, v)) : d;
    out.push({
      id: b.id, type: b.type, section: b.type === 'section' ? b.section : '', visible: b.visible !== false,
      widthFraction: num(b.widthFraction, 0.2, 1, 1), heightDp: Math.round(num(b.heightDp, 0, 800, 0)),
      cornerRadiusDp: Math.round(num(b.cornerRadiusDp, 0, 64, 12)), bgColorHex: b.bgColorHex || '', textColorHex: b.textColorHex || '#FFFFFF',
      text: b.text || '', fontSizeSp: Math.round(num(b.fontSizeSp, 8, 48, 14)), paddingDp: Math.round(num(b.paddingDp, 0, 48, 12)),
      imageUrl: b.imageUrl || '', linkUrl: b.linkUrl || ''
    });
  }
  return { screen, blocks: out };
}
const screenOk = (q, r) => UI_SCREENS.includes(q.params.screen) || (r.status(404).json({ error: 'unknown screen' }), false);
app.get('/ui/layout/:screen', w(async (q, r) => {
  if (!screenOk(q, r)) return;
  const cur = (await db.ref(`uiLayouts/${q.params.screen}/current`).get()).val();
  if (!cur) return r.status(404).json({ error: 'no layout published' });
  r.json({ layout: cur.layout, publishedAt: cur.publishedAt, versionId: cur.versionId });
}));
app.get('/admin/ui/layout/:screen', perm('config_edit'), w(async (q, r) => {
  if (!screenOk(q, r)) return;
  const v = (await db.ref(`uiLayouts/${q.params.screen}`).get()).val() || {};
  const versions = Object.entries(v.versions || {}).map(([id, x]) => ({ id, savedAt: x.savedAt, savedBy: x.savedBy, blockCount: x.layout?.blocks?.length || 0 })).sort((a, b) => b.savedAt - a.savedAt);
  r.json({ current: v.current?.layout || null, draft: v.draft?.layout || null, versions });
}));
app.put('/admin/ui/layout/:screen', perm('config_edit'), w(notBanned), w(async (q, r) => {
  if (!screenOk(q, r)) return;
  const screen = q.params.screen, res = validateLayout(screen, q.body?.layout);
  if (typeof res === 'string') return r.status(400).json({ error: res });
  const t = Date.now(), base = db.ref(`uiLayouts/${screen}`);
  if (q.body?.publish !== true) { await base.child('draft').set({ layout: res, savedAt: t, savedBy: q.uid }); return r.json({ saved: 'draft', savedAt: t }); }
  const id = String(t);
  await base.update({ current: { layout: res, publishedAt: t, publishedBy: q.uid, versionId: id }, [`versions/${id}`]: { layout: res, savedAt: t, savedBy: q.uid }, draft: null });
  const vs = Object.keys((await base.child('versions').get()).val() || {}).sort();
  if (vs.length > 10) await Promise.all(vs.slice(0, vs.length - 10).map(k => base.child(`versions/${k}`).remove()));
  r.json({ published: id, blocks: res.blocks.length });
}));
app.post('/admin/ui/layout/:screen/revert', perm('config_edit'), w(notBanned), w(async (q, r) => {
  if (!screenOk(q, r)) return;
  const vid = q.body?.versionId;
  if (typeof vid !== 'string' || !/^\d{1,20}$/.test(vid)) return r.status(400).json({ error: 'versionId required' });
  const base = db.ref(`uiLayouts/${q.params.screen}`), v = (await base.child(`versions/${vid}`).get()).val();
  if (!v) return r.status(404).json({ error: 'version not found' });
  const t = Date.now();
  await base.update({ current: { layout: v.layout, publishedAt: t, publishedBy: q.uid, versionId: vid, revertedFrom: vid }, draft: null });
  r.json({ reverted: vid });
}));
// ===== Phase 1: real chats, swipe match =====
const STORAGE_BUCKET = process.env.STORAGE_BUCKET || 'friendtalk-4e623.firebasestorage.app';
const UID_RE = /^[A-Za-z0-9]{10,128}$/;
async function isBanned(uid) {
  const b = (await db.ref(`bans/${uid}`).get()).val();
  return !!(b && (!b.until || b.until > Date.now()));
}
const directChatId = (a, b) => 'd_' + [a, b].sort().join('_');
async function openDirectChat(a, b) {
  const id = directChatId(a, b), t = Date.now();
  const exists = (await db.ref(`chatMembers/${id}`).get()).exists();
  if (!exists) {
    await db.ref().update({
      [`chats/${id}`]: { type: 'direct', createdAt: t, lastAt: t, lastMessage: '' },
      [`chatMembers/${id}/${a}`]: true, [`chatMembers/${id}/${b}`]: true,
      [`userChats/${a}/${id}`]: t, [`userChats/${b}/${id}`]: t
    });
  }
  return { chatId: id, created: !exists };
}
app.post('/chats/open', w(notBanned), w(async (q, r) => {
  const other = q.body?.otherUid;
  if (typeof other !== 'string' || !UID_RE.test(other) || other === q.uid) return r.status(400).json({ error: 'valid otherUid required' });
  if (!(await db.ref(`users/${other}`).get()).exists()) return r.status(404).json({ error: 'user not found' });
  if (await isBanned(other)) return r.status(403).json({ error: 'user unavailable' });
  r.json(await openDirectChat(q.uid, other));
}));
// Chat images: Storage objects are never client-readable; members read through here.
app.get('/chats/:chatId/image', w(async (q, r) => {
  const { chatId } = q.params, path = String(q.query.path || '');
  if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId) || !path.startsWith(`chatImages/${chatId}/`) || path.includes('..') || path.length > 400) return r.status(400).json({ error: 'bad path' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  const file = admin.storage().bucket(STORAGE_BUCKET).file(path);
  const [meta] = await file.getMetadata().catch(() => [null]);
  if (!meta || !String(meta.contentType || '').startsWith('image/')) return r.status(404).json({ error: 'not found' });
  r.set('Content-Type', meta.contentType); r.set('Cache-Control', 'private, max-age=3600');
  file.createReadStream().on('error', () => r.end()).pipe(r);
}));
const publicProfile = (uid, u) => ({ uid, displayName: u.displayName || '', username: u.username || '', avatar: u.avatar || '', bio: u.bio || '', age: u.age || null, gender: u.gender || '', interests: u.interests || [] });
app.get('/swipe/candidates', w(notBanned), w(async (q, r) => {
  const limit = Math.min(20, Math.max(1, parseInt(q.query.limit || '10', 10)));
  const [users, swiped, bans] = await Promise.all([db.ref('users').get(), db.ref(`swipes/${q.uid}`).get(), db.ref('bans').get()]);
  const s = swiped.val() || {}, b = bans.val() || {}, now = Date.now();
  const icfg = await getInterestCfg(), mineIp = (users.val() || {})[q.uid]?.interestProfile || {};
  const out = [];
  for (const [uid, u] of Object.entries(users.val() || {})) {
    if (uid === q.uid || s[uid] || !u || !u.displayName) continue;
    const ban = b[uid]; if (ban && (!ban.until || ban.until > now)) continue;
    out.push({ ...publicProfile(uid, u), interestProfile: u.interestProfile || {}, similarity: similarity(mineIp, u.interestProfile || {}, icfg) });
  }
  out.sort((x, y) => y.similarity.score - x.similarity.score);
  r.json({ candidates: out.slice(0, limit) });
}));
app.post('/swipe', w(notBanned), w(async (q, r) => {
  const { targetUid, action } = q.body || {};
  if (typeof targetUid !== 'string' || !UID_RE.test(targetUid) || targetUid === q.uid || !['like', 'pass', 'superlike'].includes(action)) return r.status(400).json({ error: 'targetUid and action (like|pass|superlike) required' });
  if (!(await db.ref(`users/${targetUid}`).get()).exists()) return r.status(404).json({ error: 'user not found' });
  const t = Date.now();
  await db.ref(`swipes/${q.uid}/${targetUid}`).set({ action, at: t });
  if (action === 'pass') return r.json({ matched: false });
  const back = (await db.ref(`swipes/${targetUid}/${q.uid}`).get()).val();
  if (!back || back.action === 'pass' || await isBanned(targetUid)) return r.json({ matched: false });
  const pairId = [q.uid, targetUid].sort().join('_');
  const { chatId } = await openDirectChat(q.uid, targetUid);
  const already = (await db.ref(`matches/${pairId}`).get()).exists();
  if (!already) await db.ref().update({
    [`matches/${pairId}`]: { members: { [q.uid]: true, [targetUid]: true }, createdAt: t, chatId },
    [`matchNotifications/${q.uid}/${pairId}`]: { otherUid: targetUid, chatId, at: t, seen: false },
    [`matchNotifications/${targetUid}/${pairId}`]: { otherUid: q.uid, chatId, at: t, seen: false }
  });
  r.json({ matched: true, pairId, chatId });
}));
// ===== Phase 2: social feed (text + images, likes, comments) =====
// Posts/likes/comments are written only here (RTDB rules: client write false).
// Post images: Storage posts/{uid}/..., owner-only create, image/* <=10MB; read via the
// Firebase download-token URL (unguessable; token set by the client SDK upload).
const POST_ID_RE = /^-[A-Za-z0-9_-]{19}$/;
const normTag = t => { const x = String(t).replace(/^#/, '').toLowerCase().normalize('NFC'); return /^[\p{L}\p{M}\p{N}_]{1,50}$/u.test(x) ? x : null; };
const VIS = ['public', 'friends', 'only_me'];
async function canView(viewer, post) {
  if (!post) return false;
  const v = post.visibility || 'public';
  if (post.authorId === viewer || v === 'public') return true;
  if (v === 'friends') return (await db.ref(`friends/${post.authorId}/${viewer}`).get()).exists();
  return false;
}
/** 404 (not 403) when the caller may not see the post, so existence is not revealed. */
async function visiblePost(q, r, id) {
  const post = (await db.ref(`posts/${id}`).get()).val();
  if (!(await canView(q.uid, post))) { r.status(404).json({ error: 'not found' }); return null; }
  return post;
}
function parseHashtags(text) {
  const out = [];
  for (const m of String(text).matchAll(/#([\p{L}\p{M}\p{N}_]{1,50})/gu)) { const t = normTag(m[1]); if (t && !out.includes(t)) out.push(t); if (out.length >= 10) break; }
  return out;
}
const cleanText = (t, max) => (typeof t === 'string' ? t.trim() : '').slice(0, max);
async function postImageUrl(uid, path) {
  if (typeof path !== 'string' || !path.startsWith(`posts/${uid}/`) || path.includes('..') || path.length > 300) return null;
  const f = admin.storage().bucket(STORAGE_BUCKET).file(path);
  const [m] = await f.getMetadata().catch(() => [null]);
  if (!m || !String(m.contentType || '').startsWith('image/') || Number(m.size) > 10 * 1024 * 1024) return null;
  let tok = (m.metadata?.firebaseStorageDownloadTokens || '').split(',')[0];
  if (!tok) { tok = crypto.randomUUID(); await f.setMetadata({ metadata: { firebaseStorageDownloadTokens: tok } }); }
  return `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${tok}`;
}
async function authorInfo(uid) {
  const u = (await db.ref(`users/${uid}`).get()).val() || {};
  return { authorName: String(u.displayName || 'ผู้ใช้').slice(0, 50), authorAvatar: String(u.avatar || '').slice(0, 500) };
}
app.post('/posts', w(notBanned), w(async (q, r) => {
  { const pf = await postingFor(q.uid, (await tierOf(q.uid)).tier, await getLimits()); if (!pf.canPost) return r.status(403).json({ code: 'posting_disabled', error: pf.reason }); }
  const text = cleanText(q.body?.text, 2000);
  const refs = Array.isArray(q.body?.imageRefs) ? q.body.imageRefs : [];
  if (refs.length > 4) return r.status(400).json({ error: 'max 4 images' });
  const images = [];
  for (const p of refs) { const u = await postImageUrl(q.uid, p); if (!u) return r.status(400).json({ error: 'invalid image' }); images.push({ path: p, url: u }); }
  if (!text && !images.length) return r.status(400).json({ error: 'text or image required' });
  const hashtags = parseHashtags(text);
  const label = cleanText(q.body?.place, 80).replace(/[\u0000-\u001f]/g, '');
  let place = null;
  if (label) {
    place = { label };
    const { lat, lng } = q.body || {};
    if (validLatLng(lat, lng)) { place.lat = Math.round(lat * 100) / 100; place.lng = Math.round(lng * 100) / 100; } // ~1 km
  }
  const ref = db.ref('posts').push();
  const visibility = VIS.includes(q.body?.visibility) ? q.body.visibility : 'public';
  const post = { authorId: q.uid, ...(await authorInfo(q.uid)), text, images, hashtags, place, visibility, createdAt: Date.now(), likeCount: 0, commentCount: 0 };
  const upd = { [`posts/${ref.key}`]: post };
  for (const t of hashtags) upd[`hashtags/${t}/${ref.key}`] = post.createdAt;
  await db.ref().update(upd);
  r.json({ id: ref.key, ...post });
}));
async function canManage(q, post) { return post.authorId === q.uid || can(await loadRole(q.uid), 'manage_content'); }
app.delete('/posts/:id', w(async (q, r) => {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  const post = (await db.ref(`posts/${id}`).get()).val(); if (!post) return r.status(404).json({ error: 'not found' });
  if (!(await canManage(q, post))) return r.status(403).json({ error: 'not allowed' });
  const del = { [`posts/${id}`]: null, [`postLikes/${id}`]: null, [`postComments/${id}`]: null };
  for (const t of post.hashtags || []) del[`hashtags/${t}/${id}`] = null;
  await db.ref().update(del);
  for (const im of post.images || []) await admin.storage().bucket(STORAGE_BUCKET).file(im.path).delete().catch(() => {});
  r.json({ ok: true });
}));
app.patch('/posts/:id', w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  const post = (await db.ref(`posts/${id}`).get()).val();
  if (!post || post.authorId !== q.uid) return r.status(404).json({ error: 'not found' });
  if (!VIS.includes(q.body?.visibility)) return r.status(400).json({ error: 'visibility must be public|friends|only_me' });
  await db.ref(`posts/${id}/visibility`).set(q.body.visibility);
  r.json({ id, visibility: q.body.visibility });
}));
app.get('/feed', w(async (q, r) => {
  const limit = Math.min(30, Math.max(1, parseInt(q.query.limit || '20', 10)));
  let qq = db.ref('posts').orderByKey();
  const cursor = String(q.query.cursor || '');
  if (cursor) { if (!POST_ID_RE.test(cursor)) return r.status(400).json({ error: 'bad cursor' }); qq = qq.endBefore(cursor); }
  const posts = [], rawKeys = [];
  const tag = q.query.tag ? normTag(String(q.query.tag)) : null;
  if (q.query.tag && !tag) return r.status(400).json({ error: 'bad tag' });
  if (tag) {
    let tq = db.ref(`hashtags/${tag}`).orderByKey();
    if (cursor) tq = tq.endBefore(cursor);
    const ids = []; (await tq.limitToLast(limit).get()).forEach(c => { ids.push(c.key); });
    rawKeys.push(...ids);
    const snaps = await Promise.all(ids.map(i => db.ref(`posts/${i}`).get()));
    snaps.forEach(c => { if (c.exists()) posts.push({ id: c.key, ...c.val() }); });
  } else {
    const snap = await qq.limitToLast(limit).get();
    snap.forEach(c => { rawKeys.push(c.key); posts.push({ id: c.key, ...c.val() }); });
  }
  posts.reverse();
  const vis = await Promise.all(posts.map(p => canView(q.uid, p)));
  const rawCount = rawKeys.length, oldest = rawKeys.sort()[0];
  posts.splice(0, posts.length, ...posts.filter((_, i) => vis[i]));
  const liked = await Promise.all(posts.map(p => db.ref(`postLikes/${p.id}/${q.uid}`).get()));
  posts.forEach((p, i) => { p.likedByMe = liked[i].exists(); p.images = p.images || []; });
  posts.forEach(p => { p.visibility = p.visibility || 'public'; if (p.authorId !== q.uid) delete p.place?.lat, delete p.place?.lng; });
  r.json({ posts, nextCursor: rawCount === limit ? oldest : null });
}));
async function setLike(q, r, on) {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  if (!(await visiblePost(q, r, id))) return;
  let changed = false;
  // Never abort: the first run sees a null local cache; returning a value lets RTDB retry with the server value.
  await db.ref(`postLikes/${id}/${q.uid}`).transaction(cur => {
    if (on) { changed = !cur; return cur || Date.now(); }
    changed = !!cur; return null;
  });
  let count;
  if (changed) {
    const t = await db.ref(`posts/${id}/likeCount`).transaction(c => Math.max(0, (c || 0) + (on ? 1 : -1)));
    count = t.snapshot.val();
  } else count = (await db.ref(`posts/${id}/likeCount`).get()).val() || 0;
  r.json({ liked: on, likeCount: count });
}
app.post('/posts/:id/like', w(notBanned), w((q, r) => setLike(q, r, true)));
app.delete('/posts/:id/like', w(notBanned), w((q, r) => setLike(q, r, false)));
app.post('/posts/:id/comments', w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  const text = cleanText(q.body?.text, 1000); if (!text) return r.status(400).json({ error: 'text required' });
  if (!(await visiblePost(q, r, id))) return;
  let parentId = q.body?.parentId || null;
  if (parentId) {
    if (!POST_ID_RE.test(parentId)) return r.status(400).json({ error: 'bad parentId' });
    const parent = (await db.ref(`postComments/${id}/${parentId}`).get()).val();
    if (!parent) return r.status(404).json({ error: 'parent not found' });
    if (parent.parentId) parentId = parent.parentId; // one level of replies
  }
  const ref = db.ref(`postComments/${id}`).push();
  const c = { authorId: q.uid, ...(await authorInfo(q.uid)), text, parentId, createdAt: Date.now() };
  await ref.set(c);
  const t = await db.ref(`posts/${id}/commentCount`).transaction(n => (n || 0) + 1);
  r.json({ id: ref.key, ...c, commentCount: t.snapshot.val() });
}));
app.get('/posts/:id/comments', w(async (q, r) => {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  if (!(await visiblePost(q, r, id))) return;
  const limit = Math.min(100, Math.max(1, parseInt(q.query.limit || '50', 10)));
  let qq = db.ref(`postComments/${id}`).orderByKey();
  const cursor = String(q.query.cursor || '');
  if (cursor) { if (!POST_ID_RE.test(cursor)) return r.status(400).json({ error: 'bad cursor' }); qq = qq.startAfter(cursor); }
  const snap = await qq.limitToFirst(limit).get();
  const comments = []; snap.forEach(c => { comments.push({ id: c.key, parentId: null, ...c.val() }); });
  r.json({ comments, nextCursor: comments.length === limit ? comments[comments.length - 1].id : null });
}));
app.delete('/posts/:id/comments/:cid', w(async (q, r) => {
  const { id, cid } = q.params; if (!POST_ID_RE.test(id) || !POST_ID_RE.test(cid)) return r.status(400).json({ error: 'bad id' });
  const c = (await db.ref(`postComments/${id}/${cid}`).get()).val(); if (!c) return r.status(404).json({ error: 'not found' });
  if (c.authorId !== q.uid && !can(await loadRole(q.uid), 'manage_content')) return r.status(403).json({ error: 'not allowed' });
  const all = (await db.ref(`postComments/${id}`).get()).val() || {};
  const upd = { [`postComments/${id}/${cid}`]: null }; let n = 1;
  if (!c.parentId) for (const [k, v] of Object.entries(all)) if (v.parentId === cid) { upd[`postComments/${id}/${k}`] = null; n++; }
  await db.ref().update(upd);
  const t = await db.ref(`posts/${id}/commentCount`).transaction(x => Math.max(0, (x || 0) - n));
  r.json({ ok: true, commentCount: t.snapshot.val() });
}));
// ===== Phase 3: friends, radar, shake =====
const RADII = [0.5, 1, 2, 5, 10, 25, 50];
const pickRadius = v => { const n = Number(v); return RADII.includes(n) ? n : 0.5; };
const GH32 = '0123456789bcdefghjkmnpqrstuvwxyz';
function geohash(lat, lng, prec) {
  let la = [-90, 90], lo = [-180, 180], bit = 0, ch = 0, even = true, out = '';
  while (out.length < prec) {
    const r = even ? lo : la, v = even ? lng : lat, mid = (r[0] + r[1]) / 2;
    if (v >= mid) { ch = (ch << 1) | 1; r[0] = mid; } else { ch = ch << 1; r[1] = mid; }
    even = !even;
    if (++bit === 5) { out += GH32[ch]; bit = 0; ch = 0; }
  }
  return out;
}
function distKm(a, b, c, d) {
  const R = 6371, t = x => x * Math.PI / 180, dl = t(c - a), dn = t(d - b);
  const h = Math.sin(dl / 2) ** 2 + Math.cos(t(a)) * Math.cos(t(c)) * Math.sin(dn / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
function roundDist(km) {
  const m = km * 1000;
  if (m < 150) return '~100 m';
  if (m < 1000) return `~${Math.round(m / 100) * 100} m`;
  if (km < 10) return `~${Math.round(km * 10) / 10} km`;
  return `~${Math.round(km)} km`;
}
const coarse = v => Math.round(v * 1000) / 1000; // ~110 m
const validLatLng = (lat, lng) => typeof lat === 'number' && typeof lng === 'number' && isFinite(lat) && isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
async function blockedEither(a, b) {
  const [x, y] = await Promise.all([db.ref(`blocks/${a}/${b}`).get(), db.ref(`blocks/${b}/${a}`).get()]);
  return x.exists() || y.exists();
}
async function summary(uid) {
  const u = (await db.ref(`users/${uid}`).get()).val();
  return u ? publicProfile(uid, u) : null;
}
// --- friends
app.post('/friends/request', w(notBanned), w(async (q, r) => {
  const to = q.body?.toUid;
  if (typeof to !== 'string' || !UID_RE.test(to) || to === q.uid) return r.status(400).json({ error: 'valid toUid required' });
  if (!(await db.ref(`users/${to}`).get()).exists()) return r.status(404).json({ error: 'user not found' });
  if (await isBanned(to) || await blockedEither(q.uid, to)) return r.status(403).json({ error: 'user unavailable' });
  if ((await db.ref(`friends/${q.uid}/${to}`).get()).exists()) return r.json({ status: 'already_friends' });
  if ((await db.ref(`friendRequests/${q.uid}/${to}`).get()).exists()) { // they already asked me -> accept
    const t = Date.now(); const { chatId } = await openDirectChat(q.uid, to);
    await db.ref().update({ [`friends/${q.uid}/${to}`]: t, [`friends/${to}/${q.uid}`]: t, [`friendRequests/${q.uid}/${to}`]: null });
    return r.json({ status: 'friends', chatId });
  }
  const sim = similarity(await ipOf(q.uid), await ipOf(to), await getInterestCfg());
  await db.ref(`friendRequests/${to}/${q.uid}`).set({ at: Date.now(), similarity: sim.percent, shared: sim.shared });
  r.json({ status: 'requested', similarity: sim });
}));
app.post('/friends/respond', w(notBanned), w(async (q, r) => {
  const from = q.body?.fromUid, accept = q.body?.accept === true;
  if (typeof from !== 'string' || !UID_RE.test(from)) return r.status(400).json({ error: 'valid fromUid required' });
  if (!(await db.ref(`friendRequests/${q.uid}/${from}`).get()).exists()) return r.status(404).json({ error: 'no such request' });
  if (!accept) { await db.ref(`friendRequests/${q.uid}/${from}`).remove(); return r.json({ status: 'declined' }); }
  if (await isBanned(from)) return r.status(403).json({ error: 'user unavailable' });
  const t = Date.now(); const { chatId } = await openDirectChat(q.uid, from);
  await db.ref().update({ [`friends/${q.uid}/${from}`]: t, [`friends/${from}/${q.uid}`]: t, [`friendRequests/${q.uid}/${from}`]: null });
  r.json({ status: 'friends', chatId });
}));
app.get('/friends', w(async (q, r) => {
  const ids = Object.keys((await db.ref(`friends/${q.uid}`).get()).val() || {}).slice(0, 500);
  r.json({ friends: (await Promise.all(ids.map(summary))).filter(Boolean) });
}));
app.get('/friends/requests', w(async (q, r) => {
  const reqs = (await db.ref(`friendRequests/${q.uid}`).get()).val() || {};
  const out = [];
  for (const [uid, v] of Object.entries(reqs).slice(0, 200)) { const p = await summary(uid); if (p) out.push({ ...p, at: v.at || 0, similarity: v.similarity ?? null, shared: v.shared || {} }); }
  r.json({ requests: out });
}));
// --- radar (coarse only: 3 decimals ~110 m + geohash7; never returned to clients)
app.post('/radar/location', w(notBanned), w(async (q, r) => {
  const { lat, lng } = q.body || {};
  if (!validLatLng(lat, lng)) return r.status(400).json({ error: 'lat,lng required' });
  const la = coarse(lat), lo = coarse(lng);
  await db.ref(`radar/${q.uid}`).set({ gh: geohash(la, lo, 7), lat: la, lng: lo, at: Date.now() });
  r.json({ ok: true, visible: true });
}));
app.delete('/radar/location', w(async (q, r) => { await db.ref(`radar/${q.uid}`).remove(); r.json({ ok: true, visible: false }); }));
app.get('/radar/nearby', w(notBanned), w(quota('radarPerDay')), w(async (q, r) => {
  const radius = pickRadius(q.query.radiusKm);
  const me = (await db.ref(`radar/${q.uid}`).get()).val();
  if (!me) return r.status(409).json({ error: 'location not shared' });
  const prec = radius <= 2 ? 5 : radius <= 20 ? 4 : 3; // geohash5 cell ~4.9x4.9 km
  const cellDeg = { 5: 0.044, 4: 0.18, 3: 1.4 }[prec];
  const step = Math.max(cellDeg, radius / 111);
  const prefixes = new Set();
  for (const dy of [-1, 0, 1]) for (const dx of [-1, 0, 1]) {
    const la = Math.max(-89.9, Math.min(89.9, me.lat + dy * step)), lo = ((me.lng + dx * step / Math.max(0.2, Math.cos(me.lat * Math.PI / 180)) + 540) % 360) - 180;
    prefixes.add(geohash(la, lo, prec));
  }
  const cutoff = Date.now() - 30 * 60 * 1000, seen = new Map();
  for (const p of prefixes) {
    const s = await db.ref('radar').orderByChild('gh').startAt(p).endAt(p + '\uf8ff').limitToFirst(500).get();
    s.forEach(c => { seen.set(c.key, c.val()); });
  }
  const out = [];
  for (const [uid, v] of seen) {
    if (uid === q.uid || !v || v.at < cutoff) continue;
    const km = distKm(me.lat, me.lng, v.lat, v.lng);
    if (km > radius) continue;
    if (await isBanned(uid) || await blockedEither(q.uid, uid)) continue;
    const p = await summary(uid); if (!p) continue;
    out.push({ ...p, distance: roundDist(km), _km: km });
  }
  out.sort((a, b) => a._km - b._km);
  r.json({ radiusKm: radius, users: out.slice(0, 50).map(({ _km, ...x }) => x) });
}));
// --- shake (15 s window)
const SHAKE_MS = 15000;

app.post('/shake', w(notBanned), w(quota('shakePerDay')), w(async (q, r) => {
  const { lat, lng } = q.body || {};
  const radiusKm = pickRadius(q.body?.radiusKm);
  const hasLoc = validLatLng(lat, lng);
  const la = hasLoc ? coarse(lat) : null, lo = hasLoc ? coarse(lng) : null;
  const now = Date.now(); let partner = null;
  await db.ref(`shakeResults/${q.uid}`).remove();
  await db.ref('shakeWaiting').transaction(cur => {
    const w8 = {}; partner = null;
    for (const [k, v] of Object.entries(cur || {})) if (v && now - v.at < SHAKE_MS && k !== q.uid) w8[k] = v;
    // With location: only users that also shared location and are within BOTH users' radius (nearest first).
    // Without location: only other users without location ("anywhere" mode).
    const ids = Object.keys(w8).filter(k => hasLoc ? (w8[k].lat != null && distKm(la, lo, w8[k].lat, w8[k].lng) <= Math.min(radiusKm, w8[k].radiusKm || 0.5)) : w8[k].lat == null);
    if (hasLoc) ids.sort((a, b) => distKm(la, lo, w8[a].lat, w8[a].lng) - distKm(la, lo, w8[b].lat, w8[b].lng));
    partner = ids[0] || null;
    if (partner) { delete w8[partner]; return w8; }
    w8[q.uid] = hasLoc ? { at: now, lat: la, lng: lo, radiusKm } : { at: now }; return w8;
  });
  if (partner && (await isBanned(partner) || await blockedEither(q.uid, partner))) partner = null;
  if (!partner) return r.json({ status: 'waiting', windowMs: SHAKE_MS, mode: hasLoc ? 'nearby' : 'anywhere', radiusKm });
  await db.ref().update({ [`shakeResults/${q.uid}`]: { other: partner, at: now }, [`shakeResults/${partner}`]: { other: q.uid, at: now } });
  r.json({ status: 'matched', user: await summary(partner) });
}));
app.get('/shake/result', w(async (q, r) => {
  const res = (await db.ref(`shakeResults/${q.uid}`).get()).val();
  if (res && Date.now() - res.at < 60000) return r.json({ status: 'matched', user: await summary(res.other) });
  const wv = (await db.ref(`shakeWaiting/${q.uid}`).get()).val();
  if (wv && Date.now() - wv.at < SHAKE_MS) return r.json({ status: 'waiting' });
  if (wv) await db.ref(`shakeWaiting/${q.uid}`).remove();
  r.json({ status: 'timeout' });
}));
app.use((e, _q, r, _n) => {
  if (e.type === 'entity.too.large') return r.status(413).json({ error: 'request too large' });
  if (e.type === 'entity.parse.failed') return r.status(400).json({ error: 'invalid JSON' });
  r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' });
});
app.listen(process.env.PORT || 8080);
