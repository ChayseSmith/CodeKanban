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

Open both apps, edit a work item on one and verify the other updates. Imported reminders remain silent until subscribed on that device. Board collaboration is described below. VS Code sync is not implemented yet; it can use the versioned record formats later.

## Data format for future clients

Firestore path: `users/{firebaseAuthUid}/records/{sha256}`. Document fields: `schema: 1`, `json` (canonical JSON string), `changeId` (unique write ID), `updatedAt` (server timestamp). Record IDs are SHA-256 of `kind + "\n" + boardId + "\n" + entityId`. JSON records have `kind` (`board`, `column`, `item`), `boardId`, `order`, `data`, and `columnId` for items. Board metadata excludes columns; column metadata excludes cards. Deletions publish `{ "deleted": true }`, rather than deleting documents. Work item IDs are board-scoped. Clients must preserve unknown data fields and reminder origins. Same-record concurrent writes are last-write-wins; this is not collaborative text merging. Existing-card wire order remains stable when its neighbors move or are removed, avoiding unrelated item writes.

## Verification

`android/tests/CloudRecordsTest.java` covers normalization, round trips, per-item edits, durable outbox, concurrent echoes, deletion and deferred remote changes. `firebase/rules.test.cjs` tests account isolation and write shape against a local emulator, using Firebase CLI, the Firebase JS SDK and `@firebase/rules-unit-testing`. With those installed and Node 22+ available, run from the repository root:

```
firebase emulators:exec --only firestore --project demo-kanban-sync "node firebase/rules.test.cjs"
```

The demo project never connects to the production backend. Publishing rules is intentionally a separate Firebase console step; no administrator credentials are committed or embedded.

## Board collaboration and inherited permissions

Publish the updated `firebase/firestore.rules` again before using collaboration, and update every participating app. Existing private sync remains compatible. Shared boards use a separate `sharedBoards/{id}` store; they are not copied into a collaborator's private record collection. Shared root discovery uses `users/{uid}/sharedRoots`; invitation documents live in `boardInvites`.

1. On a board, expand **Board permissions → Enable collaboration for this board tree**. This publishes the selected top-level board and its nested boards, retaining you as owner. It grants nobody access until invited. Record data is uploaded before discovery links are published; failed publication can be retried. Active alarms must be dismissed or snoozed before conversion.
2. Expand **Board permissions → Invite by email** and choose Read or Write. Invitations are in-app invitations, not outgoing email messages. Pending invitations can be revoked from the same section.
3. Recipients sign in using that email, choose **Settings → Cloud sync → Send email verification**, open the verification email, and tap **Refresh account / collaboration**.
4. Open **Board invitations**, then Accept. Shared boards appear with private boards; a nested board invited directly appears as a root if its parents are not accessible.
5. On any shared board, its owner can change each collaborator to **Inherit from parent**, **Read**, **Write**, or **No access**. An explicit nested setting always takes precedence, including a deeper Write overriding inherited Read or No access. Removing a parent grant does not remove explicit grants farther down the tree. The owner always retains full access.

Read access allows viewing and local column/section collapsing; it does not permit work-item/column edits or editing automations. Write allows work-item and column changes. Only the owner can manage permissions, board-level settings/buttons, shared board creation and deletion. Owners create new children using **New shared nested board**. Shared hierarchy is immutable after creation in this version and supports up to **eight levels**, keeping inherited checks within Firestore access-call limits. Boards cannot be reparented or ownership transferred yet.

Access is checked in both the app and Firestore. Server ACL metadata is separate from editable JSON payloads, and collaborators cannot change ownership, forge inheritance or upgrade their role. Revocation hides inaccessible boards when the connected client receives it; no system can retract data already downloaded to an offline device or a separate export. Offline edits remain queued and can be rejected after a downgrade. Sign-out retains downloaded boards locally but disables shared editing. Cloud execution of automations and invitation email delivery are not included.

Emulator tests cover inheritance, explicit Read/Write/No access overrides, email verification, invitation acceptance, owner guarantees, role escalation, maximum depth and bulk writes. Java permission tests cover the matching client behavior, read-only save checks and isolation from private sync.

## Automated rules deployment

The release workflow supports optional keyless Firestore rules deployment before publishing APKs. Complete the one-time [Google/GitHub setup](DEPLOYMENT.md) to enable it. Until configured, manual rules publishing remains necessary.
