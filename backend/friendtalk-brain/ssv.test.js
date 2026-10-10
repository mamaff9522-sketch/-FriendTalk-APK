// node --test ssv.test.js  (self-generated P-256 key; Google publishes no fixed test vector)
import test from 'node:test'; import assert from 'node:assert'; import crypto from 'node:crypto';
import { verifySsv } from './ssv.js';
const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const keys = { '3335741209': publicKey.export({ type: 'spki', format: 'pem' }) };
const sign = m => crypto.sign('sha256', Buffer.from(m), { key: privateKey, dsaEncoding: 'der' }).toString('base64url');
const msg = 'ad_network=5450213213286189855&ad_unit=1234567890&custom_data=aiBotMessagesPerDay&reward_amount=1&reward_item=Reward&timestamp=1507770365237823&transaction_id=18fa792de1bca816048293fc71035638&user_id=uid123';
test('valid signature accepted and params parsed', () => {
  const r = verifySsv(`${msg}&signature=${sign(msg)}&key_id=3335741209`, keys);
  assert.equal(r.ok, true); assert.equal(r.params.user_id, 'uid123'); assert.equal(r.params.custom_data, 'aiBotMessagesPerDay');
});
test('tampered message rejected', () => {
  const r = verifySsv(`${msg.replace('uid123', 'attacker')}&signature=${sign(msg)}&key_id=3335741209`, keys);
  assert.equal(r.ok, false);
});
test('unknown key rejected', () => assert.equal(verifySsv(`${msg}&signature=${sign(msg)}&key_id=1`, keys).ok, false));
test('missing signature rejected', () => assert.equal(verifySsv(msg, keys).ok, false));
