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
const PERMS = ['coins_confirm','age_approve','config_edit','ban_users','manage_content','app_lock'];
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
app.use(['/coins', '/age', '/config', '/admin', '/superadmin', '/me', '/match', '/bot', '/ui', '/chats', '/swipe', '/posts', '/feed', '/friends', '/radar', '/shake', '/limits', '/quota', '/search', '/users', '/interests', '/bots', '/gifts', '/rooms', '/reports', '/creator', '/blocks', '/chat-rules', '/companion-apply', '/companion', '/shop'], w(auth));
app.get('/me', w(async (q, r) => {
  const role = await loadRole(q.uid);
  const raw = role ? (await db.ref(`roles/${q.uid}/label`).get()).val() : null;
  r.json({ uid: q.uid, role: raw === 'moderator' ? 'moderator' : (role?.role || 'user'), permissions: role?.permissions || {} });
}));
// Whole-app lock: non-staff requests are rejected while appConfig/lock/locked is true (allow /me, /config, /ui/config, /lock).
let lockCache = { at: 0, v: null };
async function lockState() { if (Date.now() - lockCache.at < 10e3) return lockCache.v; const v = (await db.ref('appConfig/lock').get()).val() || {}; lockCache = { at: Date.now(), v }; return v; }
app.use(w(async (q, r, n) => {
  if (!q.uid) return n();
  if (/^\/(me|config|ui\/config|lock|admin|superadmin)(\/|$)/.test(q.path)) return n();
  const l = await lockState(); if (l.locked !== true) return n();
  const role = await loadRole(q.uid); if (role && ['admin', 'superadmin'].includes(role.role)) return n();
  r.status(423).json({ error: 'app locked', message: l.message || 'แอปปิดชั่วคราว' });
}));
// Per-feature lock: appConfig/featureLocks {parts:{part:true}, message}. Feature stays visible in the app (greyed + lock badge) but its endpoints return 423 {code:'feature_locked', part} for non-staff.
const PARTS = ['home','posting','discover','random','shake','radar','swipe','similar','requests','companion','live','video','chat','ai','ads','gifts','decor'];
const PART_OF = q => {
  const p = q.path;
  if (/^\/posts\/?$/.test(p) && q.method === 'POST') return 'posting';
  if (/^\/(posts|feed)(\/|$)/.test(p)) return 'home';
  if (/^\/match(\/|$)/.test(p)) return 'random';
  if (/^\/shake(\/|$)/.test(p)) return 'shake';
  if (/^\/radar(\/|$)/.test(p)) return 'radar';
  if (/^\/swipe(\/|$)/.test(p)) return 'swipe';
  if (/^\/interests\/(similar|match)/.test(p)) return 'similar';
  if (/^\/friends\/(request|respond|requests)/.test(p)) return 'requests';
  if (/^\/search(\/|$)/.test(p)) return 'discover';
  if (/^\/chats(\/|$)/.test(p)) return 'chat';
  if (/^\/(bot|bots)(\/|$)/.test(p)) return 'ai';
  if (/^\/(quota|admob)(\/|$)/.test(p)) return 'ads';
  if (/^\/rooms(\/|$)/.test(p)) return 'decor';
  if (/^\/gifts(\/|$)/.test(p)) return 'gifts';
  return null;
};
let flCache = { at: 0, v: null };
async function featureLocks() { if (Date.now() - flCache.at < 10e3) return flCache.v; const v = (await db.ref('appConfig/featureLocks').get()).val() || {}; flCache = { at: Date.now(), v }; return v; }
app.use(w(async (q, r, n) => {
  if (!q.uid) return n();
  const part = PART_OF(q); if (!part) return n();
  const f = await featureLocks(); if (!(f.parts && f.parts[part])) return n();
  const role = await loadRole(q.uid); if (role && ['admin', 'superadmin', 'moderator'].includes(role.role)) return n();
  r.status(423).json({ error: 'feature locked', code: 'feature_locked', part, message: f.message || 'ฟีเจอร์นี้ปิดใช้งานชั่วคราว' });
}));
app.put('/admin/feature-locks', perm('app_lock'), w(notBanned), w(async (q, r) => {
  const parts = {}; for (const k of Object.keys(q.body?.parts || {})) if (PARTS.includes(k) && q.body.parts[k] === true) parts[k] = true;
  const v = { parts, message: String(q.body?.message || '').slice(0, 300), by: q.uid, at: Date.now() };
  await db.ref('appConfig/featureLocks').set(v); flCache = { at: 0, v: null };
  await db.ref('adminLog').push({ action: 'app_feature_locks', by: q.uid, at: Date.now(), message: Object.keys(parts).join(',') || '(none)' });
  r.json(v);
}));
// Ad placements (live via RTDB appConfig/adPlacements; clients read directly).
const AD_PLACES = ['home', 'discover', 'chatList', 'video', 'inFeed', 'chatRoom'];
app.put('/admin/ad-placements', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, v = {};
  for (const k of AD_PLACES) if (typeof b[k] === 'boolean') v[k] = b[k];
  v.inFeedEvery = Math.max(2, Math.min(50, parseInt(b.inFeedEvery) || 5)); v.by = q.uid; v.at = Date.now();
  await db.ref('appConfig/adPlacements').set(v); r.json(v);
}));
// ===== Creator applications: creatorApplications/{uid} {status pending|approved|rejected, ...}; creators/{uid} = approved.
const CR_CATS = ['music', 'singing', 'talk', 'gaming', 'dance', 'art', 'cooking', 'fitness', 'education', 'lifestyle', 'other'];
const DEFAULT_CR_RULES = ['ต้องลงคลิปอย่างต่อเนื่อง', 'ลงคลิปสม่ำเสมอ อย่างน้อยวันละ 1 คลิป (ประมาณ 30 คลิป/เดือน) และห้ามเว้นเกิน 2 วัน', 'ภายใน 30 วัน ต้องมีผู้ชมไม่ซ้ำกัน 100,000 คน (1 คน = 1 วิว ไม่นับซ้ำ ไม่ว่าดูกี่ครั้ง)', 'สิทธิ์รับรายได้จากโฆษณาเฉพาะครีเอเตอร์ที่ได้รับอนุมัติและผ่านเกณฑ์เท่านั้น เริ่มตั้งแต่วันถัดไปหลังได้รับอนุมัติ (ผู้ใช้ทั่วไปไม่มีรายได้จากโฆษณา เป็นผู้ชมโฆษณาเท่านั้น)', 'ต้องมีผู้ติดตาม 5,000 คน', 'คลิปต้องเป็นผลงานของตัวเอง', 'ครีเอเตอร์เป็นผู้รับผิดชอบลิขสิทธิ์ทั้งหมดด้วยตนเอง แอปและผู้โฆษณาไม่รับผิดชอบ', 'โฆษณาหรือเนื้อหาโปรโมตที่ครีเอเตอร์ลง ต้องไม่ละเมิดลิขสิทธิ์', 'หากปฏิบัติตามกติกาครบทุกข้อ ครีเอเตอร์จึงเริ่มได้รับการพิจารณารายได้จากโฆษณา', 'จ่ายรายได้ทุกสิ้นเดือน', 'ยอดขั้นต่ำในการถอน 100 {currency} หากยังไม่ถึงจะสะสมยกยอดไปเดือนถัดไปจนครบ 100 {currency} จึงถอนได้', 'ฉันอายุ 18 ปีขึ้นไป', 'ยอมรับกติกาชุมชน: ไม่มีเนื้อหาลามก ความรุนแรง หลอกลวง หรือละเมิดลิขสิทธิ์'];
async function creatorRules() { const v = (await db.ref('appConfig/creatorRules').get()).val(); const cur = (v && v.payoutCurrency) || 'เหรียญ', base = v && Array.isArray(v.rules) && v.rules.length ? v.rules : DEFAULT_CR_RULES;
  return { version: (v && v.version) || 1, payoutCurrency: cur, rawRules: base, rules: base.map(x => String(x).split('{currency}').join(cur)) }; }
app.put('/admin/creator-rules', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const rules = (Array.isArray(q.body?.rules) ? q.body.rules : []).map(x => String(x).trim().slice(0, 200)).filter(Boolean).slice(0, 15);
  if (!rules.length) return r.status(400).json({ error: 'rules required' });
  const cur = await creatorRules(), v = { version: (cur.version || 1) + 1, rules, payoutCurrency: String(q.body?.payoutCurrency || cur.payoutCurrency || 'เหรียญ').trim().slice(0, 20) || 'เหรียญ', by: q.uid, at: Date.now() };
  await db.ref('appConfig/creatorRules').set(v); r.json(v);
}));
const httpsOk = u => !u || /^https:\/\/[^\s]{3,300}$/.test(u);
app.get('/creator/status', w(async (q, r) => {
  const [c, a] = await Promise.all([db.ref(`creators/${q.uid}`).get(), db.ref(`creatorApplications/${q.uid}`).get()]);
  r.json({ isCreator: c.exists(), application: a.val() || null, rules: await creatorRules(), categories: CR_CATS });
}));
app.post('/creator/apply', w(notBanned), w(async (q, r) => {
  const b = q.body || {};
  if ((await db.ref(`creators/${q.uid}`).get()).exists()) return r.status(409).json({ error: 'already a creator' });
  const cur = (await db.ref(`creatorApplications/${q.uid}`).get()).val();
  if (cur && cur.status === 'pending') return r.status(409).json({ error: 'ใบสมัครกำลังรอตรวจสอบ' });
  const name = String(b.displayName || '').trim().slice(0, 40), bio = String(b.bio || '').trim().slice(0, 300), cat = String(b.category || '');
  const links = (Array.isArray(b.links) ? b.links : []).map(x => String(x).trim()).filter(Boolean).slice(0, 5), sample = String(b.sampleUrl || '').trim();
  if (name.length < 2) return r.status(400).json({ error: 'ต้องใส่ชื่อที่แสดง' });
  if (!CR_CATS.includes(cat)) return r.status(400).json({ error: 'เลือกหมวดหมู่' });
  if (bio.length < 10) return r.status(400).json({ error: 'แนะนำตัวอย่างน้อย 10 ตัวอักษร' });
  if (!links.every(httpsOk) || !httpsOk(sample)) return r.status(400).json({ error: 'ลิงก์ต้องขึ้นต้นด้วย https://' });
  if (b.adult !== true || b.agree !== true) return r.status(400).json({ error: 'ต้องยืนยันอายุ 18+ และยอมรับกติกา' });
  const cr = await creatorRules(), agreed = Array.isArray(b.agreedRules) ? b.agreedRules.map(Number) : [];
  if (+b.rulesVersion !== cr.version) return r.status(409).json({ error: 'กติกามีการเปลี่ยนแปลง กรุณาโหลดใหม่', rules: cr });
  if (!cr.rules.every((_, i) => agreed.includes(i))) return r.status(400).json({ error: 'ต้องติ๊กยอมรับกติกาทุกข้อ' });
  const v = { uid: q.uid, displayName: name, category: cat, bio, links, sampleUrl: sample.slice(0, 300), adult: true, agree: true, rulesVersion: cr.version, rulesAgreed: cr.rules, agreedAt: Date.now(), status: 'pending', submittedAt: Date.now(), reason: '' };
  await db.ref(`creatorApplications/${q.uid}`).set(v); r.json(v);
}));
app.get('/admin/creator-applications', perm('manage_content'), w(async (q, r) => {
  const st = ['pending', 'approved', 'rejected'].includes(q.query.status) ? q.query.status : 'pending';
  const v = (await db.ref('creatorApplications').get()).val() || {};
  r.json({ applications: Object.values(v).filter(x => x.status === st).sort((a, b) => b.submittedAt - a.submittedAt).slice(0, 100) });
}));
app.post('/admin/creator-applications/:uid/decide', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, approve = q.body?.approve === true, reason = String(q.body?.reason || '').trim().slice(0, 300);
  const ref = db.ref(`creatorApplications/${uid}`), a = (await ref.get()).val();
  if (!a) return r.status(404).json({ error: 'not found' });
  if (!approve && reason.length < 3) return r.status(400).json({ error: 'ต้องใส่เหตุผลที่ปฏิเสธ' });
  const t = Date.now(), upd = { [`creatorApplications/${uid}/status`]: approve ? 'approved' : 'rejected', [`creatorApplications/${uid}/reason`]: reason,
    [`creatorApplications/${uid}/decidedAt`]: t, [`creatorApplications/${uid}/decidedBy`]: q.uid };
  if (approve) { upd[`creators/${uid}`] = { at: t, by: q.uid, category: a.category }; upd[`users/${uid}/isCreator`] = true; }
  else { upd[`creators/${uid}`] = null; upd[`users/${uid}/isCreator`] = null; }
  await db.ref().update(upd);
  await db.ref('adminLog').push({ action: approve ? 'creator_approve' : 'creator_reject', target: uid, by: q.uid, at: t, message: reason });
  r.json({ uid, status: approve ? 'approved' : 'rejected', reason });
}));
// ===== User blocks (blocks/{me}/{other}); blocked pair: no messages (RTDB rule via chatBlocked/{chatId}), no gifts, no new chat.
app.get('/blocks', w(async (q, r) => r.json({ blocked: Object.keys((await db.ref(`blocks/${q.uid}`).get()).val() || {}) })));
app.post('/blocks/:uid', w(async (q, r) => {
  const other = q.params.uid; if (!UID_RE.test(other) || other === q.uid) return r.status(400).json({ error: 'bad uid' });
  const block = q.body?.block !== false, cid = directChatId(q.uid, other), t = Date.now();
  const upd = { [`blocks/${q.uid}/${other}`]: block ? t : null };
  const otherBlocks = (await db.ref(`blocks/${other}/${q.uid}`).get()).exists();
  upd[`chatBlocked/${cid}`] = (block || otherBlocks) ? true : null;
  await db.ref().update(upd); r.json({ uid: other, blocked: block });
}));
// ===== Chat / companion safety rules (appConfig/chatRules {version, rules}); acceptance chatRulesAccepted/{uid} {version, at}
const DEFAULT_CHAT_RULES = ['ผู้ใช้ต้องมีอายุ 18 ปีขึ้นไปเท่านั้น', 'ห้ามส่งหรือขอเนื้อหาลามก/เรื่องทางเพศ', 'ห้ามคุกคาม กลั่นแกล้ง หรือข่มขู่', 'ห้ามใช้ถ้อยคำแสดงความเกลียดชัง',
  'ห้ามเปิดเผยข้อมูลส่วนตัวของผู้อื่น', 'ห้ามหลอกลวง ฉ้อโกง หรือขอเงิน', 'ห้ามแอบอ้างเป็นบุคคลอื่น', 'ห้ามติดต่อหรือชักชวนผู้เยาว์ (อายุต่ำกว่า 18 ปี)',
  'ห้ามความรุนแรง กิจกรรมผิดกฎหมาย หรือยาเสพติด', 'ห้ามสแปมหรือโฆษณา', 'อย่าโอนเงินให้คนแปลกหน้า', 'กดรายงานหรือบล็อกได้ทุกเมื่อจากเมนู ⋯ ในห้องแชท'];
