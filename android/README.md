# Code Kanban for Android

The Android app keeps its working data in private app storage. Boards can contain other boards; there are no individual board files in shared Android storage. To keep your data after uninstalling, configure the separate backup folder described below.

## Available in this first version

- Create, rename and delete boards and nested boards.
- Use dark mode by default, or switch to light mode under Defaults.
- Configure default columns, order and hexadecimal colors, or set a board's columns as the default. Add, rename, color and delete columns on a board.
- Edit the full default template using extension-style JSON. New boards copy starter items and preserve template settings and archive data; supported work-item fields appear in the app.
- When opening a board whose column order, names or colors differ from the default, apply the default layout, defer the prompt, or stop asking for that board. Migration retains work items, placing items from unmatched columns in the first default column. It does not add template starter items to existing boards.
- Create, edit, delete and move work items between columns. Edit descriptions, labels, dates, planning fields, references, checklists and comments.
- Board cards show a description preview, labels, populated planning fields and references, due date, and expandable checklists. Parent links reveal their work item; expandable children show their column progress and can be tapped to reveal them.
- The work-item editor groups details into collapsible Time, Fields, Relational and Comments sections. Recurrence, reminders, history, bookmarks and preference ranking are shown as unavailable until those features are implemented.
- Tap a column header to collapse or expand it. Its state is saved per board, and a collapsed column remains a drag destination.
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

The root Azure DevOps pipeline builds the debug APK and publishes `code-kanban-android-debug` as a pipeline artifact. Create a pipeline pointing at `azure-pipelines.yml` and run it; download the APK from the run's Artifacts section. Android may ask you to allow the browser or file manager to install apps from unknown sources. The artifact is debug-signed; a securely signed release build and stable signing key are future work.

## Feature backlog

These extension capabilities are not yet implemented in the Android app:

- Import/export of `.kanban` files, sync with a computer, and conflict handling.
- Reordering cards or columns, archive/restore, copying items, bulk actions, and custom colors beyond the presets.
- Grouped cross-board columns, field filters, sorting, saved search, a combined task view, and taskless-item view.
- Full behavior for all template fields: template archive, board label settings and unsupported work-item properties are retained but are not yet available as working app features; board-level label management and color-coded labels.
- Date/time picker and due-date validation, formatted Markdown, independently editable comments, task editing and reordering, numeric field validation, ADO links and SM copy actions.
- Reminders, alarms, snooze, notification permissions, scheduling across device restarts, and reminder sounds.
- Recurring work items, moving archived descendants, file bookmarks, column history, audit history/export and preference ranking.
- Display-density options, accessibility and large-screen polish, automated UI tests, stable release signing, and Play Store distribution.
- VS Code-specific integration such as editor selections, workspace discovery, sidebar views and window-title changes.

The Android storage format is private and is not currently the extension's `.kanban` format. Feature names shared with the extension do not imply full behavioral parity.
