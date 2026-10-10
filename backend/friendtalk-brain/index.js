import express from 'express';
import admin from 'firebase-admin';
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
app.use(['/coins', '/age', '/config', '/admin', '/superadmin', '/me', '/match', '/bot', '/ui', '/chats', '/swipe'], w(auth));
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
const BOT_DAILY_LIMIT = parseInt(process.env.BOT_DAILY_LIMIT || '2000', 10);
const BOT_HOURLY_PER_USER = parseInt(process.env.BOT_HOURLY_PER_USER || '30', 10);
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
app.post('/match', w(notBanned), w(async (q, r) => {
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
  if (!(await bump(`botRate/${q.uid}/${hour}`, BOT_HOURLY_PER_USER))) return r.status(429).json({ error: 'rate limit: try again later' });
  if (!(await bump(`botDaily/${day}`, BOT_DAILY_LIMIT))) return r.status(429).json({ error: 'daily bot limit reached' });
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
  const out = [];
  for (const [uid, u] of Object.entries(users.val() || {})) {
    if (uid === q.uid || s[uid] || !u || !u.displayName) continue;
    const ban = b[uid]; if (ban && (!ban.until || ban.until > now)) continue;
    out.push(publicProfile(uid, u)); if (out.length >= limit) break;
  }
  r.json({ candidates: out });
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
app.use((e, _q, r, _n) => {
  if (e.type === 'entity.too.large') return r.status(413).json({ error: 'request too large' });
  if (e.type === 'entity.parse.failed') return r.status(400).json({ error: 'invalid JSON' });
  r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' });
});
app.listen(process.env.PORT || 8080);
