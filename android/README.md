# Code Kanban for Android

The Android app keeps its working data in private app storage. Boards can contain other boards; there are no individual board files in shared Android storage. To keep your data after uninstalling, configure the separate backup folder described below.

## Available in this first version

- Create, rename and delete boards and nested boards.
- Long-press a board for **Rename** and **Set Icon**. The offline icon picker includes a searchable Material Design Icons catalog, preset colors, and a custom color wheel with brightness and hex input. Icon choices and colors are included in board backups.
- Use dark mode by default, or switch to light mode under Defaults.
- Configure default columns, order and hexadecimal colors, or set a board's columns as the default. Add, rename, color and delete columns on a board.
- Edit the full default template using extension-style JSON. New boards copy starter items and preserve template settings and archive data; supported work-item fields appear in the app.
- When opening a board whose column order, names or colors differ from the default, apply the default layout, defer the prompt, or stop asking for that board. Migration retains work items, placing items from unmatched columns in the first default column. It does not add template starter items to existing boards.
- Create, edit, delete and move work items between columns. Edit descriptions, labels, dates, planning fields, references, checklists and comments.
- Board cards show a description preview, labels, populated planning fields and references, due date, and expandable checklists. Parent links reveal their work item; expandable children show their column progress and can be tapped to reveal them.
- The work-item editor groups details into collapsible Time, Fields, Relational and Comments sections. Recurrence, history, bookmarks and preference ranking are shown as unavailable until those features are implemented.
- Long-press a task on a board card or in the editor to convert it into a child work item or delete it. Converting from the editor first saves the work item's draft.
- Under Time, choose a due date and time and add or remove one-time reminders. Each reminder can use a system alarm sound and optionally enable **Full-screen alarm**. Save the work item to schedule it. Normal notifications show the work-item title; full-screen alarms use a foreground audio service, ring until dismissed or snoozed, and offer repeatable ten-minute snoozes. The alarm screen can show over the lock screen and offers a swipe and an accessible dismiss button. Multiple active alarms are handled one at a time. After reboot, alarms are rescheduled once Android delivers the boot-completed event.
- Full-screen alarms require notifications, exact-alarm access and, on Android 14 or later, full-screen intent access. The reminder editor requests missing permissions. Keep the phone's alarm volume audible. Full-screen presentation and sound remain subject to device, operating-system, notification channel, battery, Do Not Disturb and permission settings. Force-stopping the app, powering off the phone or uninstalling can prevent alarms. Backup snapshots preserve reminder configurations but do not currently preserve active, delivered or snoozed occurrences; restoring an older snapshot can re-alert overdue reminders. Test the entire flow on the intended device, including reboot, lock screen, app closed, repeated snooze and a second independent alarm before relying on it.
- Tap a column header to collapse or expand it, or use **Collapse all** above the columns. The state is saved per board, and a collapsed column remains a drag destination.
- On a board, touch and hold a work item until the phone gives haptic feedback, then drag it to a highlighted column and release. Hold near the top or bottom edge to scroll. To open its action menu, release without dragging within half a second of the pickup feedback. A longer hold released in place cancels the pickup. A normal tap opens the editor.
- Use a work item's action menu to choose or clear its parent, convert a checklist task into a child work item, or convert a child without tasks or children back into a parent checklist task. The editor shows parent and child relationships. Converting a work item to a task retains only its title and completion state and asks for confirmation.
- Move a work item to another board from its action menu, or drag it to a board target that appears above the columns while dragging. Descendants move with it, into matching column names or the first column when no name matches. For a child, choose whether to move its root family or detach and move just its subtree. Families containing archived children cannot yet be moved.
- Search work items across all boards and open their editors from the results.

The editor saves when **Save** is tapped. The **More** actions leave the editor and discard unsaved edits. Deleting a board also deletes its descendants; deleting a column deletes its work items. Both require confirmation.

## Protect data across uninstall and reinstall

1. Open **Backup**, choose **Choose backup folder**, and select a folder outside the app's private storage using Android's picker. The app immediately creates a snapshot and then creates a new snapshot after each saved change. They include boards, cards, columns, collapsed state, defaults and theme. They are not `.kanban` files.
2. Before uninstalling, open **Backup** and check that the latest backup completed successfully. Backup writes run in the background, and a provider or permission failure is reported in the app. Snapshots accumulate; you may remove older ones yourself after verifying a recent backup. Do not delete the only good backup.
3. After reinstalling, open **Backup → Choose backup to restore** and select the desired `code-kanban-…json` snapshot. Confirm replacement of current app data. Then choose the backup folder again to resume automatic snapshots.

Files selected through Android's document picker are outside app-specific storage and can remain after uninstall. A device reset, manual deletion, unavailable storage provider, or a backup that hasn't finished can still result in data loss. Use a synced location or a separate copy if you need protection from losing the phone. Restoring replaces the current data; back up anything you want to keep first. This is backup and restore, not automatic synchronization between devices.

