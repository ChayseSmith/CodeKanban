# Firebase setup and first sync

The project already has Email/Password Authentication and a Standard Firestore database. The Android app uses `android/app/google-services.json`. That client configuration is public app configuration, not an administrator credential. Never add service-account private keys to the app or repository.

## One remaining console step: publish access rules

1. Open the Kanban project in https://console.firebase.google.com/.
2. Open Firestore Database and select its **Rules** tab.
3. Open `firebase/firestore.rules` from this repository, copy its entire contents, and replace the editor contents in Firebase.
4. Click **Publish**.

These rules allow only the signed-in account to access its own `users/<uid>/records` collection. Other users and signed-out clients are denied. They were tested using the Firestore emulator. The app will report permission errors until these rules are published. No collections need to be created manually.

## Connect the first phone

1. Install the new beta APK after its GitHub Actions build succeeds.
2. Open **Settings → Cloud sync → Sign in / Create account**.
3. Create an email/password account. This is a Kanban app account, separate from your Firebase administrator login. Use a password you do not share here.
4. Choose **Upload / merge local boards**, then **Connect**. Local records override cloud records with matching IDs; other cloud boards are merged in. A private local recovery snapshot is saved before connection.
5. Wait for the status to show zero pending changes. If permission errors appear, check that the rules above were published in the same project as `google-services.json`, then tap **Retry sync**.

## Connect another device

Install the same APK, sign in with the same app email/password, choose **Use cloud boards on this device**, and confirm. This replaces that device's boards, including with an empty list if the account has none. Use Upload/merge instead if the second device contains additional boards you want to keep and upload. A recovery snapshot is saved before each explicit connection.

Open both apps, edit a work item on one and verify the other updates. Imported reminders remain silent until subscribed on that device. There is no team sharing or VS Code sync implementation yet; both can use this versioned record format later.

## Data format for future clients

Firestore path: `users/{firebaseAuthUid}/records/{sha256}`. Document fields: `schema: 1`, `json` (canonical JSON string), `changeId` (unique write ID), `updatedAt` (server timestamp). Record IDs are SHA-256 of `kind + "\n" + boardId + "\n" + entityId`. JSON records have `kind` (`board`, `column`, `item`), `boardId`, `order`, `data`, and `columnId` for items. Board metadata excludes columns; column metadata excludes cards. Deletions publish `{ "deleted": true }`, rather than deleting documents. Work item IDs are board-scoped. Clients must preserve unknown data fields and reminder origins. Same-record concurrent writes are last-write-wins; this is not collaborative text merging. Existing-card wire order remains stable when its neighbors move or are removed, avoiding unrelated item writes.

## Verification

`android/tests/CloudRecordsTest.java` covers normalization, round trips, per-item edits, durable outbox, concurrent echoes, deletion and deferred remote changes. `firebase/rules.test.cjs` tests account isolation and write shape against a local emulator, using Firebase CLI, the Firebase JS SDK and `@firebase/rules-unit-testing`. With those installed and Node 22+ available, run from the repository root:

```
firebase emulators:exec --only firestore --project demo-kanban-sync "node firebase/rules.test.cjs"
```

The demo project never connects to the production backend. Publishing rules is intentionally a separate Firebase console step; no administrator credentials are committed or embedded.
