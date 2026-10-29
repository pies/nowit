# NowIt

An Android reminder app that turns screenshots into reminders, and logs how you
handle each one so it can learn your habits later.

## Install

Every push to `main` builds a debug APK and publishes it as a GitHub Release
(`build-<n>`). On your phone, open the latest release, download the `.apk`, and
open it (allow "Install unknown apps" for your browser the first time). All builds
are signed with the same committed debug key, so a new build installs over the
old one and keeps your data.

## Version 1

- **Share a screenshot to NowIt.** Text is read on the device with ML Kit Text
  Recognition; ML Kit Entity Extraction finds dates, times, addresses and phone
  numbers. (The entity model, a few MB, downloads once on first use.)
- **Confirm or edit** the suggested reminder. Other detected dates, addresses and
  phone numbers appear as chips to pick from.
- **Notification at the reminder time** with Done, Snooze 15 min and Dismiss.
  Swiping it away counts as Dismiss.
- **Source app**: taken from the screenshot's file name when the phone includes it
  (e.g. Samsung `Screenshot_20241029-123456_WhatsApp.jpg`, Xiaomi's package
  name); otherwise from the app in the foreground at the screenshot's time, which
  needs **Settings → Usage access → NowIt**.
- **Event log**: created, edited, fired, completed, snoozed, dismissed and deleted
  events with time of day, weekday, offset from the due time and source app
  (`reminder_events` table; viewable via the list icon).
- Everything is stored locally in Room.

## Building

CI (`.github/workflows/build.yml`) runs `./gradlew assembleDebug` on every push.
Builds of other branches upload the APK as a workflow artifact only.
