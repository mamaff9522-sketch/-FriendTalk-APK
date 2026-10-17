import express from 'express';
import admin from 'firebase-admin';
import crypto from 'node:crypto';
import { fetchKeys, verifySsv } from './ssv.js';
import agoraToken from 'agora-token';
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
app.get('/download/apk', (_q, r) => r.redirect(302, 'https://github.com/mamaff9522-sketch/-FriendTalk-APK/releases/latest/download/FriendTalk-latest.apk'));
const now = () => admin.database.ServerValue.TIMESTAMP;
app.get('/health', (_q, r) => r.json({ ok: true, service: 'friendtalk-brain' }));
async function auth(req, res, next) {
  const m = (req.headers.authorization || '').match(/^Bearer (.+)$/);
  if (!m) return res.status(401).json({ error: 'missing token' });
  try {
    const t = await admin.auth().verifyIdToken(m[1], true);
    req.uid = t.uid; req.tok = t;
    if (req.method !== 'GET' && req.originalUrl !== '/me/account/delete' && t.firebase?.sign_in_provider === 'password' && t.email_verified !== true) return res.status(403).json({ error: 'email not verified' });
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
// Loads the role itself, so routes registered before app.use('/admin', isAdmin) also work (was the cause of 403 on /admin/feature-locks, /admin/ad-placements).
const perm = p => (req, res, next) => (req.role ? Promise.resolve(req.role) : loadRole(req.uid)).then(r => { req.role = r; can(r, p) ? next() : res.status(403).json({ error: `missing permission ${p}` }); }).catch(e => res.status(500).json({ error: 'internal' }));
async function isSuper(req, res, next) {
  const r = await loadRole(req.uid);
  if (r?.role !== 'superadmin') return res.status(403).json({ error: 'superadmin only' });
  req.role = r; next();
}
const w = fn => (q, r, n) => fn(q, r, n).catch(e => { console.error(e.message); r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' }); });
app.use(['/push', '/coins', '/age', '/config', '/admin', '/superadmin', '/me', '/match', '/bot', '/ui', '/chats', '/swipe', '/posts', '/feed', '/friends', '/radar', '/shake', '/limits', '/quota', '/search', '/users', '/interests', '/bots', '/gifts', '/rooms', '/reports', '/creator', '/blocks', '/chat-rules', '/companion-apply', '/companion', '/shop', '/follow', '/views', '/withdraw', '/onboarding', '/idv', '/chat-send', '/rtc', '/calls', '/lives', '/vip', '/discover', '/anon', '/notifications', '/random', '/randomcard', '/payopen'], w(auth));
app.get('/me', w(async (q, r) => {
  const dh = String(q.get('X-Device-Hash') || ''); if (/^[a-f0-9]{16,64}$/.test(dh)) { db.ref(`deviceOf/${q.uid}`).set(dh).catch(() => {}); db.ref(`devices/${dh}/${q.uid}`).set(Date.now()).catch(() => {}); }
  // Safeguard: if /roles has no superadmin at all (data loss), re-seed it for the configured owner email (verified Google sign-in only; server-side).
  const owners = String(process.env.OWNER_EMAILS || '').toLowerCase().split(',').map(x => x.trim()).filter(Boolean);
  if (owners.includes(String(q.tok?.email || '').toLowerCase()) && q.tok?.email_verified === true && q.tok?.firebase?.sign_in_provider === 'google.com') {
    const all = (await db.ref('roles').get()).val() || {};
    if (!Object.values(all).some(v => (typeof v === 'string' ? v : v?.role) === 'superadmin')) {
      await db.ref(`roles/${q.uid}`).set({ role: 'superadmin', grantedBy: 'owner-reseed', grantedAt: Date.now() });
      await db.ref('adminLog').push({ action: 'owner_reseed', target: q.uid, by: 'system', at: Date.now() });
    }
  }
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
const PARTS = ['home','posting','discover','random','shake','radar','swipe','similar','requests','companion','live','video','chat','ai','ads','gifts','decor','voiceCall','videoCall'];
const PART_OF = q => {
  const p = q.path;
  if (/^\/posts\/?$/.test(p) && q.method === 'POST') return 'posting';
  if (/^\/(posts|feed)(\/|$)/.test(p)) return 'home';
  if (/^\/(match|random)(\/|$)/.test(p)) return 'random';
  if (/^\/(shake|anon)(\/|$)/.test(p)) return 'shake';
  if (/^\/radar(\/|$)/.test(p)) return 'radar';
  if (/^\/(swipe|payopen)(\/|$)/.test(p)) return 'swipe';
  if (/^\/interests\/(similar|match)/.test(p)) return 'similar';
  if (/^\/friends\/(request|respond|requests)/.test(p)) return 'requests';
  if (/^\/search(\/|$)/.test(p)) return 'discover';
  if (/^\/chats(\/|$)/.test(p)) return 'chat';
  if (/^\/(bot|bots)(\/|$)/.test(p)) return 'ai';
  if (/^\/(quota|admob)(\/|$)/.test(p)) return 'ads';
  if (/^\/rooms(\/|$)/.test(p)) return 'decor';
  if (/^\/lives(\/|$)/.test(p)) return 'live';
  if (/^\/calls(\/|$)/.test(p)) return 'chat';
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
const AD_PLACES = ['home', 'discover', 'chatList', 'video', 'inFeed', 'chatRoom', 'vipBanner']; // vipBanner = Home 'ปลดล็อก VIP' card (free users only)
app.put('/admin/ad-placements', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, v = {};
  for (const k of AD_PLACES) if (typeof b[k] === 'boolean') v[k] = b[k];
  v.inFeedEvery = Math.max(2, Math.min(50, parseInt(b.inFeedEvery) || 5)); v.by = q.uid; v.at = Date.now();
  await db.ref('appConfig/adPlacements').set(v); r.json(v);
}));
// Room-decor options the admin adds later (appConfig/decorOptions; clients read it directly):
// themes {id:{name, colors[2-3 hex], enabled}}, frames {id:{name, colors[2-3 hex], enabled}}, effects {id:{name, glyphs[1-3 emoji], enabled}}.
const DECOR_OPT_ID = /^[a-z0-9_]{2,30}$/;
app.put('/admin/decor-options', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, out = { themes: {}, frames: {}, effects: {} };
  const builtIn = { themes: THEMES, frames: FRAMES, effects: EFFECTS }, imgs = (await db.ref('appConfig/decorImages').get()).val() || {};
  for (const kind of ['themes', 'frames', 'effects']) {
    const src = b[kind] && typeof b[kind] === 'object' ? b[kind] : {};
    const ids = Object.keys(src); if (ids.length > 40) return r.status(400).json({ error: `${kind}: ไม่เกิน 40 รายการ` });
    for (const id of ids) {
      const v = src[id] || {};
      if (!DECOR_OPT_ID.test(id) || builtIn[kind].includes(id)) return r.status(400).json({ error: `${kind}: รหัส '${id}' ใช้ไม่ได้ (a-z 0-9 _ และห้ามซ้ำของในระบบ)` });
      const name = String(v.name || '').trim().slice(0, 30); if (!name) return r.status(400).json({ error: `${kind}.${id}: ต้องมีชื่อ` });
      const o = { name, enabled: v.enabled !== false }, img = imgs[kind]?.[id];
      if (typeof img === 'string' && img) o.imageUrl = img;
      if (o.imageUrl) { // picture option: colours / emojis are optional extras
        if (kind === 'effects') o.glyphs = (Array.isArray(v.glyphs) ? v.glyphs : []).map(x => String(x).trim()).filter(x => x && [...x].length <= 4).slice(0, 3);
        else o.colors = (Array.isArray(v.colors) ? v.colors : []).filter(x => /^#[0-9A-Fa-f]{6}$/.test(x)).slice(0, 3);
        out[kind][id] = o; continue;
      }
      if (kind === 'effects') {
        const g = (Array.isArray(v.glyphs) ? v.glyphs : []).map(x => String(x).trim()).filter(x => x && [...x].length <= 4).slice(0, 3);
        if (!g.length) return r.status(400).json({ error: `effects.${id}: ใส่อีโมจิอย่างน้อย 1 ตัว` }); o.glyphs = g;
      } else {
        const c = (Array.isArray(v.colors) ? v.colors : []).filter(x => /^#[0-9A-Fa-f]{6}$/.test(x)).slice(0, 3);
        if (c.length < 2) return r.status(400).json({ error: `${kind}.${id}: ต้องมีสีอย่างน้อย 2 สี (#RRGGBB)` }); o.colors = c;
      }
      out[kind][id] = o;
    }
  }
  out.by = q.uid; out.at = Date.now();
  await db.ref('appConfig/decorOptions').set(out); decorOptCache = { at: 0, v: null };
  await db.ref('adminLog').push({ action: 'decor_options', by: q.uid, at: Date.now(), message: ['themes', 'frames', 'effects'].map(k => `${k}:${Object.keys(out[k]).length}`).join(' ') });
  r.json(out);
}));
let decorOptCache = { at: 0, v: null };
async function decorOptions() {
  if (decorOptCache.v && Date.now() - decorOptCache.at < 30000) return decorOptCache.v;
  const v = (await db.ref('appConfig/decorOptions').get()).val() || {};
  const on = k => Object.entries(v[k] || {}).filter(([, o]) => o && o.enabled !== false).map(([id]) => id);
  decorOptCache = { at: Date.now(), v: { themes: on('themes'), frames: on('frames'), effects: on('effects') } };
  return decorOptCache.v;
}
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
  { const m = await moderateText(q.uid, [name, bio, ...links].join(' '), { kind: 'creator_application' }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
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
  'ห้ามความรุนแรง กิจกรรมผิดกฎหมาย หรือยาเสพติด', 'ห้ามสแปมหรือโฆษณา', 'อย่าโอนเงินให้คนแปลกหน้า', 'กดรายงานหรือบล็อกได้ทุกเมื่อจากเมนู ⋯ ในห้องแชท', 'ข้อความ โพสต์ และรูปภาพจะถูกตรวจสอบอัตโนมัติ (ระบบคัดกรอง + AI) เพื่อความปลอดภัย กรณีถูกแจ้งเตือนซ้ำ แอดมินอาจเปิดดูข้อความที่เกี่ยวข้องได้ และมีบันทึกการเข้าดูทุกครั้ง'];
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
  // 1-4 photos of the applicant (uploaded by the app under posts/{uid}/), checked + moderated; shown to admins and on the approved card
  const refs = (Array.isArray(b.photoRefs) ? b.photoRefs : []).slice(0, 4);
  if (!refs.length) return r.status(400).json({ error: 'แนบรูปของคุณอย่างน้อย 1 รูป' });
  const photos = [];
  for (const p of refs) {
    const u = await postImageUrl(q.uid, p); if (!u) return r.status(400).json({ error: 'รูปไม่ถูกต้อง ลองเลือกใหม่' });
    const mi = await moderateImage(q.uid, p, { kind: 'companion_photo' }); if (!mi.ok) return r.status(422).json({ code: 'moderated', error: 'รูปภาพนี้ขัดกติกาความปลอดภัย' });
    photos.push(u);
  }
  { const m = await moderateText(q.uid, [dn, occ, b.talkStyle, b.personality].join(' '), { kind: 'companion_application' }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
  const t = Date.now(), talkStyle = String(b.talkStyle || '').trim().slice(0, 300), personality = String(b.personality || '').trim().slice(0, 300);
  await db.ref().update({
    [`companionApplications/${q.uid}`]: { uid: q.uid, displayName: dn, occupation: occ, age, slots, hours, talkStyle, personality, modes: [...new Set(modes)], photos, status: 'pending', submittedAt: t, reason: '' },
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
  upd[`companions/${uid}`] = approve ? { displayName: a.displayName, occupation: a.occupation, age: a.age, slots: a.slots, hours: a.hours || {}, talkStyle: a.talkStyle || '', personality: a.personality || '', modes: a.modes || ['text'], photos: a.photos || [], at: t } : null; // public card only
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
  await Promise.all([db.ref(`wallets/${partner}/${cur}`).transaction(c => num(c) + share), cur === 'diamonds' ? db.ref(`wallets/${partner}/earnedDiamonds`).transaction(c => num(c) + share) : null, db.ref(`wallets/_system/${cur}`).transaction(c => num(c) + (amount - share)),
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
  { const m = await moderateText(q.uid, text, { kind: 'chat', chatId, ref: chatId }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
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
  await db.ref().update({ [`messages/${chatId}/${db.ref(`messages/${chatId}`).push().key}`]: { senderUid: q.uid, at: t, text }, [`chats/${chatId}/lastMessage`]: text.slice(0, 100), [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: q.uid });
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
  if (comment) { const m = await moderateText(q.uid, comment, { kind: 'rating', ref: partner }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
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
const crypto2 = crypto;
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
  if (cur === 'diamonds') await db.ref(`wallets/${q.uid}/paidDiamonds`).transaction(c => num(c) + amount); // bought with real money → can buy no-ads VIP
  await pref.update({ status: 'credited', currency: cur, amount, productId, orderId: g.j.orderId || '', creditedAt: Date.now() });
  const c = await playApi('POST', `applications/${encodeURIComponent(cfg.packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(token)}:consume`);
  await pref.update({ consumed: c.status === 200 || c.status === 204 });
  r.json({ credited: true, currency: cur, amount, consumed: c.status === 200 || c.status === 204 });
}));
// ======================= Shared moderation module =======================
// Keyword filter (fast) + Gemini classification (cheap flash-lite) for text; Cloud Vision SafeSearch for images.
// flagged -> modFlags; severe -> blocked; repeat offenders (>=2 flags / 24h) or severe -> admin modQueue.
const MOD_WORDS = [/หี|ควย|เย็ด|xxx|porn|nude|เซ็กส์|sex\s*cam/i, /ฆ่า(มึง|แก)|จะฆ่า|kill you|ระเบิด/i, /โอนเงิน.*(ก่อน|มัดจำ)|บัญชีม้า|ลงทุน.*กำไร.*%|crypto.*double/i, /อายุ\s*1[0-7]\s*ปี|เด็กนักเรียน/i];
const MOD_CATS = ['sexual', 'violence', 'scam', 'minor'];
async function geminiClassify(text) {
  try {
    const prompt = 'Classify this user message from a Thai social app. Reply ONLY JSON {"flags":[...],"severity":0-3} where flags is a subset of ["sexual","violence","harassment","hate","minor","scam"]. severity 3 = severe (explicit sexual content, minors, credible threats).\nMessage: <<<' + text.slice(0, 800) + '>>>';
    const res = await fetch(`https://aiplatform.googleapis.com/v1/projects/${PROJECT}/locations/global/publishers/google/models/${GEMINI_MODEL}:generateContent`, {
      method: 'POST', headers: { Authorization: `Bearer ${await gcpToken()}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: prompt }] }], generationConfig: { maxOutputTokens: 60, temperature: 0, responseMimeType: 'application/json' } })
    });
    if (!res.ok) return null;
    const j = await res.json(); const t = (j.candidates?.[0]?.content?.parts || []).map(p => p.text || '').join('');
    const o = JSON.parse(t); return { flags: (o.flags || []).filter(x => typeof x === 'string').slice(0, 6), severity: Math.max(0, Math.min(3, +o.severity || 0)) };
  } catch (_) { return null; }
}
async function moderateText(uid, text, ctx) {
  text = String(text || ''); if (!text.trim()) return { ok: true };
  const kw = MOD_WORDS.map((re, i) => re.test(text) ? MOD_CATS[i] : null).filter(Boolean);
  const ai = (process.env.MOD_AI !== 'off') ? await geminiClassify(text) : null;
  const flags = [...new Set([...kw, ...(ai?.flags || [])])], severity = Math.max(ai?.severity || 0, kw.includes('minor') || kw.includes('sexual') ? 2 : kw.length ? 1 : 0);
  if (!flags.length && severity === 0) return { ok: true };
  const t = Date.now(), dayAgo = t - 864e5;
  const prev = Object.values((await db.ref(`modFlags/${uid}`).orderByKey().limitToLast(20).get()).val() || {}).filter(x => x.at > dayAgo).length;
  await db.ref(`modFlags/${uid}`).push({ at: t, flags, severity, kind: ctx.kind, ref: ctx.ref || '', text: text.slice(0, 500) });
  if (prev >= 1 || severity >= 3) await db.ref(`modQueue/${uid}_${t}`).set({ uid, at: t, flags, severity, kind: ctx.kind, ref: ctx.ref || '', chatId: ctx.chatId || '', text: text.slice(0, 500), status: 'open', repeat: prev + 1 });
  return { ok: severity < 3, warn: true, flags, severity, message: severity >= 3 ? 'ข้อความนี้ขัดกติกาความปลอดภัยและถูกบล็อก' : 'คำเตือน: ข้อความของคุณอาจขัดกติกา หากทำซ้ำแอดมินจะตรวจสอบ' };
}
async function moderateImage(uid, gsPath, ctx) {
  try {
    const res = await fetch('https://vision.googleapis.com/v1/images:annotate', { method: 'POST', headers: { Authorization: `Bearer ${await gcpToken()}`, 'Content-Type': 'application/json', 'x-goog-user-project': PROJECT },
      body: JSON.stringify({ requests: [{ image: { source: { imageUri: `gs://${STORAGE_BUCKET}/${gsPath}` } }, features: [{ type: 'SAFE_SEARCH_DETECTION' }] }] }) });
    if (!res.ok) return { ok: true, skipped: res.status };
    const ss = (await res.json()).responses?.[0]?.safeSearchAnnotation || {}; const bad = ['LIKELY', 'VERY_LIKELY'];
    const flags = ['adult', 'violence', 'racy'].filter(k => bad.includes(ss[k]));
    if (!flags.length) return { ok: true };
    const t = Date.now(); await db.ref(`modQueue/${uid}_${t}`).set({ uid, at: t, flags, severity: ss.adult === 'VERY_LIKELY' ? 3 : 2, kind: ctx.kind, ref: ctx.ref || '', image: gsPath, status: 'open' });
    return { ok: ss.adult !== 'VERY_LIKELY', flags };
  } catch (_) { return { ok: true, skipped: 'error' }; }
}
// Moderated text send for normal chats (new app uses this; direct RTDB writes remain allowed for older app versions).
app.post('/chat-send/:chatId', w(notBanned), w(async (q, r) => {
  const { chatId } = q.params, text = String(q.body?.text || '').trim().slice(0, 2000); if (!text) return r.status(400).json({ error: 'text required' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  if ((await db.ref(`chatBlocked/${chatId}`).get()).exists() || (await db.ref(`chats/${chatId}/closed`).get()).exists()) return r.status(403).json({ error: 'แชทนี้ส่งไม่ได้' });
  if ((await db.ref(`chats/${chatId}/paid`).get()).val() === true && (await db.ref(`chats/${chatId}/companion`).get()).val() !== q.uid) return r.status(409).json({ error: 'paid chat: use companion send' });
  if ((await db.ref(`chatBlocks/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'บัญชีของคุณถูกระงับการแชท' });
  const m = await moderateText(q.uid, text, { kind: 'chat', chatId, ref: chatId });
  if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message, flags: m.flags });
  const t = Date.now(), id = db.ref(`messages/${chatId}`).push().key;
  await db.ref().update({ [`messages/${chatId}/${db.ref(`messages/${chatId}`).push().key}`]: { senderUid: q.uid, at: t, text }, [`chats/${chatId}/lastMessage`]: text.slice(0, 100), [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: q.uid });
  r.json({ id, warning: m.warn ? m.message : '' });
}));
app.get('/admin/moderation', perm('manage_content'), w(async (q, r) => {
  const v = (await db.ref('modQueue').orderByKey().limitToLast(200).get()).val() || {};
  r.json({ items: Object.entries(v).map(([id, x]) => ({ id, ...x })).filter(x => x.status === (q.query.status || 'open')).sort((a, b) => b.at - a.at) });
}));
app.get('/admin/moderation/:id/context', perm('manage_content'), w(async (q, r) => {
  const it = (await db.ref(`modQueue/${q.params.id}`).get()).val(); if (!it) return r.status(404).json({ error: 'not found' });
  const full = q.query.full === '1';
  let msgs = [];
  if (it.chatId) { const v = (await db.ref(`messages/${it.chatId}`).orderByKey().limitToLast(full ? 200 : 10).get()).val() || {}; msgs = Object.values(v).map(m => ({ from: m.senderUid, at: m.at, text: m.text || (m.imagePath ? '[รูป]' : m.gift ? '[ของขวัญ]' : '') })); }
  await db.ref('modViewLog').push({ item: q.params.id, chatId: it.chatId || '', full, by: q.uid, at: Date.now() });
  r.json({ item: it, messages: msgs });
}));
app.post('/admin/moderation/:id/decide', perm('manage_content'), w(notBanned), w(async (q, r) => {
  const it = (await db.ref(`modQueue/${q.params.id}`).get()).val(); if (!it) return r.status(404).json({ error: 'not found' });
  const action = ['dismiss', 'warn', 'mute', 'ban'].includes(q.body?.action) ? q.body.action : 'dismiss', reason = String(q.body?.reason || '').slice(0, 200), t = Date.now();
  const upd = { [`modQueue/${q.params.id}/status`]: 'closed', [`modQueue/${q.params.id}/action`]: action, [`modQueue/${q.params.id}/by`]: q.uid, [`modQueue/${q.params.id}/decidedAt`]: t };
  if (action === 'warn') upd[`modWarnings/${it.uid}/${t}`] = { reason: reason || 'เตือนเรื่องความปลอดภัย', by: q.uid };
  if (action === 'mute') upd[`chatBlocks/${it.uid}`] = { reason: reason || 'ระงับแชทจากการตรวจสอบความปลอดภัย', by: q.uid, at: t };
  if (action === 'ban') upd[`bans/${it.uid}`] = { reason: reason || 'ละเมิดกติกาความปลอดภัย', by: q.uid, at: t };
  await db.ref().update(upd); await db.ref('adminLog').push({ action: 'mod_' + action, target: it.uid, by: q.uid, at: t, message: reason });
  r.json({ ok: true, action });
}));
// ======================= Follows + unique post views (creator metrics) =======================
app.post('/follow/:uid', w(notBanned), w(async (q, r) => {
  const other = q.params.uid; if (!UID_RE.test(other) || other === q.uid) return r.status(400).json({ error: 'bad uid' });
  const on = q.body?.follow !== false, t = Date.now(), was = (await db.ref(`following/${q.uid}/${other}`).get()).exists();
  if (on !== was) {
    await db.ref().update({ [`following/${q.uid}/${other}`]: on ? t : null, [`followers/${other}/${q.uid}`]: on ? t : null });
    await db.ref(`followCounts/${other}`).transaction(c => Math.max(0, num(c) + (on ? 1 : -1)));
    await db.ref(`followLog/${q.uid}`).push({ to: other, on, at: t });
  }
  r.json({ following: on, followers: num((await db.ref(`followCounts/${other}`).get()).val()) });
}));
app.get('/follow/:uid', w(async (q, r) => r.json({ following: (await db.ref(`following/${q.uid}/${q.params.uid}`).get()).exists(), followers: num((await db.ref(`followCounts/${q.params.uid}`).get()).val()) })));
app.post('/views/:postId', w(async (q, r) => {
  const id = q.params.postId; if (!/^[A-Za-z0-9_-]{1,40}$/.test(id)) return r.status(400).json({ error: 'bad id' });
  const ref = db.ref(`postViews/${id}/${q.uid}`); if (!(await ref.get()).exists()) await ref.set(Date.now()); // 1 person = 1 view
  r.json({ ok: true });
}));
const BKK = () => new Date(Date.now() + 7 * 3600e3);
async function creatorMetrics(uid) {
  const t = Date.now(), d30 = t - 30 * 864e5;
  const [fc, followers, posts] = await Promise.all([db.ref(`followCounts/${uid}`).get(), db.ref(`followers/${uid}`).get(), db.ref(`authorPosts/${uid}`).get()]);
  const postTimes = Object.values(posts.val() || {}).map(Number).filter(x => x > d30).sort();
  const days = new Set(postTimes.map(x => new Date(x + 7 * 3600e3).toISOString().slice(0, 10)));
  let maxGap = 30; if (postTimes.length) { maxGap = 0; let prev = d30; for (const x of [...postTimes, t]) { maxGap = Math.max(maxGap, (x - prev) / 864e5); prev = x; } }
  const viewers = new Set(); for (const pid of Object.keys(posts.val() || {})) { const v = (await db.ref(`postViews/${pid}`).get()).val() || {}; for (const [vu, at] of Object.entries(v)) if (at > d30 && vu !== uid) viewers.add(vu); }
  const fl = Object.keys(followers.val() || {}).slice(0, 200);
  const newShare = fl.length ? (await Promise.all(fl.map(async f => num((await db.ref(`users/${f}/createdAt`).get()).val()) > t - 7 * 864e5))).filter(Boolean).length / fl.length : 0;
  const age = (await db.ref(`ageInfo/${uid}`).get()).val();
  const m = { followers: num(fc.val()), postsLast30: postTimes.length, activeDays30: days.size, maxGapDays: Math.round(maxGap * 10) / 10, uniqueViewers30: viewers.size, newFollowerShare: Math.round(newShare * 100), adult: !!(age && age.age >= 18), computedAt: t };
  m.criteria = { followers5000: m.followers >= 5000, dailyPosts: m.activeDays30 >= 28 && m.postsLast30 >= 30, noGapOver2Days: m.maxGapDays <= 2, uniqueViewers100k: m.uniqueViewers30 >= 100000, adult18: m.adult };
  m.eligible = Object.values(m.criteria).every(Boolean);
  await db.ref(`creatorMetrics/${uid}`).set(m); return m;
}
app.post('/admin/creator-metrics/:uid', perm('manage_content'), w(async (q, r) => r.json(await creatorMetrics(q.params.uid))));
app.post('/admin/creator-metrics-all', perm('manage_content'), w(async (_q, r) => {
  const apps = Object.keys((await db.ref('creatorApplications').get()).val() || {}); const out = {};
  for (const u of apps.slice(0, 100)) out[u] = (await creatorMetrics(u)).eligible; r.json({ computed: Object.keys(out).length, eligible: out });
}));
// ======================= Age / onboarding / ID verification =======================
function ageFrom(bd) { const [y, m, d] = bd.split('-').map(Number); const n = BKK(); let a = n.getUTCFullYear() - y; if (n.getUTCMonth() + 1 < m || (n.getUTCMonth() + 1 === m && n.getUTCDate() < d)) a--; return a; }
const OB_STEPS = ['signin', 'terms', 'id_scan', 'liveness', 'profile', 'interests', 'looking_for', 'details'];
// Only these gate the app; interests / looking_for / details can be filled in later from the profile (sign-up was too long).
const OB_REQUIRED = ['signin', 'terms', 'id_scan', 'liveness', 'profile'];
const LOOKING = ['friends', 'chat', 'dating', 'activity', 'gaming', 'study', 'work', 'travel', 'other'];
const DEFAULT_TERMS = ['FriendTalk สำหรับผู้มีอายุ 18 ปีขึ้นไปเท่านั้น ข้อมูลอายุที่ให้ต้องเป็นความจริง', 'ห้ามโพสต์ ส่ง หรือขอเนื้อหาลามกอนาจาร/ทางเพศ ทุกรูปแบบ', 'ห้ามเนื้อหาหรือกิจกรรมผิดกฎหมาย ยาเสพติด การพนัน อาวุธ',
  'ห้ามคุกคาม กลั่นแกล้ง ข่มขู่ หรือใช้ถ้อยคำแสดงความเกลียดชัง', 'ห้ามหลอกลวง ฉ้อโกง ขอเงิน ชักชวนลงทุน หรือขายของโดยไม่ได้รับอนุญาต', 'ห้ามแอบอ้างเป็นบุคคลอื่นหรือใช้บัญชีปลอม',
  'ห้ามเปิดเผยข้อมูลส่วนตัวของผู้อื่น (เบอร์ ที่อยู่ รูป เอกสาร) โดยไม่ได้รับอนุญาต', 'ห้ามติดต่อ ชักชวน หรือเผยแพร่เนื้อหาเกี่ยวกับผู้เยาว์ในทางที่ไม่เหมาะสม โดยเด็ดขาด', 'ห้ามความรุนแรง การทำร้ายตนเองหรือผู้อื่น',
  'ห้ามสแปม โฆษณา หรือส่งลิงก์อันตราย', 'เนื้อหาถูกตรวจสอบอัตโนมัติเพื่อความปลอดภัย', 'การฝ่าฝืนอาจถูกตักเตือน ลบเนื้อหา ระงับการแชท/โพสต์ ระงับบัญชีชั่วคราวหรือถาวร และยึดยอดที่ได้มาโดยมิชอบ โดยไม่ต้องแจ้งล่วงหน้า'];
async function termsCfg() { const v = (await db.ref('appConfig/terms').get()).val(); return v && Array.isArray(v.rules) && v.rules.length ? { version: v.version || 1, rules: v.rules } : { version: 1, rules: DEFAULT_TERMS }; }
async function obCfg() { const v = (await db.ref('appConfig/onboarding').get()).val() || {}; return { kycEnabled: v.kycEnabled === true, termsVersion: (await termsCfg()).version }; }
app.get('/onboarding/terms', w(async (_q, r) => r.json(await termsCfg())));
app.put('/admin/terms', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const rules = (Array.isArray(q.body?.rules) ? q.body.rules : []).map(x => String(x).trim().slice(0, 300)).filter(Boolean).slice(0, 40);
  if (!rules.length) return r.status(400).json({ error: 'rules required' });
  const cur = await termsCfg(), v = { version: cur.version + 1, rules, by: q.uid, at: Date.now() }; await db.ref('appConfig/terms').set(v); r.json(v);
}));
// Signup picklists (no income / religion / skin colour by design). multi = multi-select. Multi fields also feed interestProfile for similarity.
const OB_FIELDS = {
  profile: [
    { key: 'gender', label: 'เพศ', type: 'single', options: ['ชาย', 'หญิง', 'LGBTQ+', 'ไม่ระบุ'] },
    { key: 'interestedIn', label: 'สนใจคุยกับ', type: 'single', options: ['ชาย', 'หญิง', 'ทุกเพศ'] },
    { key: 'ageRange', label: 'ช่วงอายุที่อยากคุยด้วย', type: 'single', options: ['18-24', '25-34', '35-44', '45-54', '55+', 'ทุกช่วงอายุ'] },
    { key: 'province', label: 'จังหวัด/พื้นที่', type: 'single', options: ['กรุงเทพฯ และปริมณฑล', 'ภาคกลาง', 'ภาคเหนือ', 'ภาคอีสาน', 'ภาคตะวันออก', 'ภาคตะวันตก', 'ภาคใต้', 'ต่างประเทศ'] }],
  details: [
    { key: 'personality', label: 'นิสัย', type: 'multi', options: ['ร่าเริง', 'ใจเย็น', 'ขี้อาย', 'ชอบเข้าสังคม', 'ชอบอยู่บ้าน', 'ตลก', 'จริงจัง', 'โรแมนติก', 'ผจญภัย', 'เป็นผู้ฟังที่ดี'] },
    { key: 'lifestyle', label: 'ไลฟ์สไตล์', type: 'multi', options: ['ไม่ดื่ม', 'ดื่มบ้าง', 'ไม่สูบ', 'สูบ', 'ออกกำลังกายประจำ', 'ออกกำลังกายบ้าง', 'ตื่นเช้า', 'นอนดึก'] },
    { key: 'freeTime', label: 'เวลาว่าง', type: 'multi', options: ['เช้า', 'กลางวัน', 'เย็น', 'ดึก', 'วันหยุด'] },
    { key: 'languages', label: 'ภาษา', type: 'multi', options: ['ไทย', 'อังกฤษ', 'จีน', 'ญี่ปุ่น', 'เกาหลี', 'อื่น ๆ'] },
    { key: 'occupation', label: 'กลุ่มอาชีพ', type: 'single', options: ['นักเรียน/นักศึกษา', 'พนักงานบริษัท', 'ข้าราชการ/รัฐวิสาหกิจ', 'ธุรกิจส่วนตัว', 'ฟรีแลนซ์', 'แรงงาน/บริการ', 'ครีเอเตอร์', 'ไม่ได้ทำงาน', 'อื่น ๆ'] },
    { key: 'education', label: 'ระดับการศึกษา', type: 'single', options: ['มัธยม', 'ปวช./ปวส.', 'ปริญญาตรี', 'สูงกว่าปริญญาตรี', 'อื่น ๆ'] },
    { key: 'values', label: 'คุณค่าที่ให้ความสำคัญ', type: 'multi', options: ['ครอบครัว', 'อยากมีลูก', 'ไม่อยากมีลูก', 'ยังไม่แน่ใจเรื่องลูก', 'อาชีพการงาน', 'อิสระ', 'สุขภาพ', 'การเรียนรู้'] },
    { key: 'height', label: 'ส่วนสูง', type: 'single', options: ['< 155 ซม.', '155-164 ซม.', '165-174 ซม.', '175-184 ซม.', '185+ ซม.', 'ไม่ระบุ'] },
    { key: 'bodyType', label: 'รูปร่าง', type: 'single', options: ['ผอม', 'สมส่วน', 'มีกล้าม', 'ท้วม', 'ไม่ระบุ'] },
    { key: 'weight', label: 'น้ำหนัก', type: 'single', options: ['< 50 กก.', '50-64 กก.', '65-79 กก.', '80+ กก.', 'ไม่ขอระบุ'] }],
  interests: { key: 'hobbies', label: 'ความสนใจและงานอดิเรก', type: 'multi', options: ['ดนตรี', 'หนัง/ซีรีส์', 'เกม', 'กีฬา', 'ท่องเที่ยว', 'อาหาร/ทำอาหาร', 'อ่านหนังสือ', 'ถ่ายรูป', 'ศิลปะ', 'สัตว์เลี้ยง', 'แฟชั่น', 'เทคโนโลยี', 'ธุรกิจ/ลงทุน', 'ธรรมชาติ'] },
  lookingFor: { key: 'lookingFor', label: 'เป้าหมายในการใช้แอป', type: 'multi', options: ['หาเพื่อน', 'หาคู่', 'หาคนคุย', 'ธุรกิจ/คอนเนคชัน'] }
};
function validFields(defs, b) {
  const out = {};
  for (const f of defs) { const v = b[f.key];
    if (f.type === 'multi') { const l = [...new Set((Array.isArray(v) ? v : []).filter(x => f.options.includes(x)))]; if (!l.length) return { error: `เลือก${f.label}` }; out[f.key] = l; }
    else { if (!f.options.includes(v)) return { error: `เลือก${f.label}` }; out[f.key] = v; } }
  return { out };
}
app.get('/onboarding/fields', w(async (_q, r) => r.json(OB_FIELDS)));
async function obState(uid) {
  const cfg = await obCfg(), done = { ...((await db.ref(`onboarding/${uid}/steps`).get()).val() || {}) };
  const steps = OB_STEPS.filter(s => cfg.kycEnabled || !['id_scan', 'liveness'].includes(s));
  done.signin = done.signin || Date.now();
  const ta = (await db.ref(`termsAccepted/${uid}`).get()).val(); if (!ta || ta.version !== cfg.termsVersion) delete done.terms; // re-accept on new terms version
  const u = (await db.ref(`users/${uid}`).get()).val() || {};
  const ip = u.interestProfile || {};
  if (!done.interests && Object.keys(ip).some(k => k !== 'lookingFor')) done.interests = Date.now();
  if (!done.looking_for && Array.isArray(ip.lookingFor) && ip.lookingFor.length) done.looking_for = Date.now();
  const next = steps.find(s => OB_REQUIRED.includes(s) && !done[s]) || null;
  return { steps: steps.filter(s => OB_REQUIRED.includes(s)), optional: steps.filter(s => !OB_REQUIRED.includes(s) && !done[s]), done, next, complete: !next, kycEnabled: cfg.kycEnabled, termsVersion: cfg.termsVersion, minor: (await db.ref(`onboarding/${uid}/blockedMinor`).get()).exists() };
}
app.get('/onboarding', w(async (q, r) => r.json(await obState(q.uid))));
app.post('/onboarding/:step', w(notBanned), w(async (q, r) => {
  const step = q.params.step, st = await obState(q.uid), b = q.body || {}, t = Date.now();
  if (!OB_STEPS.includes(step) || (!st.steps.includes(step) && OB_REQUIRED.includes(step))) return r.status(400).json({ error: 'unknown step' });
  if (OB_REQUIRED.includes(step) && st.next !== step && !st.done[step]) return r.status(409).json({ error: 'ต้องทำขั้นตอนตามลำดับ', next: st.next });
  if (step === 'id_scan' || step === 'liveness') return r.status(501).json({ error: 'ยังไม่ได้เชื่อมผู้ให้บริการ eKYC' });
  if (step === 'terms') { if (b.accept !== true || +b.version !== st.termsVersion) return r.status(400).json({ error: 'ต้องยอมรับกฎก่อน' }); await db.ref(`termsAccepted/${q.uid}`).set({ version: st.termsVersion, at: t }); }
  if (step === 'profile') {
    // Only nickname, age and gender are required; real name and the 'who I want to talk to' fields are optional now.
    const vf = { out: {} };
    for (const f of OB_FIELDS.profile) { const v = b[f.key]; if (f.options.includes(v)) vf.out[f.key] = v; else if (f.key === 'gender') return r.status(400).json({ error: 'เลือกเพศ' }); else vf.out[f.key] = null; }
    const real = String(b.realName || '').trim().slice(0, 80), n = String(b.displayName || '').trim();
    if (n.length < 2 || n.length > 30) return r.status(400).json({ error: 'ชื่อเล่น 2-30 ตัวอักษร' });
    let age = parseInt(b.age), bd = String(b.birthDate || '');
    if (/^\d{4}-\d{2}-\d{2}$/.test(bd)) age = ageFrom(bd); else bd = '';
    if (!(age >= 1 && age <= 120)) return r.status(400).json({ error: 'กรอกอายุ' });
    const cur = (await db.ref(`ageInfo/${q.uid}`).get()).val(); if (cur && cur.age !== age && cur.method !== 'self_declared') return r.status(403).json({ error: 'แก้อายุได้เฉพาะแอดมิน' });
    if (cur && cur.method === 'self_declared' && cur.age !== age && st.done.profile) return r.status(403).json({ error: 'แก้อายุได้เฉพาะแอดมิน' });
    await db.ref(`ageInfo/${q.uid}`).set({ age, birthDate: bd, method: 'self_declared', at: t });
    if (age < 18) { await db.ref(`onboarding/${q.uid}/blockedMinor`).set(t); return r.status(403).json({ code: 'minor', error: 'FriendTalk สำหรับผู้มีอายุ 18 ปีขึ้นไปเท่านั้น' }); }
    const m = await moderateText(q.uid, n + ' ' + real, { kind: 'profile_name' }); if (!m.ok || (m.flags || []).length) return r.status(422).json({ error: 'ชื่อนี้ใช้ไม่ได้' });
    const key = n.toLowerCase().replace(/[.#$\[\]\/\s]/g, '_'), own = await db.ref(`displayNames/${key}`).transaction(v => (v === null || v === q.uid ? q.uid : undefined));
    if (!own.committed || own.snapshot.val() !== q.uid) return r.status(409).json({ error: 'ชื่อเล่นนี้ถูกใช้แล้ว' });
    await db.ref().update({ [`users/${q.uid}/displayName`]: n, ...(real.length >= 2 ? { [`private/${q.uid}/realName`]: real } : {}), [`users/${q.uid}/profileDetails/gender`]: vf.out.gender, [`users/${q.uid}/profileDetails/interestedIn`]: vf.out.interestedIn, [`users/${q.uid}/profileDetails/ageRange`]: vf.out.ageRange, [`users/${q.uid}/profileDetails/province`]: vf.out.province, [`users/${q.uid}/age`]: age });
  }
  if (step === 'interests' && Array.isArray(b.hobbies)) { const l = [...new Set(b.hobbies.filter(x => OB_FIELDS.interests.options.includes(x)))]; if (l.length) await db.ref(`users/${q.uid}/interestProfile/hobbies`).set(l); }
  if (step === 'interests') { const u = (await db.ref(`users/${q.uid}/interestProfile`).get()).val() || {}; if (!Object.keys(u).some(k => k !== 'lookingFor')) return r.status(400).json({ error: 'เลือกสิ่งที่ชอบก่อน' }); }
  if (step === 'looking_for') {
    const l = [...new Set((Array.isArray(b.lookingFor) ? b.lookingFor : []).filter(x => OB_FIELDS.lookingFor.options.includes(x)))];
    if (!l.length) return r.status(400).json({ error: 'เลือกสิ่งที่ต้องการอย่างน้อย 1 อย่าง' });
    if (l.length) await db.ref(`users/${q.uid}/interestProfile/lookingFor`).set(l);
  }
  if (step === 'details') {
    const vf = validFields(OB_FIELDS.details, b); if (vf.error) return r.status(400).json({ error: vf.error });
    const upd = { [`users/${q.uid}/profileDetails/details`]: vf.out };
    for (const k of ['personality', 'lifestyle', 'freeTime', 'languages', 'values']) upd[`users/${q.uid}/interestProfile/${k}`] = vf.out[k]; // feeds similarity
    await db.ref().update(upd);
  }
  await db.ref(`onboarding/${q.uid}/steps/${step}`).set(t);
  r.json(await obState(q.uid));
}));
app.put('/admin/onboarding-config', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const v = { kycEnabled: q.body?.kycEnabled === true, termsVersion: Math.max(1, parseInt(q.body?.termsVersion) || 1), by: q.uid, at: Date.now() };
  await db.ref('appConfig/onboarding').set(v); r.json(v);
}));
app.put('/admin/age/:uid', perm('age_approve'), w(notBanned), w(async (q, r) => {
  const bd = String(q.body?.birthDate || ''); if (!/^\d{4}-\d{2}-\d{2}$/.test(bd)) return r.status(400).json({ error: 'birthDate' });
  await db.ref(`ageInfo/${q.params.uid}`).set({ birthDate: bd, age: ageFrom(bd), method: 'admin', by: q.uid, at: Date.now() }); r.json({ ok: true, age: ageFrom(bd) });
}));
app.put('/idv/upload/:kind', express.raw({ type: 'image/*', limit: '4mb' }), w(notBanned), w(async (q, r) => {
  const kind = q.params.kind; if (!['id_front', 'selfie'].includes(kind)) return r.status(400).json({ error: 'kind id_front|selfie' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || buf.length < 1000) return r.status(400).json({ error: 'image required' });
  const path = `idv/${q.uid}/${kind}-${Date.now()}.jpg`;
  await admin.storage().bucket(STORAGE_BUCKET).file(path).save(buf, { contentType: 'image/jpeg', resumable: false });
  await db.ref(`idv/${q.uid}`).update({ [kind]: path, status: 'draft', updatedAt: Date.now() }); r.json({ ok: true });
}));
app.post('/idv/submit', w(notBanned), w(async (q, r) => {
  const v = (await db.ref(`idv/${q.uid}`).get()).val() || {}; if (!v.id_front || !v.selfie) return r.status(400).json({ error: 'อัปโหลดบัตรและเซลฟี่ก่อน' });
  await db.ref(`idv/${q.uid}`).update({ status: 'pending', submittedAt: Date.now() }); r.json({ status: 'pending' });
}));
app.get('/idv', w(async (q, r) => { const v = (await db.ref(`idv/${q.uid}`).get()).val() || {}; r.json({ status: v.status || 'none', reason: v.reason || '', hasId: !!v.id_front, hasSelfie: !!v.selfie }); }));
app.get('/admin/idv', perm('age_approve'), w(async (_q, r) => {
  const v = (await db.ref('idv').get()).val() || {};
  const url = async p => p ? (await admin.storage().bucket(STORAGE_BUCKET).file(p).getSignedUrl({ action: 'read', expires: Date.now() + 15 * 60e3 }).catch(() => ['']))[0] : '';
  const items = await Promise.all(Object.entries(v).filter(([, x]) => x.status === 'pending').map(async ([uid, x]) => ({ uid, submittedAt: x.submittedAt, idUrl: await url(x.id_front), selfieUrl: await url(x.selfie),
    age: (await db.ref(`ageInfo/${uid}`).get()).val(), name: (await db.ref(`users/${uid}/displayName`).get()).val() || '' })));
  r.json({ items });
}));
app.post('/admin/idv/:uid/decide', perm('age_approve'), w(notBanned), w(async (q, r) => {
  const uid = q.params.uid, v = (await db.ref(`idv/${uid}`).get()).val(); if (!v) return r.status(404).json({ error: 'not found' });
  const approve = q.body?.approve === true, reason = String(q.body?.reason || '').slice(0, 200), bd = String(q.body?.birthDate || '');
  for (const p of [v.id_front, v.selfie]) if (p) await admin.storage().bucket(STORAGE_BUCKET).file(p).delete().catch(() => {}); // auto-delete after review
  await db.ref(`idv/${uid}`).set({ status: approve ? 'approved' : 'rejected', reason, by: q.uid, at: Date.now() });
  if (approve && /^\d{4}-\d{2}-\d{2}$/.test(bd)) await db.ref(`ageInfo/${uid}`).set({ birthDate: bd, age: ageFrom(bd), method: 'id_verified', by: q.uid, at: Date.now() });
  await db.ref('adminLog').push({ action: approve ? 'idv_approve' : 'idv_reject', target: uid, by: q.uid, at: Date.now(), message: reason });
  r.json({ status: approve ? 'approved' : 'rejected' });
}));
// ======================= Withdrawals (earned diamonds only) =======================
async function payoutCfg() { return { rateTHB: 0.2, minDiamonds: 500, windowStartDay: 25, windowEndDay: 31, carryOver: true, requireIdv: false, ...((await db.ref('appConfig/payout').get()).val() || {}) }; }
app.get('/withdraw', w(async (q, r) => {
  const [cfg, wal, all] = await Promise.all([payoutCfg(), db.ref(`wallets/${q.uid}`).get(), db.ref(`withdrawalsByUser/${q.uid}`).get()]);
  const wv = wal.val() || {}, day = BKK().getUTCDate();
  const ids = Object.keys(all.val() || {}).slice(-20); const reqs = (await Promise.all(ids.map(async id => ({ id, ...((await db.ref(`withdrawals/${id}`).get()).val() || {}) })))).sort((a, b) => b.at - a.at);
  r.json({ config: cfg, earned: num(wv.earnedDiamonds), held: num(wv.heldDiamonds), diamonds: num(wv.diamonds), windowOpen: day >= cfg.windowStartDay && day <= cfg.windowEndDay, requests: reqs, idv: (await db.ref(`idv/${q.uid}/status`).get()).val() || 'none' });
}));
app.post('/withdraw', w(notBanned), w(async (q, r) => {
  const cfg = await payoutCfg(), amount = parseInt(q.body?.amount), day = BKK().getUTCDate();
  if (!(day >= cfg.windowStartDay && day <= cfg.windowEndDay)) return r.status(403).json({ error: `ถอนได้วันที่ ${cfg.windowStartDay}-${cfg.windowEndDay} ของเดือน (ยอดสะสมยกไปเดือนถัดไป)` });
  if (!(amount >= cfg.minDiamonds)) return r.status(400).json({ error: `ขั้นต่ำ ${cfg.minDiamonds} เพชร` });
  if (cfg.requireIdv && (await db.ref(`idv/${q.uid}/status`).get()).val() !== 'approved') return r.status(403).json({ code: 'idv_required', error: 'ต้องยืนยันตัวตนก่อนถอนเงิน' });
  const method = q.body?.method === 'bank' ? 'bank' : 'promptpay', acct = String(q.body?.account || '').replace(/[\s-]/g, '').slice(0, 30), name = String(q.body?.accountName || '').trim().slice(0, 80), bank = String(q.body?.bank || '').slice(0, 40);
  if (method === 'promptpay' && !/^(0\d{9}|\d{13})$/.test(acct)) return r.status(400).json({ error: 'พร้อมเพย์: เบอร์ 10 หลัก หรือเลขบัตร 13 หลัก' });
  if (method === 'bank' && (!/^\d{10,15}$/.test(acct) || !bank)) return r.status(400).json({ error: 'เลขบัญชี/ธนาคารไม่ถูกต้อง' });
  if (name.length < 2) return r.status(400).json({ error: 'กรอกชื่อบัญชี' });
  let ok = false;
  const tx = await db.ref(`wallets/${q.uid}`).transaction(wv => { if (wv === null) return wv; const e = num(wv.earnedDiamonds), d = num(wv.diamonds);
    if (e < amount || d < amount) { ok = false; return; } ok = true; return { ...wv, earnedDiamonds: e - amount, diamonds: d - amount, heldDiamonds: num(wv.heldDiamonds) + amount }; });
  if (!tx.committed || !ok) return r.status(402).json({ error: 'เพชรที่ได้รับ (ถอนได้) ไม่พอ' });
  const t = Date.now(), id = db.ref('withdrawals').push().key, risk = await riskScore(q.uid), thb = Math.round(amount * cfg.rateTHB * 100) / 100, status = risk.score >= 60 ? 'held_review' : 'pending';
  await db.ref().update({ [`withdrawals/${id}`]: { uid: q.uid, amount, thb, rateTHB: cfg.rateTHB, status, risk: risk.score, at: t }, [`withdrawalsByUser/${q.uid}/${id}`]: t,
    [`private/${q.uid}/payouts/${id}`]: { method, account: acct, accountName: name, bank }, [`payoutLedger/${id}_hold`]: { uid: q.uid, id, type: 'hold', amount, at: t } });
  r.json({ id, status, thb });
}));
app.get('/admin/withdrawals', perm('coins_confirm'), w(async (q, r) => {
  const st = q.query.status || 'pending'; const v = (await db.ref('withdrawals').get()).val() || {};
  const items = await Promise.all(Object.entries(v).filter(([, x]) => st === 'all' || x.status === st || (st === 'pending' && x.status === 'held_review')).map(async ([id, x]) => ({ id, ...x,
    payout: (await db.ref(`private/${x.uid}/payouts/${id}`).get()).val(), name: (await db.ref(`users/${x.uid}/displayName`).get()).val() || '', riskReasons: ((await db.ref(`risk/${x.uid}/reasons`).get()).val() || []) })));
  r.json({ items: items.sort((a, b) => b.at - a.at).slice(0, 100) });
}));
app.post('/admin/withdrawals/:id/decide', w(notBanned), w(async (q, r) => {
  if ((await loadRole(q.uid))?.role !== 'superadmin') return r.status(403).json({ error: 'only superadmin approves payouts' });
  const ref = db.ref(`withdrawals/${q.params.id}`), x = (await ref.get()).val(); if (!x) return r.status(404).json({ error: 'not found' });
  if (!['pending', 'held_review'].includes(x.status)) return r.status(409).json({ error: 'already decided' });
  const paid = q.body?.paid === true, refText = String(q.body?.ref || '').slice(0, 120), reason = String(q.body?.reason || '').slice(0, 200), t = Date.now();
  if (paid && refText.length < 3) return r.status(400).json({ error: 'ใส่เลขอ้างอิง/สลิปการโอน' });
  const lock = await ref.child('status').transaction(s => (s === null ? x.status : s === 'pending' || s === 'held_review' ? 'deciding' : undefined));
  if (!lock.committed || lock.snapshot.val() !== 'deciding') return r.status(409).json({ error: 'busy, retry' });
  await db.ref(`wallets/${x.uid}`).transaction(wv => { wv = wv || {}; const h = Math.max(0, num(wv.heldDiamonds) - x.amount); return paid ? { ...wv, heldDiamonds: h } : { ...wv, heldDiamonds: h, earnedDiamonds: num(wv.earnedDiamonds) + x.amount, diamonds: num(wv.diamonds) + x.amount }; });
  await db.ref().update({ [`withdrawals/${q.params.id}/status`]: paid ? 'paid' : 'rejected', [`withdrawals/${q.params.id}/ref`]: refText, [`withdrawals/${q.params.id}/reason`]: reason, [`withdrawals/${q.params.id}/decidedBy`]: q.uid, [`withdrawals/${q.params.id}/decidedAt`]: t,
    [`payoutLedger/${q.params.id}_${paid ? 'paid' : 'release'}`]: { uid: x.uid, id: q.params.id, type: paid ? 'paid' : 'release', amount: x.amount, thb: x.thb, ref: refText, by: q.uid, at: t } });
  r.json({ status: paid ? 'paid' : 'rejected' });
}));
app.put('/admin/payout-config', perm('coins_confirm'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, v = { rateTHB: Math.max(0, Math.min(100, Number(b.rateTHB) || 0)), minDiamonds: Math.max(1, parseInt(b.minDiamonds) || 100), windowStartDay: Math.max(1, Math.min(31, parseInt(b.windowStartDay) || 25)),
    windowEndDay: Math.max(1, Math.min(31, parseInt(b.windowEndDay) || 31)), carryOver: true, requireIdv: b.requireIdv !== false, by: q.uid, at: Date.now() };
  await db.ref('appConfig/payout').set(v); r.json(v);
}));
// ======================= Risk / anomaly engine (rules; no auto-ban) =======================
async function riskScore(uid) {
  const t = Date.now(), reasons = []; let score = 0; const add = (pts, why) => { score += pts; reasons.push(why); };
  const u = (await db.ref(`users/${uid}`).get()).val() || {};
  const created = num(u.createdAt); if (created && t - created < 7 * 864e5) add(20, 'บัญชีใหม่ (< 7 วัน)');
  const all = Object.values((await db.ref('giftTx').get()).val() || {}).filter(x => x.status === 'accepted');
  const got = all.filter(x => x.to === uid), sent = all.filter(x => x.from === uid);
  const bySender = {}; got.forEach(g => bySender[g.from] = (bySender[g.from] || 0) + num(g.price));
  if (Object.keys(bySender).some(s => sent.some(x => x.to === s))) add(25, 'ส่งของขวัญวนกันระหว่างบัญชีเดิม');
  const total = got.reduce((s, g) => s + num(g.price), 0), recent = got.filter(g => num(g.acceptedAt) > t - 3 * 864e5).reduce((s, g) => s + num(g.price), 0);
  if (recent > 1000 && recent / Math.max(1, total) > 0.8) add(15, 'รายได้พุ่งผิดปกติใน 3 วัน');
  if (total > 500 && Math.max(...Object.values(bySender)) / total > 0.8) add(10, 'รายได้ส่วนใหญ่มาจากผู้ส่งคนเดียว');
  const d = (await db.ref(`deviceOf/${uid}`).get()).val(); if (d) { const same = Object.keys((await db.ref(`devices/${d}`).get()).val() || {}); if (same.length > 2) add(20, `อุปกรณ์เดียวกัน ${same.length} บัญชี`); }
  const grants = Object.values((await db.ref('adminCoinLedger').orderByKey().limitToLast(300).get()).val() || {}).filter(g => g.uid === uid && g.amount > 0 && g.at > t - 3 * 864e5);
  if (grants.length) add(15, 'แอดมินเพิ่มยอดให้ภายใน 3 วัน');
  const fl = Object.values((await db.ref(`followLog/${uid}`).limitToLast(200).get()).val() || {}).filter(x => x.at > t - 864e5); if (fl.length > 60) add(10, 'กดติดตาม/เลิกติดตามรัวผิดปกติ');
  const ads = Object.values((await db.ref(`usage/${uid}`).limitToLast(7).get()).val() || {}).map(x => num(x?.ads)); if (ads.filter(a => a >= 20).length >= 3) add(10, 'ดูโฆษณาใกล้ลิมิตต่อเนื่องหลายวัน');
  const m = (await db.ref(`creatorMetrics/${uid}`).get()).val(); if (m && m.newFollowerShare > 50) add(15, 'ผู้ติดตามส่วนใหญ่เป็นบัญชีใหม่');
  const mf = Object.values((await db.ref(`modFlags/${uid}`).limitToLast(50).get()).val() || {}).filter(x => x.at > t - 7 * 864e5 && (x.flags || []).includes('minor')); if (mf.length) add(20, 'มีสัญญาณเกี่ยวกับผู้เยาว์ในข้อความ');
  score = Math.min(100, score); const v = { score, reasons, at: t };
  await db.ref(`risk/${uid}`).set(v); return v;
}
app.post('/admin/risk/:uid', perm('ban_users'), w(async (q, r) => r.json(await riskScore(q.params.uid))));
app.post('/admin/risk-scan', perm('ban_users'), w(async (_q, r) => {
  const ids = Object.keys((await db.ref('users').get()).val() || {}); let n = 0;
  for (const u of ids.slice(0, 300)) { await riskScore(u); n++; } r.json({ scanned: n });
}));
app.get('/admin/risk', perm('ban_users'), w(async (_q, r) => {
  const v = (await db.ref('risk').get()).val() || {};
  r.json({ items: (await Promise.all(Object.entries(v).filter(([, x]) => x.score > 0).map(async ([uid, x]) => ({ uid, ...x, name: (await db.ref(`users/${uid}/displayName`).get()).val() || '' })))).sort((a, b) => b.score - a.score).slice(0, 100) });
}));
app.post('/admin/risk/:uid/summary', perm('ban_users'), w(async (q, r) => {
  const v = (await db.ref(`risk/${q.params.uid}`).get()).val(); if (!v) return r.status(404).json({ error: 'scan first' });
  try {
    const res = await fetch(`https://aiplatform.googleapis.com/v1/projects/${PROJECT}/locations/global/publishers/google/models/${GEMINI_MODEL}:generateContent`, { method: 'POST', headers: { Authorization: `Bearer ${await gcpToken()}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: `สรุปให้แอดมินเป็นภาษาไทย 2-3 ประโยค ว่าบัญชีนี้มีสัญญาณเสี่ยงอะไร ห้ามตัดสินว่าผิด แนะนำสิ่งที่ควรตรวจ: คะแนน ${v.score}/100 เหตุผล: ${v.reasons.join('; ')}` }] }], generationConfig: { maxOutputTokens: 160, temperature: 0.2 } }) });
    const j = await res.json(); r.json({ summary: (j.candidates?.[0]?.content?.parts || []).map(p => p.text || '').join('').trim() });
  } catch (e) { r.status(502).json({ error: 'ai unavailable' }); }
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
  const coins = Number(v.coins) || 0, diamonds = Number(v.diamonds) || 0;
  if (coins) await db.ref(`wallets/${uid}/coins`).transaction(c => (c || 0) + coins);
  if (diamonds) await db.ref(`wallets/${uid}/diamonds`).transaction(c => (c || 0) + diamonds);
  if (diamonds) await db.ref(`wallets/${uid}/paidDiamonds`).transaction(c => num(c) + diamonds); // confirmed bank transfer = real money
  sendPush(uid, 'เติมเงินสำเร็จ ✅', [coins ? `🪙 ${coins} เหรียญ` : '', diamonds ? `💎 ${diamonds} เพชร` : ''].filter(Boolean).join(' และ ') + ' เข้ากระเป๋าแล้ว', { type: 'wallet' });
  r.json({ status: 'paid', credited: coins, creditedDiamonds: diamonds });
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
/** VIP = an active vip/{uid}. adFree (no ads) only while VIP was bought with diamonds paid for with real money (adFreeUntil);
 *  VIP from free / gifted / earned diamonds or an admin grant still sees and can watch ads. Records from before this
 *  split (no `paidSplit` flag) keep their no-ads VIP. */
async function tierOf(uid) {
  const v = (await db.ref(`vip/${uid}`).get()).val(), t = Date.now();
  if (!(v && v.until > t)) return { tier: 'free', vipUntil: null, adFree: false };
  return { tier: 'vip', vipUntil: v.until, adFree: v.paidSplit === true ? num(v.adFreeUntil) > t : true };
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
  const { tier, vipUntil, adFree } = await tierOf(uid);
  const t = L.tiers?.[tier] || {}, day = dayKey(), adMax = num(L.tiers?.[adFree ? 'vip' : 'free']?.maxAdsPerDay);
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
  return { tier, vipUntil, day, features, posting, chat, comments, ads: { watched: num(u.ads), max: adMax, perAd: L.adBonus || {}, ...(L.ads || {}), showAds: !adFree && L.ads?.adsEnabled === true, adFree,
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
  const t = Date.now(); // admin-given VIP still shows ads; a no-ads time the user already paid for is kept
  await db.ref(`vip/${uid}`).transaction(v => { const legacy = v && v.paidSplit !== true && v.until > t;
    return { until, by: q.uid, at: t, paidSplit: true, adFreeUntil: legacy ? v.until : num(v?.adFreeUntil) }; });
  r.json({ uid, vip: true, until });
}));
// ===== VIP self-purchase with diamonds (appConfig/vipPlans, admin-editable) =====
const VIP_PLANS_SEED = { enabled: true, plans: [{ id: 'd7', days: 7, price: 300 }, { id: 'd30', days: 30, price: 990 }, { id: 'd90', days: 90, price: 2490 }] };
async function vipPlans() { const v = (await db.ref('appConfig/vipPlans').get()).val(); return v && Array.isArray(v.plans) ? { enabled: v.enabled !== false, plans: v.plans } : VIP_PLANS_SEED; }
app.get('/vip/plans', w(async (q, r) => { const p = await vipPlans(), t = await tierOf(q.uid); r.json({ ...p, tier: t.tier, vipUntil: t.vipUntil }); }));
app.post('/vip/buy', w(notBanned), w(async (q, r) => {
  const cfg = await vipPlans(); if (!cfg.enabled) return r.status(403).json({ error: 'ยังไม่เปิดให้สมัคร VIP ในแอป' });
  const plan = cfg.plans.find(p => p.id === q.body?.planId); if (!plan) return r.status(404).json({ error: 'ไม่พบแพ็กเกจนี้' });
  const price = num(plan.price), days = num(plan.days); if (!(price > 0) || !(days > 0)) return r.status(400).json({ error: 'แพ็กเกจไม่ถูกต้อง' });
  const pend = await pendingSum(q.uid);
  const d = await db.ref(`wallets/${q.uid}/diamonds`).transaction(c => (c === null ? null : num(c) - pend >= price ? num(c) - price : undefined));
  if (!d.committed || d.snapshot.val() === null) return r.status(402).json({ code: 'insufficient', error: `เพชรไม่พอ (ต้องการ 💎${price})`, price });
  // paid with real-money diamonds? (paid balance is capped at the balance before this purchase, so diamonds spent elsewhere count as free ones first)
  const before = num(d.snapshot.val()) + price; let paid = false;
  await db.ref(`wallets/${q.uid}/paidDiamonds`).transaction(c => { const p = Math.min(num(c), before); paid = p >= price; return paid ? p - price : p; });
  const t = Date.now(); let until = 0, adFreeUntil = 0;
  await db.ref(`vip/${q.uid}`).transaction(v => {
    const cur = v || {}, legacy = v && v.paidSplit !== true && v.until > t; // older no-ads VIP keeps its no-ads time
    const base = cur.until > t ? cur.until : t; until = base + days * 864e5;
    const af = legacy ? cur.until : num(cur.adFreeUntil);
    adFreeUntil = paid ? (af > t ? af : t) + days * 864e5 : (af > t ? af : 0);
    return { ...cur, until, adFreeUntil, paidSplit: true, by: 'self', at: t };
  });
  await Promise.all([db.ref('wallets/_system/diamonds').transaction(c => num(c) + price),
    db.ref(`vipPurchases/${q.uid}`).push({ planId: plan.id, days, price, at: t, until, paid })]);
  r.json({ ok: true, until, adFree: adFreeUntil > t, diamonds: d.snapshot.val() });
}));
app.put('/admin/vip-plans', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {}, plans = (Array.isArray(b.plans) ? b.plans : []).slice(0, 6).map((p, i) => ({ id: /^[a-z0-9_]{1,20}$/.test(p?.id) ? p.id : `p${i}`, days: parseInt(p?.days), price: parseInt(p?.price) }));
  if (!plans.length || plans.some(p => !(p.days >= 1 && p.days <= 365) || !(p.price >= 1 && p.price <= 1e6))) return r.status(400).json({ error: 'ต้องมีอย่างน้อย 1 แพ็กเกจ: วัน 1–365, ราคา 1–1,000,000 เพชร' });
  const v = { enabled: b.enabled !== false, plans, by: q.uid, at: Date.now() };
  await db.ref('appConfig/vipPlans').set(v); r.json(v);
}));
// Ad offers the user can pick from (admin-editable). AdMob picks the creative; an offer = format + est. duration label + reward.
const COIN_AD_OFFER = { id: 'coins', title: 'รับเหรียญฟรี', adFormat: 'rewarded', estDurationLabel: '~15–30 วินาที', description: 'ดูจบรับเหรียญเข้ากระเป๋าทันที', validityHours: 0, reward: { feature: 'coins', amount: 10 }, enabled: true, order: 0 };
const SEED_AD_OFFERS = [COIN_AD_OFFER,
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
  // one-time: add the 'watch an ad → coins' offer to configs created before it existed (admin can delete/disable it after)
  if (Array.isArray(v) && !v.some(o => o?.reward?.feature === 'coins') && !(await db.ref('appConfig/adOffersCoinSeeded').get()).exists()) {
    v = [COIN_AD_OFFER, ...v]; await ref.set(v); await db.ref('appConfig/adOffersCoinSeeded').set(Date.now());
  }
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
    if (!FEATURES.includes(f) && f !== 'coins') e('reward.feature unknown (or coins)'); if (!Number.isInteger(amt) || amt < 1 || amt > 1000) e('reward.amount 1..1000');
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
  if (!FEATURES.includes(feature) && feature !== 'coins') return { status: 400, body: { error: 'bad feature' } };
  const { adFree } = await tierOf(uid);
  const max = num(L.tiers?.[adFree ? 'vip' : 'free']?.maxAdsPerDay), per = amount !== null ? num(amount) : num(L.adBonus?.[feature]), day = dayKey();
  if (txId) { const t = await db.ref(`adTx/${txId}`).transaction(c => c ? c : { uid, at: Date.now() }); if (t.snapshot.val()?.done) return { status: 200, body: { duplicate: true } }; }
  const lastRef = db.ref(`adLast/${uid}`), minGap = num(L.global?.adMinIntervalSec) * 1000;
  let tooSoon = false;
  await lastRef.transaction(c => { tooSoon = !!c && Date.now() - c < minGap; return tooSoon ? c : Date.now(); });
  if (tooSoon) return { status: 429, body: { code: 'ad_rate', error: 'wait before next ad' } };
  let over = false;
  await db.ref(`usage/${uid}/${day}/ads`).transaction(c => { c = c || 0; over = max > 0 && c >= max; return over ? c : c + 1; });
  if (max === 0 || over) return { status: 429, body: { code: 'ad_cap', limit: max, error: 'daily ad limit reached' } };
  if (feature === 'coins') {
    // 'coins' offers pay into the wallet instead of a quota
    await db.ref(`wallets/${uid}/coins`).transaction(c => num(c) + per);
    await db.ref(`adCoinLedger/${uid}`).push({ amount: per, at: Date.now(), txId: txId || '' });
  } else if (validityHours > 0) await db.ref(`quotaGrants/${uid}`).push({ feature, amount: per, until: Date.now() + validityHours * 3600000, at: Date.now() });
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
  const me = q.uid, t = Date.now(), qref = db.ref('matchQueue'), f = readFilter(q), mine = await summary(me) || {};
  const myCard = { g: mine.gender || '', age: mine.age || 0 }, ok = (card, flt) => passFilter({ gender: card?.g || '', age: card?.age || null }, flt);
  let partner = null;
  await qref.transaction(all => {
    all = all || {}; partner = null;
    for (const [uid, v] of Object.entries(all)) if (!v || t - v.ts > QUEUE_TTL_MS) delete all[uid];
    // 'ต้องการ' filter works both ways: I must want them and they must want me (entries without a filter accept anyone)
    const other = Object.keys(all).find(u => u !== me && ok(all[u].card, f) && ok(myCard, all[u].f));
    if (other) { partner = other; delete all[other]; delete all[me]; }
    else all[me] = { ts: t, card: myCard, ...(filterIsOpen(f) ? {} : { f: { g: f.g, a: f.a, b: f.b } }) };
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
// ===== Per-page UI (every page except Home's block layout): sections (hide/order/scale) + text overrides. uiPages/{page}/{current,draft,versions}
const UI_PAGES = ['global', 'home', 'discover', 'live', 'movie', 'chat', 'chatroom', 'companion', 'ads', 'profile', 'shop', 'ai', 'feed', 'wallet', 'header', 'nav'];
const PHEX = /^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/, SKEY = /^[A-Za-z0-9_-]{1,48}$/;
function validPage(p) {
  if (!p || typeof p !== 'object') return 'page object required';
  const out = { sections: {}, texts: {} };
  for (const [id, s] of Object.entries(p.sections || {}).slice(0, 80)) { if (!SKEY.test(id) || !s || typeof s !== 'object') return 'bad section ' + id;
    out.sections[id] = { hidden: s.hidden === true, order: Math.max(0, Math.min(999, parseInt(s.order) || 0)), scale: Math.max(0.5, Math.min(2, Number(s.scale) || 1)) }; }
  for (const [k, t] of Object.entries(p.texts || {}).slice(0, 400)) { if (!SKEY.test(k) || !t || typeof t !== 'object') return 'bad text ' + k;
    const o = { src: String(t.src || '').slice(0, 300) };
    if (typeof t.t === 'string') o.t = t.t.slice(0, 300);
    if (t.s != null) o.s = Math.max(6, Math.min(48, Number(t.s) || 14));
    if (t.c) { if (!PHEX.test(t.c)) return 'bad color ' + t.c; o.c = t.c; }
    if (t.w != null) o.w = Math.max(100, Math.min(900, Math.round((parseInt(t.w) || 400) / 100) * 100));
    out.texts[k] = o; }
  if (Buffer.byteLength(JSON.stringify(out)) > 120 * 1024) return 'too large';
  return out;
}
const pageOk = (q, r) => UI_PAGES.includes(q.params.page) || (r.status(404).json({ error: 'unknown page' }), false);
app.get('/ui/pages', w(async (_q, r) => {
  const v = (await db.ref('uiPages').get()).val() || {}, out = {};
  for (const p of UI_PAGES) if (v[p]?.current?.page) out[p] = v[p].current.page;
  r.json({ pages: out, list: UI_PAGES });
}));
app.get('/admin/ui/pages/:page', perm('config_edit'), w(async (q, r) => {
  if (!pageOk(q, r)) return; const v = (await db.ref(`uiPages/${q.params.page}`).get()).val() || {};
  r.json({ current: v.current?.page || null, draft: v.draft?.page || null,
    versions: Object.entries(v.versions || {}).map(([id, x]) => ({ id, savedAt: x.savedAt, savedBy: x.savedBy })).sort((a, b) => b.savedAt - a.savedAt) });
}));
app.put('/admin/ui/pages/:page', perm('config_edit'), w(notBanned), w(async (q, r) => {
  if (!pageOk(q, r)) return; const pg = validPage(q.body?.page); if (typeof pg === 'string') return r.status(400).json({ error: pg });
  const t = Date.now(), base = db.ref(`uiPages/${q.params.page}`);
  if (q.body?.publish !== true) { await base.child('draft').set({ page: pg, savedAt: t, savedBy: q.uid }); return r.json({ saved: 'draft' }); }
  const id = String(t);
  await base.update({ current: { page: pg, publishedAt: t, publishedBy: q.uid, versionId: id }, [`versions/${id}`]: { page: pg, savedAt: t, savedBy: q.uid }, draft: null });
  const vs = Object.keys((await base.child('versions').get()).val() || {}).sort();
  if (vs.length > 10) await Promise.all(vs.slice(0, vs.length - 10).map(k => base.child(`versions/${k}`).remove()));
  await db.ref('adminLog').push({ action: 'ui_page_publish', target: q.params.page, by: q.uid, at: t });
  r.json({ published: id });
}));
app.post('/admin/ui/pages/:page/revert', perm('config_edit'), w(notBanned), w(async (q, r) => {
  if (!pageOk(q, r)) return; const vid = String(q.body?.versionId || ''); if (!/^\d{1,20}$/.test(vid)) return r.status(400).json({ error: 'versionId' });
  const base = db.ref(`uiPages/${q.params.page}`), v = (await base.child(`versions/${vid}`).get()).val(); if (!v) return r.status(404).json({ error: 'version not found' });
  await base.update({ current: { page: v.page, publishedAt: Date.now(), publishedBy: q.uid, versionId: vid, revertedFrom: vid }, draft: null }); r.json({ reverted: vid });
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
/** Everyone's presence (status/{uid} {online, hidden, lastSeen}) as a snapshot-like object; read whole and filtered here,
 * so no '.indexOn: online' rule is needed (a get() query on an unindexed child fails in RTDB). */
async function onlineStatus() {
  const v = (await db.ref('status').get()).val() || {};
  const list = Object.entries(v).filter(([, x]) => x && x.online === true);
  return { forEach: fn => { list.forEach(([key, val]) => fn({ key, val: () => val })); }, val: () => Object.fromEntries(list) };
}
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
// Gender: profile setting (male/female/other) or, if unset, the onboarding answer (ชาย/หญิง/LGBTQ+).
const GENDER_TH = { 'ชาย': 'male', 'หญิง': 'female', 'LGBTQ+': 'other' };
const genderOf = u => u?.gender || GENDER_TH[u?.profileDetails?.gender] || '';
const publicProfile = (uid, u) => ({ uid, displayName: u.displayName || '', username: u.username || '', avatar: u.avatar || '', bio: u.bio || '', age: u.age || null, gender: genderOf(u), interests: u.interests || [],
  cover: u.cover || '', province: u.province || '', occupation: u.occupation || '', lookingFor: Array.isArray(u.lookingFor) ? u.lookingFor : [] });
app.get('/swipe/candidates', w(notBanned), w(async (q, r) => {
  const limit = Math.min(20, Math.max(1, parseInt(q.query.limit || '10', 10)));
  // Only people online right now (presence status/{uid}, not hidden) — the swipe page is a live 'who is here' deck.
  const [users, swiped, bans, st] = await Promise.all([db.ref('users').get(), db.ref(`swipes/${q.uid}`).get(), db.ref('bans').get(), onlineStatus()]);
  const s = swiped.val() || {}, b = bans.val() || {}, now = Date.now(), online = new Set();
  st.forEach(c => { const v = c.val(); if (v && v.online === true && v.hidden !== true) online.add(c.key); });
  const icfg = await getInterestCfg(), mineIp = (users.val() || {})[q.uid]?.interestProfile || {};
  // Distance only between people who both opened the radar (their own consent) in the last 24 h, rounded like the radar.
  const [meR, radarAll] = await Promise.all([db.ref(`radar/${q.uid}`).get(), db.ref('radar').get()]);
  const me = meR.val(), rad = radarAll.val() || {}, LOC_MS = 24 * 3600 * 1000, RECYCLE_MS = 24 * 3600 * 1000, sf = readFilter(q);
  const out = [];
  for (const [uid, u] of Object.entries(users.val() || {})) {
    if (uid === q.uid || !u || !u.displayName || !online.has(uid)) continue;
    // Likes stay out; people passed more than a day ago come back (small user base at launch).
    const sw = s[uid]; if (sw && (sw.action !== 'pass' || now - num(sw.at) < RECYCLE_MS)) continue;
    const ban = b[uid]; if (ban && (!ban.until || ban.until > now)) continue;
    if (await blockedEither(q.uid, uid)) continue;
    const rv = rad[uid], km = me && rv && now - num(rv.at) < LOC_MS && validLatLng(rv.lat, rv.lng) ? distKm(me.lat, me.lng, rv.lat, rv.lng) : null;
    if (!passFilter(publicProfile(uid, u), sf)) continue;
    if (sf.km && me && (km == null || km > sf.km)) continue;
    out.push({ ...publicProfile(uid, u), interestProfile: u.interestProfile || {}, similarity: similarity(mineIp, u.interestProfile || {}, icfg),
      distance: km == null ? '' : roundDist(km), _km: km == null ? Infinity : km, _seen: !!sw });
  }
  // New faces first, then nearest, then most similar.
  out.sort((x, y) => (x._seen - y._seen) || (x._km - y._km) || (y.similarity.score - x.similarity.score));
  const top = out.slice(0, limit);
  const cards = await Promise.all(top.map(x => db.ref(`randomCards/${x.uid}`).get().then(c => rcPublic(c.val())).catch(() => null)));
  r.json({ candidates: top.map(({ _km, _seen, ...x }, i) => ({ ...x, online: true, card: cards[i] })) });
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
  if (!already) { sendPush(targetUid, 'แมตช์ใหม่! 💞', `คุณกับ ${await nameOf(q.uid)} แมตช์กันแล้ว`, { type: 'chat', chatId }); sendPush(q.uid, 'แมตช์ใหม่! 💞', `คุณกับ ${await nameOf(targetUid)} แมตช์กันแล้ว`, { type: 'chat', chatId }); }
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
async function postImageUrl(uid, path) { return postMediaUrl(uid, path, 'image/', 10 * 1024 * 1024); }
const POST_VIDEO_MAX = 50 * 1024 * 1024;
/** Download-token URL for a file the caller uploaded under posts/{uid}/ (type and size checked from Storage metadata). */
async function postMediaUrl(uid, path, typePrefix, maxBytes) {
  if (typeof path !== 'string' || !path.startsWith(`posts/${uid}/`) || path.includes('..') || path.length > 300) return null;
  const f = admin.storage().bucket(STORAGE_BUCKET).file(path);
  const [m] = await f.getMetadata().catch(() => [null]);
  if (!m || !String(m.contentType || '').startsWith(typePrefix) || Number(m.size) > maxBytes) return null;
  let tok = (m.metadata?.firebaseStorageDownloadTokens || '').split(',')[0];
  if (!tok) { tok = crypto.randomUUID(); await f.setMetadata({ metadata: { firebaseStorageDownloadTokens: tok } }); }
  return `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${tok}`;
}
/** Posts / comments store the author's name + avatar at the time of writing; show the current ones (profile photo changed later). */
async function freshAuthors(items) {
  const ids = [...new Set(items.map(x => x && x.authorId).filter(x => typeof x === 'string' && UID_RE.test(x)))];
  const info = new Map(await Promise.all(ids.map(async id => [id, await authorInfo(id)])));
  for (const x of items) { const a = x && info.get(x.authorId); if (a) { x.authorName = a.authorName; x.authorAvatar = a.authorAvatar; } }
}
async function authorInfo(uid) {
  const u = (await db.ref(`users/${uid}`).get()).val() || {};
  return { authorName: String(u.displayName || 'ผู้ใช้').slice(0, 50), authorAvatar: String(u.avatar || '').slice(0, 500) };
}
// ===== My profile: details + avatar / cover photo (images uploaded by the app under posts/{uid}/, checked and moderated here) =====
const LOOKING_FOR = ['friends', 'chat', 'dating', 'travel', 'gaming', 'study', 'work'];
app.put('/me/profile', w(notBanned), w(async (q, r) => {
  const b = q.body || {}, upd = {};
  if (b.displayName !== undefined) { const n = cleanText(b.displayName, 50).replace(/[\u0000-\u001f]/g, ''); if (!n) return r.status(400).json({ error: 'กรุณาใส่ชื่อที่แสดง' }); upd.displayName = n; }
  if (b.bio !== undefined) upd.bio = cleanText(b.bio, 300);
  if (b.gender !== undefined) { if (!['', 'male', 'female', 'other'].includes(b.gender)) return r.status(400).json({ error: 'bad gender' }); upd.gender = b.gender; }
  if (b.province !== undefined) upd.province = cleanText(b.province, 60);
  if (b.occupation !== undefined) upd.occupation = cleanText(b.occupation, 60);
  if (b.lookingFor !== undefined) upd.lookingFor = (Array.isArray(b.lookingFor) ? b.lookingFor : []).filter(x => LOOKING_FOR.includes(x)).slice(0, LOOKING_FOR.length);
  if (!Object.keys(upd).length) return r.status(400).json({ error: 'nothing to update' });
  { const m = await moderateText(q.uid, [upd.displayName, upd.bio, upd.occupation].filter(Boolean).join(' '), { kind: 'profile' }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
  upd.lastActive = Date.now();
  await db.ref(`users/${q.uid}`).update(upd);
  r.json(publicProfile(q.uid, (await db.ref(`users/${q.uid}`).get()).val() || {}));
}));
app.put('/me/photo', w(notBanned), w(async (q, r) => {
  const kind = q.body?.kind, ref = q.body?.ref;
  if (!['avatar', 'cover'].includes(kind)) return r.status(400).json({ error: 'kind must be avatar or cover' });
  if (ref === null || ref === '') { await db.ref(`users/${q.uid}/${kind}`).set(''); return r.json({ kind, url: '' }); }
  const url = await postImageUrl(q.uid, ref); if (!url) return r.status(400).json({ error: 'invalid image' });
  const mi = await moderateImage(q.uid, ref, { kind: `profile_${kind}` }); if (!mi.ok) return r.status(422).json({ code: 'moderated', error: 'รูปภาพนี้ขัดกติกาความปลอดภัย' });
  await db.ref(`users/${q.uid}/${kind}`).set(url);
  r.json({ kind, url });
}));
app.post('/posts', w(notBanned), w(async (q, r) => {
  { const pf = await postingFor(q.uid, (await tierOf(q.uid)).tier, await getLimits()); if (!pf.canPost) return r.status(403).json({ code: 'posting_disabled', error: pf.reason }); }
  const text = cleanText(q.body?.text, 2000);
  const refs = Array.isArray(q.body?.imageRefs) ? q.body.imageRefs : [];
  if (refs.length > 4) return r.status(400).json({ error: 'max 4 images' });
  const images = [];
  for (const p of refs) { const u = await postImageUrl(q.uid, p); if (!u) return r.status(400).json({ error: 'invalid image' }); images.push({ path: p, url: u }); }
  let video = null;
  if (q.body?.videoRef !== undefined && q.body?.videoRef !== null) {
    const vu = await postMediaUrl(q.uid, q.body.videoRef, 'video/', POST_VIDEO_MAX);
    if (!vu) return r.status(400).json({ error: 'invalid video' });
    video = { path: q.body.videoRef, url: vu };
    if (q.body.posterRef) { const pu = await postImageUrl(q.uid, q.body.posterRef); if (!pu) return r.status(400).json({ error: 'invalid video cover' }); video.posterPath = q.body.posterRef; video.posterUrl = pu; }
  }
  if (!text && !images.length && !video) return r.status(400).json({ error: 'text, image or video required' });
  // Feeling / activity ("รู้สึก 😊 มีความสุข", "🎬 กำลังดูหนัง"): short label + one emoji, moderated with the text.
  const fb = q.body?.feeling, feeling = fb && ['feeling', 'activity'].includes(fb.kind) && cleanText(fb.label, 40)
    ? { kind: fb.kind, emoji: String(fb.emoji || '').slice(0, 8), label: cleanText(fb.label, 40).replace(/[\u0000-\u001f]/g, '') } : null;
  { const m = await moderateText(q.uid, [text, cleanText(q.body?.place, 80), feeling?.label || ''].join(' '), { kind: 'post' }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
  for (const im of images) { const mi = await moderateImage(q.uid, im.path, { kind: 'post_image' }); if (!mi.ok) return r.status(422).json({ code: 'moderated', error: 'รูปภาพนี้ขัดกติกาความปลอดภัย' }); }
  if (video?.posterPath) { const mv = await moderateImage(q.uid, video.posterPath, { kind: 'post_video_cover' }); if (!mv.ok) return r.status(422).json({ code: 'moderated', error: 'วิดีโอนี้ขัดกติกาความปลอดภัย' }); }
  const hashtags = parseHashtags(text);
  const label = cleanText(q.body?.place, 80).replace(/[\u0000-\u001f]/g, '');
  let place = null;
  if (label) {
    place = { label };
    const { lat, lng } = q.body || {};
    if (validLatLng(lat, lng)) {
      place.lat = Math.round(lat * 100) / 100; place.lng = Math.round(lng * 100) / 100; // ~1 km
      if (q.body?.shareCoords === true) place.share = true; // author agreed to show the ~1 km point to viewers
    }
  }
  const ref = db.ref('posts').push();
  const visibility = VIS.includes(q.body?.visibility) ? q.body.visibility : 'public';
  const post = { authorId: q.uid, ...(await authorInfo(q.uid)), text, images, ...(video ? { video } : {}), hashtags, place, ...(feeling ? { feeling } : {}), visibility, createdAt: Date.now(), likeCount: 0, commentCount: 0 };
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
  for (const im of post.images || []) await admin.storage().bucket(STORAGE_BUCKET).file(im.path).delete().catch(() => {}); for (const vp of [post.video?.path, post.video?.posterPath].filter(Boolean)) await admin.storage().bucket(STORAGE_BUCKET).file(vp).delete().catch(() => {});
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
  posts.forEach(p => { p.commentsOff = p.commentsOff === true; p.visibility = p.visibility || 'public'; if (p.authorId !== q.uid && p.place?.share !== true) delete p.place?.lat, delete p.place?.lng; });
  await freshAuthors(posts);
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
  { const m = await moderateText(q.uid, text, { kind: 'comment', ref: q.params.id }); if (!m.ok) return r.status(422).json({ code: 'moderated', error: m.message }); }
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
  await freshAuthors(comments);
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
// ===== Shared 'ต้องการ' filter (หาเพื่อน): gender, age range, distance. Sent by the app as query params on
// /discover/online, /match, /shake, /radar/nearby, /swipe/candidates (fGender=male|female|other, fAgeMin, fAgeMax, fKm).
function readFilter(q) {
  const src = q.query || {};
  const g = ['male', 'female', 'other'].includes(src.fGender) ? src.fGender : '';
  let a = parseInt(src.fAgeMin, 10), b = parseInt(src.fAgeMax, 10);
  a = Number.isFinite(a) ? Math.min(99, Math.max(18, a)) : 18; b = Number.isFinite(b) ? Math.min(99, Math.max(a, b)) : 99;
  const km = Number(src.fKm);
  return { g, a, b, km: RADII.includes(km) ? km : 0 };
}
const filterIsOpen = f => !f || (!f.g && f.a <= 18 && f.b >= 99);
// Unknown age only passes when no age range is set (we never guess someone's age).
const passFilter = (p, f) => !f || ((!f.g || p.gender === f.g) && ((f.a <= 18 && f.b >= 99) || (p.age && p.age >= f.a && p.age <= f.b)));
const shuffle = a => { for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
// All online users (presence status/{uid}.online, not hidden), random order, with age + rounded distance when both shared location.
app.get('/discover/online', w(notBanned), w(async (q, r) => {
  const f = readFilter(q), now = Date.now(), LOC_MS = 24 * 3600 * 1000;
  const [st, meR, rad] = await Promise.all([onlineStatus(), db.ref(`radar/${q.uid}`).get(), db.ref('radar').get()]);
  const me = meR.val(), radar = rad.val() || {};
  const ids = []; st.forEach(c => { const v = c.val(); if (c.key !== q.uid && v && v.online === true && v.hidden !== true) ids.push(c.key); });
  const out = [];
  await Promise.all(shuffle(ids).slice(0, 400).map(async id => {
    if (await isBanned(id) || await blockedEither(q.uid, id)) return;
    const p = await summary(id); if (!p || !p.displayName || !passFilter(p, f)) return;
    const rv = radar[id], km = me && rv && now - num(rv.at) < LOC_MS && validLatLng(rv.lat, rv.lng) ? distKm(me.lat, me.lng, rv.lat, rv.lng) : null;
    if (f.km && me && (km == null || km > f.km)) return; // distance filter only when I shared my location
    out.push({ u: { ...p, distance: km == null ? '' : roundDist(km), online: true }, km });
  }));
  // nearest first; people without a known distance follow in random order (exact km never leaves the server)
  const sorted = shuffle(out).sort((a, b) => (a.km == null ? Infinity : a.km) - (b.km == null ? Infinity : b.km));
  r.json({ users: sorted.slice(0, 200).map(x => x.u), hasLocation: !!me });
}));
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
    sendPush(to, 'เป็นเพื่อนกันแล้ว 🎉', `คุณกับ ${await nameOf(q.uid)} เป็นเพื่อนกันแล้ว`, { type: 'chat', chatId });
    return r.json({ status: 'friends', chatId });
  }
  const sim = similarity(await ipOf(q.uid), await ipOf(to), await getInterestCfg());
  await db.ref(`friendRequests/${to}/${q.uid}`).set({ at: Date.now(), similarity: sim.percent, shared: sim.shared });
  sendPush(to, 'คำขอเป็นเพื่อนใหม่', `${await nameOf(q.uid)} ขอเป็นเพื่อนกับคุณ`, { type: 'friend_request', uid: q.uid });
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
  sendPush(from, 'ตอบรับคำขอเป็นเพื่อนแล้ว 🎉', `${await nameOf(q.uid)} ตอบรับคำขอเป็นเพื่อนของคุณ`, { type: 'chat', chatId });
  r.json({ status: 'friends', chatId });
}));
app.get('/friends', w(async (q, r) => {
  const ids = Object.keys((await db.ref(`friends/${q.uid}`).get()).val() || {}).slice(0, 500);
  const [profiles, st] = await Promise.all([Promise.all(ids.map(summary)), Promise.all(ids.map(id => db.ref(`status/${id}`).get()))]);
  r.json({ friends: profiles.map((p, i) => p && { ...p, online: (s => !!s && s.online === true && s.hidden !== true)(st[i].val()) }).filter(Boolean) });
}));
// Friend state with one person, for the 'เพิ่มเพื่อน' button: friends / requested (I asked) / incoming (they asked me) / none.
app.get('/friends/status/:uid', w(async (q, r) => {
  const o = q.params.uid; if (!UID_RE.test(o)) return r.status(400).json({ error: 'bad uid' });
  if (o === q.uid) return r.json({ status: 'self' });
  const [f, out, inc] = await Promise.all([db.ref(`friends/${q.uid}/${o}`).get(), db.ref(`friendRequests/${o}/${q.uid}`).get(), db.ref(`friendRequests/${q.uid}/${o}`).get()]);
  r.json({ status: f.exists() ? 'friends' : out.exists() ? 'requested' : inc.exists() ? 'incoming' : 'none' });
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
  const f = readFilter(q), radius = f.km || pickRadius(q.query.radiusKm);
  const list = await nearbyFor(q.uid, radius);
  if (!list) return r.status(409).json({ error: 'location not shared' });
  r.json({ radiusKm: radius, users: list.filter(p => passFilter(p, f)).map(({ _km, ...x }) => x) });
}));
// --- shake (15 s window)
const SHAKE_MS = 15000;

app.post('/shake', w(notBanned), w(quota('shakePerDay')), w(async (q, r) => {
  const { lat, lng } = q.body || {};
  const f = readFilter(q), radiusKm = f.km || pickRadius(q.body?.radiusKm);
  const hasLoc = validLatLng(lat, lng);
  const la = hasLoc ? coarse(lat) : null, lo = hasLoc ? coarse(lng) : null;
  const mine = await summary(q.uid) || {}, myCard = { g: mine.gender || '', age: mine.age || 0 };
  const ok = (card, flt) => passFilter({ gender: card?.g || '', age: card?.age || null }, flt);
  const now = Date.now(); let partner = null;
  await db.ref(`shakeResults/${q.uid}`).remove();
  await db.ref('shakeWaiting').transaction(cur => {
    const w8 = {}; partner = null;
    for (const [k, v] of Object.entries(cur || {})) if (v && now - v.at < SHAKE_MS && k !== q.uid) w8[k] = v;
    // With location: only users that also shared location and are within BOTH users' radius (nearest first).
    // Without location: only other users without location ("anywhere" mode). The 'ต้องการ' filter must pass both ways.
    const ids = Object.keys(w8).filter(k => (hasLoc ? (w8[k].lat != null && distKm(la, lo, w8[k].lat, w8[k].lng) <= Math.min(radiusKm, w8[k].radiusKm || 0.5)) : w8[k].lat == null)
      && ok(w8[k].card, f) && ok(myCard, w8[k].f));
    if (hasLoc) ids.sort((a, b) => distKm(la, lo, w8[a].lat, w8[a].lng) - distKm(la, lo, w8[b].lat, w8[b].lng));
    partner = ids[0] || null;
    if (partner) { delete w8[partner]; return w8; }
    w8[q.uid] = { at: now, card: myCard, ...(hasLoc ? { lat: la, lng: lo, radiusKm } : {}), ...(filterIsOpen(f) ? {} : { f: { g: f.g, a: f.a, b: f.b } }) }; return w8;
  });
  if (partner && (await isBanned(partner) || await blockedEither(q.uid, partner))) partner = null;
  if (!partner) return r.json({ status: 'waiting', windowMs: SHAKE_MS, mode: hasLoc ? 'nearby' : 'anywhere', radiusKm });
  // Matched → anonymous 60-second room; real names/photos stay hidden until both press the heart.
  const room = await openAnonRoom(q.uid, partner);
  await db.ref().update({ [`shakeResults/${q.uid}`]: { other: partner, room, at: now }, [`shakeResults/${partner}`]: { other: q.uid, room, at: now } });
  r.json({ status: 'matched', room });
}));
app.get('/shake/result', w(async (q, r) => {
  const res = (await db.ref(`shakeResults/${q.uid}`).get()).val();
  if (res && Date.now() - res.at < 60000) return r.json(res.room ? { status: 'matched', room: res.room } : { status: 'matched', user: await summary(res.other) });
  const wv = (await db.ref(`shakeWaiting/${q.uid}`).get()).val();
  if (wv && Date.now() - wv.at < SHAKE_MS) return r.json({ status: 'waiting' });
  if (wv) await db.ref(`shakeWaiting/${q.uid}`).remove();
  r.json({ status: 'timeout' });
}));
// ===== Bell: one list of everything for me, newest first (friend requests with accept/decline, likes, comments,
// new followers, matches). unseen = items newer than notifSeen/{uid} (+ every open friend request).
app.get('/notifications', w(async (q, r) => {
  const me = q.uid, val = async p => (await db.ref(p).get()).val() || {};
  const [reqs, follows, matches, myPosts, seenAt] = await Promise.all([val(`friendRequests/${me}`), val(`followers/${me}`), val(`matchNotifications/${me}`),
    db.ref(`authorPosts/${me}`).orderByValue().limitToLast(15).get().then(x => x.val() || {}), db.ref(`notifSeen/${me}`).get().then(x => num(x.val()))]);
  const items = [];
  for (const [uid, v] of Object.entries(reqs)) items.push({ type: 'friend_request', uid, at: num(v?.at) });
  for (const [uid, at] of Object.entries(follows)) if (uid !== me) items.push({ type: 'follow', uid, at: num(at) });
  for (const [id, m] of Object.entries(matches)) if (m?.otherUid) items.push({ type: 'match', uid: m.otherUid, at: num(m.at), chatId: m.chatId || '', id });
  await Promise.all(Object.keys(myPosts).map(async pid => {
    const [likes, comments, post] = await Promise.all([val(`postLikes/${pid}`), db.ref(`postComments/${pid}`).orderByKey().limitToLast(20).get().then(x => x.val() || {}), db.ref(`posts/${pid}/text`).get().then(x => String(x.val() || ''))]);
    const snip = post.slice(0, 40);
    for (const [uid, at] of Object.entries(likes)) if (uid !== me) items.push({ type: 'like', uid, at: num(at), postId: pid, snippet: snip });
    for (const [cid, c] of Object.entries(comments)) if (c?.authorId && c.authorId !== me) items.push({ type: 'comment', uid: c.authorId, at: num(c.createdAt), postId: pid, snippet: String(c.text || '').slice(0, 60), id: cid });
  }));
  items.sort((x, y) => y.at - x.at);
  const top = items.slice(0, 80), names = new Map();
  await Promise.all([...new Set(top.map(i => i.uid))].map(async uid => { const p = await summary(uid); if (p) names.set(uid, { name: p.displayName || 'ผู้ใช้', avatar: p.avatar || '' }); }));
  const out = top.filter(i => names.has(i.uid)).map(i => ({ ...i, ...names.get(i.uid), unseen: i.type === 'friend_request' || i.at > seenAt }));
  r.json({ items: out, unseen: out.filter(i => i.unseen).length, requests: Object.keys(reqs).length, seenAt });
}));
app.post('/notifications/seen', w(async (q, r) => { await db.ref(`notifSeen/${q.uid}`).set(Date.now()); r.json({ ok: true }); }));
// ===== สุ่มหาเพื่อน card (VIP decorated intro card) =====
// Card: randomCards/{uid} (server-only). Prices: appConfig/randomCardCatalog (admin, live — no APK update needed).
// Free: profile/background images, intro text, positions. Paid (diamonds): frame, name frame, effect, intro video, voice greeting —
// charged EVERY time the chosen paid item changes (switching back to an item used before is charged again).
const RC_KINDS = ['frames', 'nameFrames', 'effects'];
const RC_SEED = {
  enabled: true, videoPrice: 80, audioPrice: 40, customFramePrice: 0, customNameFramePrice: 0, customEffectPrice: 0,
  frames: { gold: { name: 'ทอง', price: 30, colors: ['#FFD700', '#B8860B', '#FFF3B0'] }, diamond: { name: 'เพชร', price: 50, colors: ['#B9F2FF', '#FFFFFF', '#5CC8E8'] },
    neon: { name: 'นีออน', price: 40, colors: ['#00E5FF', '#FF00C8', '#00E5FF'] }, rose: { name: 'กุหลาบ', price: 30, colors: ['#FFB3C7', '#E8A598', '#FF6F91'] },
    rainbow: { name: 'สายรุ้ง', price: 60, colors: ['#FF5F6D', '#FFC371', '#47E891', '#4FACFE', '#B06AB3'] } },
  nameFrames: { gold: { name: 'ป้ายทอง', price: 20, colors: ['#FFD700', '#B8860B'], style: 'royal' }, neon: { name: 'ป้ายนีออน', price: 25, colors: ['#00E5FF', '#FF00C8'], style: 'neon' },
    rose: { name: 'ป้ายชมพู', price: 20, colors: ['#FF6FD8', '#FF9A8B'], style: 'pill' }, galaxy: { name: 'ป้ายกาแล็กซี', price: 35, colors: ['#3A1C71', '#D76D77', '#FFAF7B'], style: 'glitter' } },
  effects: { hearts: { name: 'หัวใจลอย', price: 30, glyphs: ['💗', '💖'], motion: 'rise' }, stars: { name: 'ดาวระยิบ', price: 30, glyphs: ['⭐', '✨'], motion: 'twinkle' },
    sparkle: { name: 'ประกายวิ้ง', price: 40, glyphs: ['✨'], motion: 'burst' }, petals: { name: 'กลีบซากุระ', price: 30, glyphs: ['🌸', '🌺'], motion: 'fall' },
    confetti: { name: 'พลุฉลอง', price: 50, glyphs: ['🎉', '🎊', '✦'], motion: 'rain' }, fireflies: { name: 'หิ่งห้อย', price: 40, glyphs: ['•'], motion: 'sway' } }
};
// v2: more name-frame shapes and effect movements (added once to the live catalog; admin edits stay)
const RC_STYLES = ['pill', 'neon', 'ribbon', 'bubble', 'badge', 'glitter', 'outline', 'royal', 'cloud', 'tag'];
const RC_MOTIONS = ['rise', 'fall', 'orbit', 'twinkle', 'burst', 'sway', 'spiral', 'rain', 'zigzag', 'shoot'];
const RC_V2 = {
  nameFrames: { ribbon: { name: 'ริบบิ้น', price: 25, colors: ['#FF5F8F', '#FF9AB8'], style: 'ribbon' }, bubble: { name: 'บับเบิลแชท', price: 20, colors: ['#7AF5FF', '#4FACFE'], style: 'bubble' },
    badge: { name: 'เหรียญตรา', price: 30, colors: ['#9B3DFF', '#FF2FA8'], style: 'badge' }, outline: { name: 'เส้นคู่', price: 15, colors: ['#FFFFFF', '#FFD45C'], style: 'outline' },
    cloud: { name: 'ก้อนเมฆ', price: 25, colors: ['#E0F2FF', '#B9DCFF'], style: 'cloud' }, tag: { name: 'ป้ายแท็ก', price: 20, colors: ['#47E891', '#00B8D9'], style: 'tag' },
    sunset: { name: 'พระอาทิตย์ตก', price: 30, colors: ['#FF5F6D', '#FFC371'], style: 'glitter' }, mint: { name: 'มินต์นีออน', price: 25, colors: ['#00FFB3', '#00C2FF'], style: 'neon' } },
  effects: { snow: { name: 'หิมะโปรย', price: 30, glyphs: ['❄️', '❅', '·'], motion: 'fall' }, bubbles: { name: 'ฟองสบู่', price: 30, glyphs: ['🫧', '○'], motion: 'sway' },
    orbit: { name: 'หัวใจโคจร', price: 45, glyphs: ['💞', '💗'], motion: 'orbit' }, fireworks: { name: 'พลุระเบิด', price: 50, glyphs: ['🎆', '🎇', '✨'], motion: 'burst' },
    butterflies: { name: 'ผีเสื้อบิน', price: 40, glyphs: ['🦋'], motion: 'zigzag' }, shooting: { name: 'ดาวตก', price: 45, glyphs: ['🌠', '✦'], motion: 'shoot' },
    music: { name: 'โน้ตดนตรีหมุน', price: 35, glyphs: ['🎵', '🎶'], motion: 'spiral' }, money: { name: 'ฝนเพชร', price: 60, glyphs: ['💎', '💰'], motion: 'rain' } }
};
(async () => {
  try {
    await db.ref('appConfig/randomCardCatalog').transaction(c => (c === null ? RC_SEED : undefined));
    const flag = await db.ref('appConfig/rcCatalogV2').transaction(c => (c ? undefined : { at: Date.now() }));
    if (flag.committed) await db.ref('appConfig/randomCardCatalog').transaction(c => {
      if (!c) return c;
      for (const kind of ['nameFrames', 'effects']) {
        c[kind] = c[kind] || {};
        const key = kind === 'effects' ? 'motion' : 'style';
        for (const [id, v] of Object.entries(c[kind])) if (v && !v[key]) v[key] = RC_SEED[kind][id]?.[key] || (kind === 'effects' ? 'rise' : 'pill');
        let order = Object.keys(c[kind]).length;
        for (const [id, v] of Object.entries(RC_V2[kind])) if (!c[kind][id]) c[kind][id] = { ...v, enabled: true, order: order++ };
      }
      return c;
    });
  } catch (e) { console.error('seed rc', e.message); }
})();
async function rcCatalog() { const c = (await db.ref('appConfig/randomCardCatalog').get()).val() || RC_SEED; return { ...RC_SEED, ...c, frames: c.frames || {}, nameFrames: c.nameFrames || {}, effects: c.effects || {} }; }
const rcItem = (cat, kind, id) => (id && id !== 'none' ? cat[kind]?.[id] : null);
const clamp01 = v => Math.min(1, Math.max(0, Number(v) || 0));
function rcLayout(l) {
  const one = (o, dx, dy) => ({ x: o && Number.isFinite(+o.x) ? clamp01(o.x) : dx, y: o && Number.isFinite(+o.y) ? clamp01(o.y) : dy, s: Math.min(2.2, Math.max(0.5, Number(o?.s) || 1)) });
  return { avatar: one(l?.avatar, 0.5, 0.36), name: one(l?.name, 0.5, 0.62), intro: one(l?.intro, 0.5, 0.78) };
}
/** What the draft costs against the saved card. Unknown/disabled items → error (unless unchanged). */
// own uploads: draft ref field, saved path field, price field in the catalog, saved URL field
const RC_CUSTOM = { frame: ['frameRef', 'frameImagePath', 'customFramePrice', 'frameImageUrl'], nameFrame: ['nameFrameRef', 'nameFrameImagePath', 'customNameFramePrice', 'nameFrameImageUrl'],
  effect: ['effectRef', 'effectImagePath', 'customEffectPrice', 'effectImageUrl'] };
async function rcQuote(uid, d) {
  const cat = await rcCatalog(), saved = (await db.ref(`randomCards/${uid}`).get()).val() || {};
  const items = []; let error = '';
  for (const [kind, key, label] of [['frames', 'frame', 'กรอบรูป'], ['nameFrames', 'nameFrame', 'กรอบป้ายชื่อ'], ['effects', 'effect', 'เอฟเฟกต์']]) {
    const want = typeof d?.[key] === 'string' ? d[key] : 'none', have = saved[key] || 'none';
    if (want === 'custom') { // the owner's own uploaded picture: charged (admin price, default free) whenever a new file is used
      const [refKey, pathKey, priceKey] = RC_CUSTOM[key], ref = d?.[refKey];
      if (typeof ref === 'string' && ref && !(have === 'custom' && ref === saved[pathKey])) items.push({ kind: key, id: 'custom', name: `${label}: อัปโหลดเอง`, price: Math.max(0, num(cat[priceKey])) });
      else if (have !== 'custom') error = `${label}: ยังไม่ได้อัปโหลดรูป`;
      continue;
    }
    if (want === have || want === 'none') continue;
    const it = rcItem(cat, kind, want);
    if (!it || it.enabled === false) { error = `${label}นี้ปิดใช้งานแล้ว`; continue; }
    items.push({ kind: key, id: want, name: `${label}: ${it.name}`, price: Math.max(0, num(it.price)) });
  }
  if (typeof d?.videoRef === 'string' && d.videoRef && d.videoRef !== saved.videoPath) items.push({ kind: 'video', id: 'video', name: 'วิดีโอแนะนำตัว', price: Math.max(0, num(cat.videoPrice)) });
  if (typeof d?.audioRef === 'string' && d.audioRef && d.audioRef !== saved.audioPath) items.push({ kind: 'audio', id: 'audio', name: 'เสียงทักทาย', price: Math.max(0, num(cat.audioPrice)) });
  return { items, total: items.reduce((a, x) => a + x.price, 0), error, cat, saved };
}
/** Public view of a card (style snapshots saved with it, so it still renders if the admin later removes an item). */
const rcPublic = c => c ? { avatarUrl: c.avatarUrl || '', bgUrl: c.bgUrl || '', intro: c.intro || '', videoUrl: c.videoUrl || '', audioUrl: c.audioUrl || '',
  frame: c.frame || 'none', frameColors: c.frameColors || [], nameFrame: c.nameFrame || 'none', nameFrameColors: c.nameFrameColors || [],
  effect: c.effect || 'none', effectGlyphs: c.effectGlyphs || [], layout: rcLayout(c.layout), updatedAt: num(c.updatedAt),
  nameFrameStyle: c.nameFrameStyle || 'pill', effectMotion: c.effectMotion || 'rise', nameDeco: c.nameDeco || '', nameColor: c.nameColor || '',
  frameImageUrl: c.frame === 'custom' ? c.frameImageUrl || '' : c.frame !== 'none' ? c.frameItemImageUrl || '' : '',
  nameFrameImageUrl: c.nameFrame === 'custom' ? c.nameFrameImageUrl || '' : c.nameFrame !== 'none' ? c.nameFrameItemImageUrl || '' : '',
  effectImageUrl: c.effect === 'custom' ? c.effectImageUrl || '' : c.effect !== 'none' ? c.effectItemImageUrl || '' : '' } : null;
// Free name-tag decoration the owner picks (sticker on the tag + text colour)
const RC_DECOS = ['', '♡', '✦', '👑', '🌸', '⭐', '🎀', '🔥', '💎', '🦋', '🐱', '🌙'];
app.get('/randomcard/catalog', w(async (q, r) => {
  const c = await rcCatalog(), list = kind => Object.entries(c[kind] || {}).filter(([, v]) => v && v.enabled !== false)
    .map(([id, v]) => ({ id, name: v.name || id, price: num(v.price), colors: v.colors || [], glyphs: v.glyphs || [], style: v.style || 'pill', motion: v.motion || 'rise', imageUrl: v.imageUrl || '', order: num(v.order) })).sort((a, b) => a.order - b.order || a.price - b.price);
  const [t, wl] = await Promise.all([tierOf(q.uid), db.ref(`wallets/${q.uid}/diamonds`).get()]);
  r.json({ enabled: c.enabled !== false, videoPrice: num(c.videoPrice), audioPrice: num(c.audioPrice),
    customFramePrice: num(c.customFramePrice), customNameFramePrice: num(c.customNameFramePrice), customEffectPrice: num(c.customEffectPrice), frames: list('frames'), nameFrames: list('nameFrames'), effects: list('effects'),
    vip: t.tier === 'vip', diamonds: num(wl.val()) - await pendingSum(q.uid) });
}));
app.get('/randomcard/me', w(async (q, r) => { const c = (await db.ref(`randomCards/${q.uid}`).get()).val(); r.json({ card: rcPublic(c), videoRef: c?.videoPath || '', audioRef: c?.audioPath || '' }); }));
app.post('/randomcard/quote', w(async (q, r) => { const x = await rcQuote(q.uid, q.body || {}); r.json({ items: x.items, total: x.total, error: x.error }); }));
// Voice greeting upload (raw audio, ≤ 1.5 MB, ~30 s) → Storage randomCardAudio/{uid}/…; returns the ref used in the draft.
app.put('/randomcard/audio', express.raw({ type: 'audio/*', limit: '1600kb' }), w(notBanned), w(async (q, r) => {
  const buf = q.body; if (!Buffer.isBuffer(buf) || buf.length < 500) return r.status(400).json({ error: 'audio required' });
  const path = `randomCardAudio/${q.uid}/${Date.now()}.m4a`;
  await admin.storage().bucket(STORAGE_BUCKET).file(path).save(buf, { contentType: 'audio/mp4', resumable: false });
  r.json({ ref: path });
}));
async function audioUrl(uid, path) {
  if (typeof path !== 'string' || !path.startsWith(`randomCardAudio/${uid}/`) || path.includes('..')) return null;
  const f = admin.storage().bucket(STORAGE_BUCKET).file(path); const [m] = await f.getMetadata().catch(() => [null]); if (!m) return null;
  let tok = (m.metadata?.firebaseStorageDownloadTokens || '').split(',')[0];
  if (!tok) { tok = crypto.randomUUID(); await f.setMetadata({ metadata: { firebaseStorageDownloadTokens: tok } }); }
  return `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${tok}`;
}
// Confirm & use: VIP only; price recomputed here (the app's number is only checked, never trusted); diamonds taken atomically, ledger kept.
app.post('/randomcard/save', w(notBanned), w(async (q, r) => {
  if ((await tierOf(q.uid)).tier !== 'vip') return r.status(403).json({ code: 'vip_only', error: 'ตกแต่งการ์ดสุ่มได้เฉพาะสมาชิก VIP' });
  const d = q.body || {}, x = await rcQuote(q.uid, d);
  if (x.cat.enabled === false) return r.status(403).json({ error: 'ปิดระบบตกแต่งการ์ดชั่วคราว' });
  if (x.error) return r.status(400).json({ error: x.error });
  if (Number(d.expectedTotal) !== x.total) return r.status(409).json({ code: 'price_changed', error: 'ราคามีการเปลี่ยนแปลง กรุณาตรวจสอบอีกครั้ง', items: x.items, total: x.total });
  // resolve media first (nothing is charged if a file is invalid)
  const card = { ...x.saved };
  if (d.avatarRef !== undefined) { if (!d.avatarRef) { card.avatarUrl = ''; card.avatarPath = ''; } else if (d.avatarRef !== x.saved.avatarPath) { const u = await postImageUrl(q.uid, d.avatarRef); if (!u) return r.status(400).json({ error: 'รูปโปรไฟล์ไม่ถูกต้อง' }); const mi = await moderateImage(q.uid, d.avatarRef, { kind: 'random_card' }); if (!mi.ok) return r.status(422).json({ code: 'moderated', error: 'รูปภาพนี้ขัดกติกาความปลอดภัย' }); card.avatarUrl = u; card.avatarPath = d.avatarRef; } }
  if (d.bgRef !== undefined) { if (!d.bgRef) { card.bgUrl = ''; card.bgPath = ''; } else if (d.bgRef !== x.saved.bgPath) { const u = await postImageUrl(q.uid, d.bgRef); if (!u) return r.status(400).json({ error: 'รูปพื้นหลังไม่ถูกต้อง' }); const mi = await moderateImage(q.uid, d.bgRef, { kind: 'random_card' }); if (!mi.ok) return r.status(422).json({ code: 'moderated', error: 'รูปภาพนี้ขัดกติกาความปลอดภัย' }); card.bgUrl = u; card.bgPath = d.bgRef; } }
  if (d.videoRef !== undefined) { if (!d.videoRef) { card.videoUrl = ''; card.videoPath = ''; } else if (d.videoRef !== x.saved.videoPath) { const u = await postMediaUrl(q.uid, d.videoRef, 'video/', POST_VIDEO_MAX); if (!u) return r.status(400).json({ error: 'วิดีโอไม่ถูกต้อง' }); card.videoUrl = u; card.videoPath = d.videoRef; } }
  if (d.audioRef !== undefined) { if (!d.audioRef) { card.audioUrl = ''; card.audioPath = ''; } else if (d.audioRef !== x.saved.audioPath) { const u = await audioUrl(q.uid, d.audioRef); if (!u) return r.status(400).json({ error: 'ไฟล์เสียงไม่ถูกต้อง' }); card.audioUrl = u; card.audioPath = d.audioRef; } }
  card.intro = cleanText(d.intro, 200);
  for (const [kind, key, styleKey, styleField] of [['frames', 'frame', 'frameColors', 'colors'], ['nameFrames', 'nameFrame', 'nameFrameColors', 'colors'], ['effects', 'effect', 'effectGlyphs', 'glyphs']]) {
    const want = typeof d[key] === 'string' ? d[key] : 'none';
    if (want === 'custom') {
      const [refKey, pathKey, , urlKey] = RC_CUSTOM[key], ref = d[refKey];
      if (typeof ref === 'string' && ref && ref !== x.saved[pathKey]) {
        const u = await postImageUrl(q.uid, ref); if (!u) return r.status(400).json({ error: 'ไฟล์ที่อัปโหลดไม่ถูกต้อง (รูป PNG / GIF / WebP ไม่เกิน 10MB)' });
        const mi = await moderateImage(q.uid, ref, { kind: 'random_card' }); if (!mi.ok) return r.status(422).json({ error: 'รูปนี้ไม่ผ่านการตรวจเนื้อหา' });
        card[urlKey] = u; card[pathKey] = ref;
      } else if (x.saved[key] !== 'custom' || !x.saved[urlKey]) return r.status(400).json({ error: 'ยังไม่ได้อัปโหลดรูป' });
      card[key] = 'custom'; card[styleKey] = [];
      continue;
    }
    if (want === 'none') { card[key] = 'none'; card[styleKey] = []; card[`${key}ItemImageUrl`] = ''; }
    else if (want !== x.saved[key]) { const it = rcItem(x.cat, kind, want); card[key] = want; card[styleKey] = (it?.[styleField] || []).slice(0, 8); card[`${key}ItemImageUrl`] = it?.imageUrl || '';
      if (kind === 'nameFrames') card.nameFrameStyle = RC_STYLES.includes(it?.style) ? it.style : 'pill';
      if (kind === 'effects') card.effectMotion = RC_MOTIONS.includes(it?.motion) ? it.motion : 'rise'; }
  }
  card.nameDeco = RC_DECOS.includes(d.nameDeco) ? d.nameDeco : (card.nameDeco || '');
  card.nameColor = typeof d.nameColor === 'string' && /^#[0-9A-Fa-f]{6}$/.test(d.nameColor) ? d.nameColor : d.nameColor === '' ? '' : (card.nameColor || '');
  card.layout = rcLayout(d.layout); card.updatedAt = Date.now();
  const price = x.total;
  if (price > 0) {
    const pend = await pendingSum(q.uid);
    const tx = await db.ref(`wallets/${q.uid}/diamonds`).transaction(c => (c === null ? null : num(c) - pend >= price ? num(c) - price : undefined));
    if (!tx.committed || tx.snapshot.val() === null) return r.status(402).json({ code: 'insufficient', error: `เพชรไม่พอ (ต้องการ 💎${price})`, price });
  }
  const t = Date.now();
  await db.ref(`randomCards/${q.uid}`).set(card);
  await Promise.all([price > 0 ? db.ref('wallets/_system/diamonds').transaction(c => num(c) + price) : null,
    db.ref(`randomCardTx/${q.uid}`).push({ items: x.items, total: price, at: t })]);
  const wl = await db.ref(`wallets/${q.uid}/diamonds`).get();
  r.json({ ok: true, charged: price, diamonds: num(wl.val()), card: rcPublic(card) });
}));
// Admin: prices / items (add, edit, disable) — the app reads them live.
app.get('/admin/randomcard-catalog', perm('config_edit'), w(async (q, r) => r.json(await rcCatalog())));
app.put('/admin/randomcard-catalog', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const pr = v => Math.min(1e6, Math.max(0, parseInt(v) || 0));
  const b = q.body || {}, out = { enabled: b.enabled !== false, videoPrice: pr(b.videoPrice), audioPrice: pr(b.audioPrice),
    customFramePrice: pr(b.customFramePrice), customNameFramePrice: pr(b.customNameFramePrice), customEffectPrice: pr(b.customEffectPrice) };
  const HEX = /^#[0-9A-Fa-f]{6}$/, prev = (await db.ref('appConfig/randomCardCatalog').get()).val() || {};
  for (const kind of RC_KINDS) {
    out[kind] = {};
    for (const [id, v] of Object.entries(b[kind] || {}).slice(0, 40)) {
      if (!/^[a-z0-9_]{1,24}$/.test(id) || id === 'none' || !v) continue;
      const price = parseInt(v.price); if (!(price >= 0 && price <= 1e6)) return r.status(400).json({ error: `ราคา ${id} ต้องเป็น 0–1,000,000` });
      const item = { name: String(v.name || id).slice(0, 30), price, enabled: v.enabled !== false, order: num(parseInt(v.order)) };
      if (kind === 'effects') item.motion = RC_MOTIONS.includes(v.motion) ? v.motion : 'rise';
      if (kind === 'nameFrames') item.style = RC_STYLES.includes(v.style) ? v.style : 'pill';
      if (kind === 'effects') item.glyphs = (Array.isArray(v.glyphs) ? v.glyphs : []).map(g => String(g).slice(0, 4)).filter(Boolean).slice(0, 6);
      else item.colors = (Array.isArray(v.colors) ? v.colors : []).filter(c => HEX.test(c)).slice(0, 6);
      const img = prev[kind]?.[id]?.imageUrl; if (typeof img === 'string' && img && v.dropImage !== true) item.imageUrl = img; // admin picture (uploaded separately)
      const willImg = !!item.imageUrl || v.pendingImage === true;
      if (!willImg && (kind === 'effects' ? !item.glyphs.length : item.colors.length < 2)) return r.status(400).json({ error: `${item.name}: ${kind === 'effects' ? 'ต้องมีอีโมจิอย่างน้อย 1 ตัว หรือใส่รูป' : 'ต้องมีสีอย่างน้อย 2 สี หรือใส่รูป'}` });
      out[kind][id] = item;
    }
  }
  out.by = q.uid; out.at = Date.now();
  await db.ref('appConfig/randomCardCatalog').set(out);
  await db.ref('adminLog').push({ action: 'randomcard_catalog', by: q.uid, at: out.at });
  r.json(out);
}));
// Admin picture for one catalog item (PNG / GIF / WebP / JPEG ≤ 2MB): frame = overlay on the card edge, name frame = tag background, effect = animated overlay.
const decorImg = buf => { const webp = buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP', png = buf[0] === 0x89 && buf[1] === 0x50, gif = buf.slice(0, 3).toString() === 'GIF', jpg = buf[0] === 0xff && buf[1] === 0xd8;
  return webp ? 'image/webp' : png ? 'image/png' : gif ? 'image/gif' : jpg ? 'image/jpeg' : null; };
async function saveDecorAsset(name, buf) {
  const ct = decorImg(buf); if (!ct) return null;
  const fileId = `${name}_${Date.now().toString(36)}`;
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${fileId}.webp`).save(buf, { contentType: ct, resumable: false });
  return `${assetUrl(fileId)}`;
}
app.put('/admin/randomcard-catalog/:kind/:id/image', perm('config_edit'), express.raw({ type: 'image/*', limit: '2100kb' }), w(async (q, r) => {
  const { kind, id } = q.params; if (!RC_KINDS.includes(kind) || !/^[a-z0-9_]{1,24}$/.test(id)) return r.status(400).json({ error: 'bad kind/id' });
  const ref = db.ref(`appConfig/randomCardCatalog/${kind}/${id}`); if (!(await ref.child('name').get()).exists()) return r.status(404).json({ error: 'บันทึกรายการก่อน แล้วค่อยใส่รูป' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image required' });
  if (buf.length > 2 * 1024 * 1024) return r.status(413).json({ error: 'รูปต้องไม่เกิน 2MB' });
  const url = await saveDecorAsset(`rc_${kind}_${id}`, buf); if (!url) return r.status(400).json({ error: 'รองรับ PNG, GIF, WebP, JPG' });
  await ref.update({ imageUrl: url }); r.json({ imageUrl: url });
}));
app.delete('/admin/randomcard-catalog/:kind/:id/image', perm('config_edit'), w(async (q, r) => {
  const { kind, id } = q.params; if (!RC_KINDS.includes(kind) || !/^[a-z0-9_]{1,24}$/.test(id)) return r.status(400).json({ error: 'bad kind/id' });
  await db.ref(`appConfig/randomCardCatalog/${kind}/${id}/imageUrl`).remove(); r.json({ ok: true });
}));
// Room decor option picture (theme = background, frame = overlay, effect = animated overlay). Stored next to the option; PUT /admin/decor-options keeps it.
app.put('/admin/decor-options/:kind/:id/image', perm('config_edit'), express.raw({ type: 'image/*', limit: '2100kb' }), w(async (q, r) => {
  const { kind, id } = q.params; if (!['themes', 'frames', 'effects'].includes(kind) || !DECOR_OPT_ID.test(id)) return r.status(400).json({ error: 'bad kind/id' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image required' });
  if (buf.length > 2 * 1024 * 1024) return r.status(413).json({ error: 'รูปต้องไม่เกิน 2MB' });
  const url = await saveDecorAsset(`decor_${kind}_${id}`, buf); if (!url) return r.status(400).json({ error: 'รองรับ PNG, GIF, WebP, JPG' });
  await db.ref(`appConfig/decorImages/${kind}/${id}`).set(url); decorOptCache = { at: 0, v: null };
  if ((await db.ref(`appConfig/decorOptions/${kind}/${id}/name`).get()).exists()) await db.ref(`appConfig/decorOptions/${kind}/${id}/imageUrl`).set(url);
  r.json({ imageUrl: url });
}));
app.delete('/admin/decor-options/:kind/:id/image', perm('config_edit'), w(async (q, r) => {
  const { kind, id } = q.params; if (!['themes', 'frames', 'effects'].includes(kind) || !DECOR_OPT_ID.test(id)) return r.status(400).json({ error: 'bad kind/id' });
  await db.ref().update({ [`appConfig/decorImages/${kind}/${id}`]: null, [`appConfig/decorOptions/${kind}/${id}/imageUrl`]: null }); decorOptCache = { at: 0, v: null };
  r.json({ ok: true });
}));
// สุ่มหาเพื่อน: tap the turtle → one random real person (online first), shown with their card; 💔 = next, ❤️ = chat.
// Uses the 'ต้องการ' filter and the randomMatchPerDay quota. Falls back to a labelled AI character when nobody fits.
app.post('/random/next', w(notBanned), w(quota('randomMatchPerDay')), w(async (q, r) => {
  const f = readFilter(q), me = q.uid, now = Date.now();
  const skip = new Set((Array.isArray(q.body?.skip) ? q.body.skip : []).filter(x => typeof x === 'string').slice(0, 100));
  const [users, st, seenS] = await Promise.all([db.ref('users').get(), onlineStatus(), db.ref(`randomSeen/${me}`).get()]);
  const all = users.val() || {}, online = new Set(), seen = seenS.val() || {};
  st.forEach(c => { const v = c.val(); if (v?.online === true && v.hidden !== true) online.add(c.key); });
  const pick = async ids => {
    for (const id of shuffle(ids)) {
      if (id === me || skip.has(id) || now - num(seen[id]) < 20 * 60e3) continue;
      const u = all[id]; if (!u || !u.displayName) continue;
      const p = publicProfile(id, u); if (!passFilter(p, f)) continue;
      if (await isBanned(id) || await blockedEither(me, id)) continue;
      return p;
    }
    return null;
  };
  const p = await pick([...online]) || await pick(Object.keys(all).filter(id => !online.has(id)));
  if (p) {
    await db.ref(`randomSeen/${me}/${p.uid}`).set(now);
    const card = (await db.ref(`randomCards/${p.uid}`).get()).val();
    return r.json({ type: 'user', user: { ...p, online: online.has(p.uid) }, card: rcPublic(card) });
  }
  if (await botsEnabled()) {
    const bots = Object.entries((await db.ref('aiBots').get()).val() || {}).filter(([, b]) => b && b.enabled === true);
    if (bots.length) { const [id, b] = bots[Math.floor(Math.random() * bots.length)]; return r.json({ type: 'bot', bot: publicBot(id, b) }); }
  }
  r.json({ type: 'none' });
}));
// ===== Anonymous shake room: 60 s, no real name/photo, heart halves from 20 s, mutual heart → reveal + normal chat.
// Server-only data (anonRooms/{id}); the app polls GET /anon/:id. Time is always the server's.
const ANON_MS = 60_000, ANON_HEART_MS = 20_000, ANON_ID = /^an_[a-f0-9]{16}$/;
const ANON_NAMES = ['จิ้งจอก 🦊', 'แพนด้า 🐼', 'แมวเหมียว 🐱', 'กระต่าย 🐰', 'นกฮูก 🦉', 'เพนกวิน 🐧', 'โคอาล่า 🐨', 'ยูนิคอร์น 🦄', 'ผีเสื้อ 🦋', 'ดาวน้อย ⭐'];
async function openAnonRoom(a, b) {
  const id = 'an_' + crypto.randomBytes(8).toString('hex');
  const n = shuffle([...ANON_NAMES]);
  await db.ref(`anonRooms/${id}`).set({ a, b, startAt: Date.now(), state: 'active', alias: { a: n[0], b: n[1] } });
  return id;
}
async function anonLoad(q, r) {
  const id = q.params.id; if (!ANON_ID.test(id)) { r.status(400).json({ error: 'bad room' }); return null; }
  const ref = db.ref(`anonRooms/${id}`), room = (await ref.get()).val();
  if (!room || (room.a !== q.uid && room.b !== q.uid)) { r.status(404).json({ error: 'not found' }); return null; }
  const side = room.a === q.uid ? 'a' : 'b', other = side === 'a' ? 'b' : 'a';
  const elapsed = Date.now() - num(room.startAt);
  if (room.state === 'active' && elapsed >= ANON_MS) { await ref.child('state').transaction(st => (st === 'active' ? 'ended' : st)); room.state = (await ref.child('state').get()).val(); }
  return { id, ref, room, side, other, elapsed };
}
async function anonView(x, me, afterAt = 0) {
  const { room, side, other, elapsed } = x;
  const msgs = Object.values(room.msgs || {}).filter(m => num(m.at) > afterAt).sort((m1, m2) => m1.at - m2.at).slice(-100)
    .map(m => ({ mine: m.s === side, text: m.t, at: m.at }));
  const out = { state: room.state, elapsedMs: Math.min(elapsed, ANON_MS), heartAfterMs: ANON_HEART_MS, durationMs: ANON_MS,
    myAlias: room.alias?.[side] || 'นิรนาม', otherAlias: room.alias?.[other] || 'นิรนาม', myHeart: room.hearts?.[side] === true, messages: msgs };
  if (room.state === 'matched') { // only now: real profiles + the normal chat
    const [mine, theirs] = await Promise.all([summary(me), summary(room[other])]);
    Object.assign(out, { chatId: room.chatId, me: mine, other: theirs });
  }
  return out;
}
app.get('/anon/:id', w(async (q, r) => {
  const x = await anonLoad(q, r); if (!x) return;
  r.json(await anonView(x, q.uid, Number(q.query.after) || 0));
}));
app.post('/anon/:id/msg', w(notBanned), w(async (q, r) => {
  const x = await anonLoad(q, r); if (!x) return;
  if (x.room.state !== 'active') return r.status(409).json({ error: 'ended', state: x.room.state });
  const text = cleanText(q.body?.text, 300); if (!text) return r.status(400).json({ error: 'text required' });
  if (Object.keys(x.room.msgs || {}).length >= 200) return r.status(429).json({ error: 'too many messages' });
  await x.ref.child('msgs').push({ s: x.side, t: text, at: Date.now() });
  r.json({ ok: true });
}));
app.post('/anon/:id/heart', w(notBanned), w(async (q, r) => {
  const x = await anonLoad(q, r); if (!x) return;
  if (x.room.state !== 'active') return r.status(409).json({ error: 'ended', state: x.room.state });
  if (x.elapsed < ANON_HEART_MS - 1500) return r.status(409).json({ error: 'too early' });
  let both = false;
  await x.ref.transaction(room => {
    if (!room || room.state !== 'active') return room;
    room.hearts = { ...(room.hearts || {}), [x.side]: true };
    both = room.hearts.a === true && room.hearts.b === true;
    if (both) room.state = 'matching';
    return room;
  });
  if (both) { // open the normal chat right away, no need to wait for 60 s
    const { chatId } = await openDirectChat(x.room.a, x.room.b);
    await x.ref.update({ state: 'matched', chatId, matchedAt: Date.now() });
    const t = Date.now(), pid = [x.room.a, x.room.b].sort().join('_');
    await db.ref().update({ [`matchNotifications/${x.room.a}/${pid}`]: { otherUid: x.room.b, chatId, at: t, seen: false }, [`matchNotifications/${x.room.b}/${pid}`]: { otherUid: x.room.a, chatId, at: t, seen: false } });
  }
  const fresh = await anonLoad(q, r); if (!fresh) return;
  r.json(await anonView(fresh, q.uid));
}));
app.post('/anon/:id/leave', w(async (q, r) => {
  const x = await anonLoad(q, r); if (!x) return;
  await x.ref.child('state').transaction(st => (st === 'active' ? 'ended' : st));
  r.json({ ok: true });
}));
// ===== Gift catalog (live / vip / chat) + stickers; admin-managed, images in Storage giftAssets/{id}.webp =====
// chatStickers = free stickers users send as chat messages (admin uploads them); stickers = VIP room-decor stickers
// payopen = 'เปย์เปิดใจ' gifts on the swipe page (picture + optional animated effect GIF/WebP)
const CHANNELS = ['live', 'vip', 'chat', 'stickers', 'chatStickers', 'payopen'];
const FREE_OK = ['stickers', 'chatStickers'];
const ITEM_ID = /^[A-Za-z0-9_-]{1,40}$/;
const SEED_LIVE = [
  ['gift_heart', 'หัวใจมินิ', '💖', 10, 'POP'], ['gift_flower', 'ดอกกุหลาบ', '🌹', 50, 'FLOAT'], ['gift_tea', 'ชานมไข่มุก', '🧋', 100, 'POP'],
  ['gift_crown', 'มงกุฎทอง', '👑', 500, 'ROYAL'], ['gift_rocket', 'จรวดซูเปอร์สตาร์', '🚀', 1000, 'BLAST']];
(async () => { try { await db.ref('catalog/live').transaction(c => c || Object.fromEntries(SEED_LIVE.map(([id, name, icon, price, fx], i) => [id, { name, icon, price, animationEffect: fx, category: 'live', enabled: true, order: i, imagePath: '' }]))); } catch (e) { console.error('seed live', e.message); } })();
// Built-in live gift pack (artwork in assets/gifts, served below). Added once; afterwards admins own the catalog (edit / disable / delete stays as they left it).
// Page 1 of the mockups (cheapest, shown first). 'existing' = same gift already in the catalog → only give it the picture, no duplicate.
const GIFT_PACK_1 = [
  ['wingheart', 'หัวใจวิ้ง', '💖', 30], ['teddy', 'ตุ๊กตาหมี', '🧸', 50], ['cake', 'เค้กวันเกิด', '🎂', 80], ['bouquet', 'ช่อดอกไม้', '💐', 100],
  ['goldcrown', 'มงกุฎทอง', '👑', 300, 'gift_crown'], ['diamondring', 'แหวนเพชร', '💍', 500], ['luckystar', 'ดาวนำโชค', '⭐', 20], ['loveballoon', 'ลูกโป่งรัก', '🎈', 40],
  ['bubbletea', 'ชานมไข่มุก', '🧋', 60, 'gift_tea'], ['giftbox', 'กล่องของขวัญ', '🎁', 120]];
// Chat-gift catalog seeds (catalog/chat) that match a pack picture: add the picture only if the item has none.
const CHAT_PICS = { heart: 'wingheart', bear: 'teddy', cake: 'cake', bouquet: 'bouquet', crown: 'goldcrown', ring: 'diamondring', coffee: 'coffee' };
const GIFT_PACK = [
  ['sportscar', 'รถสปอร์ต', '🏎️', 800], ['castle', 'ปราสาทฝัน', '🏰', 1000], ['sundae', 'ไอศกรีมซันเดย์', '🍨', 70], ['kitty', 'แมวเหมียว', '🐱', 90],
  ['lovekey', 'กุญแจรัก', '🗝️', 150], ['starglasses', 'แว่นดาว', '🤩', 110], ['rocket', 'จรวดทะยานฟ้า', '🚀', 250], ['trophy', 'ถ้วยรางวัล', '🏆', 400],
  ['moon', 'พระจันทร์ยิ้ม', '🌙', 55], ['karaoke', 'ไมค์คาราโอเกะ', '🎤', 130], ['yacht', 'เรือยอร์ช', '🛥️', 1500], ['handbag', 'กระเป๋าหรู', '👜', 600],
  ['swans', 'หงส์คู่', '🦢', 700], ['sushi', 'ซูชิเซ็ต', '🍣', 95], ['crystalball', 'ลูกแก้ววิเศษ', '🔮', 220], ['goldwatch', 'นาฬิกาทอง', '⌚', 550],
  ['butterfly', 'ผีเสื้อคริสตัล', '🦋', 180], ['musicnote', 'โน้ตเพลง', '🎵', 45], ['magiclamp', 'โคมไฟวิเศษ', '🪔', 320], ['angelwings', 'ปีกนางฟ้า', '👼', 900],
  ['sunflower', 'ดอกทานตะวัน', '🌻', 85], ['hearttrain', 'รถไฟหัวใจ', '🚂', 260], ['unicorn', 'ยูนิคอร์น', '🦄', 480], ['rainbow', 'สายรุ้ง', '🌈', 140],
  ['candle', 'เทียนหอม', '🕯️', 75], ['moneybag', 'ถุงเงิน', '💰', 350], ['queencrown', 'มงกุฎราชินี', '👑', 650], ['throne', 'บัลลังก์ทอง', '🪑', 1200],
  ['coffee', 'แก้วกาแฟ', '☕', 50], ['camera', 'กล้องถ่ายรูป', '📷', 170], ['villa', 'วิลล่าหรู', '🏡', 2000], ['dolphin', 'โลมาน้อย', '🐬', 160],
  ['moneytree', 'ต้นไม้เงินทอง', '🌳', 450], ['shootingstar', 'ดาวตกอธิษฐาน', '🌠', 210], ['guitar', 'กีตาร์ไฟฟ้า', '🎸', 280], ['macaron', 'ขนมมาการอง', '🧁', 65],
  ['rockinghorse', 'ม้าโยก', '🐴', 125], ['treasure', 'หีบสมบัติ', '💎', 750], ['bigdiamond', 'เพชรเม็ดโต', '💎', 888], ['championbelt', 'แชมป์เปี้ยนเบลท์', '🥇', 999]];
const PACK_URL = 'https://friendtalk-brain-123224091480.asia-southeast1.run.app/gift-pack/';
const packFx = price => price >= 1000 ? 'BLAST' : price >= 500 ? 'ROYAL' : price >= 100 ? 'FLOAT' : 'POP';
app.get('/gift-pack/:f', (q, r) => {
  if (!/^[a-z]{2,20}\.webp$/.test(q.params.f)) return r.status(400).end();
  r.set('Cache-Control', 'public, max-age=604800'); r.type('image/webp');
  r.sendFile(new URL(`./assets/gifts/${q.params.f}`, import.meta.url).pathname, e => { if (e && !r.headersSent) r.status(404).end(); });
});
(async () => {
  try {
    const live = (await db.ref('catalog/live').get()).val() || {}, upd = {};
    let order = Object.keys(live).length;
    const add = ([id, name, icon, price, existing]) => {
      if (existing) { if (live[existing] && !live[existing].imageUrl) upd[`catalog/live/${existing}/imageUrl`] = `${PACK_URL}${id}.webp?v=4`; if (live[existing]) return; }
      upd[`catalog/live/p_${id}`] = { name, icon, price, animationEffect: packFx(price), category: 'live', enabled: true, order: order++, imagePath: '', imageUrl: `${PACK_URL}${id}.webp?v=4`, updatedAt: Date.now(), updatedBy: 'system' };
    };
    // each part is added once (flag); afterwards whatever an admin edits or deletes stays that way
    const once = async flag => (await db.ref(`appConfig/${flag}`).transaction(c => (c ? undefined : { at: Date.now() }))).committed;
    const v1 = await once('giftPackV1'), v2 = await once('giftPackV2');
    if (v2) GIFT_PACK_1.forEach(add);
    if (v1) GIFT_PACK.forEach(add);
    if (v2) {
      const chat = (await db.ref('catalog/chat').get()).val() || {};
      for (const [cid, pic] of Object.entries(CHAT_PICS)) if (chat[cid] && !chat[cid].imageUrl) upd[`catalog/chat/${cid}/imageUrl`] = `${PACK_URL}${pic}.webp?v=4`;
    }
    // v3: pictures re-cut with a transparent background → new ?v so phones drop the cached square ones (only items still using our pack picture)
    // v4: pictures re-cut with an AI cut-out (no glow halo) → bump every pack picture URL to ?v=4
    const v4 = await once('giftPackV4');
    if (v4) for (const ch of ['live', 'chat']) {
      const cur = ch === 'live' ? live : ((await db.ref('catalog/chat').get()).val() || {});
      for (const [id, it] of Object.entries(cur)) if (typeof it?.imageUrl === 'string' && it.imageUrl.startsWith(PACK_URL) && !it.imageUrl.endsWith('?v=4') && !(`catalog/${ch}/${id}` in upd))
        upd[`catalog/${ch}/${id}/imageUrl`] = it.imageUrl.split('?')[0] + '?v=4';
    }
    const v3 = await once('giftPackV3');
    if (v3) for (const ch of ['live', 'chat']) {
      const cur = ch === 'live' ? { ...live, ...Object.fromEntries(Object.entries(upd).filter(([k]) => k.startsWith('catalog/live/') && k.split('/').length === 3).map(([k, v]) => [k.split('/')[2], v])) } : ((await db.ref('catalog/chat').get()).val() || {});
      for (const [id, it] of Object.entries(cur)) if (typeof it?.imageUrl === 'string' && it.imageUrl.startsWith(PACK_URL) && !it.imageUrl.includes('?v=')) upd[`catalog/${ch}/${id}/imageUrl`] = `${it.imageUrl}?v=4`;
      for (const [k, v] of Object.entries(upd)) if (k.startsWith(`catalog/${ch}/`) && k.endsWith('/imageUrl') && typeof v === 'string' && v.startsWith(PACK_URL) && !v.includes('?v=')) upd[k] = `${v}?v=4`;
    }
    if (Object.keys(upd).length) await db.ref().update(upd).catch(async e => { // failed → let the next start try again
      for (const [f, mine] of [['giftPackV1', v1], ['giftPackV2', v2], ['giftPackV3', v3], ['giftPackV4', v4]]) if (mine) await db.ref(`appConfig/${f}`).remove().catch(() => {}); throw e; });
  } catch (e) { console.error('seed gift pack', e.message); }
})();
// Built-in chat sticker packs (free, artwork in assets/stickers/sNN_MM.webp). Added once to catalog/chatStickers; admins own them afterwards.
const STICKER_PACKS = [
  ['คู่ซี้ 1', ['ตกลงตามนี้', 'ได้เลยจ้า', 'ขอเวลาแป๊บ', 'ฟินเว่อร์', 'ว้าวมาก', 'อย่าดื้อ', 'นอนก่อนนะ', 'รักนะจุ๊บๆ', 'คิดบวกไว้', 'วันนี้สู้ตาย']],
  ['คู่ซี้ 2', ['สวัสดีจ้า', 'มาแล้ววว', 'คิดถึงนะ', 'โอเคเลย', 'จัดไป!', 'ปังมาก', 'เริ่ดสุด', 'จ๊านจริง', 'รักเลย', 'ขอบคุณน้า']],
  ['คู่ซี้ 3', ['จริงดิ', 'ไม่ไหวแล้ว', 'ใครอยู่บ้าง', 'หายไปไหน', 'รับแอดหน่อย', 'แวะมาทัก', 'ลุยยาว', 'เดี๋ยวโทรหา', 'มีเรื่องจะบอก', 'คุยกันไหม']],
  ['คู่ซี้ 4', ['หิวข้าวแล้ว', 'งานเข้า!', 'รอแป๊บ', 'มาเมาท์กัน', 'ส่งใจให้', 'สู้ๆนะ', 'โหดจัด', 'เท่มาก', 'น่ารักอะ', 'เขินนะ']],
  ['คู่ซี้ 5', ['555+', 'ฮาไม่ไหว', 'งงเด้', 'ใจเย็นก่อน', 'เดี๋ยวมา', 'ไปก่อนน้า', 'ฝันดีนะ', 'ตื่นยัง', 'รับทราบ', 'โอ๊ยยย']],
  ['สายฟิน', ['ดีต่อใจ', 'ฟินสุด', 'ว้าวซ่า', 'คอลเลย', 'อยู่เป็นเพื่อน', 'คิดบวกไว้', 'ช็อตฟีล', 'รักนะจ๊ะ', 'ผ่านฉลุย', 'เลิศมาก']],
  ['ออฟฟิศ', ['รับทราบ', 'งานแน่น', 'รอแป๊บนึง', 'ตกใจหมด', 'เอ็นดู', 'สาธุ', 'โอ้โหเด็ด', 'อย่าหาทำ', 'ขำแรง', 'นอนก่อนนะ']],
  ['สตรีท', ['โย่ว!', 'พร้อมลุย', 'โอ๊ยยย', 'คิดถึงเด้อ', 'เดี๋ยวมา', 'โอเคค้าบ', 'จี๊ดมาก', 'หิวอีกแล้ว', 'แอบงอน', 'ฝันดีน้า']],
  ['แก๊งเท่', ['มาดิ', 'ปังไม่ไหว', 'จัดให้', 'ขอพักแป๊บ', 'น่ารักอ่า', 'เอาเรื่อง!', 'รีบตอบ', 'ยิ้มหน่อย', 'ขอบใจมาก', 'คืนนี้คุยกัน']],
  ['สายลุย', ['สู้โว้ย', 'ลุยต่อ', 'เทพจัด', 'ง่วงแล้ว', 'รอรับเลย', 'แชมป์!', 'ใจฟู', 'ห้ามพลาด', 'เดือดจัด', 'บอกต่อด้วย']]];
const STICKER_URL = 'https://friendtalk-brain-123224091480.asia-southeast1.run.app/sticker-pack/';
app.get('/sticker-pack/:f', (q, r) => {
  if (!/^s\d{2}_\d{2}\.webp$/.test(q.params.f)) return r.status(400).end();
  r.set('Cache-Control', 'public, max-age=604800'); r.type('image/webp');
  r.sendFile(new URL(`./assets/stickers/${q.params.f}`, import.meta.url).pathname, e => { if (e && !r.headersSent) r.status(404).end(); });
});
(async () => {
  try {
    const flag = await db.ref('appConfig/stickerPackV1').transaction(c => (c ? undefined : { at: Date.now() }));
    if (!flag.committed) return; // added once; whatever admins change afterwards stays
    let order = Object.keys((await db.ref('catalog/chatStickers').get()).val() || {}).length;
    const upd = {}, t = Date.now(), pad = n => String(n).padStart(2, '0');
    STICKER_PACKS.forEach(([cat, names], si) => names.forEach((name, k) => {
      upd[`catalog/chatStickers/pk${pad(si)}_${pad(k)}`] = { name, icon: '', price: 0, animationEffect: 'POP', category: cat, enabled: true, order: order++, imagePath: '', imageUrl: `${STICKER_URL}s${pad(si)}_${pad(k)}.webp`, updatedAt: t, updatedBy: 'system' };
    }));
    await db.ref().update(upd).catch(async e => { await db.ref('appConfig/stickerPackV1').remove().catch(() => {}); throw e; });
  } catch (e) { console.error('seed sticker pack', e.message); }
})();
const GIFT_CFG_SEED = { enabled: true, autoFriend: true, adminPercent: 20, recipientPercent: 80 };
async function giftCfg() { return { ...GIFT_CFG_SEED, ...((await db.ref('appConfig/gifts').get()).val() || {}) }; }
const assetUrl = id => `https://friendtalk-brain-123224091480.asia-southeast1.run.app/gift-assets/${id}`;
app.get('/gift-assets/:id', w(async (q, r) => {
  if (!/^[A-Za-z0-9_-]{1,90}$/.test(q.params.id)) return r.status(400).end();
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
  if (price < 0 || price > 1000000 || (price === 0 && !FREE_OK.includes(ch))) return r.status(400).json({ error: FREE_OK.includes(ch) ? 'price 0..1000000' : 'price 1..1000000' });
  const ref = db.ref(`catalog/${ch}/${id}`), cur = (await ref.get()).val() || {};
  let order = Number.isInteger(b.order) ? b.order : cur.order;
  if (!Number.isInteger(order)) order = Object.keys((await db.ref(`catalog/${ch}`).get()).val() || {}).length;
  const item = { name, price, category: String(b.category || cur.category || ch).slice(0, 30), enabled: b.enabled !== false, order,
    icon: String(b.icon ?? cur.icon ?? '').slice(0, 8), animationEffect: String(b.animationEffect ?? cur.animationEffect ?? 'POP').slice(0, 12), imagePath: cur.imagePath || '', imageUrl: cur.imageUrl || '', updatedAt: Date.now(), updatedBy: q.uid };
  if (cur.effectUrl) { item.effectUrl = cur.effectUrl; item.effectPath = cur.effectPath || ''; }
  if (cur.videoUrl) { item.videoUrl = cur.videoUrl; item.videoPath = cur.videoPath || ''; }
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
// Animated effect for a gift (GIF / animated WebP / PNG, ≤ 2 MB) — plays in the middle of the recipient's screen (เปย์เปิดใจ).
app.put('/admin/catalog/:ch/:id/effect', catPerm, express.raw({ type: 'image/*', limit: '2100kb' }), w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  const ref = db.ref(`catalog/${ch}/${id}`); if (!(await ref.get()).exists()) return r.status(404).json({ error: 'save item first' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'image body required' });
  if (buf.length > 2 * 1024 * 1024) return r.status(413).json({ error: 'effect too large (max 2MB)' });
  const isWebp = buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP', isPng = buf[0] === 0x89 && buf[1] === 0x50, isGif = buf.slice(0, 3).toString() === 'GIF';
  if (!isWebp && !isPng && !isGif) return r.status(400).json({ error: 'gif, webp or png only' });
  const fileId = `${ch}_${id}_fx`;
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${fileId}.webp`).save(buf, { contentType: isGif ? 'image/gif' : isWebp ? 'image/webp' : 'image/png', resumable: false });
  const url = `${assetUrl(fileId)}?v=${Date.now()}`;
  await ref.update({ effectPath: `giftAssets/${fileId}.webp`, effectUrl: url });
  r.json({ effectUrl: url, bytes: buf.length });
}));
app.delete('/admin/catalog/:ch/:id/effect', catPerm, w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  await db.ref(`catalog/${ch}/${id}`).update({ effectUrl: null, effectPath: null });
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${ch}_${id}_fx.webp`).delete().catch(() => {});
  r.json({ ok: true });
}));
// Video effect (MP4 / WebM, max 10MB) for any channel: plays full screen when the gift is sent (chat, live, เปย์เปิดใจ).
// Served from Storage with a download token so players can seek (range requests).
const GIFT_VIDEO_MAX = 10 * 1024 * 1024;
app.put('/admin/catalog/:ch/:id/video', catPerm, express.raw({ type: 'video/*', limit: '10300kb' }), w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  const ref = db.ref(`catalog/${ch}/${id}`), cur = (await ref.get()).val(); if (!cur) return r.status(404).json({ error: 'save item first' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || !buf.length) return r.status(400).json({ error: 'video body required' });
  if (buf.length > GIFT_VIDEO_MAX) return r.status(413).json({ error: 'วิดีโอต้องไม่เกิน 10MB' });
  const isMp4 = buf.slice(4, 8).toString() === 'ftyp', isWebm = buf[0] === 0x1a && buf[1] === 0x45 && buf[2] === 0xdf && buf[3] === 0xa3;
  if (!isMp4 && !isWebm) return r.status(400).json({ error: 'รองรับเฉพาะวิดีโอ MP4 หรือ WebM' });
  const bucket = admin.storage().bucket(STORAGE_BUCKET), path = `giftAssets/${ch}_${id}_vfx_${Date.now().toString(36)}.${isMp4 ? 'mp4' : 'webm'}`, tok = crypto.randomUUID();
  await bucket.file(path).save(buf, { contentType: isMp4 ? 'video/mp4' : 'video/webm', resumable: false, metadata: { cacheControl: 'public, max-age=86400', metadata: { firebaseStorageDownloadTokens: tok } } });
  const url = `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${tok}`;
  if (cur.videoPath && cur.videoPath !== path) await bucket.file(cur.videoPath).delete().catch(() => {});
  await ref.update({ videoUrl: url, videoPath: path });
  r.json({ videoUrl: url, bytes: buf.length });
}));
app.delete('/admin/catalog/:ch/:id/video', catPerm, w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  const ref = db.ref(`catalog/${ch}/${id}`), p = (await ref.child('videoPath').get()).val();
  await ref.update({ videoUrl: null, videoPath: null });
  if (typeof p === 'string' && p.startsWith('giftAssets/')) await admin.storage().bucket(STORAGE_BUCKET).file(p).delete().catch(() => {});
  r.json({ ok: true });
}));
app.delete('/admin/catalog/:ch/:id', catPerm, w(async (q, r) => {
  const { ch, id } = q.params; if (!CHANNELS.includes(ch) || !ITEM_ID.test(id)) return r.status(400).json({ error: 'bad channel/id' });
  { const p = (await db.ref(`catalog/${ch}/${id}/videoPath`).get()).val(); if (typeof p === 'string' && p.startsWith('giftAssets/')) await admin.storage().bucket(STORAGE_BUCKET).file(p).delete().catch(() => {}); }
  await db.ref(`catalog/${ch}/${id}`).remove();
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${ch}_${id}.webp`).delete().catch(() => {});
  await admin.storage().bucket(STORAGE_BUCKET).file(`giftAssets/${ch}_${id}_fx.webp`).delete().catch(() => {});
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
  const gift = { txId, giftId, name: item.name, price: item.price, imageUrl: item.imageUrl || '', icon: item.icon || '', videoUrl: item.videoUrl || '', status: 'pending' };
  await db.ref().update({
    [`giftTx/${txId}`]: { from: q.uid, to, chatId, giftId, name: item.name, price: item.price, imageUrl: item.imageUrl || '', status: 'pending', createdAt: t },
    [`giftPending/${q.uid}/${txId}`]: item.price,
    [`messages/${chatId}/${txId}`]: { senderUid: q.uid, at: t, text: `🎁 ${item.name}`, gift },
    [`chats/${chatId}/lastMessage`]: `🎁 ส่งของขวัญ ${item.name}`, [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: q.uid
  });
  sendPush(to, 'คุณได้รับของขวัญ 🎁', `${await nameOf(q.uid)} ส่ง ${item.name} ให้คุณ แตะเพื่อรับ`, { type: 'chat', chatId });
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
  await Promise.all([db.ref(`wallets/${tx.to}/diamonds`).transaction(c => num(c) + recipientShare), db.ref(`wallets/${tx.to}/earnedDiamonds`).transaction(c => num(c) + recipientShare), db.ref('wallets/_system/diamonds').transaction(c => num(c) + adminShare)]);
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
// ===== เปย์เปิดใจ (swipe page): send a gift → recipient has 10 s to accept =====
// Diamonds are taken at send (escrow on payOpens/{id}); accept → recipient share + system share, friends both ways, chat opened;
// reject / no answer by the deadline → full refund to the sender. Exactly one outcome per gift: state pending → settling → accepted|refunded,
// the money and the final state are written together in ONE multi-path update (ServerValue.increment), so nothing is charged or refunded twice.
// payOpenInbox/{to}/{id} (recipient's app shows the effect + buttons), payOpenResults/{from}/{id} (sender's app plays the refund effect).
const PO_ID = /^po[0-9a-z]{8,40}$/;
const PO_MS = 30000; // ~4 s effect + 10 s countdown on screen + slack for slow networks; the server is the clock that matters
const inc = n => admin.database.ServerValue.increment(n);
const PO_SEED = [ // pack picture, name, price, its own effect style (app draws a different show for each)
  ['wingheart', 'หัวใจวิ้ง', 20, 'HEARTS'], ['luckystar', 'ดาวนำโชค', 30, 'STARS'], ['loveballoon', 'ลูกโป่งรัก', 40, 'BALLOONS'], ['teddy', 'ตุ๊กตาหมี', 50, 'BOUNCE'],
  ['bubbletea', 'ชานมไข่มุก', 60, 'BUBBLES'], ['bouquet', 'ช่อดอกไม้', 80, 'PETALS'], ['lovekey', 'กุญแจรัก', 100, 'ORBIT'], ['rainbow', 'สายรุ้ง', 120, 'RAINBOW'],
  ['butterfly', 'ผีเสื้อคริสตัล', 150, 'BUTTERFLY'], ['diamondring', 'แหวนเพชร', 300, 'SPARKLE'], ['goldcrown', 'มงกุฎทอง', 500, 'ROYAL'], ['rocket', 'จรวดทะยานฟ้า', 800, 'FIREWORK']];
(async () => {
  try {
    const flag = await db.ref('appConfig/payOpenPackV1').transaction(c => (c ? undefined : { at: Date.now() }));
    if (!flag.committed) return; // added once; admins own the list afterwards
    const t = Date.now(), upd = {};
    PO_SEED.forEach(([pic, name, price, fx], i) => { upd[`catalog/payopen/po_${pic}`] = { name, icon: '', price, animationEffect: fx, category: 'payopen', enabled: true, order: i, imagePath: '', imageUrl: `${PACK_URL}${pic}.webp?v=4`, updatedAt: t, updatedBy: 'system' }; });
    await db.ref().update(upd).catch(async e => { await db.ref('appConfig/payOpenPackV1').remove().catch(() => {}); throw e; });
  } catch (e) { console.error('seed payopen', e.message); }
})();
// Voices: appConfig/payOpenVoices/{id} {text, gender male|female, kind arrival|refund, audioUrl, audioPath, order}. No audio → the app speaks the text (on-device TTS, free).
const PO_REFUND_LINES = ['แห้วเลยจ้า~', 'ขอโทษนะจ๊ะ~', 'ไว้ลองใหม่เนอะ~', 'อุ๊ย! นกแล้วจ้า~', 'ยังไม่ใช่เนื้อคู่จ้า~', 'เพชรกลับมาแล้วจ้า~', 'อย่าเพิ่งเสียใจน้า~', 'จีบใหม่อีกทีนะ~'];
(async () => {
  try {
    await db.ref('appConfig/payOpenVoices').transaction(c => {
      if (c !== null) return undefined;
      const v = { arr_f: { text: 'สายเปย์มาแล้ว~', gender: 'female', kind: 'arrival', audioUrl: '', audioPath: '', order: 0 },
        arr_m: { text: 'สายเปย์มาแล้ว~', gender: 'male', kind: 'arrival', audioUrl: '', audioPath: '', order: 1 } };
      PO_REFUND_LINES.forEach((text, i) => { for (const g of ['female', 'male']) v[`r${i + 1}_${g[0]}`] = { text, gender: g, kind: 'refund', audioUrl: '', audioPath: '', order: 10 + i }; });
      return v;
    });
  } catch (e) { console.error('seed payopen voices', e.message); }
})();
const poVoiceList = v => Object.entries(v || {}).filter(([, x]) => x && x.text).map(([id, x]) => ({ id, text: String(x.text), gender: x.gender === 'male' ? 'male' : 'female',
  kind: x.kind === 'arrival' ? 'arrival' : 'refund', audioUrl: x.audioUrl || '', order: num(x.order) })).sort((a, b) => a.order - b.order);
app.get('/payopen/voices', w(async (_q, r) => r.json({ voices: poVoiceList((await db.ref('appConfig/payOpenVoices').get()).val()) })));
app.get('/payopen/catalog', w(async (q, r) => {
  const v = (await db.ref('catalog/payopen').get()).val() || {};
  const items = Object.entries(v).filter(([, x]) => x && x.enabled !== false && num(x.price) > 0).map(([id, x]) => ({ id, name: x.name || id, price: num(x.price), imageUrl: x.imageUrl || '', effectUrl: x.effectUrl || '', videoUrl: x.videoUrl || '', icon: x.icon || '', fx: x.animationEffect || 'HEARTS', order: num(x.order) })).sort((a, b) => a.order - b.order);
  const [dm, pend] = await Promise.all([db.ref(`wallets/${q.uid}/diamonds`).get(), pendingSum(q.uid)]);
  r.json({ items, diamonds: num(dm.val()) - pend });
}));
async function poPeople(uid) { const u = (await db.ref(`users/${uid}`).get()).val() || {}; return { name: String(u.displayName || 'ผู้ใช้').slice(0, 40), avatar: u.avatar || '', gender: genderOf(u) }; }
/** Settle once. want = 'accept' | 'refund'. Returns the final payOpens node (or null if missing). */
async function poSettle(id, want, reason) {
  const ref = db.ref(`payOpens/${id}`), t = Date.now();
  const lock = await ref.transaction(p => {
    if (p === null) return null; // cold cache: the SDK retries with the real value
    if (p.state === 'pending') { const to = want === 'accept' && t <= num(p.deadline) ? 'accept' : 'refund'; return { ...p, state: 'settling', settleTo: to, lockAt: t, reason: to === 'refund' ? (want === 'accept' ? 'expired' : reason) : '' }; }
    if (p.state === 'settling' && t - num(p.lockAt) > 120000) return { ...p, lockAt: t }; // a crashed settle: money not moved yet (it moves with the final state)
    return undefined;
  });
  const p = lock.snapshot.val();
  if (!lock.committed || !p || p.state !== 'settling' || num(p.lockAt) !== t) return p;
  const upd = { [`payOpenInbox/${p.to}/${id}`]: null, [`payOpens/${id}/settledAt`]: t, [`payOpens/${id}/lockAt`]: null };
  if (p.settleTo === 'accept') {
    const cfg = await giftCfg(), price = num(p.price), share = Math.floor(price * num(cfg.recipientPercent) / 100), sys = price - share;
    const { chatId } = await openDirectChat(p.from, p.to);
    Object.assign(upd, {
      [`wallets/${p.to}/diamonds`]: inc(share), [`wallets/${p.to}/earnedDiamonds`]: inc(share), 'wallets/_system/diamonds': inc(sys),
      [`payOpens/${id}/state`]: 'accepted', [`payOpens/${id}/chatId`]: chatId, [`payOpens/${id}/split`]: { recipientShare: share, adminShare: sys },
      [`friends/${p.from}/${p.to}`]: t, [`friends/${p.to}/${p.from}`]: t, [`friendRequests/${p.to}/${p.from}`]: null, [`friendRequests/${p.from}/${p.to}`]: null,
      [`messages/${chatId}/${db.ref(`messages/${chatId}`).push().key}`]: { senderUid: p.from, at: t, text: `💝 เปย์เปิดใจด้วย ${p.giftName} — เป็นเพื่อนกันแล้ว 🎉` },
      [`chats/${chatId}/lastMessage`]: `💝 เปย์เปิดใจ ${p.giftName}`, [`chats/${chatId}/lastAt`]: t, [`chats/${chatId}/lastSenderUid`]: p.from,
      [`payOpenResults/${p.from}/${id}`]: { state: 'accepted', chatId, giftName: p.giftName, toName: p.toName || '', at: t }
    });
  } else {
    Object.assign(upd, {
      [`wallets/${p.from}/diamonds`]: inc(num(p.price)), [`payOpens/${id}/state`]: 'refunded', [`payOpens/${id}/refunded`]: num(p.price),
      [`payOpenResults/${p.from}/${id}`]: { state: 'refunded', price: num(p.price), giftName: p.giftName, voiceGender: p.fromGender === 'male' ? 'female' : p.fromGender === 'female' ? 'male' : '', at: t }
    });
  }
  await db.ref().update(upd); // money + final state together: either both happened or neither (then the 2-min retry above finishes it)
  await db.ref(`payOpenTx/${id}`).set({ from: p.from, to: p.to, giftId: p.giftId, price: num(p.price), state: p.settleTo === 'accept' ? 'accepted' : 'refunded', reason: p.reason || '', at: num(p.at), settledAt: t }).catch(() => {});
  if (p.settleTo === 'accept') sendPush(p.from, 'เปย์เปิดใจสำเร็จ 💝', `${p.toName || 'เพื่อน'} รับ ${p.giftName} แล้ว เป็นเพื่อนกันแล้ว`, { type: 'chat', chatId: upd[`payOpens/${id}/chatId`] });
  return (await ref.get()).val();
}
let poSweepAt = 0;
async function poSweep() { // refund everything past its deadline (also runs lazily on each payopen call — Cloud Run may idle the timer)
  if (Date.now() - poSweepAt < 5000) return; poSweepAt = Date.now();
  const s = await db.ref('payOpens').orderByChild('state').equalTo('pending').limitToFirst(200).get(), late = [];
  s.forEach(c => { if (num(c.val()?.deadline) < Date.now()) late.push(c.key); });
  const stuck = await db.ref('payOpens').orderByChild('state').equalTo('settling').limitToFirst(50).get();
  stuck.forEach(c => { if (Date.now() - num(c.val()?.lockAt) > 120000) late.push(c.key); });
  for (const id of late) await poSettle(id, 'refund', 'timeout').catch(e => console.error('payopen settle', id, e.message));
}
setInterval(() => { poSweep().catch(e => console.error('payopen sweep', e.message)); }, 10000).unref?.();
const poView = (id, p) => p ? { id, state: p.state === 'settling' ? 'pending' : p.state, from: p.from, to: p.to, giftId: p.giftId, giftName: p.giftName, imageUrl: p.imageUrl || '', effectUrl: p.effectUrl || '', videoUrl: p.videoUrl || '', fx: p.fx || '',
  price: num(p.price), at: num(p.at), deadline: num(p.deadline), chatId: p.chatId || '', fromName: p.fromName || '', fromAvatar: p.fromAvatar || '', fromGender: p.fromGender || '',
  voiceGender: p.fromGender === 'male' ? 'female' : p.fromGender === 'female' ? 'male' : '', serverNow: Date.now() } : null;
app.post('/payopen/send', w(notBanned), w(async (q, r) => {
  const to = q.body?.toUid, giftId = q.body?.giftId;
  if (typeof to !== 'string' || !UID_RE.test(to) || to === q.uid || typeof giftId !== 'string' || !ITEM_ID.test(giftId)) return r.status(400).json({ error: 'toUid/giftId required' });
  poSweep().catch(() => {});
  const cfg = await giftCfg(); if (!cfg.enabled) return r.status(403).json({ code: 'gifts_disabled', error: 'ปิดระบบของขวัญชั่วคราว' });
  if (!(await db.ref(`users/${to}`).get()).exists()) return r.status(404).json({ error: 'ไม่พบผู้ใช้' });
  if (await isBanned(to) || await blockedEither(q.uid, to)) return r.status(403).json({ error: 'ส่งให้ผู้ใช้นี้ไม่ได้' });
  if ((await db.ref(`friends/${q.uid}/${to}`).get()).exists()) return r.status(409).json({ code: 'already_friends', error: 'เป็นเพื่อนกันอยู่แล้ว' });
  const waiting = (await db.ref(`payOpenInbox/${to}`).get()).val() || {};
  if (Object.values(waiting).some(x => x?.from === q.uid)) return r.status(409).json({ code: 'waiting', error: 'กำลังรออีกฝ่ายตอบอยู่' });
  const item = (await db.ref(`catalog/payopen/${giftId}`).get()).val();
  if (!item || item.enabled === false || !(num(item.price) > 0)) return r.status(404).json({ error: 'ไม่พบของขวัญนี้' });
  // price from the catalog only (never from the app); the app's expectedPrice is only compared, so an admin price change is never silently charged
  const price = num(item.price);
  if (q.body?.expectedPrice !== undefined && num(q.body.expectedPrice) !== price) return r.status(409).json({ code: 'price_changed', error: `ราคาเปลี่ยนเป็น 💎${price} แล้ว`, price });
  const pend = await pendingSum(q.uid);
  const d = await db.ref(`wallets/${q.uid}/diamonds`).transaction(c => (c === null ? null : num(c) - pend >= price ? num(c) - price : undefined));
  if (!d.committed || d.snapshot.val() === null) return r.status(402).json({ code: 'insufficient', error: `เพชรไม่พอ (ต้องการ 💎${price})`, price });
  const t = Date.now(), id = 'po' + t.toString(36) + crypto.randomBytes(4).toString('hex');
  try {
    const [me, them] = await Promise.all([poPeople(q.uid), poPeople(to)]);
    const p = { from: q.uid, to, giftId, giftName: item.name || '', imageUrl: item.imageUrl || '', effectUrl: item.effectUrl || '', videoUrl: item.videoUrl || '', fx: item.animationEffect || 'HEARTS', icon: item.icon || '',
      price, state: 'pending', at: t, deadline: t + PO_MS, fromName: me.name, fromAvatar: me.avatar, fromGender: me.gender, toName: them.name };
    await db.ref().update({ [`payOpens/${id}`]: p, [`payOpenInbox/${to}/${id}`]: { id, ...poView(id, p), state: 'pending' } });
  } catch (e) { // could not create the gift → give the diamonds straight back
    await db.ref(`wallets/${q.uid}/diamonds`).set(inc(price)).catch(() => {});
    throw e;
  }
  sendPush(to, 'สายเปย์มาแล้ว~ 💝', `${(await poPeople(q.uid)).name} ส่งของขวัญเปิดใจให้คุณ เปิดแอปเพื่อรับภายใน 10 วินาที`, { type: 'payopen', id });
  r.json({ id, state: 'pending', price, diamonds: d.snapshot.val(), deadline: t + PO_MS });
}));
app.get('/payopen/inbox', w(async (q, r) => {
  await poSweep().catch(() => {});
  const v = (await db.ref(`payOpenInbox/${q.uid}`).get()).val() || {};
  r.json({ items: Object.values(v).filter(x => x && x.deadline > Date.now()).map(x => ({ ...x, serverNow: Date.now() })) });
}));
app.get('/payopen/:id', w(async (q, r) => {
  const id = q.params.id; if (!PO_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  let p = (await db.ref(`payOpens/${id}`).get()).val();
  if (!p || (p.from !== q.uid && p.to !== q.uid)) return r.status(404).json({ error: 'not found' });
  if (p.state === 'pending' && Date.now() > num(p.deadline)) p = await poSettle(id, 'refund', 'timeout');
  r.json(poView(id, p));
}));
app.post('/payopen/:id/respond', w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!PO_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const p0 = (await db.ref(`payOpens/${id}`).get()).val();
  if (!p0 || p0.to !== q.uid) return r.status(404).json({ error: 'not found' });
  const accept = q.body?.accept === true && !(await blockedEither(p0.from, p0.to)) && !(await isBanned(p0.from));
  const p = await poSettle(id, accept ? 'accept' : 'refund', accept ? '' : 'rejected');
  r.json(poView(id, p));
}));
// --- admin: voices (text, gender, audio file) — the app reads them live, no APK update
const PV_ID = /^[a-z0-9_]{1,30}$/;
app.get('/admin/payopen-voices', catPerm, w(async (_q, r) => r.json({ voices: poVoiceList((await db.ref('appConfig/payOpenVoices').get()).val()) })));
app.put('/admin/payopen-voices/:id', catPerm, w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!PV_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const b = q.body || {}, text = String(b.text || '').trim().slice(0, 60);
  if (!text) return r.status(400).json({ error: 'ต้องใส่ข้อความ' });
  if (!['male', 'female'].includes(b.gender)) return r.status(400).json({ error: 'gender male|female' });
  const ref = db.ref(`appConfig/payOpenVoices/${id}`), cur = (await ref.get()).val() || {};
  const order = Number.isInteger(b.order) ? b.order : Number.isInteger(cur.order) ? cur.order : 100 + Object.keys((await db.ref('appConfig/payOpenVoices').get()).val() || {}).length;
  const v = { text, gender: b.gender, kind: b.kind === 'arrival' ? 'arrival' : 'refund', audioUrl: cur.audioUrl || '', audioPath: cur.audioPath || '', order, updatedAt: Date.now(), updatedBy: q.uid };
  await ref.set(v); r.json({ id, ...v });
}));
app.put('/admin/payopen-voices/:id/audio', catPerm, express.raw({ type: 'audio/*', limit: '2100kb' }), w(async (q, r) => {
  const id = q.params.id; if (!PV_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const ref = db.ref(`appConfig/payOpenVoices/${id}`), cur = (await ref.get()).val(); if (!cur) return r.status(404).json({ error: 'save the line first' });
  const buf = q.body; if (!Buffer.isBuffer(buf) || buf.length < 200) return r.status(400).json({ error: 'audio required' });
  const mime = /^audio\/[a-z0-9.+-]{1,30}$/.test(String(q.headers['content-type'] || '')) ? String(q.headers['content-type']) : 'audio/mpeg';
  const ext = { 'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/mp4': 'm4a', 'audio/aac': 'aac', 'audio/ogg': 'ogg', 'audio/wav': 'wav', 'audio/x-wav': 'wav', 'audio/webm': 'webm' }[mime] || 'audio';
  const path = `payOpenVoices/${id}_${Date.now()}.${ext}`, tok = crypto.randomUUID(), bucket = admin.storage().bucket(STORAGE_BUCKET);
  await bucket.file(path).save(buf, { contentType: mime, resumable: false, metadata: { metadata: { firebaseStorageDownloadTokens: tok } } });
  const url = `https://firebasestorage.googleapis.com/v0/b/${STORAGE_BUCKET}/o/${encodeURIComponent(path)}?alt=media&token=${tok}`;
  if (cur.audioPath) await bucket.file(cur.audioPath).delete().catch(() => {});
  await ref.update({ audioUrl: url, audioPath: path, updatedAt: Date.now(), updatedBy: q.uid });
  r.json({ audioUrl: url, bytes: buf.length });
}));
app.delete('/admin/payopen-voices/:id', catPerm, w(async (q, r) => {
  const id = q.params.id; if (!PV_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const ref = db.ref(`appConfig/payOpenVoices/${id}`), cur = (await ref.get()).val();
  if (cur?.audioPath) await admin.storage().bucket(STORAGE_BUCKET).file(cur.audioPath).delete().catch(() => {});
  await ref.remove(); r.json({ deleted: id });
}));
app.get('/admin/payopen/tx', perm('coins_confirm'), w(async (q, r) => {
  let qq = db.ref('payOpenTx').orderByKey(); if (q.query.cursor) qq = qq.endBefore(String(q.query.cursor));
  const s = await qq.limitToLast(50).get(), out = []; s.forEach(c => { out.push({ id: c.key, ...c.val() }); });
  out.reverse(); r.json({ tx: out, nextCursor: out.length === 50 ? out[out.length - 1].id : null });
}));
// ===== Voice/video calls + live streaming (Agora RTC; free 10,000 min/month on the Free package, service pauses instead of billing) =====
// Env (set on Cloud Run, never in the app): AGORA_APP_ID, AGORA_APP_CERTIFICATE. Clients only ever get a short-lived token from here.
const AGORA_APP_ID = process.env.AGORA_APP_ID || '', AGORA_CERT = process.env.AGORA_APP_CERTIFICATE || '';
const rtcReady = () => !!(AGORA_APP_ID && AGORA_CERT);
// Stable 31-bit numeric Agora uid per Firebase uid (Agora needs ints; 0 is reserved).
const agoraUid = uid => (crypto.createHash('sha256').update(String(uid)).digest().readUInt32BE(0) & 0x7fffffff) || 1;
function rtcToken(channel, uid, publisher, ttlSec = 3600) {
  const { RtcTokenBuilder, RtcRole } = agoraToken;
  return RtcTokenBuilder.buildTokenWithUid(AGORA_APP_ID, AGORA_CERT, channel, agoraUid(uid), publisher ? RtcRole.PUBLISHER : RtcRole.SUBSCRIBER, ttlSec, ttlSec);
}
const rtcOff = r => r.status(503).json({ code: 'rtc_not_configured', error: 'ระบบโทร/ไลฟ์ยังไม่ได้ตั้งค่า (แอดมินต้องใส่ AGORA_APP_ID และ AGORA_APP_CERTIFICATE)' });
const CALL_ID = /^c[0-9a-z]{6,30}$/, LIVE_ID = /^l[0-9a-z]{6,30}$/;
const newId = p => p + Date.now().toString(36) + crypto.randomBytes(3).toString('hex');

app.get('/rtc/status', w(async (_q, r) => r.json({ ready: rtcReady() })));

// Calls: calls/{id} {callerUid, calleeUid, chatId, video, status ringing|accepted|declined|missed|ended, at, answeredAt, endedAt}
// callInbox/{uid} = {callId, fromUid, fromName, video, at} while ringing (the callee's app listens to it).
app.post('/calls', w(notBanned), w(async (q, r) => {
  if (!rtcReady()) return rtcOff(r);
  const chatId = String(q.body?.chatId || ''); const video = q.body?.video === true;
  { // admin lock for voice / video calls separately (staff can still call)
    const f = await featureLocks(), part = video ? 'videoCall' : 'voiceCall';
    if (f.parts && f.parts[part]) { const role = await loadRole(q.uid); if (!(role && ['admin', 'superadmin', 'moderator'].includes(role.role)))
      return r.status(423).json({ error: 'feature locked', code: 'feature_locked', part, message: f.message || (video ? 'วิดีโอคอลปิดใช้งานชั่วคราว' : 'การโทรปิดใช้งานชั่วคราว') }); }
  }
  if (!/^[A-Za-z0-9_-]{1,300}$/.test(chatId)) return r.status(400).json({ error: 'bad chatId' });
  const other = await chatOther(chatId, q.uid); if (!other) return r.status(403).json({ error: 'not a member' });
  if ((await db.ref(`chats/${chatId}/closed`).get()).exists()) return r.status(403).json({ error: 'แชทนี้ถูกปิดอยู่' });
  if (await blockedEither(q.uid, other)) return r.status(403).json({ error: 'โทรหาผู้ใช้นี้ไม่ได้' });
  if (!(await bump(`rate/calls/${q.uid}/${new Date().toISOString().slice(0, 13)}`, 30))) return r.status(429).json({ error: 'โทรบ่อยเกินไป ลองใหม่ภายหลัง' });
  const busy = (await db.ref(`callInbox/${other}`).get()).val();
  if (busy && Date.now() - (busy.at || 0) < 60e3) return r.status(409).json({ code: 'busy', error: 'ปลายสายกำลังมีสายอื่นอยู่' });
  const id = newId('c'), t = Date.now(), fromName = await nameOf(q.uid);
  await db.ref().update({
    [`calls/${id}`]: { callerUid: q.uid, calleeUid: other, chatId, video, status: 'ringing', at: t },
    [`callInbox/${other}`]: { callId: id, fromUid: q.uid, fromName, video, chatId, at: t }
  });
  sendPush(other, video ? '📹 วิดีโอคอลเข้า' : '📞 สายเข้า', `${fromName} กำลังโทรหาคุณ`, { type: 'call', callId: id, chatId });
  r.json({ callId: id, appId: AGORA_APP_ID, channel: id, uid: agoraUid(q.uid), peerUid: agoraUid(other), token: rtcToken(id, q.uid, true), video });
}));
app.post('/calls/:id/answer', w(notBanned), w(async (q, r) => {
  if (!rtcReady()) return rtcOff(r);
  const id = q.params.id; if (!CALL_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const c = (await db.ref(`calls/${id}`).get()).val(); if (!c || c.calleeUid !== q.uid) return r.status(404).json({ error: 'not found' });
  await db.ref(`callInbox/${q.uid}`).remove();
  if (c.status !== 'ringing') return r.status(409).json({ error: 'สายนี้จบไปแล้ว', status: c.status });
  const accept = q.body?.accept === true, t = Date.now();
  await db.ref(`calls/${id}`).update(accept ? { status: 'accepted', answeredAt: t } : { status: 'declined', endedAt: t });
  if (!accept) return r.json({ status: 'declined' });
  r.json({ status: 'accepted', callId: id, appId: AGORA_APP_ID, channel: id, uid: agoraUid(q.uid), peerUid: agoraUid(c.callerUid), token: rtcToken(id, q.uid, true), video: c.video === true });
}));
app.post('/calls/:id/end', w(async (q, r) => {
  const id = q.params.id; if (!CALL_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const c = (await db.ref(`calls/${id}`).get()).val(); if (!c || (c.callerUid !== q.uid && c.calleeUid !== q.uid)) return r.status(404).json({ error: 'not found' });
  const t = Date.now(), status = c.status === 'ringing' ? (c.callerUid === q.uid ? 'missed' : 'declined') : c.status === 'accepted' ? 'ended' : c.status;
  const inbox = (await db.ref(`callInbox/${c.calleeUid}`).get()).val();
  await db.ref().update({ [`calls/${id}/status`]: status, [`calls/${id}/endedAt`]: c.endedAt || t, ...(inbox?.callId === id ? { [`callInbox/${c.calleeUid}`]: null } : {}) });
  if (status === 'missed') sendPush(c.calleeUid, 'สายที่ไม่ได้รับ', `${await nameOf(c.callerUid)} โทรหาคุณ`, { type: 'chat', chatId: c.chatId });
  r.json({ status, seconds: c.answeredAt ? Math.round(((c.endedAt || t) - c.answeredAt) / 1000) : 0 });
}));

// Live: lives/{id} {hostUid, name, avatar, title, category, status live|ended, viewers, startedAt, lastPing}; liveChat/{id}/{msgId} {uid, name, text, at}
const LIVE_CATS = ['talk', 'music', 'sing', 'game', 'friend'];
app.post('/lives', w(notBanned), w(async (q, r) => {
  if (!rtcReady()) return rtcOff(r);
  const title = String(q.body?.title || '').trim().slice(0, 60) || 'มาคุยกัน', cat = LIVE_CATS.includes(q.body?.category) ? q.body.category : 'talk';
  const m = await moderateText(q.uid, title, { kind: 'live_title' }); if (!m.ok) return r.status(400).json({ error: m.message });
  // one live per host: end any older one
  const mine = (await db.ref('lives').orderByChild('hostUid').equalTo(q.uid).get()).val() || {};
  const upd = {}; for (const [k, v] of Object.entries(mine)) if (v.status === 'live') { upd[`lives/${k}/status`] = 'ended'; upd[`lives/${k}/endedAt`] = Date.now(); }
  const u = (await db.ref(`users/${q.uid}`).get()).val() || {};
  const id = newId('l'), t = Date.now();
  upd[`lives/${id}`] = { hostUid: q.uid, name: u.displayName || 'ผู้ใช้', avatar: u.avatar || '', cover: u.avatar || '', title, category: cat, status: 'live', viewers: 0, startedAt: t, lastPing: t, verified: u.isCreator === true };
  await db.ref().update(upd);
  r.json({ liveId: id, appId: AGORA_APP_ID, channel: id, uid: agoraUid(q.uid), token: rtcToken(id, q.uid, true, 4 * 3600) });
}));
app.post('/lives/:id/ping', w(async (q, r) => {
  const id = q.params.id; if (!LIVE_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const l = (await db.ref(`lives/${id}`).get()).val(); if (!l || l.hostUid !== q.uid) return r.status(404).json({ error: 'not found' });
  if (l.status !== 'live') return r.status(409).json({ status: l.status });
  await db.ref(`lives/${id}/lastPing`).set(Date.now()); r.json({ ok: true, viewers: l.viewers || 0 });
}));
app.post('/lives/:id/end', w(async (q, r) => {
  const id = q.params.id; if (!LIVE_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const l = (await db.ref(`lives/${id}`).get()).val(); if (!l) return r.status(404).json({ error: 'not found' });
  if (l.hostUid !== q.uid && !can(await loadRole(q.uid), 'manage_content')) return r.status(403).json({ error: 'only the host' });
  await db.ref(`lives/${id}`).update({ status: 'ended', endedAt: Date.now() }); r.json({ status: 'ended' });
}));
const liveAlive = l => l && l.status === 'live' && Date.now() - (l.lastPing || 0) < 90e3;
app.post('/lives/:id/join', w(notBanned), w(async (q, r) => {
  if (!rtcReady()) return rtcOff(r);
  const id = q.params.id; if (!LIVE_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const l = (await db.ref(`lives/${id}`).get()).val(); if (!liveAlive(l)) return r.status(410).json({ error: 'ไลฟ์นี้จบแล้ว' });
  if (await blockedEither(q.uid, l.hostUid)) return r.status(403).json({ error: 'ดูไลฟ์นี้ไม่ได้' });
  if (l.hostUid !== q.uid && !(await db.ref(`liveViewers/${id}/${q.uid}`).get()).exists()) {
    await db.ref(`liveViewers/${id}/${q.uid}`).set(Date.now()); await db.ref(`lives/${id}/viewers`).transaction(v => (v || 0) + 1);
    const u = (await db.ref(`users/${q.uid}`).get()).val() || {};
    await db.ref(`liveChat/${id}`).push({ type: 'join', uid: q.uid, name: u.displayName || 'ผู้ใช้', avatar: u.avatar || '', at: Date.now() });
  }
  r.json({ liveId: id, appId: AGORA_APP_ID, channel: id, uid: agoraUid(q.uid), hostUid: agoraUid(l.hostUid), token: rtcToken(id, q.uid, false, 4 * 3600) });
}));
app.post('/lives/:id/leave', w(async (q, r) => {
  const id = q.params.id; if (!LIVE_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  if ((await db.ref(`liveViewers/${id}/${q.uid}`).get()).exists()) {
    await db.ref(`liveViewers/${id}/${q.uid}`).remove(); await db.ref(`lives/${id}/viewers`).transaction(v => Math.max(0, (v || 0) - 1));
  }
  r.json({ ok: true });
}));
app.post('/lives/:id/chat', w(notBanned), w(async (q, r) => {
  const id = q.params.id; if (!LIVE_ID.test(id)) return r.status(400).json({ error: 'bad id' });
  const text = String(q.body?.text || '').trim().slice(0, 200); if (!text) return r.status(400).json({ error: 'empty' });
  const l = (await db.ref(`lives/${id}`).get()).val(); if (!liveAlive(l)) return r.status(410).json({ error: 'ไลฟ์นี้จบแล้ว' });
  if (!(await bump(`rate/liveChat/${q.uid}/${Math.floor(Date.now() / 60e3)}`, 20))) return r.status(429).json({ error: 'ส่งเร็วเกินไป' });
  const m = await moderateText(q.uid, text, { kind: 'live_chat', ref: id }); if (!m.ok) return r.status(400).json({ error: m.message });
  const u = (await db.ref(`users/${q.uid}`).get()).val() || {};
  await db.ref(`liveChat/${id}`).push({ uid: q.uid, name: u.displayName || 'ผู้ใช้', avatar: u.avatar || '', text, at: Date.now() });
  r.json({ ok: true });
}));
// Live gift: paid immediately from the viewer's diamonds (no accept step); host gets recipientPercent like chat gifts.
// Shown to everyone as liveChat {type:'gift'} (banner + chat line) and totalled on lives/{id}/giftDiamonds.
app.post('/lives/:id/gift', w(notBanned), w(async (q, r) => {
  const id = q.params.id, giftId = q.body?.giftId, count = Math.max(1, Math.min(99, parseInt(q.body?.count) || 1));
  if (!LIVE_ID.test(id) || typeof giftId !== 'string' || !ITEM_ID.test(giftId)) return r.status(400).json({ error: 'bad request' });
  const l = (await db.ref(`lives/${id}`).get()).val(); if (!liveAlive(l)) return r.status(410).json({ error: 'ไลฟ์นี้จบแล้ว' });
  if (l.hostUid === q.uid) return r.status(400).json({ error: 'ส่งของขวัญให้ตัวเองไม่ได้' });
  if (await blockedEither(q.uid, l.hostUid)) return r.status(403).json({ error: 'ส่งของขวัญให้ผู้ใช้นี้ไม่ได้' });
  const cfg = await giftCfg(); if (!cfg.enabled) return r.status(403).json({ code: 'gifts_disabled', error: 'ปิดระบบของขวัญชั่วคราว' });
  const item = (await db.ref(`catalog/live/${giftId}`).get()).val();
  if (!item || item.enabled === false || !(item.price > 0)) return r.status(404).json({ error: 'ไม่พบของขวัญนี้' });
  const price = item.price * count, pend = await pendingSum(q.uid);
  const d = await db.ref(`wallets/${q.uid}/diamonds`).transaction(c => (c === null ? null : num(c) - pend >= price ? num(c) - price : undefined));
  if (!d.committed || d.snapshot.val() === null) return r.status(402).json({ code: 'insufficient', error: `เพชรไม่พอ (ต้องการ 💎${price})`, price });
  const hostShare = Math.floor(price * cfg.recipientPercent / 100), adminShare = price - hostShare, t = Date.now();
  await Promise.all([db.ref(`wallets/${l.hostUid}/diamonds`).transaction(c => num(c) + hostShare), db.ref(`wallets/${l.hostUid}/earnedDiamonds`).transaction(c => num(c) + hostShare),
    db.ref('wallets/_system/diamonds').transaction(c => num(c) + adminShare), db.ref(`lives/${id}/giftDiamonds`).transaction(c => num(c) + price)]);
  const u = (await db.ref(`users/${q.uid}`).get()).val() || {};
  await db.ref().update({
    [`liveGiftTx/${id}/${t}_${q.uid.slice(0, 8)}`]: { from: q.uid, to: l.hostUid, giftId, name: item.name, price, count, hostShare, adminShare, at: t },
    [`liveChat/${id}/${t}_g${crypto.randomBytes(3).toString('hex')}`]: { type: 'gift', uid: q.uid, name: u.displayName || 'ผู้ใช้', avatar: u.avatar || '', giftId, giftName: item.name, icon: item.icon || '', imageUrl: item.imageUrl || '', videoUrl: item.videoUrl || '', price: item.price, fx: item.animationEffect || 'POP', count, at: t }
  });
  r.json({ ok: true, price, diamonds: d.snapshot.val() });
}));

// ===== VIP room decor for 1:1 chats =====
const THEMES = ['royal_gold', 'diamond_luxury', 'galaxy', 'romantic', 'neon_vip', 'luxury_palace', 'sakura', 'ocean', 'sunset', 'midnight', 'mint_garden', 'rose_gold'];
const FRAMES = ['none', 'diamond', 'gold', 'sparkle', 'neon', 'rose'], EFFECTS = ['none', 'sparkle', 'hearts', 'stars', 'snow', 'bubbles', 'petals', 'confetti', 'fireflies'], PATTERNS = ['none', 'dots', 'stripes', 'hearts', 'stars', 'diamonds'];
const BUBBLES = ['default', 'rounded', 'glass', 'gold', 'neon', 'gradient', 'outline'];
// Built-in emoji decor stickers (same ids as the app's ChatSticker.BUILT_IN 'e_*' + decor-only 'd_*'); usable without an admin catalog.
const DECOR_BUILTIN = new Set(['hi', 'love', 'lol', 'ok', 'thanks', 'sad', 'wow', 'party', 'sleep', 'hug', 'heart', 'cry'].map(x => 'e_' + x)
  .concat(['crown', 'rose', 'diamond', 'star', 'balloon', 'ribbon', 'moon', 'butterfly', 'sparkles', 'cake', 'teddy', 'rainbow'].map(x => 'd_' + x)));
const DHEX = /^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$/;
function lum(hex) { const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
const contrast = (a, b) => { const x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
function validDecor(chatId, d, extra = { themes: [], frames: [], effects: [] }) {
  if (!d || typeof d !== 'object') return 'decor required';
  if (!THEMES.includes(d.theme) && !extra.themes.includes(d.theme)) return 'theme invalid';
  const bg = d.bg || {}, out = { theme: d.theme, bg: { color: DHEX.test(bg.color || '') ? bg.color : '', pattern: PATTERNS.includes(bg.pattern) ? bg.pattern : 'none', imagePath: '' },
    frame: FRAMES.includes(d.frame) || extra.frames.includes(d.frame) ? d.frame : 'none', effect: EFFECTS.includes(d.effect) || extra.effects.includes(d.effect) ? d.effect : 'none',
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
  const d = validDecor(chatId, q.body?.decor, await decorOptions()); if (typeof d === 'string') return r.status(400).json({ error: d });
  if (d.items.some(i => !DECOR_BUILTIN.has(i.stickerId))) { const st = (await db.ref('catalog/stickers').get()).val() || {}; if (d.items.some(i => !DECOR_BUILTIN.has(i.stickerId) && (!st[i.stickerId] || st[i.stickerId].enabled === false))) return r.status(400).json({ error: 'unknown sticker' }); }
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
  if (amount > 0) sendPush(uid, 'ได้รับ' + (cur === 'diamonds' ? 'เพชร 💎' : 'เหรียญ 🪙'), `แอดมินเติม${cur === 'diamonds' ? 'เพชร' : 'เหรียญ'}ให้คุณ ${amount} ${reason ? '· ' + reason : ''}`.trim(), { type: 'wallet' });
  r.json({ uid, currency: cur, [cur]: res.snapshot.val() });
}));
// Recent manual grants (newest first) for the admin 'เติมให้ผู้ใช้' screen.
app.get('/admin/coin-ledger', perm('coins_confirm'), w(async (_q, r) => {
  const v = (await db.ref('adminCoinLedger').orderByKey().limitToLast(30).get()).val() || {};
  const items = Object.entries(v).reverse().map(([id, x]) => ({ id, ...x }));
  const names = {}; await Promise.all([...new Set(items.map(x => x.uid))].map(async u => { names[u] = await nameOf(u); }));
  r.json({ items: items.map(x => ({ ...x, name: names[x.uid] })) });
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
  if (type === 'post') { const p = (await db.ref(`posts/${b.targetId}`).get()).val(); if (!p || !(await canView(q.uid, p))) return r.status(404).json({ error: 'post not found' }); targetUid = p.authorId; preview = String(p.text || '').slice(0, 140); }
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
      for (const im of post.images || []) await admin.storage().bucket(STORAGE_BUCKET).file(im.path).delete().catch(() => {}); for (const vp of [post.video?.path, post.video?.posterPath].filter(Boolean)) await admin.storage().bucket(STORAGE_BUCKET).file(vp).delete().catch(() => {});
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
  const v = (await db.ref('adminLog').limitToLast(200).get()).val() || {};
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

// ===== Push notifications (FCM). Tokens: pushTokens/{uid}/{hash} (server-only). Opt-out: users/{uid}/settings/push === false =====
const tokKey = t => crypto.createHash('sha256').update(t).digest('hex').slice(0, 32);
async function nameOf(uid) { return String((await db.ref(`users/${uid}/displayName`).get()).val() || 'เพื่อน').slice(0, 40); }
async function sendPush(uid, title, body, data = {}) {
  try {
    if ((await db.ref(`users/${uid}/settings/push`).get()).val() === false) return 0;
    const toks = Object.entries((await db.ref(`pushTokens/${uid}`).get()).val() || {}).filter(([, v]) => v && typeof v.token === 'string');
    if (!toks.length) return 0;
    const res = await admin.messaging().sendEachForMulticast({
      tokens: toks.map(([, v]) => v.token),
      notification: { title: String(title).slice(0, 80), body: String(body).slice(0, 180) },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      // chat messages → 'ft_msg_pop' channel with the app's Soft Pop sound (res/raw/ft_message); older apps without it fall back to the default channel
      android: { priority: 'high', notification: data.kind === 'message' ? { channelId: 'ft_msg_pop', sound: 'ft_message' } : { channelId: 'ft_default', sound: 'default' } },
    });
    const dead = {};
    res.responses.forEach((x, i) => { const c = String(x.error?.code || ''); if (/registration-token-not-registered|invalid-registration-token|invalid-argument/.test(c)) dead[`pushTokens/${uid}/${toks[i][0]}`] = null; });
    if (Object.keys(dead).length) await db.ref().update(dead);
    return res.successCount;
  } catch (e) { console.error('push', e.message); return 0; }
}
app.post('/push/token', w(async (q, r) => {
  const t = q.body?.token; if (typeof t !== 'string' || t.length < 20 || t.length > 4096) return r.status(400).json({ error: 'token required' });
  const k = tokKey(t), cur = (await db.ref(`pushTokens/${q.uid}`).get()).val() || {};
  const upd = { [`pushTokens/${q.uid}/${k}`]: { token: t, at: Date.now() } };
  Object.entries(cur).filter(([x]) => x !== k).sort((a, b) => (b[1]?.at || 0) - (a[1]?.at || 0)).slice(9).forEach(([x]) => { upd[`pushTokens/${q.uid}/${x}`] = null; }); // keep 10 devices
  await db.ref().update(upd); r.json({ ok: true });
}));
app.delete('/push/token', w(async (q, r) => {
  const t = q.body?.token; if (typeof t === 'string' && t.length >= 20) await db.ref(`pushTokens/${q.uid}/${tokKey(t)}`).remove();
  r.json({ ok: true });
}));
// The app writes chat messages straight to RTDB, then calls this so the other member gets a push for the newest message.
app.post('/push/chat/:chatId', w(async (q, r) => {
  const { chatId } = q.params; if (!/^[A-Za-z0-9_-]{1,128}$/.test(chatId)) return r.status(400).json({ error: 'bad chat' });
  if (!(await db.ref(`chatMembers/${chatId}/${q.uid}`).get()).exists()) return r.status(403).json({ error: 'not a member' });
  const c = (await db.ref(`chats/${chatId}`).get()).val() || {};
  if (c.lastSenderUid !== q.uid || Date.now() - (Number(c.lastAt) || 0) > 60000) return r.json({ sent: 0 });
  const rate = db.ref(`pushRate/${chatId}/${q.uid}`), last = Number((await rate.get()).val()) || 0;
  if (Date.now() - last < 4000) return r.json({ sent: 0, throttled: true });
  await rate.set(Date.now());
  const others = Object.keys((await db.ref(`chatMembers/${chatId}`).get()).val() || {}).filter(u => u !== q.uid);
  const me = await nameOf(q.uid); let sent = 0;
  for (const u of others) { if (await blockedEither(q.uid, u)) continue; sent += await sendPush(u, me, String(c.lastMessage || 'ส่งข้อความถึงคุณ'), { type: 'chat', kind: 'message', chatId }); }
  r.json({ sent });
}));

// ===== Launch showcase: admin-uploaded AI sample images for 'ไลฟ์สดตอนนี้' / 'คนใกล้คุณ' while there are no real users yet.
// The app shows them labelled "ตัวอย่าง" only when the real list is empty, so they disappear by themselves once people use the app.
const SHOW_URL = /^https:\/\/friendtalk-brain-123224091480\.asia-southeast1\.run\.app\/ui-bg\/[A-Za-z0-9_-]{1,60}-\d{10,14}$/;
const cleanShow = a => (Array.isArray(a) ? a : []).filter(x => SHOW_URL.test(String(x?.url || ''))).slice(0, 12)
  .map(x => ({ url: String(x.url), name: String(x.name || '').slice(0, 30) }));
app.put('/admin/showcase', perm('config_edit'), w(notBanned), w(async (q, r) => {
  const b = q.body || {};
  const v = { enabled: b.enabled !== false, live: cleanShow(b.live), nearby: cleanShow(b.nearby), updatedBy: q.uid, updatedAt: Date.now() };
  await db.ref('appConfig/showcase').set(v); r.json(v);
}));

// ===== Coin / diamond top-up by bank transfer (works without Google Play). Admin sets pay info + packages, then confirms each request. =====
const TOPUP_DEF = { enabled: false, bankName: '', accountName: '', accountNo: '', promptPay: '', note: '', packages: [] };
async function topupCfg() { return { ...TOPUP_DEF, ...((await db.ref('appConfig/topup').get()).val() || {}) }; }
const cleanPkg = (x, i) => ({ id: String(x?.id || `p${i}`).replace(/[^A-Za-z0-9_-]/g, '').slice(0, 24) || `p${i}`, label: String(x?.label || '').slice(0, 40),
  coins: Math.max(0, Math.min(1e7, parseInt(x?.coins) || 0)), diamonds: Math.max(0, Math.min(1e7, parseInt(x?.diamonds) || 0)),
  priceThb: Math.max(0, Math.min(1e6, Number(x?.priceThb) || 0)), active: x?.active !== false });
app.get('/coins/topup', w(async (q, r) => {
  const c = await topupCfg();
  r.json({ ...c, packages: (c.packages || []).filter(p => p.active && (p.coins || p.diamonds) && p.priceThb > 0) });
}));
app.put('/admin/coins/topup', perm('coins_confirm'), w(notBanned), w(async (q, r) => {
  const b = q.body || {};
  const v = { enabled: b.enabled === true, bankName: String(b.bankName || '').slice(0, 60), accountName: String(b.accountName || '').slice(0, 80),
    accountNo: String(b.accountNo || '').slice(0, 40), promptPay: String(b.promptPay || '').slice(0, 40), note: String(b.note || '').slice(0, 300),
    packages: (Array.isArray(b.packages) ? b.packages : []).slice(0, 30).map(cleanPkg), updatedBy: q.uid, updatedAt: Date.now() };
  await db.ref('appConfig/topup').set(v); r.json(v);
}));
app.post('/coins/topup', w(notBanned), w(async (q, r) => {
  const c = await topupCfg(); if (!c.enabled) return r.status(403).json({ error: 'ยังไม่เปิดเติมเงินแบบโอน' });
  const pkg = (c.packages || []).find(p => p.id === q.body?.packageId && p.active);
  const ref = String(q.body?.paymentRef || '').trim().slice(0, 100);
  if (!pkg) return r.status(400).json({ error: 'ไม่พบแพ็กเกจ' });
  if (ref.length < 3) return r.status(400).json({ error: 'กรุณากรอกเลขอ้างอิงการโอน' });
  const mine = Object.values((await db.ref(`coinTransactions/${q.uid}`).get()).val() || {});
  if (mine.filter(t => t?.status === 'pending').length >= 3) return r.status(429).json({ error: 'มีคำขอรอยืนยันอยู่แล้ว 3 รายการ' });
  const tx = db.ref(`coinTransactions/${q.uid}`).push();
  await tx.set({ type: 'topup', packageId: pkg.id, coins: pkg.coins, diamonds: pkg.diamonds, priceThb: pkg.priceThb, paymentRef: ref, status: 'pending', createdAt: Date.now() });
  r.status(202).json({ txId: tx.key, status: 'pending' });
}));
app.get('/coins/topup/mine', w(async (q, r) => {
  const v = (await db.ref(`coinTransactions/${q.uid}`).orderByKey().limitToLast(20).get()).val() || {};
  r.json({ items: Object.entries(v).map(([id, x]) => ({ id, ...x })).filter(x => x.type === 'topup' || x.type === 'purchase').reverse() });
}));
app.get('/admin/coins/pending', perm('coins_confirm'), w(async (q, r) => {
  const all = (await db.ref('coinTransactions').get()).val() || {}, out = [];
  for (const [uid, txs] of Object.entries(all)) for (const [id, t] of Object.entries(txs || {})) if (t?.status === 'pending' && (t.type === 'topup' || t.type === 'purchase')) out.push({ uid, id, ...t });
  out.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  const list = out.slice(0, 200);
  await Promise.all(list.map(async x => { x.name = await nameOf(x.uid); }));
  r.json({ items: list });
}));
app.post('/admin/coins/reject', perm('coins_confirm'), w(notBanned), w(async (q, r) => {
  const { uid, txId } = q.body || {}; const reason = String(q.body?.reason || '').slice(0, 200);
  if (typeof uid !== 'string' || typeof txId !== 'string') return r.status(400).json({ error: 'uid and txId required' });
  const res = await db.ref(`coinTransactions/${uid}/${txId}`).transaction(t => { if (t === null) return null; if (t.status !== 'pending') return; return { ...t, status: 'rejected', reason, rejectedBy: q.uid, rejectedAt: Date.now() }; });
  if (!res.committed || res.snapshot.val()?.status !== 'rejected') return r.status(409).json({ error: 'tx not found or not pending' });
  sendPush(uid, 'คำขอเติมเงินไม่ผ่าน', reason || 'กรุณาตรวจสอบการโอนแล้วลองใหม่', { type: 'wallet' });
  r.json({ status: 'rejected' });
}));
// ===== Account deletion (Google Play requirement): in-app POST /me/account/delete + public web request page, and the public privacy policy.
// Removes the profile, photos, posts, wallet, location, tokens and app data of the account, then the Firebase Auth user itself.
// Kept (stated on /privacy): money records needed for accounting/law (coinTransactions, withdrawals, gift/VIP purchase logs) and
// reports/bans made against the account (safety), plus messages already delivered to the other member of a chat (shown as a deleted user).
const PER_UID = ['users', 'wallets', 'giftPending', 'vip', 'pushTokens', 'radar', 'shakeWaiting', 'shakeResults', 'swipes', 'friendRequests', 'matchQueue',
  'private', 'idv', 'kyc', 'ageInfo', 'onboarding', 'usage', 'quotaBonus', 'quotaGrants', 'chatRulesAccepted', 'termsAccepted', 'creators', 'creatorMetrics',
  'creatorApplications', 'companionApplications', 'userChats', 'callInbox', 'payOpenInbox', 'payOpenResults', 'following', 'followers', 'followCounts', 'friends', 'authorPosts',
  'deviceOf', 'postBlocks', 'chatBlocks', 'blocks', 'notifSeen', 'randomCards', 'randomSeen'];
async function deleteAccountData(uid) {
  const del = {};
  for (const k of PER_UID) del[`${k}/${uid}`] = null;
  const val = async p => (await db.ref(p).get()).val() || {};
  const [posts, friends, following, followers] = await Promise.all([val(`authorPosts/${uid}`), val(`friends/${uid}`), val(`following/${uid}`), val(`followers/${uid}`)]);
  for (const id of Object.keys(posts)) {
    const p = (await db.ref(`posts/${id}`).get()).val(); if (!p || p.authorId !== uid) continue;
    Object.assign(del, { [`posts/${id}`]: null, [`postLikes/${id}`]: null, [`postComments/${id}`]: null });
    for (const t of p.hashtags || []) del[`hashtags/${t}/${id}`] = null;
  }
  for (const o of Object.keys(friends)) del[`friends/${o}/${uid}`] = null;
  for (const o of Object.keys(following)) del[`followers/${o}/${uid}`] = null;
  for (const o of Object.keys(followers)) del[`following/${o}/${uid}`] = null;
  const lives = (await db.ref('lives').orderByChild('hostUid').equalTo(uid).get()).val() || {};
  for (const id of Object.keys(lives)) del[`lives/${id}`] = null;
  await db.ref().update(del);
  const bucket = admin.storage().bucket(STORAGE_BUCKET);
  for (const prefix of [`posts/${uid}/`, `idv/${uid}/`, `avatars/${uid}/`]) await bucket.deleteFiles({ prefix }).catch(() => {});
  await db.ref(`deletedAccounts/${uid}`).set({ at: Date.now() });
  await admin.auth().deleteUser(uid).catch(e => { if (e?.code !== 'auth/user-not-found') throw e; });
}
app.post('/me/account/delete', w(async (q, r) => {
  if (q.body?.confirm !== 'DELETE') return r.status(400).json({ error: 'confirm required' });
  await deleteAccountData(q.uid);
  r.json({ ok: true });
}));
// Admin: process a web deletion request (after checking the requester owns the account).
app.get('/admin/deletion-requests', perm('ban_users'), w(async (_q, r) => {
  const v = (await db.ref('deletionRequests').orderByKey().limitToLast(50).get()).val() || {};
  r.json({ items: Object.entries(v).map(([id, x]) => ({ id, ...x })).reverse() });
}));
app.post('/admin/deletion-requests/:id/done', perm('ban_users'), w(async (q, r) => {
  const id = q.params.id; if (!/^[A-Za-z0-9_-]{1,40}$/.test(id)) return r.status(400).json({ error: 'bad id' });
  const req = (await db.ref(`deletionRequests/${id}`).get()).val(); if (!req) return r.status(404).json({ error: 'not found' });
  let uid = typeof q.body?.uid === 'string' ? q.body.uid : '';
  if (!uid && req.email) uid = (await admin.auth().getUserByEmail(req.email).catch(() => null))?.uid || '';
  if (uid) await deleteAccountData(uid);
  await db.ref(`deletionRequests/${id}`).update({ status: uid ? 'deleted' : 'no_account', uid: uid || null, by: q.uid, doneAt: Date.now() });
  await db.ref('adminLog').push({ action: 'account_delete', target: uid || req.email, by: q.uid, at: Date.now() });
  r.json({ ok: true, uid: uid || null });
}));
const CONTACT = () => process.env.CONTACT_EMAIL || '';
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const page = (title, body) => `<!doctype html><html lang="th"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<style>body{font-family:system-ui,-apple-system,"Noto Sans Thai",sans-serif;max-width:760px;margin:0 auto;padding:20px 16px 48px;line-height:1.65;color:#1e1b2e;background:#fff}
h1{color:#c2187a;font-size:26px}h2{font-size:18px;margin-top:28px}input,textarea{width:100%;box-sizing:border-box;padding:10px;border:1px solid #ccc;border-radius:10px;font:inherit}
button{background:linear-gradient(90deg,#ff2fa8,#9b3dff);color:#fff;border:0;border-radius:12px;padding:12px 20px;font:inherit;font-weight:700}
.box{background:#f7f2fb;border-radius:14px;padding:14px 16px}.muted{color:#666;font-size:14px}</style></head><body>${body}</body></html>`;
const contactLine = () => CONTACT() ? `อีเมล / Email: <a href="mailto:${esc(CONTACT())}">${esc(CONTACT())}</a>` : 'ผ่านเมนู "รายงาน / ติดต่อแอดมิน" ในแอป หรือแบบฟอร์มที่หน้า <a href="/delete-account">/delete-account</a>';
app.get('/delete-account', (_q, r) => r.type('html').send(page('ลบบัญชี FriendTalk', `
<h1>ลบบัญชี FriendTalk</h1>
<div class="box"><b>วิธีที่เร็วที่สุด — ลบในแอป:</b> เปิดแอป FriendTalk → แท็บ <b>โปรไฟล์</b> → เลื่อนลงล่างสุด → <b>🗑 ลบบัญชี</b> → พิมพ์ยืนยัน บัญชีจะถูกลบทันที</div>
<h2>ส่งคำขอลบบัญชีผ่านเว็บ (กรณีเข้าแอปไม่ได้)</h2>
<form method="post" action="/delete-account"><p>อีเมลที่ใช้เข้าสู่ระบบ<br><input name="email" type="email" required maxlength="200"></p>
<p>ชื่อในแอป (ถ้ามี)<br><input name="name" maxlength="80"></p><p>หมายเหตุ (ไม่บังคับ)<br><textarea name="note" rows="3" maxlength="500"></textarea></p>
<button type="submit">ส่งคำขอลบบัญชี</button></form>
<p class="muted">ทีมงานจะตรวจสอบว่าเป็นเจ้าของบัญชีจริง และลบภายใน 30 วัน</p>
<h2>ข้อมูลที่ถูกลบ</h2><p>โปรไฟล์ รูปภาพ โพสต์ ความคิดเห็นในโพสต์ เพื่อน/ผู้ติดตาม ตำแหน่งโดยประมาณ ยอดเหรียญ/เพชรคงเหลือ สถานะ VIP เอกสารยืนยันตัวตน โทเคนแจ้งเตือน และบัญชีเข้าสู่ระบบ</p>
<h2>ข้อมูลที่เก็บต่อ</h2><p>ประวัติการชำระเงิน/ถอนเงิน (เก็บตามกฎหมายบัญชีสูงสุด 5 ปี) · รายงานและการแบนเพื่อความปลอดภัยของผู้ใช้อื่น · ข้อความที่ส่งถึงผู้อื่นแล้วจะยังอยู่ในแชทของผู้รับ โดยแสดงเป็นผู้ใช้ที่ลบบัญชีแล้ว</p>
<p class="muted">English: Delete in the app (Profile → 🗑 Delete account) or submit the form above. We delete your profile, photos, posts, social graph, approximate location, balances, VIP, ID documents and login. Payment records (up to 5 years, legal), safety reports and messages already delivered to others are kept.</p>
<p><a href="/privacy">นโยบายความเป็นส่วนตัว / Privacy policy</a></p>`)));
const delHits = new Map();
app.post('/delete-account', express.urlencoded({ extended: false, limit: '8kb' }), w(async (q, r) => {
  const ip = String(q.headers['x-forwarded-for'] || q.ip || '').split(',')[0].trim(), n = (delHits.get(ip) || 0) + 1; delHits.set(ip, n);
  if (delHits.size > 5000) delHits.clear();
  const email = String(q.body?.email || '').trim().toLowerCase().slice(0, 200);
  if (n > 5 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return r.status(400).type('html').send(page('ลบบัญชี', '<h1>ส่งไม่สำเร็จ</h1><p>กรุณาตรวจสอบอีเมลแล้วลองใหม่</p><p><a href="/delete-account">กลับ</a></p>'));
  await db.ref('deletionRequests').push({ email, name: String(q.body?.name || '').slice(0, 80), note: String(q.body?.note || '').slice(0, 500), status: 'pending', at: Date.now() });
  r.type('html').send(page('ลบบัญชี', '<h1>ได้รับคำขอแล้ว ✅</h1><p>ทีมงานจะตรวจสอบและลบบัญชีภายใน 30 วัน / We received your request and will delete the account within 30 days.</p>'));
}));
// Child safety standards (required by Google Play for social apps): public page linked from the Play Console declaration.
app.get('/child-safety', (_q, r) => r.type('html').send(page('มาตรฐานความปลอดภัยของเด็ก FriendTalk', `
<h1>มาตรฐานความปลอดภัยของเด็ก — FriendTalk</h1><p class="muted">Child Safety Standards · ปรับปรุงล่าสุด: 16 ตุลาคม 2026</p>
<p>FriendTalk (แพ็กเกจ th.friendtalk.app) เป็นแอปสำหรับผู้ใช้อายุ 18 ปีขึ้นไปเท่านั้น เรา<strong>ไม่ยอมรับ</strong>การล่วงละเมิดทางเพศและการแสวงประโยชน์จากเด็ก (CSAE) และสื่อการล่วงละเมิดทางเพศเด็ก (CSAM) ในทุกรูปแบบ</p>
<h2>1. สิ่งที่ห้ามโดยเด็ดขาด</h2><ul>
<li>เนื้อหา รูปภาพ วิดีโอ หรือข้อความใดๆ ที่แสดง ส่งเสริม หรือชักชวนให้เกิดการล่วงละเมิดทางเพศหรือการแสวงประโยชน์จากเด็ก</li>
<li>การล่อลวง (grooming) การติดต่อผู้เยาว์ในเชิงชู้สาว หรือการขอรูปภาพจากผู้เยาว์</li>
<li>การใช้งานโดยผู้ที่อายุต่ำกว่า 18 ปี</li></ul>
<h2>2. วิธีที่เราป้องกัน</h2><ul>
<li>ยืนยันอายุตอนสมัคร และระงับบัญชีที่พบว่าอายุต่ำกว่า 18 ปี</li>
<li>ตรวจรูปภาพที่อัปโหลดด้วยระบบตรวจเนื้อหาอัตโนมัติ และกรองข้อความในแชท</li>
<li>ทีมผู้ดูแลตรวจสอบรายงานและเนื้อหาที่ถูกระบบตั้งค่าสถานะ</li></ul>
<h2>3. การรายงานในแอป</h2>
<p>ผู้ใช้กดรายงานผู้ใช้ โพสต์ ความคิดเห็น ห้องแชท หรือไลฟ์ได้จากเมนู "🚩 รายงาน" และบล็อกผู้ใช้ได้ทันที รายงานที่เกี่ยวกับความปลอดภัยของเด็กจะได้รับการตรวจสอบเป็นลำดับแรก</p>
<h2>4. เมื่อพบเนื้อหา CSAM / CSAE</h2><ul>
<li>ลบเนื้อหาทันทีและระงับบัญชีที่เกี่ยวข้องอย่างถาวร</li>
<li>เก็บหลักฐานตามที่กฎหมายกำหนด และรายงานต่อหน่วยงานที่เกี่ยวข้อง เช่น สำนักงานตำรวจแห่งชาติ / ศูนย์ปราบปรามการกระทำความผิดต่อเด็ก (TICAC) และ NCMEC ตามกฎหมายที่บังคับใช้</li>
<li>ให้ความร่วมมือกับเจ้าหน้าที่ในการสอบสวน</li></ul>
<h2>5. การปฏิบัติตามกฎหมาย</h2>
<p>เราปฏิบัติตามกฎหมายคุ้มครองเด็กที่เกี่ยวข้อง รวมถึงกฎหมายไทยและนโยบายของ Google Play</p>
<h2>6. ผู้ติดต่อด้านความปลอดภัยของเด็ก</h2>
<p>อีเมล: <a href="mailto:mama.ff9522@gmail.com">mama.ff9522@gmail.com</a> หรือใช้เมนูรายงานในแอป</p>
<hr><p class="muted">FriendTalk has zero tolerance for child sexual abuse and exploitation (CSAE) and child sexual abuse material (CSAM). The app is for adults 18+ only. Users can report users and content in the app; reported content is reviewed, removed, the accounts are banned, and CSAM is reported to the relevant authorities (including NCMEC and Thai police) as required by law. Child-safety contact: mama.ff9522@gmail.com</p>
`)));
app.get('/privacy', (_q, r) => r.type('html').send(page('นโยบายความเป็นส่วนตัว FriendTalk', `
<h1>นโยบายความเป็นส่วนตัว — FriendTalk</h1><p class="muted">ปรับปรุงล่าสุด: 15 ตุลาคม 2026</p>
<p>FriendTalk เป็นแอปหาเพื่อนคุย แชท โทร และไลฟ์สด สำหรับผู้ใช้อายุ 18 ปีขึ้นไป นโยบายนี้อธิบายว่าเราเก็บข้อมูลอะไร ใช้ทำอะไร และคุณควบคุมข้อมูลได้อย่างไร</p>
<h2>1. ข้อมูลที่เราเก็บ</h2><ul>
<li><b>บัญชี:</b> อีเมล ชื่อ และรหัสผู้ใช้จาก Google/อีเมล ผ่าน Firebase Authentication</li>
<li><b>โปรไฟล์:</b> ชื่อที่แสดง รูปโปรไฟล์/ปก ประวัติย่อ ความสนใจ เพศ และวันเกิด/อายุ (เพื่อยืนยันว่าอายุ 18+)</li>
<li><b>ตำแหน่งโดยประมาณ:</b> ใช้เมื่อคุณเปิดฟีเจอร์ "คนใกล้คุณ/เรดาร์" เท่านั้น เราปัดเป็นระดับประมาณ 1 กม. และไม่แสดงพิกัดแม่นยำให้ผู้อื่น</li>
<li><b>เนื้อหาที่คุณสร้าง:</b> โพสต์ รูป วิดีโอ ความคิดเห็น ข้อความแชท และของขวัญที่ส่ง</li>
<li><b>โทรและไลฟ์สด:</b> เสียงและวิดีโอส่งแบบเรียลไทม์ผ่าน Agora เราไม่บันทึกเสียงหรือวิดีโอการโทร/ไลฟ์ เก็บเฉพาะข้อมูลห้อง (ผู้เข้าร่วม เวลา ข้อความในไลฟ์)</li>
<li><b>การยืนยันตัวตน (ถ้าคุณส่ง):</b> รูปบัตรและเซลฟี่ ใช้ตรวจอายุ/ตัวตน และลบไฟล์ภาพหลังตรวจเสร็จ</li>
<li><b>การชำระเงิน:</b> รายการซื้อเหรียญ/เพชร/VIP ผ่าน Google Play (เราไม่เห็นข้อมูลบัตร) และประวัติยอดคงเหลือ</li>
<li><b>อุปกรณ์:</b> โทเคนแจ้งเตือน (Firebase Cloud Messaging) รหัสอุปกรณ์แบบแฮชเพื่อป้องกันการโกง และรหัสโฆษณา (Advertising ID) ที่ Google AdMob ใช้แสดงโฆษณา</li></ul>
<h2>2. เราใช้ข้อมูลเพื่อ</h2><p>ให้บริการแชท โทร ไลฟ์ และจับคู่เพื่อน · แสดงคนใกล้เคียง · ประมวลผลการซื้อและของขวัญ · ส่งการแจ้งเตือน · ตรวจจับสแปม การโกง และเนื้อหาไม่เหมาะสม (รวมถึงการตรวจภาพอัตโนมัติ) · ปกป้องผู้เยาว์ · แสดงโฆษณา</p>
<p>ตัวละคร AI ในแอปมีป้ายกำกับ "AI" เสมอ ข้อความที่คุณคุยกับ AI ถูกส่งไปประมวลผลเพื่อสร้างคำตอบ</p>
<h2>3. การแบ่งปันข้อมูล</h2><p>เราไม่ขายข้อมูลส่วนบุคคล เราใช้ผู้ให้บริการต่อไปนี้: Google Firebase / Google Cloud (เก็บข้อมูล ระบบล็อกอิน แจ้งเตือน) · Agora (โทรและไลฟ์) · Google AdMob (โฆษณา) · Google Play Billing (ชำระเงิน) ข้อมูลอาจถูกเปิดเผยเมื่อกฎหมายกำหนด</p>
<h2>4. ความปลอดภัย</h2><p>ข้อมูลส่งผ่านการเข้ารหัส (HTTPS/TLS) และจำกัดสิทธิ์การเข้าถึงฐานข้อมูลตามบัญชี</p>
<h2>5. การเก็บรักษาและการลบ</h2><p>เราเก็บข้อมูลตราบที่บัญชียังใช้งาน คุณลบบัญชีได้เองในแอป (โปรไฟล์ → 🗑 ลบบัญชี) หรือที่ <a href="/delete-account">หน้าคำขอลบบัญชี</a> ประวัติการเงินเก็บตามกฎหมายสูงสุด 5 ปี</p>
<h2>6. สิทธิของคุณ</h2><p>ขอเข้าถึง แก้ไข ลบ หรือคัดค้านการประมวลผลข้อมูลได้ตาม พ.ร.บ.คุ้มครองข้อมูลส่วนบุคคล (PDPA) ปิดการติดตามโฆษณาได้ในการตั้งค่า Google ของเครื่อง</p>
<h2>7. เด็ก</h2><p>แอปนี้ไม่อนุญาตให้ผู้ที่อายุต่ำกว่า 18 ปีใช้งาน หากพบบัญชีของผู้เยาว์ เราจะระงับและลบ</p>
<h2>8. ติดต่อเรา</h2><p>${contactLine()}</p>
<hr><h2>English summary</h2><p>FriendTalk (18+) collects account info (email, name), profile data, approximate location (~1 km, only when you use Nearby/Radar; never shown precisely), content you post and messages, optional ID verification images (deleted after review), purchase records (via Google Play; we never see card data), push tokens, a hashed device ID (anti-fraud) and the Advertising ID (Google AdMob). Calls and live video stream in real time through Agora and are not recorded. Processors: Google Firebase/Cloud, Agora, Google AdMob, Google Play Billing. We do not sell personal data. Delete your account in the app (Profile → Delete account) or at <a href="/delete-account">/delete-account</a>. Contact: ${CONTACT() ? esc(CONTACT()) : 'in-app report menu'}.</p>`)));
app.use((e, _q, r, _n) => {
  if (e.type === 'entity.too.large') return r.status(413).json({ error: 'request too large' });
  if (e.type === 'entity.parse.failed') return r.status(400).json({ error: 'invalid JSON' });
  r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' });
});
// ===== First-run defaults so a fresh project works without any admin setup.
// Each block only writes when that config has never been set — an admin's own settings (including turning a feature off) are never overwritten.
const BRAIN_URL = 'https://friendtalk-brain-123224091480.asia-southeast1.run.app';
app.get('/bot-assets/:f', (q, r) => {
  if (!/^bot[1-9]\.webp$/.test(q.params.f)) return r.status(400).end();
  r.set('Cache-Control', 'public, max-age=86400'); r.type('image/webp');
  r.sendFile(new URL(`./assets/bots/${q.params.f}`, import.meta.url).pathname, e => { if (e && !r.headersSent) r.status(404).end(); });
});
const SEED_BOTS = {
  mint: { name: 'มิ้นท์', avatarUrl: `${BRAIN_URL}/bot-assets/bot1.webp`, bio: 'ชอบคาเฟ่ ดูซีรีส์ เล่าเรื่องสนุก ๆ ให้ฟังได้ทั้งวัน', interests: ['คาเฟ่', 'ซีรีส์', 'ท่องเที่ยว'], enabled: true,
    persona: 'ผู้หญิงร่าเริง เป็นกันเอง พูดสุภาพ ใช้คำลงท้าย ค่ะ/นะคะ ชวนคุยเรื่องชีวิตประจำวัน คาเฟ่ ซีรีส์ ถามกลับเสมอ' },
  fah: { name: 'ฟ้า', avatarUrl: `${BRAIN_URL}/bot-assets/bot2.webp`, bio: 'สายเพลงและหนัง ชอบฟังเรื่องของคุณ', interests: ['ดนตรี', 'หนัง', 'ถ่ายรูป'], enabled: true,
    persona: 'ผู้หญิงอ่อนโยน ใจเย็น เป็นผู้ฟังที่ดี ชอบแนะนำเพลงและหนัง ใช้ ค่ะ/นะคะ ให้กำลังใจเมื่ออีกฝ่ายเครียด' },
  ploy: { name: 'พลอย', avatarUrl: `${BRAIN_URL}/bot-assets/bot3.webp`, bio: 'ชอบเที่ยวทะเล ทำอาหาร และคุยเรื่องฮา ๆ', interests: ['ทะเล', 'ทำอาหาร', 'สัตว์เลี้ยง'], enabled: true,
    persona: 'ผู้หญิงสดใส ตลก ขี้เล่นแบบสุภาพ ชอบเล่าเรื่องเที่ยวและสูตรอาหาร ใช้ ค่ะ/นะคะ' },
  nam: { name: 'น้ำ', avatarUrl: `${BRAIN_URL}/bot-assets/bot4.webp`, bio: 'สายเกมและอนิเมะ ชวนคุยได้ทุกเรื่อง', interests: ['เกม', 'อนิเมะ', 'เทคโนโลยี'], enabled: true,
    persona: 'ผู้หญิงชอบเกมและอนิเมะ คุยสนุก เป็นกันเอง ใช้ ค่ะ/นะคะ แนะนำเกมหรืออนิเมะตามที่อีกฝ่ายชอบ' }
};
const SEED_CHAT_GIFTS = [['rose', 'กุหลาบ', '🌹', 10], ['coffee', 'กาแฟ', '☕', 20], ['heart', 'หัวใจ', '💖', 30], ['bear', 'ตุ๊กตาหมี', '🧸', 50],
  ['cake', 'เค้ก', '🎂', 80], ['bouquet', 'ช่อดอกไม้', '💐', 100], ['crown', 'มงกุฎ', '👑', 300], ['ring', 'แหวนเพชร', '💍', 500]];
const SEED_CHAT_STICKERS = [['hi', 'สวัสดี', '👋'], ['love', 'รักนะ', '🥰'], ['lol', 'ขำ', '🤣'], ['ok', 'โอเค', '👌'], ['thanks', 'ขอบคุณ', '🙏'],
  ['sad', 'เศร้า', '🥺'], ['wow', 'ว้าว', '😮'], ['party', 'เย่', '🥳'], ['sleep', 'ฝันดี', '😴'], ['hug', 'กอด', '🤗']];
async function seedDefaults() {
  const once = async (path, value) => { await db.ref(path).transaction(c => (c === null ? value : undefined)); };
  try {
    await once('aiBots', SEED_BOTS);
    await once('appConfig/aiBotsEnabled', true);
    const gifts = {}; SEED_CHAT_GIFTS.forEach(([id, name, icon, price], i) => { gifts[id] = { name, icon, price, imageUrl: '', category: 'ทั่วไป', enabled: true, order: i, animationEffect: 'POP' }; });
    await once('catalog/chat', gifts);
    const st = {}; SEED_CHAT_STICKERS.forEach(([id, name, icon], i) => { st[id] = { name, icon, price: 0, imageUrl: '', category: 'อีโมจิ', enabled: true, order: i, animationEffect: 'POP' }; });
    await once('catalog/chatStickers', st);
  } catch (e) { console.error('seedDefaults', e?.message); }
}
seedDefaults();
app.listen(process.env.PORT || 8080);
