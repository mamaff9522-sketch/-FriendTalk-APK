# FriendTalk backend

No secret values are in this file or in this repository.

## Google Cloud / Firebase project
- Project: `friendtalk-4e623` (project number 123224091480), region `asia-southeast1`.

## Cloud Run services
| Service | URL | Source in repo | Runs as |
|---|---|---|---|
| friendtalk-auth | https://friendtalk-auth-123224091480.asia-southeast1.run.app | `backend/friendtalk-auth/` | `ft-auth-sa@friendtalk-4e623.iam.gserviceaccount.com` |
| friendtalk-brain | https://friendtalk-brain-123224091480.asia-southeast1.run.app | `backend/friendtalk-brain/` | `ft-brain-sa@friendtalk-4e623.iam.gserviceaccount.com` |

Environment variables (names only):
- friendtalk-auth: `FIREBASE_PROJECT_ID` (plain), `FIREBASE_WEB_API_KEY` (from Secret Manager).
- friendtalk-brain: `DATABASE_URL`, `CORS_ORIGINS` (plain). Optional in code: `RATE_LIMIT_PER_MIN`, `PORT`.

## Realtime Database
- URL: https://friendtalk-4e623-default-rtdb.asia-southeast1.firebasedatabase.app
- Rules: `firebase/database.rules.json` (copied from the live database on 2026-10-10).
- Data layout: `firebase/rtdb-structure.json`.
- Default: everything is closed (`.read`/`.write` false) unless a path opens it.
- Top-level paths: users, usernames, roles, bans, wallets, coinTransactions, posts, postLikes, postComments, userFeeds, chats, chatMembers, userChats, messages, lives, liveViewers, liveComments, liveGifts, clubs, clubMembers, userClubs, gifts, coinPackages, appConfig, reports.

## Roles
- Stored at `roles/{uid}` with a value such as `superadmin` or `moderator`.
- `roles` and `bans` can be read by signed-in users, but the app can never write them (`.write: false`). Only backend code with admin access can change them.
- A user cannot write their own `users/{uid}` while listed in `bans`.
- `wallets/{uid}` and `coinTransactions/{uid}`: readable only by that user, never writable from the app. Coin changes must go through the backend.

## Budget
- Budget `friendtalk-budget`: 100 THB, scoped to this project, alerts at 50%, 90% and 100% of current spend. Alerts only; it does not stop spending.

## Artifact Registry cleanup policy
- Repository `cloud-run-source-deploy` (asia-southeast1).
- Status checked 2026-10-10: **no cleanup policy is set on this repository.** The intended policy (keep the 3 most recent versions, keep images tagged `serving*`, delete everything else older than 1 day) has not been applied to FriendTalk yet.

## Hardening status (2026-10-10)
Done:
- Every service runs under its own service account.
- The web API key is held in Secret Manager.
- Max instances = 2 on both services (limits runaway cost).
- RTDB rules default closed. Roles, bans, wallets and transactions can't be written from the client.
- No keys or `.env` files are committed. `google-services.json` is supplied in CI from a GitHub secret.

Open:
- Both services allow unauthenticated calls (`allUsers` invoker, ingress `all`). Each service must check the Firebase ID token itself.
- Artifact Registry cleanup policy not applied (see above).
- The Android app still runs mostly on local mock data and does not call these services yet.