async function chatRules() { const v = (await db.ref('appConfig/chatRules').get()).val(); return v && Array.isArray(v.rules) && v.rules.length ? { version: v.version || 1, rules: v.rules } : { version: 1, rules: DEFAULT_CHAT_RULES }; }
app.get('/chat-rules', w(async (q, r) => { const cr = await chatRules(); const a = (await db.ref(`chatRulesAccepted/${q.uid}`).get()).val(); r.json({ ...cr, accepted: !!(a && a.version === cr.version), acceptedAt: a?.at || 0 }); }));
app.post('/chat-rules/accept', w(async (q, r) => {
  const cr = await chatRules(); if (+q.body?.version !== cr.version) return r.status(409).json({ error: 'rules changed', ...cr });
  await db.ref(`chatRulesAccepted/${q.uid}`).set({ version: cr.version, at: Date.now() }); r.json({ accepted: true, version: cr.version });
}));
app.put('/admin/chat-rules', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const rules = (Array.isArray(q.body?.rules) ? q.body.rules : []).map(x => String(x).trim().slice(0, 200)).filter(Boolean).slice(0, 25);
  if (!rules.length) return r.status(400).json({ error: 'rules required' });
  const cur = await chatRules(), v = { version: cur.version + 1, rules, by: q.uid, at: Date.now() }; await db.ref('appConfig/chatRules').set(v); r.json(v);
}));
// ===== เพื่อนคุย partner applications: public part companionApplications/{uid}; private part private/{uid}/companion (real name, phone) — never returned to other users.
const SLOTS = ['morning', 'afternoon', 'evening', 'night'], DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
const HR = /^([01]\d|2[0-3]):[0-5]\d-([01]\d|2[0-3]):[0-5]\d$/;
app.get('/companion-apply', w(async (q, r) => {
  const [a, pv] = await Promise.all([db.ref(`companionApplications/${q.uid}`).get(), db.ref(`private/${q.uid}/companion`).get()]);
  r.json({ application: a.val() || null, private: pv.val() || null }); // own data only
}));
app.post('/companion-apply', w(notBanned), w(async (q, r) => {
  const b = q.body || {};
  const cur = (await db.ref(`companionApplications/${q.uid}`).get()).val(); if (cur && cur.status === 'pending') return r.status(409).json({ error: 'ใบสมัครกำลังรอตรวจสอบ' });
  const realName = String(b.realName || '').trim().slice(0, 80), occ = String(b.occupation || '').trim().slice(0, 60), dn = String(b.displayName || '').trim().slice(0, 40);
  const age = parseInt(b.age), phone = String(b.phone || '').replace(/[\s-]/g, '');
  if (realName.length < 2) return r.status(400).json({ error: 'กรอกชื่อจริง' });
  if (occ.length < 2) return r.status(400).json({ error: 'กรอกอาชีพ' });
  if (dn.length < 2) return r.status(400).json({ error: 'กรอกชื่อที่แสดงในแอป' });
  if (!(age >= 18 && age <= 99)) return r.status(400).json({ error: 'ต้องมีอายุ 18 ปีขึ้นไป' });
  if (!/^(0[689]\d{8}|0[2-7]\d{7}|\+66[689]\d{8})$/.test(phone)) return r.status(400).json({ error: 'เบอร์โทรไม่ถูกต้อง (เช่น 0812345678)' });
  const slots = (Array.isArray(b.slots) ? b.slots : []).filter(x => SLOTS.includes(x)); if (!slots.length) return r.status(400).json({ error: 'เลือกช่วงเวลาที่สะดวกอย่างน้อย 1 ช่วง' });
  const hours = {}; for (const d of DAYS) { const v = String(b.hours?.[d] || '').trim(); if (v) { if (!HR.test(v)) return r.status(400).json({ error: `เวลา ${d} ต้องเป็นรูปแบบ 18:00-22:00` }); hours[d] = v; } }
  const modes = (Array.isArray(b.modes) ? b.modes : []).filter(x => ['text', 'voice', 'video'].includes(x)); if (!modes.length) return r.status(400).json({ error: 'เลือกรูปแบบการคุยอย่างน้อย 1 แบบ' });
  const t = Date.now(), talkStyle = String(b.talkStyle || '').trim().slice(0, 300), personality = String(b.personality || '').trim().slice(0, 300);
  await db.ref().update({
    [`companionApplications/${q.uid}`]: { uid: q.uid, displayName: dn, occupation: occ, age, slots, hours, talkStyle, personality, modes: [...new Set(modes)], status: 'pending', submittedAt: t, reason: '' },
    [`private/${q.uid}/companion`]: { realName, phone, updatedAt: t }
  });
  r.json({ status: 'pending' });
}));
app.get('/admin/companion-applications', perm('manage_content'), w(async (q, r) => {
  const st = ['pending', 'approved', 'rejected'].includes(q.query.status) ? q.query.status : 'pending';
  const v = (await db.ref('companionApplications').get()).val() || {};
  const list = Object.values(v).filter(x => x.status === st).sort((a, b) => b.submittedAt - a.submittedAt).slice(0, 100);
  const withPriv = await Promise.all(list.map(async a => ({ ...a, private: (await db.ref(`private/${a.uid}/companion`).get()).val() || null })));
  r.json({ applications: withPriv });
}));
app.post('/admin/companion-applications/:uid/decide', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, approve = q.body?.approve === true, reason = String(q.body?.reason || '').trim().slice(0, 300);
  const a = (await db.ref(`companionApplications/${uid}`).get()).val(); if (!a) return r.status(404).json({ error: 'not found' });
  if (!approve && reason.length < 3) return r.status(400).json({ error: 'ต้องใส่เหตุผลที่ปฏิเสธ' });
  const t = Date.now(), upd = { [`companionApplications/${uid}/status`]: approve ? 'approved' : 'rejected', [`companionApplications/${uid}/reason`]: reason, [`companionApplications/${uid}/decidedAt`]: t, [`companionApplications/${uid}/decidedBy`]: q.uid };
  upd[`companions/${uid}`] = approve ? { displayName: a.displayName, occupation: a.occupation, age: a.age, slots: a.slots, hours: a.hours || {}, talkStyle: a.talkStyle || '', personality: a.personality || '', modes: a.modes || ['text'], at: t } : null; // public card only
  await db.ref().update(upd);
  await db.ref('adminLog').push({ action: approve ? 'companion_approve' : 'companion_reject', target: uid, by: q.uid, at: t, message: reason });
  r.json({ uid, status: approve ? 'approved' : 'rejected' });
}));
// ===== เพื่อนคุย rates (admin only) + server-side text-chat charging. appConfig/companionRates.
const RATE_DEF = { textMode: 'per_message', textAmount: 1, sessionMinutes: 60, voicePerMin: 0, videoPerMin: 0, currency: 'diamonds', partnerPercent: 80 };
async function companionRates() { return { ...RATE_DEF, ...((await db.ref('appConfig/companionRates').get()).val() || {}) }; }
app.get('/companion/rates', w(async (_q, r) => r.json(await companionRates())));
app.put('/admin/companion-rates', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, int = (x, lo, hi, d) => { const n = parseInt(x); return Number.isFinite(n) ? Math.max(lo, Math.min(hi, n)) : d; };
  const v = { textMode: b.textMode === 'per_session' ? 'per_session' : 'per_message', textAmount: int(b.textAmount, 0, 100000, 1), sessionMinutes: int(b.sessionMinutes, 5, 1440, 60),
    voicePerMin: int(b.voicePerMin, 0, 100000, 0), videoPerMin: int(b.videoPerMin, 0, 100000, 0), currency: b.currency === 'coins' ? 'coins' : 'diamonds',
    partnerPercent: int(b.partnerPercent, 0, 100, 80), by: q.uid, at: Date.now() };
  await db.ref('appConfig/companionRates').set(v); r.json(v);
}));
async function chargeCompanion(payer, partner, amount, rates, memo) {
  if (amount <= 0) return { ok: true, charged: 0 };
  const cur = rates.currency;
  const d = await db.ref(`wallets/${payer}/${cur}`).transaction(c => (c === null ? null : num(c) >= amount ? num(c) - amount : undefined));
  if (!d.committed || d.snapshot.val() === null) return { ok: false };
  const share = Math.floor(amount * rates.partnerPercent / 100);
  await Promise.all([db.ref(`wallets/${partner}/${cur}`).transaction(c => num(c) + share), db.ref(`wallets/_system/${cur}`).transaction(c => num(c) + (amount - share)),
    db.ref('companionLedger').push({ payer, partner, amount, share, currency: cur, memo, at: Date.now() }), db.ref(`companionPaid/${partner}/${payer}`).set(Date.now())]);
  return { ok: true, charged: amount, balance: d.snapshot.val() };
}
app.post('/companion/:uid/open', w(notBanned), w(async (q, r) => {
  const partner = q.params.uid; if (!UID_RE.test(partner) || partner === q.uid) return r.status(400).json({ error: 'bad uid' });
  const cp = (await db.ref(`companions/${partner}`).get()).val(); if (!cp) return r.status(404).json({ error: 'ไม่ใช่เพื่อนคุยที่ได้รับอนุมัติ' });
  if (!(cp.modes || ['text']).includes('text')) return r.status(400).json({ error: 'เพื่อนคุยคนนี้ไม่รับคุยข้อความ' });
  if (await blockedEither(q.uid, partner)) return r.status(403).json({ code: 'blocked', error: 'ไม่สามารถแชทกับผู้ใช้นี้ได้' });
  const rt = await companionRates(), bal = num((await db.ref(`wallets/${q.uid}/${rt.currency}`).get()).val());
  if (bal <= 0 || bal < rt.textAmount) return r.status(402).json({ code: 'insufficient', error: rt.currency === 'diamonds' ? 'เติมเพชรก่อนเริ่มคุย' : 'เติมเหรียญก่อนเริ่มคุย', balance: bal, price: rt.textAmount, currency: rt.currency });
  const o = await openDirectChat(q.uid, partner);
  await db.ref(`chats/${o.chatId}`).update({ paid: true, companion: partner });
  r.json({ ...o, rates: await companionRates() });
}));
async function paidChat(chatId, uid) {
  const c = (await db.ref(`chats/${chatId}`).get()).val(); if (!c || !c.paid) return null;
  if (!(await db.ref(`chatMembers/${chatId}/${uid}`).get()).exists()) return null;
  return c;
}
app.post('/companion/chats/:chatId/session', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params, c = await paidChat(chatId, q.uid); if (!c) return r.status(404).json({ error: 'not a paid chat' });
  const rates = await companionRates(); if (rates.textMode !== 'per_session') return r.status(400).json({ error: 'not session mode' });
  const sref = db.ref(`companionSessions/${chatId}/${q.uid}`), cur = (await sref.get()).val();
  if (cur && cur.until > Date.now()) return r.json({ active: true, until: cur.until, charged: 0 });
  const ch = await chargeCompanion(q.uid, c.companion, rates.textAmount, rates, `session ${chatId}`);
  if (!ch.ok) return r.status(402).json({ code: 'insufficient', error: rates.currency === 'diamonds' ? 'เพชรไม่พอ' : 'เหรียญไม่พอ', price: rates.textAmount, currency: rates.currency });
  const until = Date.now() + rates.sessionMinutes * 60e3; await sref.set({ until, at: Date.now() });
  r.json({ active: true, until, charged: ch.charged, balance: ch.balance });
}));
app.post('/companion/chats/:chatId/send', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params, c = await paidChat(chatId, q.uid); if (!c) return r.status(404).json({ error: 'not a paid chat' });
  const text = String(q.body?.text || '').trim().slice(0, 2000); if (!text) return r.status(400).json({ error: 'text required' });
  if ((await db.ref(`chatBlocked/${chatId}`).get()).exists() || (await db.ref(`chats/${chatId}/closed`).get()).exists()) return r.status(403).json({ error: 'แชทนี้ส่งไม่ได้' });
  const rates = await companionRates(); let charged = 0, balance;
  if (q.uid !== c.companion) {
    if (rates.textMode === 'per_message') {
      const ch = await chargeCompanion(q.uid, c.companion, rates.textAmount, rates, `msg ${chatId}`);
      if (!ch.ok) return r.status(402).json({ code: 'insufficient', error: rates.currency === 'diamonds' ? 'เพชรไม่พอ ส่งข้อความไม่ได้' : 'เหรียญไม่พอ ส่งข้อความไม่ได้', price: rates.textAmount, currency: rates.currency });
      charged = ch.charged; balance = ch.balance;
    } else {
      const sv = (await db.ref(`companionSessions/${chatId}/${q.uid}`).get()).val();
      if (!sv || sv.until < Date.now()) return r.status(402).json({ code: 'session_required', error: 'เริ่มรอบคุยก่อน', price: rates.textAmount, currency: rates.currency, minutes: rates.sessionMinutes });
    }
  }
  const t = Date.now(), id = db.ref(`messages/${chatId}`).push().key;
  await db.ref().update({ [`messages/${chatId}/${id}`]: { senderUid: q.uid, at: t, text }, [`chats/${chatId}/lastMessage`]: text.slice(0, 100), [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: q.uid });
  r.json({ id, charged, balance });
}));
// เพื่อนคุย ratings: only payers (companionPaid) can rate; one per user per partner (editable); aggregate on companions/{p}/rating.
async function recomputeRating(partner) {
  const v = Object.values((await db.ref(`companionRatings/${partner}`).get()).val() || {});
  const count = v.length, avg = count ? Math.round(v.reduce((s, x) => s + num(x.stars), 0) / count * 10) / 10 : 0;
  if ((await db.ref(`companions/${partner}`).get()).exists()) await db.ref(`companions/${partner}/rating`).set({ avg, count });
  return { avg, count };
}
app.post('/companion/:uid/rate', w(notBanned), w(async (q, r) => {
  const partner = q.params.uid, stars = parseInt(q.body?.stars), comment = String(q.body?.comment || '').trim().slice(0, 200);
  if (!UID_RE.test(partner) || !(stars >= 1 && stars <= 5)) return r.status(400).json({ error: 'stars 1-5 required' });
  if (!(await db.ref(`companionPaid/${partner}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'ให้คะแนนได้เฉพาะผู้ที่เคยคุยแบบชำระเงินแล้ว' });
  await db.ref(`companionRatings/${partner}/${q.uid}`).set({ stars, comment, at: Date.now() });
  r.json(await recomputeRating(partner));
}));
app.get('/companion/:uid/my-rating', w(async (q, r) => r.json({ rating: (await db.ref(`companionRatings/${q.params.uid}/${q.uid}`).get()).val(), canRate: (await db.ref(`companionPaid/${q.params.uid}/${q.uid}`).get()).exists() })));
app.get('/admin/companion-ratings/:uid', perm('manage_content'), w(async (q, r) => {
  const v = (await db.ref(`companionRatings/${q.params.uid}`).get()).val() || {};
  r.json({ ratings: Object.entries(v).map(([uid, x]) => ({ uid, ...x })).sort((a, b) => b.at - a.at) });
}));
app.delete('/admin/companion-ratings/:partner/:user', perm('manage_content'), w(async (q, r) => {
  await db.ref(`companionRatings/${q.params.partner}/${q.params.user}`).remove();
  await db.ref('adminLog').push({ action: 'companion_rating_remove', target: q.params.partner, user: q.params.user, by: q.uid, at: Date.now() });
  r.json(await recomputeRating(q.params.partner));
}));
// ===== Shop (Google Play Billing). appConfig/shopProducts {enabled, diamonds:[{productId, amount}], coins:[...]}; server verifies tokens via androidpublisher.
const PLAY_PACKAGE = process.env.PLAY_PACKAGE || 'com.example';
const crypto2 = require('crypto');
async function shopCfg() { const v = (await db.ref('appConfig/shopProducts').get()).val() || {}; return { enabled: v.enabled === true, diamonds: v.diamonds || [], coins: v.coins || [], packageName: v.packageName || PLAY_PACKAGE }; }
app.get('/shop/products', w(async (_q, r) => r.json(await shopCfg())));
app.put('/admin/shop-products', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const clean = l => (Array.isArray(l) ? l : []).map((x, i) => ({ productId: String(x.productId || '').trim(), amount: parseInt(x.amount) || 0, label: String(x.label || '').slice(0, 40),
      bonus: Math.max(0, Math.min(1e6, parseInt(x.bonus) || 0)), priceTHB: Math.max(0, Math.min(100000, Number(x.priceTHB) || 0)), enabled: x.enabled !== false, order: Number.isFinite(+x.order) ? +x.order : i }))
    .filter(x => /^[a-z0-9][a-z0-9._]{0,98}$/.test(x.productId) && x.amount > 0 && x.amount <= 1e6).slice(0, 12);
  const pk = String(q.body?.packageName || '').trim();
  const v = { enabled: q.body?.enabled === true, diamonds: clean(q.body?.diamonds), coins: clean(q.body?.coins), packageName: /^[a-zA-Z][\w.]{2,100}$/.test(pk) ? pk : PLAY_PACKAGE, by: q.uid, at: Date.now() };
  await db.ref('appConfig/shopProducts').set(v);
  // Push THB prices to Play Console (only works once the app + products exist in Play and ft-brain-sa has Play Console access).
  const sync = [];
  if (q.body?.syncPlay === true) for (const x of [...v.diamonds, ...v.coins]) {
    if (!(x.priceTHB > 0)) continue;
    const res = await playApi('PATCH', `applications/${encodeURIComponent(v.packageName)}/inappproducts/${encodeURIComponent(x.productId)}?autoConvertMissingPrices=true&body=`.replace('&body=', ''), {
      packageName: v.packageName, sku: x.productId, status: x.enabled ? 'active' : 'inactive', defaultPrice: { priceMicros: String(Math.round(x.priceTHB * 1e6)), currency: 'THB' } }).catch(e => ({ status: 0, j: { error: e.message } }));
    sync.push({ productId: x.productId, status: res.status, error: res.status === 200 ? '' : JSON.stringify(res.j?.error?.message || res.j?.error || '').slice(0, 200) });
  }
  r.json({ ...v, playSync: sync });
}));
async function playApi(method, path, body) {
  const tok = (await admin.credential.applicationDefault().getAccessToken()).access_token;
  const res = await fetch(`https://androidpublisher.googleapis.com/androidpublisher/v3/${path}`, { method, headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const txt = await res.text(); let j = null; try { j = txt ? JSON.parse(txt) : {}; } catch (_) {}
  return { status: res.status, j };
}
app.post('/shop/verify', w(notBanned), w(async (q, r) => {
  const cfg = await shopCfg(); if (!cfg.enabled) return r.status(403).json({ code: 'shop_closed', error: 'ยังไม่เปิดขาย' });
  const productId = String(q.body?.productId || ''), token = String(q.body?.purchaseToken || '');
  if (!token || token.length > 1000) return r.status(400).json({ error: 'purchaseToken required' });
  const cur = cfg.diamonds.find(x => x.productId === productId) ? 'diamonds' : cfg.coins.find(x => x.productId === productId) ? 'coins' : null;
  if (!cur) return r.status(400).json({ error: 'unknown product' });
  const pk = cfg[cur].find(x => x.productId === productId), amount = pk.amount + (pk.bonus || 0);
  const key = crypto2.createHash('sha256').update(token).digest('hex').slice(0, 40), pref = db.ref(`shopPurchases/${key}`);
  const done = (await pref.get()).val(); if (done && done.status === 'credited') return r.json({ already: true, currency: done.currency, amount: done.amount, uid: done.uid === q.uid });
  const g = await playApi('GET', `applications/${encodeURIComponent(cfg.packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}`);
  if (g.status !== 200 || !g.j) return r.status(402).json({ error: 'verify failed', playStatus: g.status });
  if (g.j.purchaseState !== 0) return r.status(402).json({ error: 'purchase not completed', purchaseState: g.j.purchaseState });
  if (g.j.obfuscatedExternalAccountId && g.j.obfuscatedExternalAccountId !== q.uid.slice(0, 64)) return r.status(403).json({ error: 'purchase belongs to another account' });
  const lock = await pref.transaction(v => (v === null ? { status: 'crediting', uid: q.uid, at: Date.now() } : undefined));
  if (!lock.committed) return r.status(409).json({ error: 'already processing' });
  await db.ref(`wallets/${q.uid}/${cur}`).transaction(c => num(c) + amount);
  await pref.update({ status: 'credited', currency: cur, amount, productId, orderId: g.j.orderId || '', creditedAt: Date.now() });
  const c = await playApi('POST', `applications/${encodeURIComponent(cfg.packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}:consume`);
  await pref.update({ consumed: c.status === 200 || c.status === 204 });
  r.json({ credited: true, currency: cur, amount, consumed: c.status === 200 || c.status === 204 });
}));
app.get('/lock', w(async (_q, r) => { const l = await lockState(); r.json({ locked: l.locked === true, message: l.message || '', imageUrl: l.imageUrl || '' }); }));
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
  posting: { enabled: true, freeEnabled: true, vipEnabled: true, reason: '' },
  // person-to-person chat switches; read by RTDB rules too (messages write)
  chat: { enabled: true, freeEnabled: true, vipEnabled: true, reason: '' },
  comments: { enabled: true, reason: '' }
};
const withDefaults = L => ({ ...L, posting: { ...SEED_LIMITS.posting, ...(L.posting || {}) }, chat: { ...SEED_LIMITS.chat, ...(L.chat || {}) }, comments: { ...SEED_LIMITS.comments, ...(L.comments || {}) } });
const chatDay = () => Math.floor((Date.now() + 7 * 3600 * 1000) / 86400000); // Bangkok day number (same formula as RTDB rules)
async function chatFor(uid, tier, L) {
  const C = { ...SEED_LIMITS.chat, ...(L.chat || {}) };
  const blk = (await db.ref(`chatBlocks/${uid}`).get()).val();
  if (blk) return { canChat: false, reason: String(blk.reason || 'บัญชีนี้ถูกระงับการแชท') };
  if (!C.enabled) return { canChat: false, reason: C.reason || 'ปิดระบบแชทชั่วคราว' };
  if (!(tier === 'vip' ? C.vipEnabled : C.freeEnabled)) return { canChat: false, reason: C.reason || 'ระดับสมาชิกของคุณยังแชทไม่ได้' };
  return { canChat: true, reason: '' };
}
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
/** Timed ad grants (offer.validityHours > 0): {feature: amount} still valid now. */
async function activeGrants(uid) {
  const v = (await db.ref(`quotaGrants/${uid}`).get()).val() || {}, out = {}, now = Date.now(), dead = {};
  for (const [k, g] of Object.entries(v)) { if (g && g.until > now) out[g.feature] = (out[g.feature] || 0) + num(g.amount); else dead[k] = null; }
  if (Object.keys(dead).length) db.ref(`quotaGrants/${uid}`).update(dead).catch(() => {});
  return out;
}
async function quotaInfo(uid, L) {
  const { tier, vipUntil } = await tierOf(uid);
  const t = L.tiers?.[tier] || {}, day = dayKey();
  const [used, bonus] = await Promise.all([db.ref(`usage/${uid}/${day}`).get(), db.ref(`quotaBonus/${uid}/${day}`).get()]);
  const u = used.val() || {}, b = { ...(bonus.val() || {}) };
  for (const [f, a] of Object.entries(await activeGrants(uid))) b[f] = num(b[f]) + a;
  const features = {};
  for (const f of FEATURES) {
    const base = num(t[f]), extra = num(b[f]), lim = base === 0 ? 0 : base + extra;
    features[f] = { limit: lim, base, bonus: extra, used: num(u[f]), remaining: lim === 0 ? null : Math.max(0, lim - num(u[f])), unlimited: base === 0 };
  }
  const cc = (await db.ref(`chatCount/${uid}`).get()).val();
  { const f = features.chatMessagesPerDay; if (f) { f.used = cc && cc.day === chatDay() ? num(cc.n) : 0; f.limit = f.base; f.bonus = 0; f.remaining = f.base === 0 ? null : Math.max(0, f.base - f.used); } }
  const posting = await postingFor(uid, tier, L), chat = await chatFor(uid, tier, L);
  const comments = { ...SEED_LIMITS.comments, ...(L.comments || {}) };
  return { tier, vipUntil, day, features, posting, chat, comments, ads: { watched: num(u.ads), max: num(t.maxAdsPerDay), perAd: L.adBonus || {}, ...(L.ads || {}), showAds: tier !== 'vip' && L.ads?.adsEnabled === true,
    offers: (await getAdOffers()).filter(o => o.enabled !== false).sort((a, b) => (a.order || 0) - (b.order || 0)) } };
}
/** Atomically consume 1 unit of `feature`; returns null if OK, else the limit hit. 0 = unlimited. */
async function consume(uid, feature) {
  const L = await getLimits();
  const { tier } = await tierOf(uid);
  const base = num(L.tiers?.[tier]?.[feature]);
  if (base === 0) return null;
  const day = dayKey();
  const lim = base + num((await db.ref(`quotaBonus/${uid}/${day}/${feature}`).get()).val()) + num((await activeGrants(uid))[feature]);
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
app.get('/admin/limits', perm('config_edit'), w(async (_q, r) => { limitsCache.at = 0; r.json(withDefaults(await getLimits())); }));
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
    for (const grp of ['chat', 'comments']) {
      out[grp] = { ...SEED_LIMITS[grp], ...(out[grp] || {}) };
      for (const [k, v] of Object.entries(b[grp] || {})) {
        if (k === 'reason') { if (typeof v !== 'string' || v.length > 200) return r.status(400).json({ error: `bad ${grp}.reason` }); out[grp].reason = v; continue; }
        if (!Object.keys(SEED_LIMITS[grp]).includes(k) || typeof v !== 'boolean') return r.status(400).json({ error: `bad ${grp}.${k}` });
        out[grp][k] = v;
      }
    }
  } catch (e) { return r.status(e.status || 400).json({ error: e.message }); }
  out.updatedAt = Date.now(); out.updatedBy = q.uid;
  await db.ref('appConfig/limits').set(out);
  limitsCache = { at: Date.now(), v: out };
  r.json(withDefaults(out));
}));
app.put('/admin/post-block/:uid', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  if (q.body?.blocked === false) { await db.ref(`postBlocks/${uid}`).remove(); return r.json({ uid, blocked: false }); }
  await db.ref(`postBlocks/${uid}`).set({ reason: String(q.body?.reason || '').slice(0, 200) || 'บัญชีนี้ถูกระงับการโพสต์', by: q.uid, at: Date.now() });
  r.json({ uid, blocked: true });
}));
// Per-user restrictions: noPost (postBlocks) and noChat (chatBlocks, also enforced by RTDB rules on messages).
app.get('/admin/restrict/:uid', perm('manage_content'), w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  const [p, c] = await Promise.all([db.ref(`postBlocks/${uid}`).get(), db.ref(`chatBlocks/${uid}`).get()]);
  r.json({ uid, noPost: p.exists(), noChat: c.exists(), postReason: p.val()?.reason || '', chatReason: c.val()?.reason || '' });
}));
app.put('/admin/restrict/:uid', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, b = q.body || {}; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  const reason = String(b.reason || '').slice(0, 200), upd = {};
  if (typeof b.noPost === 'boolean') upd[`postBlocks/${uid}`] = b.noPost ? { reason: reason || 'บัญชีนี้ถูกระงับการโพสต์', by: q.uid, at: Date.now() } : null;
  if (typeof b.noChat === 'boolean') upd[`chatBlocks/${uid}`] = b.noChat ? { reason: reason || 'บัญชีนี้ถูกระงับการแชท', by: q.uid, at: Date.now() } : null;
  if (!Object.keys(upd).length) return r.status(400).json({ error: 'noPost or noChat (boolean) required' });
  await db.ref().update(upd);
  const [p, c] = await Promise.all([db.ref(`postBlocks/${uid}`).get(), db.ref(`chatBlocks/${uid}`).get()]);
  r.json({ uid, noPost: p.exists(), noChat: c.exists() });
}));
app.put('/admin/vip/:uid', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid; if (!UID_RE.test(uid)) return r.status(400).json({ error: 'bad uid' });
  const until = q.body?.until;
  if (until === null) { await db.ref(`vip/${uid}`).remove(); return r.json({ uid, vip: false }); }
  if (!Number.isInteger(until) || until < Date.now()) return r.status(400).json({ error: 'until must be a future ms timestamp or null' });
  await db.ref(`vip/${uid}`).set({ until, by: q.uid, at: Date.now() });
  r.json({ uid, vip: true, until });
}));
// Ad offers the user can pick from (admin-editable). AdMob picks the creative; an offer = format + est. duration label + reward.
const SEED_AD_OFFERS = [
  { id: 'short', title: 'โฆษณาสั้น', adFormat: 'rewarded_interstitial', estDurationLabel: '~5–15 วินาที', description: 'ใช้คุยกับบอท AI ทุกตัว', validityHours: 0, reward: { feature: 'aiBotMessagesPerDay', amount: 3 }, enabled: true, order: 1 },
  { id: 'standard', title: 'โฆษณามาตรฐาน', adFormat: 'rewarded', estDurationLabel: '~15–30 วินาที', description: 'ใช้คุยกับบอท AI ทุกตัว', validityHours: 0, reward: { feature: 'aiBotMessagesPerDay', amount: 10 }, enabled: true, order: 2 },
  { id: 'radar', title: 'บูสต์เรดาร์', adFormat: 'rewarded', estDurationLabel: '~15–30 วินาที', description: 'ใช้ค้นหาคนใกล้ ๆ ด้วยเรดาร์', validityHours: 24, reward: { feature: 'radarPerDay', amount: 5 }, enabled: true, order: 3 }
];
const AD_FORMATS = ['rewarded', 'rewarded_interstitial'];
let offersCache = { at: 0, v: null };
async function getAdOffers() {
  if (offersCache.v && Date.now() - offersCache.at < 30000) return offersCache.v;
  const ref = db.ref('appConfig/adOffers');
  let v = (await ref.get()).val();
  if (!v) { await ref.transaction(c => c || SEED_AD_OFFERS); v = (await ref.get()).val(); }
  v = (Array.isArray(v) ? v : Object.values(v || {})).filter(Boolean);
  offersCache = { at: Date.now(), v }; return v;
}
function validOffers(list) {
  if (!Array.isArray(list) || list.length > 20) throw new Error('offers must be an array of at most 20');
  const ids = new Set();
  return list.map((o, i) => {
    const e = m => { throw new Error(`offers[${i}]: ${m}`); };
    if (!o || typeof o !== 'object') e('object required');
    const id = String(o.id || ''); if (!/^[a-z0-9_-]{1,32}$/.test(id) || ids.has(id)) e('id must be unique a-z0-9_- (1..32)'); ids.add(id);
    const title = String(o.title || '').trim(); if (!title || title.length > 60) e('title 1..60 chars');
    if (!AD_FORMATS.includes(o.adFormat)) e('adFormat must be rewarded|rewarded_interstitial');
    const label = String(o.estDurationLabel || '').trim(); if (label.length > 40) e('estDurationLabel max 40 chars');
    const f = o.reward?.feature, amt = o.reward?.amount;
    if (!FEATURES.includes(f)) e('reward.feature unknown'); if (!Number.isInteger(amt) || amt < 1 || amt > 1000) e('reward.amount 1..1000');
    const description = String(o.description || '').trim(); if (description.length > 200) e('description max 200 chars');
    const vh = o.validityHours === undefined ? 0 : o.validityHours; if (!Number.isInteger(vh) || vh < 0 || vh > 72) e('validityHours 0..72 (0 = until midnight Bangkok)');
    return { id, title, adFormat: o.adFormat, estDurationLabel: label, description, validityHours: vh, reward: { feature: f, amount: amt }, enabled: o.enabled !== false, order: Number.isInteger(o.order) ? o.order : i };
  });
}
app.get('/admin/ad-offers', perm('config_edit'), w(async (_q, r) => { offersCache.at = 0; r.json({ offers: await getAdOffers() }); }));
app.put('/admin/ad-offers', perm('config_edit'), w(notBanned), w(async (q, r) => {
  let v; try { v = validOffers(q.body?.offers); } catch (e) { return r.status(400).json({ error: e.message }); }
  await db.ref('appConfig/adOffers').set(v.length ? v : null);
  offersCache = { at: Date.now(), v }; r.json({ offers: v });
}));
async function offerReward(offerId) {
  const o = (await getAdOffers()).find(x => x.id === offerId && x.enabled !== false);
  return o ? { feature: o.reward.feature, amount: o.reward.amount, offerId: o.id, validityHours: num(o.validityHours) } : null;
}
/** Credit one ad reward (shared by client path and SSV callback). `amount` overrides adBonus (offer rewards). */
async function creditAd(uid, feature, txId, amount = null, validityHours = 0) {
  const L = await getLimits();
  if (!(L.ads?.adsEnabled && L.ads?.rewardEnabled)) return { status: 403, body: { error: 'ad rewards disabled' } };
  if (!FEATURES.includes(feature)) return { status: 400, body: { error: 'bad feature' } };
  const { tier } = await tierOf(uid);
  const max = num(L.tiers?.[tier]?.maxAdsPerDay), per = amount !== null ? num(amount) : num(L.adBonus?.[feature]), day = dayKey();
  if (txId) { const t = await db.ref(`adTx/${txId}`).transaction(c => c ? c : { uid, at: Date.now() }); if (t.snapshot.val()?.done) return { status: 200, body: { duplicate: true } }; }
  const lastRef = db.ref(`adLast/${uid}`), minGap = num(L.global?.adMinIntervalSec) * 1000;
  let tooSoon = false;
  await lastRef.transaction(c => { tooSoon = !!c && Date.now() - c < minGap; return tooSoon ? c : Date.now(); });
  if (tooSoon) return { status: 429, body: { code: 'ad_rate', error: 'wait before next ad' } };
  let over = false;
  await db.ref(`usage/${uid}/${day}/ads`).transaction(c => { c = c || 0; over = max > 0 && c >= max; return over ? c : c + 1; });
  if (max === 0 || over) return { status: 429, body: { code: 'ad_cap', limit: max, error: 'daily ad limit reached' } };
  if (validityHours > 0) await db.ref(`quotaGrants/${uid}`).push({ feature, amount: per, until: Date.now() + validityHours * 3600000, at: Date.now() });
  else await db.ref(`quotaBonus/${uid}/${day}/${feature}`).transaction(c => (c || 0) + per);
  if (txId) await db.ref(`adTx/${txId}/done`).set(true);
  return { status: 200, body: { credited: per, feature, ...(await quotaInfo(uid, L)) } };
}
app.post('/quota/ad-reward', w(notBanned), w(async (q, r) => {
  const L = await getLimits();
  let feature = q.body?.feature || 'aiBotMessagesPerDay', amount = null, vh = 0;
  if (q.body?.offerId !== undefined) {
    const o = await offerReward(String(q.body.offerId)); if (!o) return r.status(404).json({ error: 'offer not found or disabled' });
    feature = o.feature; amount = o.amount; vh = o.validityHours;
  }
  if (L.ads?.ssvRequired) return r.status(202).json({ pending: true, note: 'credited by AdMob SSV callback' });
  const x = await creditAd(q.uid, feature, null, amount, vh); r.status(x.status).json(x.body);
}));
// AdMob SSV callback (public; trust comes from Google's ECDSA signature).
app.get('/admob/ssv', w(async (q, r) => {
  const raw = q.originalUrl.split('?')[1] || '';
  if (!raw) return r.status(200).send('ok'); // AdMob console "verify URL" ping
  const v = verifySsv(raw, await fetchKeys());
  if (!v.ok) return r.status(403).json({ error: v.reason });
  const cd = String(v.params.custom_data || ''); let feature = cd || 'aiBotMessagesPerDay', amount = null, vh = 0;
  if (cd.startsWith('offer:')) { const o = await offerReward(cd.slice(6)); if (!o) return r.status(200).json({ error: 'offer not found' }); feature = o.feature; amount = o.amount; vh = o.validityHours; }
  const uid = v.params.user_id, tx = String(v.params.transaction_id || '').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 100);
  if (!UID_RE.test(uid || '') || !tx) return r.status(400).json({ error: 'missing user_id/transaction_id' });
  const x = await creditAd(uid, feature, tx, amount, vh);
  r.status(x.status === 429 ? 200 : x.status).json(x.body); // 200 so AdMob does not retry a capped reward
}));
// Simple real user search (by display name prefix), quota-limited.
// Friend search: name prefix (searchPerDay) and/or nearby people from radar (near=1, radarPerDay). Nearby name matches ranked first.
app.get('/search/users', w(notBanned), w(async (q, r) => {
  const term = String(q.query.q || '').trim().slice(0, 50), near = q.query.near === '1';
  if (term.length < 1 && !near) return r.status(400).json({ error: 'q required' });
  let nearby = null, nearbyStatus = 'off';
  if (near) {
    const lim = await consume(q.uid, 'radarPerDay');
    if (lim !== null) { if (!term) return r.status(429).json({ code: 'quota', feature: 'radarPerDay', limit: lim, error: 'quota exceeded' }); nearbyStatus = 'quota'; }
    else { nearby = await nearbyFor(q.uid, pickRadius(q.query.radiusKm)); nearbyStatus = nearby ? 'ok' : 'no_location'; }
  }
  if (!term) return r.json({ users: [], nearby: (nearby || []).map(({ _km, ...x }) => x), nearbyStatus, radiusKm: pickRadius(q.query.radiusKm) });
  { const lim = await consume(q.uid, 'searchPerDay'); if (lim !== null) return r.status(429).json({ code: 'quota', feature: 'searchPerDay', limit: lim, error: 'quota exceeded' }); }
  const s = await db.ref('users').orderByChild('displayName').startAt(term).endAt(term + '\uf8ff').limitToFirst(20).get();
  const byName = []; s.forEach(c => { if (c.key !== q.uid && c.val()?.displayName) byName.push(publicProfile(c.key, c.val())); });
  const lc = term.toLowerCase(), nearMap = new Map((nearby || []).map(u => [u.uid, u]));
  const nearMatches = (nearby || []).filter(u => String(u.displayName || '').toLowerCase().includes(lc));
  const out = [...nearMatches.map(({ _km, ...x }) => ({ ...x, nearby: true }))];
  for (const u of byName) {
    if (out.some(x => x.uid === u.uid)) continue;
    if (await isBanned(u.uid) || await blockedEither(q.uid, u.uid)) continue;
    const n = nearMap.get(u.uid); out.push(n ? { ...u, distance: n.distance, nearby: true } : { ...u, nearby: false });
  }
  r.json({ users: out, nearbyStatus });
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
// Public list of enabled AI characters (for the AI hub); empty when bots are globally off.
app.get('/bots', w(async (_q, r) => {
  if (!(await botsEnabled())) return r.json({ enabled: false, bots: [] });
  const all = (await db.ref('aiBots').get()).val() || {};
  r.json({ enabled: true, bots: Object.entries(all).filter(([, b]) => b && b.enabled === true).map(([id, b]) => publicBot(id, b)) });
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
// ===== Remote UI config (AppUiConfig) — published by admins for all users; stored as a JSON string (RTDB-safe) =====
const RUI = () => db.ref('appConfig/remoteUi');
function validRemoteUi(cfg) {
  if (!cfg || typeof cfg !== 'object' || Array.isArray(cfg)) return 'config object required';
  const json = JSON.stringify(cfg);
  if (Buffer.byteLength(json) > 100 * 1024) return 'config too large (max 100KB)';
  const bgs = cfg.theme && cfg.theme.backgrounds;
  if (bgs && typeof bgs === 'object') for (const [k, b] of Object.entries(bgs)) {
    if (!/^(app|tab_[a-z_]{2,20}|sec_[A-Za-z0-9_-]{1,40})$/.test(k)) return 'bad background key ' + k;
    if (b && b.imageUrl && !/^https:\/\//.test(String(b.imageUrl))) return 'background imageUrl must be https';
  }
  return json;
}
app.get('/ui/config', w(async (_q, r) => {
  const c = (await RUI().child('current').get()).val();
  if (!c) return r.json({ config: null });
  r.json({ config: JSON.parse(c.configJson), versionId: c.versionId, label: c.label || '', publishedAt: c.publishedAt, notes: c.notes || '' });
}));
app.get('/admin/ui/config/versions', perm('config_edit'), w(async (_q, r) => {
  const v = (await RUI().get()).val() || {};
  const versions = Object.entries(v.versions || {}).map(([id, x]) => ({ versionId: id, label: x.label || '', savedAt: x.savedAt, savedBy: x.savedByName || x.savedBy, notes: x.notes || '', config: JSON.parse(x.configJson) }))
    .sort((a, b) => b.savedAt - a.savedAt);
  r.json({ currentVersionId: v.current?.versionId || null, versions });
}));
app.put('/admin/ui/config', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const json = validRemoteUi(q.body?.config); if (!json.startsWith('{')) return r.status(400).json({ error: json });
  const notes = String(q.body?.notes || '').slice(0, 300), label = String(q.body?.label || '').slice(0, 40), byName = String(q.body?.byName || '').slice(0, 80);
  const t = Date.now(), id = String(t);
  await RUI().update({ current: { configJson: json, versionId: id, label, notes, publishedAt: t, publishedBy: q.uid },
    [`versions/${id}`]: { configJson: json, label, notes, savedAt: t, savedBy: q.uid, savedByName: byName } });
  const vs = Object.keys((await RUI().child('versions').get()).val() || {}).sort();
  if (vs.length > 10) await Promise.all(vs.slice(0, vs.length - 10).map(k => RUI().child(`versions/${k}`).remove()));
  r.json({ published: id, label });
}));
app.post('/admin/ui/config/revert', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const vid = q.body?.versionId;
  if (typeof vid !== 'string' || !/^\d{1,20}$/.test(vid)) return r.status(400).json({ error: 'versionId required' });
  const v = (await RUI().child(`versions/${vid}`).get()).val();
  if (!v) return r.status(404).json({ error: 'version not found' });
  await RUI().child('current').set({ configJson: v.configJson, versionId: vid, label: v.label || '', notes: v.notes || '', publishedAt: Date.now(), publishedBy: q.uid, revertedFrom: vid });
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
  if (await blockedEither(q.uid, other)) return r.status(403).json({ code: 'blocked', error: 'ไม่สามารถแชทกับผู้ใช้นี้ได้' });
  { const cf = await chatFor(q.uid, (await tierOf(q.uid)).tier, await getLimits()); if (!cf.canChat) return r.status(403).json({ code: 'chat_disabled', error: cf.reason }); }
  r.json(await openDirectChat(q.uid, other));
}));
// Can I send in this chat right now? (app shows the reason and disables the input; RTDB rules enforce the same)
app.get('/chats/:chatId/status', w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  const L = await getLimits(), { tier } = await tierOf(q.uid);
  const closed = (await db.ref(`chats/${chatId}/closed`).get()).val();
  const cf = await chatFor(q.uid, tier, L), lim = num(L.tiers?.[tier]?.chatMessagesPerDay);
  const cc = (await db.ref(`chatCount/${q.uid}`).get()).val(), used = cc && cc.day === chatDay() ? num(cc.n) : 0;
  let canSend = true, reason = '';
  if (closed) { canSend = false; reason = closed.by === q.uid ? 'คุณปิดแชทนี้แล้ว' : 'อีกฝ่ายปิดแชทนี้แล้ว'; }
  else if (!cf.canChat) { canSend = false; reason = cf.reason; }
  else if (lim > 0 && used >= lim) { canSend = false; reason = `ส่งข้อความครบ ${lim} ข้อความของวันนี้แล้ว`; }
  r.json({ canSend, reason, closed: closed ? { byMe: closed.by === q.uid, at: closed.at } : null, chatLimit: lim, chatUsed: used, day: chatDay() });
}));
// 'Close my chat': a member closes (or the closer reopens) a chat; RTDB rules block new messages while closed.
app.post('/chats/:chatId/close', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  const ref = db.ref(`chats/${chatId}/closed`), cur = (await ref.get()).val();
  if (q.body?.closed === false) {
    if (cur && cur.by !== q.uid) return r.status(403).json({ error: 'only the member who closed can reopen' });
    await ref.remove(); return r.json({ chatId, closed: false });
  }
  if (!cur) await ref.set({ by: q.uid, at: Date.now() });
  r.json({ chatId, closed: true });
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
  upd[`authorPosts/${q.uid}/${ref.key}`] = post.createdAt;
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
  del[`authorPosts/${post.authorId}/${id}`] = null;
  await db.ref().update(del);
  for (const im of post.images || []) await admin.storage().bucket(STORAGE_BUCKET).file(im.path).delete().catch(() => {});
  r.json({ ok: true });
}));
app.patch('/posts/:id', w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!POST_ID_RE.test(id)) return r.status(400).json({ error: 'bad id' });
  const post = (await db.ref(`posts/${id}`).get()).val();
  if (!post || post.authorId !== q.uid) return r.status(404).json({ error: 'not found' });
  const upd = {};
  if (q.body?.visibility !== undefined) { if (!VIS.includes(q.body.visibility)) return r.status(400).json({ error: 'visibility must be public|friends|only_me' }); upd.visibility = q.body.visibility; }
  if (q.body?.commentsOff !== undefined) { if (typeof q.body.commentsOff !== 'boolean') return r.status(400).json({ error: 'commentsOff must be boolean' }); upd.commentsOff = q.body.commentsOff; }
  if (!Object.keys(upd).length) return r.status(400).json({ error: 'visibility or commentsOff required' });
  await db.ref(`posts/${id}`).update(upd);
  r.json({ id, visibility: upd.visibility ?? post.visibility ?? 'public', commentsOff: upd.commentsOff ?? !!post.commentsOff });
}));
app.get('/feed', w(async (q, r) => {
  const limit = Math.min(30, Math.max(1, parseInt(q.query.limit || '20', 10)));
  let qq = db.ref('posts').orderByKey();
  const cursor = String(q.query.cursor || '');
  if (cursor) { if (!POST_ID_RE.test(cursor)) return r.status(400).json({ error: 'bad cursor' }); qq = qq.endBefore(cursor); }
  const posts = [], rawKeys = [];
  const tag = q.query.tag ? normTag(String(q.query.tag)) : null;
  if (q.query.tag && !tag) return r.status(400).json({ error: 'bad tag' });
  const author = q.query.author ? String(q.query.author) : null;
  if (author && !UID_RE.test(author)) return r.status(400).json({ error: 'bad author' });
  if (tag || author) {
    let tq = db.ref(tag ? `hashtags/${tag}` : `authorPosts/${author}`).orderByKey();
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
  posts.forEach(p => { p.commentsOff = p.commentsOff === true; p.visibility = p.visibility || 'public'; if (p.authorId !== q.uid) delete p.place?.lat, delete p.place?.lng; });
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
  const vp = await visiblePost(q, r, id); if (!vp) return;
  { const C = { ...SEED_LIMITS.comments, ...((await getLimits()).comments || {}) };
    if (!C.enabled) return r.status(403).json({ code: 'comments_off', error: C.reason || 'ปิดการแสดงความคิดเห็นชั่วคราว' });
    if ((await db.ref(`posts/${id}/commentsOff`).get()).val() === true) return r.status(403).json({ code: 'comments_off', error: 'เจ้าของโพสต์ปิดความคิดเห็น' }); }
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
  const [profiles, st] = await Promise.all([Promise.all(ids.map(summary)), Promise.all(ids.map(id => db.ref(`status/${id}`).get()))]);
  r.json({ friends: profiles.map((p, i) => p && { ...p, online: (s => !!s && s.online === true && s.hidden !== true)(st[i].val()) }).filter(Boolean) });
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
/** Nearby visible users around uid (coarse distance only). Returns null if uid has no shared location. */
async function nearbyFor(uid, radius) {
  const me = (await db.ref(`radar/${uid}`).get()).val();
  if (!me) return null;
  const prec = radius <= 2 ? 5 : radius <= 20 ? 4 : 3; // geohash5 cell ~4.9x4.9 km
  const cellDeg = { 5: 0.044, 4: 0.18, 3: 1.4 }[prec];
  const step = Math.max(cellDeg, radius / 111);
  const prefixes = new Set();
  for (const dy of [-1, 0, 1]) for (const dx of [-1, 0, 1]) {
    const la = Math.max(-89.9, Math.min(89.9, me.lat + dy * step)), lo = ((me.lng + dx * step / Math.max(0.2, Math.cos(me.lat * Math.PI / 180)) + 540) % 360) - 180;
    prefixes.add(geohash(la, lo, prec));
  }
  const cutoff = Date.now() - 30 * 60 * 1000, seen = new Map();
  await Promise.all([...prefixes].map(async p => {
    const s = await db.ref('radar').orderByChild('gh').startAt(p).endAt(p + '\uf8ff').limitToFirst(500).get();
    s.forEach(c => { seen.set(c.key, c.val()); });
  }));
  const out = [];
  for (const [id, v] of seen) {
    if (id === uid || !v || v.at < cutoff) continue;
    const km = distKm(me.lat, me.lng, v.lat, v.lng);
    if (km > radius) continue;
    if (await isBanned(id) || await blockedEither(uid, id)) continue;
    const p = await summary(id); if (!p) continue;
    out.push({ ...p, distance: roundDist(km), _km: km });
  }
  out.sort((a, b) => a._km - b._km);
  return out.slice(0, 50);
}
app.get('/radar/nearby', w(notBanned), w(quota('radarPerDay')), w(async (q, r) => {
  const radius = pickRadius(q.query.radiusKm);
  const list = await nearbyFor(q.uid, radius);
  if (!list) return r.status(409).json({ error: 'location not shared' });
  r.json({ radiusKm: radius, users: list.map(({ _km, ...x }) => x) });
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
// ===== Gift catalog (live / vip / chat) + stickers; admin-managed, images in Storage giftAssets/{id}.webp =====
const CHANNELS = ['live', 'vip', 'chat', 'stickers'];
const ITEM_ID = /^[A-Za-z0-9_-]{1,40}$/;
const SEED_LIVE = [
  ['gift_heart', 'หัวใจมินิ', '💖', 10, 'POP'], ['gift_flower', 'ดอกกุหลาบ', '🌹', 50, 'FLOAT'], ['gift_tea', 'ชานมไข่มุก', '🧋', 100, 'POP'],
  ['gift_crown', 'มงกุฎทอง', '👑', 500, 'ROYAL'], ['gift_rocket', 'จรวดซูเปอร์สตาร์', '🚀', 1000, 'BLAST']];
(async () => { try { await db.ref('catalog/live').transaction(c => c || Object.fromEntries(SEED_LIVE.map(([id, name, icon, price, fx], i) => [id, { name, icon, price, animationEffect: fx, category: 'live', enabled: true, order: i, imagePath: '' }]))); } catch (e) { console.error('seed live', e.message); } })();
const GIFT_CFG_SEED = { enabled: true, autoFriend: true, adminPercent: 20, recipientPercent: 80 };
async function giftCfg() { return { ...GIFT_CFG_SEED, ...((await db.ref('appConfig/gifts').get()).val() || {}) }; }
const assetUrl = id => `https://friendtalk-brain-123224091480.asia-southeast1.run.app/gift-assets/${id}`;
app.get('/gift-assets/:id', w(async (q, r) => {
  if (!ITEM_ID.test(q.params.id)) return r.status(400).end();
  const file = admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${q.params.id}.webp`);
  const [meta] = await file.getMetadata().catch(() => [null]);
  if (!meta) return r.status(404).end();
  r.set('Content-Type', meta.contentType || 'image/webp'); r.set('Cache-Control', 'public, max-age=86400'); r.set('ETag', meta.etag || meta.generation);
  file.createReadStream().on('error', () => r.end()).pipe(r);
}));
const catPerm = perm('manage_content');
app.get('/admin/catalog/:ch', catPerm, w(async (q, r) => {
  if (!CHANNELS.includes(q.params.ch)) return r.status(400).json({ error: 'bad channel' });
  const v = (await db.ref(`catalog/${q.params.ch}`).get()).val() || {};
  const all = Object.entries(v).map(([id, x]) => ({ id, ...x })).sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  const page = Math.max(0, parseInt(q.query.page || '0', 10)), size = Math.min(100, Math.max(1, parseInt(q.query.size || '50', 10)));
  r.json({ items: all.slice(page * size, page * size + size), total: all.length, page, size });
}));
// Item fields (image uploaded separately as raw image/webp ≤512KB)
app.put('/admin/catalog/:ch/:id', catPerm, w(notBanned), w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  const b = q.body || {}, name = String(b.name || '').trim().slice(0, 40);
  const price = Number.isInteger(b.price) ? b.price : -1;
  if (!name) return r.status(400).json({ error: 'name required' });
  if (price < 0 || price > 1000000 || (price === 0 && ch !== 'stickers')) return r.status(400).json({ error: ch === 'stickers' ? 'price 0..1000000' : 'price 1..1000000' });
  const ref = db.ref(`catalog/${ch}/${id}`), cur = (await ref.get()).val() || {};
  let order = Number.isInteger(b.order) ? b.order : cur.order;
  if (!Number.isInteger(order)) order = Object.keys((await db.ref(`catalog/${ch}`).get()).val() || {}).length;
  const item = { name, price, category: String(b.category || cur.category || ch).slice(0, 30), enabled: b.enabled !== false, order,
    icon: String(b.icon ?? cur.icon ?? '').slice(0, 8), animationEffect: String(b.animationEffect ?? cur.animationEffect ?? 'POP').slice(0, 12), imagePath: cur.imagePath || '', imageUrl: cur.imageUrl || '', updatedAt: Date.now(), updatedBy: q.uid };
  await ref.set(item); r.json({ id, ...item });
}));
app.put('/admin/catalog/:ch/:id/image', catPerm, express.raw({ type: 'image/*', limit: '600kb' }), w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  const ref = db.ref(`catalog/${ch}/${id}`); if (!(await ref.get()).exists()) return r.status(404).json({ error: 'save item first' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image/webp body required' });
  if (buf.length > 512 * 1024) return r.status(413).json({ error: 'image too large (max 512KB)' });
  const isWebp = buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP', isPng = buf[0] === 0x89 && buf[1] === 0x50;
  if (!isWebp && !isPng) return r.status(400).json({ error: 'webp or png only' });
  const fileId = `${ch}_${id}`;
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${fileId}.webp`).save(buf, { contentType: isWebp ? 'image/webp' : 'image/png', resumable: false });
  const url = `${assetUrl(fileId)}?v=${Date.now()}`;
  await ref.update({ imagePath: `giftAssets/${fileId}.webp`, imageUrl: url });
  r.json({ imageUrl: url, bytes: buf.length });
}));
app.delete('/admin/catalog/:ch/:id', catPerm, w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  await db.ref(`catalog/${ch}/${id}`).remove();
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${ch}_${id}.webp`).delete().catch(() => {});
  r.json({ deleted: id });
}));
app.post('/admin/catalog/:ch/order', catPerm, w(async (q, r) => {
  const { ch } = q.params, ids = q.body?.ids; if (!CHANNELS.includes(ch) || !Array.isArray(ids) || ids.length > 5000 || !ids.every(x => typeof x === 'string' && ITEM_ID.test(x))) return r.status(400).json({ error: 'ids[] required' });
  const have = (await db.ref(`catalog/${ch}`).get()).val() || {}, upd = {};
  ids.forEach((id, i) => { if (have[id]) upd[`catalog/${ch}/${id}/order`] = i; });
  await db.ref().update(upd); r.json({ ordered: Object.keys(upd).length });
}));
app.get('/admin/gifts/config', perm('config_edit'), w(async (_q, r) => r.json(await giftCfg())));
app.put('/admin/gifts/config', perm('config_edit'), w(async (q, r) => {
  const b = q.body || {}, c = await giftCfg();
  for (const k of ['enabled', 'autoFriend']) if (typeof b[k] === 'boolean') c[k] = b[k];
  if (b.adminPercent !== undefined || b.recipientPercent !== undefined) {
    const a = b.adminPercent, p = b.recipientPercent;
    if (!Number.isInteger(a) || !Number.isInteger(p) || a < 0 || p < 0 || a + p !== 100) return r.status(400).json({ error: 'adminPercent + recipientPercent must be integers totalling 100' });
    c.adminPercent = a; c.recipientPercent = p;
  }
  await db.ref('appConfig/gifts').set(c); r.json(c);
}));
app.get('/admin/gifts/tx', perm('coins_confirm'), w(async (q, r) => {
  let qq = db.ref('giftTx').orderByKey(); if (q.query.cursor) qq = qq.endBefore(String(q.query.cursor));
  const s = await qq.limitToLast(50).get(), out = []; s.forEach(c => { out.push({ id: c.key, ...c.val() }); });
  out.reverse(); r.json({ tx: out, nextCursor: out.length === 50 ? out[out.length - 1].id : null });
}));
// ===== Chat gifts: no deduction at send; recipient accepts -> atomic split (admin/recipient) + auto-friend =====
async function chatOther(chatId, uid) {
  const m = (await db.ref(`chatMembers/${chatId}`).get()).val() || {};
  if (!m[uid]) return null; return Object.keys(m).find(k => k !== uid) || null;
}
async function giftGuard(chatId, from, to) {
  if ((await db.ref(`chats/${chatId}/closed`).get()).exists()) return 'แชทนี้ถูกปิดอยู่';
  if (await blockedEither(from, to)) return 'ส่งของขวัญให้ผู้ใช้นี้ไม่ได้';
  if (await isBanned(to)) return 'ผู้รับถูกระงับบัญชี';
  const [a, b] = await Promise.all([db.ref(`chatBlocks/${from}`).get(), db.ref(`chatBlocks/${to}`).get()]);
  if (a.exists()) return 'บัญชีของคุณถูกระงับการแชท'; if (b.exists()) return 'ผู้รับถูกระงับการแชท';
  return null;
}
const pendingSum = async uid => Object.values((await db.ref(`giftPending/${uid}`).get()).val() || {}).reduce((s, x) => s + num(x), 0);
async function notifyGift(uids, payload) { const t = Date.now(), u = {}; for (const id of uids) u[`giftNotifications/${id}/${t}_${Math.random().toString(36).slice(2, 7)}`] = { ...payload, at: t }; await db.ref().update(u); }
app.post('/chats/:chatId/gift', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params, giftId = q.body?.giftId;
  if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId) || typeof giftId !== 'string' || !ITEM_ID.test(giftId)) return r.status(400).json({ error: 'chatId/giftId required' });
  const to = await chatOther(chatId, q.uid); if (!to) return r.status(403).json({ error: 'not a member' });
  const cfg = await giftCfg(); if (!cfg.enabled) return r.status(403).json({ code: 'gifts_disabled', error: 'ปิดระบบของขวัญชั่วคราว' });
  const g = await giftGuard(chatId, q.uid, to); if (g) return r.status(403).json({ code: 'blocked', error: g });
  const item = (await db.ref(`catalog/chat/${giftId}`).get()).val();
  if (!item || item.enabled === false || !(item.price > 0)) return r.status(404).json({ error: 'ไม่พบของขวัญนี้' });
  const dia = num((await db.ref(`wallets/${q.uid}/diamonds`).get()).val()), pend = await pendingSum(q.uid);
  if (dia < item.price + pend) return r.status(402).json({ code: 'insufficient', error: `เพชรไม่พอ (มี 💎${dia}, รอรับอยู่ ${pend}, ต้องการ ${item.price})`, diamonds: dia, pending: pend, price: item.price });
  const t = Date.now(), txId = db.ref('giftTx').push().key;
  const gift = { txId, giftId, name: item.name, price: item.price, imageUrl: item.imageUrl || '', icon: item.icon || '', status: 'pending' };
  await db.ref().update({
    [`giftTx/${txId}`]: { from: q.uid, to, chatId, giftId, name: item.name, price: item.price, imageUrl: item.imageUrl || '', status: 'pending', createdAt: t },
    [`giftPending/${q.uid}/${txId}`]: item.price,
    [`messages/${chatId}/${txId}`]: { senderUid: q.uid, at: t, text: `🎁 ${item.name}`, gift },
    [`chats/${chatId}/lastMessage`]: `🎁 ส่งของขวัญ ${item.name}`, [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: q.uid
  });
  r.json({ txId, status: 'pending', price: item.price, currency: 'diamonds' });
}));
app.post('/gifts/:txId/accept', w(notBanned), w(async (q, r) => {
  const { txId } = q.params; if (!/^[A-Za-z0-9_-]{1,40}$/.test(txId)) return r.status(400).json({ error: 'bad id' });
  const txRef = db.ref(`giftTx/${txId}`), tx = (await txRef.get()).val();
  if (!tx) return r.status(404).json({ error: 'not found' });
  if (tx.to !== q.uid) return r.status(403).json({ error: 'only the recipient can accept' });
  if (tx.status === 'accepted') return r.json({ status: 'accepted', already: true, ...tx.split });
  const g = await giftGuard(tx.chatId, tx.from, tx.to); if (g) return r.status(403).json({ code: 'blocked', error: g });
  // lock: pending -> accepting (one concurrent accept only; idempotent)
  const lock = await txRef.child('status').transaction(s => (s === null ? 'pending' : s === 'pending' ? 'accepting' : undefined)); // null = cold cache guess, retried by server
  if (lock.committed && lock.snapshot.val() !== 'accepting') return r.status(409).json({ error: 'busy, retry' });
  if (!lock.committed) { const s = (await txRef.child('status').get()).val(); return s === 'accepted' ? r.json({ status: 'accepted', already: true }) : r.status(409).json({ error: 'busy, retry' }); }
  const price = num(tx.price);
  const d = await db.ref(`wallets/${tx.from}/diamonds`).transaction(c => (c === null ? null : num(c) >= price ? num(c) - price : undefined));
  if (!d.committed || d.snapshot.val() === null) {
    await txRef.update({ status: 'pending', lastFailAt: Date.now(), lastFail: 'insufficient' });
    await notifyGift([tx.from, tx.to], { type: 'gift_failed', txId, chatId: tx.chatId, name: tx.name, price, msg: 'รับของขวัญไม่สำเร็จ: เพชรของผู้ส่งไม่พอ' });
    return r.status(402).json({ code: 'insufficient', error: 'เพชรของผู้ส่งไม่พอ รับของขวัญไม่สำเร็จ' });
  }
  const cfg = await giftCfg(), recipientShare = Math.floor(price * cfg.recipientPercent / 100), adminShare = price - recipientShare;
  await Promise.all([db.ref(`wallets/${tx.to}/diamonds`).transaction(c => num(c) + recipientShare), db.ref('wallets/_system/diamonds').transaction(c => num(c) + adminShare)]);
  const t = Date.now(), split = { recipientShare, adminShare, adminPercent: cfg.adminPercent, recipientPercent: cfg.recipientPercent, senderBalanceAfter: d.snapshot.val() };
  const upd = { [`giftTx/${txId}/status`]: 'accepted', [`giftTx/${txId}/acceptedAt`]: t, [`giftTx/${txId}/split`]: split,
    [`giftPending/${tx.from}/${txId}`]: null, [`messages/${tx.chatId}/${txId}/gift/status`]: 'accepted' };
  let friended = false;
  if (cfg.autoFriend && !(await db.ref(`friends/${tx.from}/${tx.to}`).get()).exists()) {
    upd[`friends/${tx.from}/${tx.to}`] = t; upd[`friends/${tx.to}/${tx.from}`] = t; upd[`friendRequests/${tx.to}/${tx.from}`] = null; upd[`friendRequests/${tx.from}/${tx.to}`] = null; friended = true;
  }
  upd[`giftTx/${txId}/friended`] = friended;
  await db.ref().update(upd);
  r.json({ status: 'accepted', ...split, friended });
}));
app.get('/gifts/wallet', w(async (q, r) => {
  const [c, dm, p] = await Promise.all([db.ref(`wallets/${q.uid}/coins`).get(), db.ref(`wallets/${q.uid}/diamonds`).get(), pendingSum(q.uid)]);
  // Gifts are priced/paid in diamonds; pending = diamonds reserved by unaccepted gifts.
  r.json({ coins: num(c.val()), diamonds: num(dm.val()), pending: p, available: num(dm.val()) - p, currency: 'diamonds' });
}));
// ===== VIP room decor for 1:1 chats =====
const THEMES = ['royal_gold', 'diamond_luxury', 'galaxy', 'romantic', 'neon_vip', 'luxury_palace'];
const FRAMES = ['none', 'diamond', 'gold', 'sparkle'], EFFECTS = ['none', 'sparkle', 'hearts', 'stars', 'snow', 'bubbles'], PATTERNS = ['none', 'dots', 'stripes', 'hearts', 'stars', 'diamonds'];
const BUBBLES = ['default', 'rounded', 'glass', 'gold', 'neon'];
const DHEX = /^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/;
function lum(hex) { const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
const contrast = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
function validDecor(chatId, d) {
  if (!d || typeof d !== 'object') return 'decor required';
  if (!THEMES.includes(d.theme)) return 'theme invalid';
  const bg = d.bg || {}, out = { theme: d.theme, bg: { color: DHEX.test(bg.color || '') ? bg.color : '', pattern: PATTERNS.includes(bg.pattern) ? bg.pattern : 'none', imagePath: '' },
    frame: FRAMES.includes(d.frame) ? d.frame : 'none', effect: EFFECTS.includes(d.effect) ? d.effect : 'none',
    bubble: { style: BUBBLES.includes(d.bubble?.style) ? d.bubble.style : 'default', color: DHEX.test(d.bubble?.color || '') ? d.bubble.color : '#7C3AED', textColor: DHEX.test(d.bubble?.textColor || '') ? d.bubble.textColor : '#FFFFFF' }, items: [] };
  if (bg.imagePath) { if (typeof bg.imagePath !== 'string' || !bg.imagePath.startsWith(`roomDecor/${chatId}/`) || bg.imagePath.includes('..')) return 'bg.imagePath invalid'; out.bg.imagePath = bg.imagePath; out.bg.animated = bg.animated === true; }
  if (contrast(out.bubble.color.slice(0, 7), out.bubble.textColor.slice(0, 7)) < 4.5) return 'สีตัวอักษรกับสีบับเบิลตัดกันไม่พอ (contrast < 4.5)';
  const items = Array.isArray(d.items) ? d.items : []; if (items.length > 30) return 'max 30 items';
  for (const it of items) {
    const ok = it && typeof it.stickerId === 'string' && ITEM_ID.test(it.stickerId) && [it.x, it.y, it.scale, it.rotation].every(v => typeof v === 'number' && isFinite(v));
    if (!ok || it.x < 0 || it.x > 1 || it.y < 0 || it.y > 1 || it.scale < 0.2 || it.scale > 4 || Math.abs(it.rotation) > 360) return 'item invalid (stickerId, x/y 0..1, scale 0.2..4, rotation ±360)';
    out.items.push({ stickerId: it.stickerId, x: it.x, y: it.y, scale: it.scale, rotation: it.rotation });
  }
  return out;
}
const isVipNow = async uid => (await tierOf(uid)).tier === 'vip';
async function decorView(chatId) {
  const d = (await db.ref(`roomDecor/${chatId}`).get()).val(); if (!d) return { decor: null };
  if (!(await isVipNow(d.ownerUid))) return { decor: null, hidden: true, reason: 'VIP ของเจ้าของห้องหมดอายุ (เก็บการตกแต่งไว้แล้ว)', ownerUid: d.ownerUid };
  return { decor: d.decor, ownerUid: d.ownerUid, updatedAt: d.updatedAt, rev: d.rev };
}
app.get('/rooms/:chatId/decor', w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await chatOther(chatId, q.uid))) return r.status(403).json({ error: 'not a member' });
  const v = await decorView(chatId); r.json({ ...v, canEdit: (await isVipNow(q.uid)) && (!v.ownerUid || v.ownerUid === q.uid) });
}));
app.post('/rooms/decor/previews', w(async (q, r) => {
  const ids = (Array.isArray(q.body?.chatIds) ? q.body.chatIds : []).filter(x => typeof x === 'string' && /^[A-Za-z0-9_-]{1,300}$/.test(x)).slice(0, 100), out = {};
  await Promise.all(ids.map(async id => {
    if (!(await db.ref(`chatMembers/${id}/${q.uid}`).get()).exists()) return;
    const v = await decorView(id); if (v.decor) out[id] = { theme: v.decor.theme, frame: v.decor.frame, bgColor: v.decor.bg.color, bubbleColor: v.decor.bubble.color };
  }));
  r.json({ previews: out });
}));
app.put('/rooms/:chatId/decor', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await chatOther(chatId, q.uid))) return r.status(403).json({ error: 'not a member' });
  if (!(await isVipNow(q.uid))) return r.status(403).json({ code: 'vip_required', error: 'เฉพาะสมาชิก VIP' });
  const cur = (await db.ref(`roomDecor/${chatId}`).get()).val();
  if (cur && cur.ownerUid !== q.uid) return r.status(403).json({ code: 'not_owner', error: 'ห้องนี้ถูกตกแต่งโดยอีกฝ่าย ต้องให้เจ้าของรีเซ็ตก่อน' });
  const d = validDecor(chatId, q.body?.decor); if (typeof d === 'string') return r.status(400).json({ error: d });
  if (d.items.length) { const st = (await db.ref('catalog/stickers').get()).val() || {}; if (d.items.some(i => !st[i.stickerId] || st[i.stickerId].enabled === false)) return r.status(400).json({ error: 'unknown sticker' }); }
  const t = Date.now(); await db.ref(`roomDecor/${chatId}`).set({ ownerUid: q.uid, decor: d, updatedAt: t, rev: t });
  r.json({ saved: true, rev: t });
}));
app.delete('/rooms/:chatId/decor', w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  const cur = (await db.ref(`roomDecor/${chatId}`).get()).val(); if (!cur) return r.json({ reset: true });
  if (cur.ownerUid !== q.uid && !can(await loadRole(q.uid), 'manage_content')) return r.status(403).json({ error: 'only the owner can reset' });
  await db.ref(`roomDecor/${chatId}`).remove(); r.json({ reset: true });
}));
app.post('/rooms/:chatId/decor/image', w(notBanned), express.raw({ type: 'image/*', limit: '5mb' }), w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await chatOther(chatId, q.uid))) return r.status(403).json({ error: 'not a member' });
  if (!(await isVipNow(q.uid))) return r.status(403).json({ code: 'vip_required', error: 'เฉพาะสมาชิก VIP' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image body required' });
  const h = buf.slice(0, 12), isGif = h.slice(0, 3).toString() === 'GIF', isPng = h[0] === 0x89 && h[1] === 0x50, isJpg = h[0] === 0xFF && h[1] === 0xD8, isWebp = h.slice(0, 4).toString() === 'RIFF' && h.slice(8, 12).toString() === 'WEBP';
  const animWebp = isWebp && buf.includes(Buffer.from('ANIM'));
  if (!(isGif || isPng || isJpg || isWebp)) return r.status(400).json({ error: 'jpeg/png/webp/gif only' });
  const animated = isGif || animWebp; if (buf.length > (animated ? 3 : 5) * 1024 * 1024) return r.status(413).json({ error: animated ? 'GIF/ภาพเคลื่อนไหว ไม่เกิน 3MB' : 'รูปไม่เกิน 5MB' });
  const ext = isGif ? 'gif' : isPng ? 'png' : isJpg ? 'jpg' : 'webp', path = `roomDecor/${chatId}/${q.uid}/${Date.now()}.${ext}`;
  await admin.storage().bucket(STORAGE_BUCKET).file(path).save(buf, { contentType: `image/${ext === 'jpg' ? 'jpeg' : ext}`, resumable: false });
  r.json({ imagePath: path, animated });
}));
app.get('/rooms/:chatId/decor/image', w(async (q, r) => {
  const { chatId } = q.params, path = String(q.query.path || '');
  if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId) || !path.startsWith(`roomDecor/${chatId}/`) || path.includes('..')) return r.status(400).json({ error: 'bad path' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  const file = admin.storage().bucket(STORAGE_BUCKET).file(path); const [meta] = await file.getMetadata().catch(() => [null]);
  if (!meta) return r.status(404).json({ error: 'not found' });
  r.set('Content-Type', meta.contentType); r.set('Cache-Control', 'private, max-age=86400'); r.set('ETag', meta.etag || meta.generation);
  file.createReadStream().on('error', () => r.end()).pipe(r);
}));
app.post('/rooms/:chatId/decor/report', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  if (!(await chatOther(chatId, q.uid))) return r.status(403).json({ error: 'not a member' });
  const d = (await db.ref(`roomDecor/${chatId}`).get()).val(); if (!d) return r.status(404).json({ error: 'no decor' });
  const id = db.ref('decorReports').push().key;
  await db.ref(`decorReports/${id}`).set({ chatId, by: q.uid, ownerUid: d.ownerUid, imagePath: d.decor?.bg?.imagePath || '', reason: String(q.body?.reason || '').slice(0, 300), at: Date.now(), status: 'open' });
  r.json({ reported: id });
}));
app.get('/admin/decor/reports', perm('manage_content'), w(async (_q, r) => {
  const v = (await db.ref('decorReports').orderByKey().limitToLast(100).get()).val() || {};
  r.json({ reports: Object.entries(v).map(([id, x]) => ({ id, ...x })).reverse() });
}));
// Admin removes an inappropriate background (keeps the rest of the decor) and closes the report.
app.post('/admin/decor/:chatId/remove-bg', perm('manage_content'), w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad id' });
  const ref = db.ref(`roomDecor/${chatId}`), d = (await ref.get()).val(); if (!d) return r.status(404).json({ error: 'no decor' });
  const p = d.decor?.bg?.imagePath; if (p) await admin.storage().bucket(STORAGE_BUCKET).file(p).delete().catch(() => {});
  await ref.update({ 'decor/bg/imagePath': '', 'decor/bg/animated': false, rev: Date.now(), removedBgBy: q.uid });
  if (q.body?.reportId && ITEM_ID.test(q.body.reportId)) await db.ref(`decorReports/${q.body.reportId}`).update({ status: 'resolved', by: q.uid });
  r.json({ removed: !!p });
}));

// ===== Admin console (real data; every route server-gated by role/permission) =====
const bkkMidnight = () => { const t = Date.now() + 7 * 3600e3; return t - (t % 86400e3) - 7 * 3600e3; };
const roleOf = v => (typeof v === 'string' ? v : (v?.label === 'moderator' ? 'moderator' : v?.role)) || 'user';
app.get('/admin/overview', w(async (q, r) => {
  const [users, roles, bans, posts, kyc, ctx, reps, ledger] = await Promise.all(['users', 'roles', 'bans', 'posts', 'kyc', 'coinTransactions', 'reports', 'adminCoinLedger'].map(p => db.ref(p).get().then(s => s.val() || {})));
  const day = bkkMidnight(), now24 = Date.now() - 86400e3;
  let pendingCoins = 0; for (const u of Object.values(ctx)) for (const t of Object.values(u || {})) if (t?.status === 'pending') pendingCoins++;
  const confirmed = Object.values(ctx).flatMap(u => Object.values(u || {})).filter(t => t?.status === 'paid').reduce((a, t) => a + (Number(t.coins) || 0), 0);
  const granted = Object.values(ledger).reduce((a, x) => a + (Number(x.amount) || 0), 0);
  r.json({
    users: Object.keys(users).length,
    active24h: Object.values(users).filter(u => Number(u?.lastActive) >= now24).length,
    newToday: Object.values(users).filter(u => Number(u?.createdAt) >= day).length,
    postsToday: Object.values(posts).filter(p => Number(p?.createdAt) >= day).length,
    postsTotal: Object.keys(posts).length,
    banned: Object.values(bans).filter(b => b && (!b.until || b.until > Date.now())).length,
    admins: Object.values(roles).filter(v => ['admin', 'superadmin', 'moderator'].includes(roleOf(v))).length,
    coinsIssued: confirmed + granted, coinsPurchased: confirmed, coinsGranted: granted,
    pendingCoinRequests: pendingCoins, pendingAgeRequests: Object.values(kyc).filter(k => k?.status === 'pending').length,
    openReports: Object.values(reps).filter(x => (x?.status || 'open') === 'open').length,
    yourRole: q.role.role, yourPermissions: q.role.permissions,
  });
}));
app.get('/admin/users', w(async (q, r) => {
  const term = String(q.query.q || '').trim().toLowerCase(), roleF = String(q.query.role || 'all').toLowerCase();
  const [users, roles, bans] = await Promise.all(['users', 'roles', 'bans'].map(p => db.ref(p).get().then(s => s.val() || {})));
  let list = Object.entries(users).map(([uid, u]) => {
    const b = bans[uid]; const banned = !!(b && (!b.until || b.until > Date.now()));
    return { uid, displayName: u?.displayName || '', username: u?.username || '', avatar: u?.avatar || '', lastActive: Number(u?.lastActive) || 0, createdAt: Number(u?.createdAt) || 0, role: roleOf(roles[uid]), banned };
  });
  const counts = { all: list.length, banned: list.filter(u => u.banned).length }; for (const u of list) counts[u.role] = (counts[u.role] || 0) + 1;
  if (term) list = list.filter(u => [u.uid, u.displayName, u.username].some(x => x.toLowerCase().includes(term)));
  if (roleF === 'banned') list = list.filter(u => u.banned);
  else if (roleF !== 'all') list = list.filter(u => u.role === roleF);
  list.sort((a, b) => b.lastActive - a.lastActive);
  const off = Math.max(0, parseInt(q.query.offset) || 0);
  r.json({ total: list.length, counts, users: list.slice(off, off + 50), next: off + 50 < list.length ? off + 50 : null });
}));
app.get('/admin/users/:uid', w(async (q, r) => {
  const uid = q.params.uid;
  const [u, role, ban, pb, cb, wal, reps, vip] = await Promise.all([`users/${uid}`, `roles/${uid}`, `bans/${uid}`, `postBlocks/${uid}`, `chatBlocks/${uid}`, `wallets/${uid}`, 'reports', `vip/${uid}`].map(p => db.ref(p).get().then(s => s.val())));
  if (!u) return r.status(404).json({ error: 'user not found' });
  const au = await admin.auth().getUser(uid).catch(() => null);
  const against = Object.entries(reps || {}).filter(([, x]) => x?.targetUid === uid || x?.targetId === uid).map(([id, x]) => ({ id, ...x })).slice(-50).reverse();
  r.json({ uid, profile: { displayName: u.displayName || '', username: u.username || '', avatar: u.avatar || '', bio: u.bio || '', createdAt: u.createdAt || 0, lastActive: u.lastActive || 0 },
    email: (can(q.role, 'ban_users') || q.role.role === 'superadmin') ? (au?.email || '') : '', emailVerified: !!au?.emailVerified,
    role: roleOf(role), permissions: (typeof role === 'object' && role?.permissions) || {},
    ban: ban && (!ban.until || ban.until > Date.now()) ? ban : null, noPost: pb || null, noChat: cb || null,
    coins: Number(wal?.coins) || 0, vip: vip || null, reports: against });
}));
app.post('/admin/users/:uid/ban', perm('ban_users'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, b = q.body || {};
  if (uid === q.uid) return r.status(400).json({ error: 'cannot ban yourself' });
  const tr = await loadRole(uid);
  if (tr && ['admin', 'superadmin'].includes(tr.role) && q.role.role !== 'superadmin') return r.status(403).json({ error: 'only superadmin can ban admins' });
  if (tr?.role === 'superadmin') return r.status(403).json({ error: 'cannot ban a superadmin' });
  if (b.banned === false) { await db.ref(`bans/${uid}`).remove(); await db.ref('adminLog').push({ action: 'unban', uid, by: q.uid, at: Date.now() }); return r.json({ uid, banned: false }); }
  const days = Math.max(0, Math.min(3650, Number(b.days) || 0));
  const entry = { reason: String(b.reason || '').slice(0, 200) || 'ละเมิดกฎการใช้งาน', by: q.uid, at: Date.now(), until: days ? Date.now() + days * 86400e3 : null };
  await db.ref(`bans/${uid}`).set(entry);
  await admin.auth().revokeRefreshTokens(uid).catch(() => {});
  await db.ref('adminLog').push({ action: 'ban', uid, days, by: q.uid, at: Date.now() });
  r.json({ uid, banned: true, ...entry });
}));
app.post('/admin/users/:uid/coins', perm('coins_confirm'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, amount = Math.trunc(Number(q.body?.amount)), reason = String(q.body?.reason || '').trim().slice(0, 200);
  if (!Number.isFinite(amount) || amount === 0 || Math.abs(amount) > 100000) return r.status(400).json({ error: 'amount must be a non-zero integer within ±100000' });
  if (reason.length < 3) return r.status(400).json({ error: 'reason required' });
  if (amount < 0 && (await loadRole(q.uid))?.role !== 'superadmin') return r.status(403).json({ error: 'only superadmin can decrease balances' });
  if (!(await db.ref(`users/${uid}`).get()).exists()) return r.status(404).json({ error: 'user not found' });
  let insufficient = false; const cur = q.body?.currency === 'diamonds' ? 'diamonds' : 'coins';
  const res = await db.ref(`wallets/${uid}/${cur}`).transaction(c => { const v = Number(c) || 0; if (v + amount < 0) { insufficient = true; return; } insufficient = false; return v + amount; });
  if (!res.committed) return r.status(409).json({ error: insufficient ? 'balance would go negative' : 'busy, retry' });
  await db.ref('adminCoinLedger').push({ uid, amount, currency: cur, reason, by: q.uid, at: Date.now() });
  r.json({ uid, currency: cur, [cur]: res.snapshot.val() });
}));
// superadmin: set role user|moderator|admin with permissions (superadmin roles are not changeable here)
app.put('/superadmin/users/:uid/role', w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, role = String(q.body?.role || '');
  if (!['user', 'moderator', 'admin'].includes(role)) return r.status(400).json({ error: 'role must be user|moderator|admin' });
  if (uid === q.uid) return r.status(400).json({ error: 'cannot change your own role' });
  const cur = await loadRole(uid); if (cur?.role === 'superadmin') return r.status(409).json({ error: 'target is superadmin' });
  if (role === 'user') { await db.ref(`roles/${uid}`).remove(); return r.json({ uid, role }); }
  const p = {}; for (const k of PERMS) p[k] = q.body?.permissions?.[k] === true;
  if (role === 'moderator') { p.coins_confirm = false; p.config_edit = false; p.age_approve = false; }
  const entry = { role: role === 'moderator' ? 'admin' : 'admin', label: role, permissions: p, grantedBy: q.uid, grantedAt: Date.now() };
  await db.ref(`roles/${uid}`).set(entry); r.json({ uid, ...entry });
}));
// reports: users file, admins with manage_content resolve
app.post('/reports', w(notBanned), w(async (q, r) => {
  const b = q.body || {}, type = String(b.targetType || '');
  if (!['post', 'user', 'comment', 'chat'].includes(type) || typeof b.targetId !== 'string' || !b.targetId || b.targetId.length > 128) return r.status(400).json({ error: 'targetType and targetId required' });
  const today = new Date(Date.now() + 7 * 3600e3).toISOString().slice(0, 10);
  const cnt = await db.ref(`usage/${q.uid}/${today.replace(/-/g, '')}/reports`).transaction(c => (Number(c) || 0) + 1);
  if (cnt.snapshot.val() > 30) return r.status(429).json({ error: 'too many reports today' });
  let targetUid = typeof b.targetUid === 'string' ? b.targetUid : null, preview = '';
  if (type === 'post') { const p = (await db.ref(`posts/${b.targetId}`).get()).val(); if (!p) return r.status(404).json({ error: 'post not found' }); targetUid = p.authorId; preview = String(p.text || '').slice(0, 140); }
  if (type === 'user') targetUid = b.targetId;
  const ref = db.ref('reports').push();
  await ref.set({ reporterUid: q.uid, targetType: type, targetId: b.targetId, targetUid, preview, reason: String(b.reason || '').slice(0, 300) || 'ไม่ระบุ', status: 'open', createdAt: Date.now() });
  r.status(201).json({ id: ref.key });
}));
app.get('/admin/reports', perm('manage_content'), w(async (q, r) => {
  const status = String(q.query.status || 'open');
  const [reps, users] = await Promise.all([db.ref('reports').get(), db.ref('users').get()]);
  const us = users.val() || {};
  const list = Object.entries(reps.val() || {}).map(([id, x]) => ({ id, reporterUid: x.reporterUid || '', targetType: x.targetType || 'user', targetId: x.targetId || x.targetUid || '', targetUid: x.targetUid || '',
    preview: x.preview || x.details || '', reason: x.reason || '', status: x.status || 'open', createdAt: Number(x.createdAt) || 0,
    reporterName: us[x.reporterUid]?.displayName || '', targetName: us[x.targetUid]?.displayName || '' }))
    .filter(x => status === 'all' || x.status === status).sort((a, b) => b.createdAt - a.createdAt).slice(0, 200);
  r.json({ reports: list });
}));
app.post('/admin/reports/:id/resolve', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const id = q.params.id, action = String(q.body?.action || '');
  const rep = (await db.ref(`reports/${id}`).get()).val(); if (!rep) return r.status(404).json({ error: 'not found' });
  if (!['dismiss', 'remove_post', 'ban'].includes(action)) return r.status(400).json({ error: 'action must be dismiss|remove_post|ban' });
  if (action === 'remove_post') {
    const pid = rep.targetType === 'post' ? rep.targetId : null; if (!pid) return r.status(400).json({ error: 'report is not about a post' });
    const post = (await db.ref(`posts/${pid}`).get()).val();
    if (post) {
      const del = { [`posts/${pid}`]: null, [`postLikes/${pid}`]: null, [`postComments/${pid}`]: null, [`authorPosts/${post.authorId}/${pid}`]: null };
      for (const t of post.hashtags || []) del[`hashtags/${t}/${pid}`] = null;
      await db.ref().update(del);
      for (const im of post.images || []) await admin.storage().bucket(STORAGE_BUCKET).file(im.path).delete().catch(() => {});
    }
  }
  if (action === 'ban') {
    if (!can(q.role, 'ban_users')) return r.status(403).json({ error: 'missing permission ban_users' });
    const tu = rep.targetUid; if (!tu) return r.status(400).json({ error: 'no target user' });
    const tr = await loadRole(tu); if (tr?.role === 'superadmin' || (tr?.role === 'admin' && q.role.role !== 'superadmin')) return r.status(403).json({ error: 'cannot ban this account' });
    const days = Math.max(0, Math.min(3650, Number(q.body?.days) || 0));
    await db.ref(`bans/${tu}`).set({ reason: `รายงาน: ${rep.reason || ''}`.slice(0, 200), by: q.uid, at: Date.now(), until: days ? Date.now() + days * 86400e3 : null });
    await admin.auth().revokeRefreshTokens(tu).catch(() => {});
  }
  await db.ref(`reports/${id}`).update({ status: action === 'dismiss' ? 'dismissed' : 'actioned', action, resolvedBy: q.uid, resolvedAt: Date.now() });
  await db.ref('adminLog').push({ action: `report_${action}`, reportId: id, by: q.uid, at: Date.now() });
  r.json({ id, status: action === 'dismiss' ? 'dismissed' : 'actioned' });
}));

app.put('/admin/lock', perm('app_lock'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}; if (typeof b.locked !== 'boolean') return r.status(400).json({ error: 'locked (boolean) required' });
  const img = String(b.imageUrl || ''); if (img && !/^https:\/\//.test(img)) return r.status(400).json({ error: 'imageUrl must be https' });
  const v = { locked: b.locked, message: String(b.message || '').slice(0, 300) || 'แอปปิดปรับปรุงชั่วคราว ขออภัยในความไม่สะดวก', imageUrl: img.slice(0, 500), by: q.uid, at: Date.now() };
  await db.ref('appConfig/lock').set(v); lockCache = { at: 0, v: null };
  await db.ref('adminLog').push({ action: b.locked ? 'app_lock' : 'app_unlock', by: q.uid, at: Date.now(), message: v.message });
  r.json(v);
}));
app.get('/admin/lock/log', perm('app_lock'), w(async (_q, r) => {
  const v = (await db.ref('adminLog').orderByChild('at').limitToLast(200).get()).val() || {};
  r.json({ log: Object.values(v).filter(x => String(x.action).startsWith('app_')).sort((a, b) => b.at - a.at).slice(0, 30) });
}));
// Coming-soon placeholders (superadmin only). appConfig/comingSoon/{target} — clients read live via RTDB.
const CS_ID = /^[a-z0-9_]{2,40}$/;
const csUrl = id => `https://friendtalk-brain-123224091480.asia-southeast1.run.app/cs-assets/${id}`;
// --- UI background images (admin config_edit). Stored in Storage uiBg/{key}-{ts}.jpg, served publicly (non-sensitive decor).
const BG_KEY = /^(app|tab_[a-z_]{2,20}|sec_[A-Za-z0-9_-]{1,40})$/, BG_FILE = /^[A-Za-z0-9_-]{1,60}-\d{10,14}$/;
app.get('/ui-bg/:f', w(async (q, r) => {
  if (!BG_FILE.test(q.params.f)) return r.status(400).end();
  const f = admin.storage().bucket(STORAGE_BUCKET).file(`uiBg/${q.params.f}`);
  const [ok] = await f.exists(); if (!ok) return r.status(404).end();
  const [meta] = await f.getMetadata(); r.set('Content-Type', meta.contentType || 'image/jpeg'); r.set('Cache-Control', 'public, max-age=86400, immutable');
  f.createReadStream().on('error', () => r.status(500).end()).pipe(r);
}));
app.put('/admin/ui/bg/:key', perm('config_edit'), w(notBanned), express.raw({ type: 'image/*', limit: '3mb' }), w(async (q, r) => {
  const key = q.params.key; if (!BG_KEY.test(key)) return r.status(400).json({ error: 'bad key' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image body required' });
  const isWebp = buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP', isPng = buf[0] === 0x89 && buf[1] === 0x50, isJpg = buf[0] === 0xff && buf[1] === 0xd8;
  if (!isWebp && !isPng && !isJpg) return r.status(400).json({ error: 'webp/png/jpeg only' });
  const name = `${key}-${Date.now()}`;
  await admin.storage().bucket(STORAGE_BUCKET).file(`uiBg/${name}`).save(buf, { contentType: isWebp ? 'image/webp' : isPng ? 'image/png' : 'image/jpeg', resumable: false });
  r.json({ url: `https://friendtalk-brain-123224091480.asia-southeast1.run.app/ui-bg/${name}` });
}));
app.get('/cs-assets/:id', w(async (q, r) => {
  if (!CS_ID.test(q.params.id)) return r.status(400).end();
  const f = admin.storage().bucket(STORAGE_BUCKET).file(`comingSoon/${q.params.id}.webp`);
  const [ok] = await f.exists(); if (!ok) return r.status(404).end();
  const [meta] = await f.getMetadata(); r.set('Content-Type', meta.contentType || 'image/webp'); r.set('Cache-Control', 'public, max-age=300');
  f.createReadStream().on('error', () => r.status(500).end()).pipe(r);
}));
app.put('/superadmin/coming-soon/:id', w(notBanned), w(async (q, r) => {
  const id = q.params.id, b = q.body || {}; if (!CS_ID.test(id)) return r.status(400).json({ error: 'bad target' });
  const ref = db.ref(`appConfig/comingSoon/${id}`), cur = (await ref.get()).val() || {};
  const v = { enabled: b.enabled !== false, text: String(b.text ?? cur.text ?? '').slice(0, 120) || 'เร็ว ๆ นี้ รอติดตาม', sticker: String(b.sticker ?? cur.sticker ?? '').slice(0, 16),
    imageUrl: b.removeImage ? '' : (cur.imageUrl || ''), by: q.uid, at: Date.now() };
  await ref.set(v); r.json(v);
}));
app.put('/superadmin/coming-soon/:id/image', express.raw({ type: 'image/*', limit: '1mb' }), w(async (q, r) => {
  const id = q.params.id; if (!CS_ID.test(id)) return r.status(400).json({ error: 'bad target' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image body required' });
  const isWebp = buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP', isPng = buf[0] === 0x89 && buf[1] === 0x50, isJpg = buf[0] === 0xff && buf[1] === 0xd8;
  if (!isWebp && !isPng && !isJpg) return r.status(400).json({ error: 'webp/png/jpeg only' });
  await admin.storage().bucket(STORAGE_BUCKET).file(`comingSoon/${id}.webp`).save(buf, { contentType: isWebp ? 'image/webp' : isPng ? 'image/png' : 'image/jpeg', resumable: false });
  const ref = db.ref(`appConfig/comingSoon/${id}`), cur = (await ref.get()).val() || {};
  const v = { enabled: cur.enabled !== false, text: cur.text || 'เร็ว ๆ นี้ รอติดตาม', sticker: cur.sticker || '', imageUrl: `${csUrl(id)}?v=${Date.now()}`, by: q.uid, at: Date.now() };
  await ref.set(v); r.json(v);
}));
app.delete('/superadmin/coming-soon/:id', w(async (q, r) => {
  const id = q.params.id; if (!CS_ID.test(id)) return r.status(400).json({ error: 'bad target' });
  await db.ref(`appConfig/comingSoon/${id}`).remove();
  await admin.storage().bucket(STORAGE_BUCKET).file(`comingSoon/${id}.webp`).delete().catch(() => {});
  r.json({ removed: id });
}));
app.use((e, _q, r, _n) => {
  if (e.type === 'entity.too.large') return r.status(413).json({ error: 'request too large' });
  if (e.type === 'entity.parse.failed') return r.status(400).json({ error: 'invalid JSON' });
  r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' });
});
app.listen(process.env.PORT || 8080);
