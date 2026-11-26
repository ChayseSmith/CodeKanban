# Code Kanban for Android

The Android app keeps its boards in private app storage. Boards can contain other boards; there are no board files in shared Android storage. Data is local to the installation; uninstalling the app normally removes it. Android device backup may be available depending on device settings, but is not a substitute for an export.

## Available in this first version

- Create, rename and delete boards and nested boards.
- Use dark mode by default, or switch to light mode under Defaults.
- Configure default columns, order and hexadecimal colors, or set a board's columns as the default. Add, rename, color and delete columns on a board.
- Edit the full default template using extension-style JSON. New boards copy starter items and preserve template settings and archive data; supported work-item fields appear in the app.
- When opening a board whose column order, names or colors differ from the default, apply the default layout, defer the prompt, or stop asking for that board. Migration retains work items, placing items from unmatched columns in the first default column. It does not add template starter items to existing boards.
- Create, edit, delete and move work items between columns. Edit descriptions, labels, dates, planning fields, references, checklists and comments.
- Search work items across all boards and open their editors from the results.

The editor saves when **Save** is tapped. The **More** actions leave the editor and discard unsaved edits. Deleting a board also deletes its descendants; deleting a column deletes its work items. Both require confirmation.

## Build and installation

Open `android` in Android Studio, install Android SDK 35, and run the app on a device or emulator. Alternatively, with JDK 17 and the Android SDK installed, run `./gradlew :app:assembleDebug` from this directory. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

The root Azure DevOps pipeline builds the debug APK and publishes `code-kanban-android-debug` as a pipeline artifact. Create a pipeline pointing at `azure-pipelines.yml` and run it; download the APK from the run's Artifacts section. Android may ask you to allow the browser or file manager to install apps from unknown sources. The artifact is debug-signed; a securely signed release build and stable signing key are future work.

## Feature backlog

These extension capabilities are not yet implemented in the Android app:

- Import/export of `.kanban` files, sync with a computer, conflict handling, and manual backup/restore.
- Drag and drop, reordering cards or columns, moving items across boards, archive/restore, copying items, bulk actions, and custom colors beyond the presets.
- Grouped cross-board columns, field filters, sorting, saved search, a combined task view, and taskless-item view.
- Full behavior for all template fields: template archive, board label settings and unsupported work-item properties are retained but are not yet available as working app features; board-level label management and color-coded labels.
- Date/time picker and due-date validation, formatted Markdown, independently editable comments, task editing and reordering, numeric field validation, ADO links and SM copy actions.
- Reminders, alarms, snooze, notification permissions, scheduling across device restarts, and reminder sounds.
- Recurring work items, parent/child relationships, file bookmarks, column history, audit history/export and preference ranking.
- Display-density options, accessibility and large-screen polish, automated UI tests, stable release signing, and Play Store distribution.
- VS Code-specific integration such as editor selections, workspace discovery, sidebar views and window-title changes.

The Android storage format is private and is not currently the extension's `.kanban` format. Feature names shared with the extension do not imply full behavioral parity.
