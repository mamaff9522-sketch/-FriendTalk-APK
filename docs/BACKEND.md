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
- Status checked 2026-10-10: **cleanup policy is active** (not dry-run):
  - `keep-3-recent`: always keep the 3 most recent versions.
  - `keep-serving`: keep images tagged with prefix `serving`.
  - `delete-older-1d`: delete any other version older than 1 day.

## Hardening status (2026-10-10)
Done:
- Every service runs under its own service account.
- The web API key is held in Secret Manager.
- Max instances = 2 on both services (limits runaway cost).
- Artifact Registry cleanup policy active (old images deleted automatically).
- RTDB rules default closed. Roles, bans, wallets and transactions can't be written from the client.
- No keys or `.env` files are committed. `google-services.json` is supplied in CI from a GitHub secret.

Open:
- Both services allow unauthenticated calls (`allUsers` invoker, ingress `all`). Each service must check the Firebase ID token itself.
- The Android app still runs mostly on local mock data and does not call these services yet.

## AI chat characters (friendtalk-brain)
- Always labeled AI; they say they are AI if asked; free (never take coins or gifts).
- RTDB: `aiBots/{botId}` {name, avatarUrl, bio, persona, interests, enabled} (6 Thai personas, 18+, non-sexual); `appConfig/aiBotsEnabled` global switch; chats in `botChats/{uid}/{botId}` (owner read-only); counters `botRate/{uid}/{hour}`, `botDaily/{yyyymmdd}`; `matchQueue` for random match (60 s TTL).
- Endpoints (Firebase ID token required): `POST /match` (real waiting user first, else an enabled bot if bots are on, else `waiting`), `POST /bot/chat {botId, message}`, `DELETE /bot/chat/:botId` (end chat). Admin (`config_edit`): `GET /admin/bots`, `PUT /admin/bots/enabled {enabled}`, `PUT /admin/bots/:botId {enabled, ...}`.
- Model: Vertex AI `gemini-2.5-flash-lite` (global endpoint), max 150 output tokens, last 10 messages as context, safety filters at BLOCK_LOW_AND_ABOVE. `ft-brain-sa` has `roles/aiplatform.user`.
- Limits (env): `BOT_HOURLY_PER_USER=30`, `BOT_DAILY_LIMIT=2000` messages/day total; over the limit the endpoint returns 429.

## Posts: video, place and privacy (2026-10-14)
- `POST /posts` accepts `videoRef` (Storage `posts/{uid}/…`, `video/*`, ≤ 50 MB) and optional `posterRef` (cover image, moderated like post images). Saved as `posts/{id}/video {path,url,posterPath,posterUrl}`; older app versions ignore it.
- Place: `place` (name, ≤ 80 chars) and optional `lat`/`lng`, rounded to ~1 km. `/feed` returns the point only to the author, or to everyone when the author sent `shareCoords: true` (stored as `place.share`).
- Privacy: `visibility` = `public` / `friends` / `only_me`, checked on the server (`canView`) for `/feed`, likes, comments and now `/reports` too.
- Needs deploying: `firebase/storage.rules` (allows `video/*` under `posts/{uid}/`) and friendtalk-brain. Until both are deployed, the app shows an error for video posts; text, photo and place posts keep working.

## Chat stickers (2026-10-14)
- Admins add free chat stickers in the app: Admin → ของขวัญ → **สติกเกอร์แชท** (`catalog/chatStickers`, image webp/png ≤ 512 KB via `PUT /admin/catalog/chatStickers/{id}/image`, price 0). `chatStickers` is a new catalog channel in friendtalk-brain — deploy brain before adding stickers.
- Users send them from the 😊 button in a chat room. A sticker is a normal chat text `🎨 สติกเกอร์ [id]` (same RTDB rules and daily limits as text); the app draws the catalog image, older app versions show the text.
