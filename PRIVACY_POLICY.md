# Privacy Policy — WaterReminder

**Last updated: May 2, 2026**

---

## 1. Overview

WaterReminder ("the App") is a free, offline hydration-tracking application for Android. This Privacy Policy explains what information the App collects, how it is used, and your rights as a user.

The short version: **the App collects no personal data and sends nothing outside your device.**

---

## 2. Information We Collect

### 2.1 Data stored locally on your device

The App stores the following information exclusively in your device's private app storage (Android SharedPreferences). This data never leaves your device.

| Data | Purpose |
|------|---------|
| Daily water intake (ml) | Track your progress toward the daily goal |
| Historical daily intake (per date) | Display the 7-day analytics chart |
| Daily goal and unit preference | Personalise the experience |
| Reminder schedule configuration | Trigger hydration notifications at the times you choose |
| Streak counter | Show consecutive days the daily goal was met |
| Custom quick-add button value | Remember your personalised shortcut |
| Notification enabled/disabled flag | Respect your notification preference |
| Tutorial completion flag | Avoid showing the first-launch tour more than once |

### 2.2 Data we do NOT collect

- No name, email address, or any other personally identifiable information (PII)
- No location data
- No device identifiers (IMEI, advertising ID, etc.)
- No usage analytics or event telemetry
- No crash or error reports
- No camera, microphone, or contacts access
- No data shared with third parties

---

## 3. Internet and Network Access

The App **does not request Internet or network permissions**. It is entirely offline-capable by design. No data is transmitted to any server, cloud service, or third party at any time.

---

## 4. Third-Party Libraries

The App uses the following open-source libraries, all of which operate strictly on-device:

| Library | Purpose | Network access |
|---------|---------|----------------|
| MPAndroidChart | Render the 7-day intake bar chart | None |
| TapTargetView | Display the interactive first-launch tutorial | None |
| AndroidX / Material Components | Standard UI components | None |

None of these libraries collect, transmit, or process personal data.

---

## 5. Notifications

If you configure reminders, the App uses Android's AlarmManager and NotificationManager to deliver local push notifications at the times you set. These notifications are generated entirely on your device. No notification content or delivery data is sent to any external service.

You can disable all notifications at any time from within the App's Reminders screen or through your device's system notification settings.

---

## 6. Permissions

The App requests the following Android permissions:

| Permission | Reason |
|-----------|--------|
| `POST_NOTIFICATIONS` | Display hydration reminder notifications (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Reschedule your reminders after a device reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Deliver reminders at the exact times you configured (Android 12+) |

No other permissions are requested or used.

---

## 7. Data Retention and Deletion

All app data is stored locally in your device's private storage and is accessible only to the App. You can delete all data at any time by:

- Clearing app data via **Device Settings → Apps → WaterReminder → Storage → Clear Data**, or
- Uninstalling the App.

Both actions permanently remove all data the App has stored. There is no remote backup to delete because no data is ever uploaded.

---

## 8. Children's Privacy

The App does not collect any personal information from anyone, including children under the age of 13. It is safe for use by all ages.

---

## 9. Changes to This Policy

If a future version of the App introduces features that change these data practices, this policy will be updated and the "Last updated" date at the top will change. We encourage you to review this policy periodically.

---

## 10. Contact

If you have questions or concerns about this Privacy Policy, please contact:

**David Ricardo**

---

*This policy applies to the WaterReminder Android application.*
