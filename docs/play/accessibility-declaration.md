# Play Console: accessibility declaration (draft)

Not legal advice. Paste, then check against the live form
([policy](https://support.google.com/googleplay/android-developer/answer/10964491)). Replace "Voice Mode"
with the final name. Wording must match what the app really does, so re-check if the service changes.

**Is the app an accessibility tool?** No. Do not tick it; `isAccessibilityTool` is not set.

**Core functionality (what the app does)**
Voice Mode is a keyboard that turns speech into text. It also offers an optional floating dictation button
that works in any app. The accessibility service powers only that optional button.

**Why accessibility is needed**
A floating button is not a keyboard, so it has no connection to the text field in another app. The
accessibility service is the only way to (1) know when a text field is focused, so the button appears only
then, and (2) put the dictated words into that field at the cursor.

**Data accessed and how it is used**
- The focused text field only: its text, cursor position and whether it is editable, plus the name of the
  front app (for the user's per-app show/hide list). Used on the device to insert dictated words at the right place.
- Password fields are ignored.
- Nothing from other apps is stored, logged or sent anywhere.
- Only the user's voice recording is sent, to the speech provider the user selected, as with keyboard dictation.

**Not used for**
Reading other screen content, screenshots, advertising, analytics, selling data, clicking or navigating on
the user's behalf, or any automation beyond inserting the text the user just dictated.

**Alternatives considered**
- Keyboard-attached button: built in and the default; works without accessibility but only where Voice Mode is the active keyboard.
- Android's `InputConnection`: only available to the active keyboard, not to an overlay.
- Share sheet / copy-paste: too slow and not available in every field.
- Overlay permission alone: can draw a button but cannot see the focused field or insert text.

**Disclosure and consent**
Shown in the app before the user is sent to Android's Accessibility settings, with "I agree" and "No thanks".
Backing out does nothing. Consent is stored with time and version and re-requested if the text changes.
The feature is optional and off by default.

## Demo video script (~60 s, screen recording with Android settings visible)

1. (0–5 s) Home screen. Open Voice Mode → Dictate → Floating button. Say: "This is an optional feature, off by default."
2. (5–10 s) Switch the floating button on. Tap "Accessibility service".
3. (10–25 s) The disclosure appears. Pause and scroll so all text is readable: what it does, what it does not do.
4. (25–32 s) Tap **No thanks**. Show that nothing opens and the keyboard still works. Tap the row again.
5. (32–38 s) Tap **I agree**. Android Accessibility settings open.
6. (38–45 s) Find "Voice Mode floating button", open it, show Android's description, turn it on and accept Android's prompt. Grant mic and overlay if asked.
7. (45–58 s) Open a notes app, tap a text field. The bubble appears. Tap it, say a sentence, tap again. Text appears in the field. Open a password field and show the bubble does not appear.
8. (58–60 s) Return to settings and show the service can be turned off.
