# Deployed components (source of truth = this repo)

| Component | Path | Deployed as |
|---|---|---|
| Android app | `app/` | GitHub Actions `build_apk.yml` → releases (`FriendTalk-latest.apk`) |
| Brain API | `backend/friendtalk-brain/` | Cloud Run `friendtalk-brain` (asia-southeast1, project friendtalk-4e623), `gcloud run deploy friendtalk-brain --source backend/friendtalk-brain` |
| Auth service | `backend/friendtalk-auth/` | Cloud Run `friendtalk-auth` |
| RTDB rules | `firebase/database.rules.json` | Realtime Database rules (matches deployed as of 2026-10-11) |
| Storage rules | `firebase/storage.rules` | Firebase Storage |

Runtime config is via Cloud Run env vars (e.g. `OWNER_EMAILS`) and the service account — no keys are stored in this repo.
`docs/legacy-docs-snapshot/` holds an older rules snapshot kept for reference only.
