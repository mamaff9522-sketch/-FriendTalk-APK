#!/bin/bash
# Allow the Google Play build (th.friendtalk.app, signed by Google's app-signing key) to use the Firebase Android API key.
# Keeps every app already allowed (com.example …) and only adds the Play app. Safe to run twice.
P=friendtalk-4e623
KEY_STRING=AIzaSyAQi63Z5_h9kikOPY8SdGM1Juw-h59CQfU
NEW_PKG=th.friendtalk.app
NEW_SHA=D3:60:53:13:BF:17:F9:4E:34:32:A7:DE:5C:35:90:25:88:44:19:D1
KEY=""
for n in $(gcloud services api-keys list --project "$P" --format='value(name)'); do
  s=$(gcloud services api-keys get-key-string "$n" --format='value(keyString)' 2>/dev/null)
  if [ "$s" = "$KEY_STRING" ]; then KEY="$n"; fi
done
if [ -z "$KEY" ]; then echo "ไม่พบ API key $KEY_STRING ในโปรเจกต์ $P"; exit 1; fi
echo "พบคีย์: $KEY"
gcloud services api-keys describe "$KEY" --format=json > /tmp/ftkey.json
ARGS=$(python3 - "$NEW_PKG" "$NEW_SHA" <<'PY'
import json, sys
k = json.load(open('/tmp/ftkey.json'))
r = k.get('restrictions') or {}
apps = (r.get('androidKeyRestrictions') or {}).get('allowedApplications')
if not apps:
    print('NONE'); sys.exit(0)
pkg, sha = sys.argv[1], sys.argv[2]
norm = lambda x: x.replace(':', '').lower()
have = [(a.get('packageName'), a.get('sha1Fingerprint', '')) for a in apps]
if any(p == pkg and norm(s) == norm(sha) for p, s in have):
    print('ALREADY'); sys.exit(0)
have.append((pkg, sha))
print(' '.join(f'--allowed-application=sha1_fingerprint={s},package_name={p}' for p, s in have))
PY
)
if [ "$ARGS" = "NONE" ]; then echo "คีย์นี้ไม่ได้จำกัดแอป Android ไว้ — สาเหตุไม่ใช่ API key (ส่งข้อความนี้ให้ Claude)"; exit 0; fi
if [ "$ARGS" = "ALREADY" ]; then echo "มี th.friendtalk.app อยู่แล้ว ไม่ต้องทำอะไร"; exit 0; fi
gcloud services api-keys update "$KEY" $ARGS && echo "✅ เพิ่ม th.friendtalk.app แล้ว — รอ 5 นาทีแล้วลองล็อกอินในแอป Play Store"
