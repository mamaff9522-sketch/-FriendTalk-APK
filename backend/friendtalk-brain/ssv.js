// AdMob rewarded-ad Server-Side Verification (ECDSA P-256 / SHA-256).
// https://developers.google.com/admob/android/ssv
import crypto from 'node:crypto';
export const VERIFIER_KEYS_URL = 'https://www.gstatic.com/admob/reward/verifier-keys.json';
let cache = { at: 0, keys: null };
export async function fetchKeys(fetchFn = fetch) {
  if (cache.keys && Date.now() - cache.at < 6 * 3600 * 1000) return cache.keys;
  const j = await (await fetchFn(VERIFIER_KEYS_URL)).json();
  const keys = {}; for (const k of j.keys || []) keys[String(k.keyId)] = k.pem;
  cache = { at: Date.now(), keys }; return keys;
}
/** rawQuery: the URL query string exactly as received (without '?'). keys: {keyId: pem}. */
export function verifySsv(rawQuery, keys) {
  const i = rawQuery.indexOf('&signature=');
  if (i < 0) return { ok: false, reason: 'no signature' };
  const message = rawQuery.slice(0, i);
  const rest = new URLSearchParams(rawQuery.slice(i + 1));
  const sig = rest.get('signature'), keyId = rest.get('key_id');
  const pem = keys[String(keyId)];
  if (!sig || !pem) return { ok: false, reason: 'unknown key' };
  const der = Buffer.from(sig.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
  const ok = crypto.verify('sha256', Buffer.from(message), { key: pem, dsaEncoding: 'der' }, der);
  if (!ok) return { ok: false, reason: 'bad signature' };
  return { ok: true, params: Object.fromEntries(new URLSearchParams(message)) };
}
