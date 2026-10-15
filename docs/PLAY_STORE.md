# FriendTalk — Google Play checklist (com.friendtalk.app)

## URLs for Play Console
- Privacy policy: https://friendtalk-brain-123224091480.asia-southeast1.run.app/privacy
- Delete account: https://friendtalk-brain-123224091480.asia-southeast1.run.app/delete-account

## Before the first AAB upload
1. Firebase Console → Project settings → Add app → Android → `com.friendtalk.app` (+ SHA-1 of the upload key; after Play App Signing is on, also add Play's app-signing SHA-1 from Play Console → App integrity).
2. Download the new `google-services.json` (contains both `com.example` and `com.friendtalk.app`) → GitHub secret `GOOGLE_SERVICES_JSON`.
3. AdMob: add the app (Android, `com.friendtalk.app`) and link it once it is live on Play.
4. Play Console → Monetize → In-app products: create the product IDs used by the shop packs (admin → การเงิน → ราคาแพ็กเพชร/เหรียญ).

## Data safety form (answers)
| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Email, name, user ID | Yes | No | Account management, app functionality | No |
| Photos (profile/posts), videos | Yes | No | App functionality | Yes |
| Messages (chat), other user-generated content | Yes | No | App functionality, safety | No |
| Approximate location | Yes | No | App functionality (Nearby) | Yes |
| Date of birth / age | Yes | No | App functionality, safety (18+) | No |
| Voice / video (calls, live) | Processed in real time (Agora), not stored | Service provider | App functionality | Yes |
| ID images (verification) | Yes, deleted after review | No | Fraud prevention, safety | Yes |
| Purchase history | Yes | No | App functionality | No |
| Device or other IDs (push token, Advertising ID) | Yes | Yes (AdMob) | Advertising, notifications, fraud prevention | No |

- Data encrypted in transit: Yes
- Users can request deletion: Yes (in app + web URL above)

## Other forms
- Content rating: social / user interaction, users can communicate, shares location (approximate), digital purchases.
- Target audience: 18+ only.
- Ads: Yes (AdMob).
- App access: give reviewers a test login (email + password) that skips age/ID checks.
- New personal developer accounts: closed testing with 12+ testers for 14 days before production.