## Build and installation

Open `android` in Android Studio, install Android SDK 35, and run the app on a device or emulator. Alternatively, with JDK 17 and the Android SDK installed, run `./gradlew :app:assembleDebug` from this directory. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow `.github/workflows/android-release.yml` builds, signs and verifies a release APK, then publishes `code-kanban.apk` and `update.json` to a GitHub Release. It runs on pushes to the repository's default branch and can also be started manually on another branch. Successful builds also have a `code-kanban-android` workflow artifact. The older Azure pipeline file is retained for the existing Azure remote; GitHub Actions is the release workflow described here.

Before enabling it, complete this one-time setup in the GitHub repository:

1. Make the repository public if the app should download updates without signing in. Create a persistent Android keystore on a trusted computer **outside the repository**. For example, from PowerShell (choose a private folder you have created):

   ```powershell
   keytool -genkeypair -v -keystore "$env:USERPROFILE\Documents\CodeKanbanSigning\code-kanban.keystore" -alias codekanban -keyalg RSA -keysize 3072 -validity 10000
   ```

   Follow the interactive prompts. Keep the keystore and passwords in a secure location with a separate backup. Losing the signing key prevents in-place updates; never regenerate it for each build. If you already have the key that signed the installed APK, use that key instead.
2. Under **Settings → Secrets and variables → Actions → Secrets**, add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. For the sample command, the alias is `codekanban`; with the default PKCS12 keystore, use the same password for the keystore and key. To copy the keystore's Base64 to the clipboard for the secret without putting it in Git, run:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\Documents\CodeKanbanSigning\code-kanban.keystore")) | Set-Clipboard
   ```

   Paste it into the `ANDROID_KEYSTORE_BASE64` secret and clear your clipboard afterward. Do not commit the keystore, Base64 value, or passwords. Authorize Actions to create releases if repository or organization policy restricts the workflow token.
3. Under **Settings → Secrets and variables → Actions → Variables**, set `ANDROID_VERSION_BASE` to a non-negative integer at least as large as the version code of any APK signed with this key that is already installed. For a new key and fresh installation, `1000000` is a suitable starting value. The workflow adds its GitHub run number to this base. Never lower the base: Android refuses version downgrades. A rerun has the same run number and cannot overwrite an existing release tag; start a new workflow run instead.
4. Commit and push the changes to the GitHub default branch, or start **Android release** manually in Actions. Download `code-kanban.apk` from the release. The build log displays the public signing-certificate fingerprint; successive builds should show the same fingerprint.

If the installed app was signed with a different key and that key is unavailable, Android cannot update it with the new key. First use **Backup** in the installed app and verify a snapshot has completed. Then uninstall the old app, install the newly signed APK, restore the snapshot, and reconnect the backup folder. Uninstalling removes app-private data; snapshots do not include ringing, snoozed, or delivered reminder occurrence state. Re-test reminders after the transition. Later builds signed with the same key can update in place. A local `assembleDebug` build uses a different debug key and cannot update the signed release.

The release APK checks the latest published GitHub release when the app opens or returns to the foreground; **Updates** performs a manual check and reports errors in detail. When a newer release exists, the app offers to download it, checks its SHA-256, package ID, version and signing certificate, and asks Android to install it with your confirmation. Android may ask you to allow Code Kanban to install unknown apps. The app does not update silently or poll continuously in the background. A public repository and both release assets are required. Keep unrelated releases out of the repository's latest-release slot, or include the Android update assets in them. Debug and Azure-built APKs do not have the GitHub update source configured.

## Feature backlog

These extension capabilities are not yet implemented in the Android app:

- Import/export of `.kanban` files, sync with a computer, and conflict handling.
- Reordering cards or columns, archive/restore, copying items, bulk actions, and custom colors beyond the presets.
- Grouped cross-board columns, field filters, sorting, saved search, a combined task view, and taskless-item view.
- Full behavior for all template fields: template archive, board label settings and unsupported work-item properties are retained but are not yet available as working app features; board-level label management and color-coded labels.
- Date/time picker and due-date validation, formatted Markdown, independently editable comments, task editing and reordering, numeric field validation, ADO links and SM copy actions.
- Recurring reminders, configurable snooze duration, and backup/restore of delivered, ringing and snoozed occurrence state. Android may delay notifications because of device power management, and notification presentation depends on system and channel settings.
- Recurring work items, moving archived descendants, file bookmarks, column history, audit history/export and preference ranking.
- Display-density options, accessibility and large-screen polish, automated UI tests, and Play Store distribution.
- VS Code-specific integration such as editor selections, workspace discovery, sidebar views and window-title changes.

The Android storage format is private and is not currently the extension's `.kanban` format. Feature names shared with the extension do not imply full behavioral parity.

The board picker bundles [Material Design Icons](https://github.com/Templarian/MaterialDesign-Webfont) version 7.4.47. Its font, icon-name index, and license notices are in `app/src/main/assets/mdi/`.
