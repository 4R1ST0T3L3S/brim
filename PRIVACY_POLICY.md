# Privacy Policy — FillMeUp!

**Last updated: August 27, 2026**

---

## 1. Overview

FillMeUp! ("the App") is a free, offline hydration-tracking application for Android. This Privacy Policy explains what information the App collects, how it is used, and your rights as a user.

The short version: **the App has no servers, no accounts, and no analytics. Everything you enter stays in the App's private storage on your device.** The only way any of it leaves your device is if you export a backup file yourself (section 6), or if you have Android's own system backup switched on (section 7) — both are under your control.

---

## 2. Information We Collect

### 2.1 Data stored locally on your device

The App stores the following information in your device's private app storage (Android SharedPreferences).

| Data | Purpose |
|------|---------|
| Daily water intake | Track your progress toward the daily goal |
| Historical daily intake (per date) | Display the analytics chart and full history |
| Daily goal, personal goal override, unit preference | Personalise the experience |
| Age and body weight *(optional)* | Calculate your recommended daily goal. You choose whether to enter these; the App works without them |
| Reminder schedule configuration | Trigger hydration notifications at the times you choose |
| Reminder time window (start and end hour) | Keep repeating reminders inside your waking hours |
| Quick-add and quick-remove amounts | Remember the shortcut buttons you configured |
| Notification quick-log amounts | Remember which amounts appear as buttons on a reminder notification |
| Notification enabled/disabled flag | Respect your notification preference |
| Pause reminders once the goal is reached | Respect your notification preference |
| Celebration animations flag | Respect your preference for goal celebrations |
| Theme preference | Remember your light / dark / colour choice |
| Streak counter | Show consecutive days the daily goal was met |
| Setup and tutorial completion flags | Avoid showing the first-launch tour and profile setup more than once |

### 2.2 Data we do NOT collect

- No name, email address, account, or contact details
- No location data
- No device identifiers (IMEI, advertising ID, etc.)
- No usage analytics or event telemetry
- No crash or error reports
- No camera, microphone, or contacts access
- No data sent to us or to any third party

**About age and weight.** These are the only body-related values the App stores, they are optional, and they exist solely to compute a recommended daily goal on your device. They are never transmitted anywhere and are not linked to any identity, because the App has no notion of who you are. You can change or erase them at any time in **Settings → Profile**, which removes them from storage immediately.

---

## 3. Internet and Network Access

The App **does not request the `INTERNET` permission**. It cannot open a network connection, and it has no server, cloud service, or third-party endpoint to talk to. It is entirely offline-capable by design.

---

## 4. Third-Party Libraries

The App uses the following open-source libraries, all of which operate strictly on-device:

| Library | Purpose | Network access |
|---------|---------|----------------|
| MPAndroidChart | Render the intake bar chart | None |
| TapTargetView | Display the interactive first-launch tutorial | None |
| AndroidX / Material Components | Standard UI components | None |

None of these libraries collect, transmit, or process personal data. The App ships no advertising, attribution, or analytics SDK of any kind.

---

## 5. Notifications

If you configure reminders, the App uses Android's AlarmManager and NotificationManager to deliver local notifications at the times you set. A reminder notification may include quick-log buttons that record an amount straight from the notification shade without opening the App. All of this is generated and handled entirely on your device. No notification content or delivery data is sent to any external service.

You can disable all notifications at any time from within the App's Reminders screen or through your device's system notification settings.

---

## 6. Data Export and Import

The App lets you export everything it has stored to a backup file at a location you choose on your device, and restore it later by importing that file. The file is plain-text JSON and contains the values listed in section 2.1 — including your age and weight if you entered them. The App never uploads or transmits it; where the file goes afterwards is entirely up to you, so treat it like any other personal document if you copy it off the device or into cloud storage.

Importing a backup **replaces** the App's current data with the contents of the file.

---

## 7. Android System Backup

The App participates in Android's built-in backup mechanism. If the device owner has system backup enabled (for example "Backup by Google One", or another backup transport supplied by the device), **Android** copies the App's saved preferences — the data in section 2.1, including age and weight if entered — into that backup, and restores them when the App is reinstalled or the device is migrated.

Points worth being clear about:

- This is performed by the Android operating system, not by the App. The App has no access to the backup and no server of its own.
- The backup is stored under your own account with your backup provider and is governed by that provider's privacy policy. On current Android versions it is encrypted with your device credentials before leaving the device.
- Devices without a backup transport (for example a de-Googled ROM) never produce such a backup.
- The first-launch tour flag is deliberately excluded, so the guide replays after a reinstall.
- **To opt out:** turn off backup for the App in **Device Settings → Google → Backup** (or **Settings → System → Backup**, depending on your device). Existing backups can be deleted from the same screen.

---

## 8. Permissions

The App requests the following Android permissions:

| Permission | Reason |
|-----------|--------|
| `POST_NOTIFICATIONS` | Display hydration reminder notifications (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Reschedule your reminders after a device reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Deliver reminders at the exact times you configured (Android 12+) |

The App also reschedules reminders when the device leaves airplane mode or after an app update; neither requires an additional permission. No other permissions are requested or used.

---

## 9. Data Retention and Deletion

All app data is stored locally in your device's private storage and is accessible only to the App. You can delete it by:

- Clearing your age and weight only, via **Settings → Profile** in the App, or
- Clearing everything via **Device Settings → Apps → FillMeUp! → Storage → Clear Data**, or
- Uninstalling the App.

Note that these actions do not touch copies that exist outside the App's own storage: any backup file you exported yourself, and any Android system backup, must be deleted separately (see sections 6 and 7).

---

## 10. Children's Privacy

The App is not directed at children and collects no personally identifiable information from anyone. If an age under 18 is entered, it is used only to select a fixed recommended minimum instead of the adult weight-based estimate, and it is stored locally exactly like every other setting.

---

## 11. Changes to This Policy

If a future version of the App introduces features that change these data practices, this policy will be updated and the "Last updated" date at the top will change. We encourage you to review this policy periodically.

---

## 12. Contact

If you have questions or concerns about this Privacy Policy, please contact:

**David Ricardo**

---

*This policy applies to the FillMeUp! Android application.*
