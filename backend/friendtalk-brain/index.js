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
app.use(express.json({ limit: '32kb' }));
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
app.use(['/coins', '/age', '/config', '/admin', '/superadmin'], w(auth));
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
app.use((e, _q, r, _n) => r.status(e.code === 404 ? 404 : 500).json({ error: e.code === 404 ? 'user not found' : 'internal' }));
app.listen(process.env.PORT || 8080);
