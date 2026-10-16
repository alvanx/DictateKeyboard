# Accessibility service audit

Not legal advice. This is an engineering review against Google Play's published rules; have the
person who signs the Play Console declaration read the live policy pages.

## Policy rules relied on

I could not open the Play Help pages directly from the build sandbox (blocked), so the points below come
from search-result excerpts of those pages. **Re-read the live pages before submitting** and correct
any wording here that differs.

- [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491):
  apps that are not accessibility tools need an in-app disclosure and consent, a permissions declaration
  in Play Console, and a demo video (disclosure, consent, decline, and the feature using the API).
  The `isAccessibilityTool` flag is only for apps whose main purpose is helping people with disabilities.
  Changing how the API is used means resubmitting the form. Since the 30 Oct 2025 update, using the API
  to autonomously initiate, plan and execute actions is prohibited.
- [Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561)
  and the User Data policy ([answer 10144311](https://support.google.com/googleplay/android-developer/answer/10144311)):
  disclosure must be inside the app during normal use (not only in the listing or privacy policy), say
  what data is accessed and how it is used, be separate from other notices, and consent must be an
  affirmative step. Backing out, tapping away or pressing home must not count as consent.

## What the service listens to (`accessibility_service_config.xml`)

| Setting | Value | Needed? |
| --- | --- | --- |
| Event types | view focused, view clicked, text selection changed, text changed, window state changed, windows changed | Yes. Focus/click/window events decide when the bubble appears. Selection changes keep the cursor position current. Text-changed is used only to learn that text was added (count and timestamp), never its content. |
| `canRetrieveWindowContent` | true | Yes. Without it the service cannot find the focused field or write into it. Reads are limited to the focused editable field. |
| `flagRetrieveInteractiveWindows` | on | Yes. Used to tell whether a keyboard window is on screen (`windows`, type only). |
| `flagInputMethodEditor` | on | Yes. Lets the service use the field's input connection on Android 13+ for more reliable insertion. |
| `flagReportViewIds` | **removed** | The service never read a view's resource id. Removed in this change. |
| `packageNames` | not set (all apps) | The bubble must work in any app. The user can limit it per app inside Voice Mode, and the service does not read fields in apps the user left out. |
| `isAccessibilityTool` | not set | Correct. This is not an accessibility tool and must not claim to be. |
| `notificationTimeout` | 0 | Performance only. |

## What it reads and writes in other apps

- **Reads:** only the input-focused editable field: its text, cursor/selection and whether it is
  editable, plus which app is in front (package name, for the per-app list). Text is needed because
  Android's set-text action replaces the whole field, so the existing text must be sent back with the
  dictated words inserted at the cursor. The window list is read only for window *type*.
- **Writes:** the dictated words into that field (set-text, or clipboard + paste as a fallback). On the
  paste fallback the dictated text is briefly placed on the clipboard and the clipboard is cleared again.
- **Does not:** read other nodes on screen, take screenshots, click or scroll for the user, or fill in
  anything the user did not just dictate.
- **Password fields:** before this change a focused password field was treated like any other field.
  Now they are never a dictation target (`!isPassword` in `DictateAccessibilityService`): no bubble, no read, no write.
- **Stored or sent:** nothing read from other apps is stored or transmitted. The only data that leaves the
  device is the voice recording (and the user's own prompt text) sent to the speech provider the user chose,
  which is the same as keyboard dictation.

## Permissions involved

| Permission | Why |
| --- | --- |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`) | Detect the focused field, insert text. Enabled by the user in Android settings. |
| Draw over other apps | Show the floating button. |
| Microphone (`RECORD_AUDIO`) | Record the dictation. Same permission the keyboard uses. |
| Foreground service + microphone type | Android only allows background mic capture while a visible notification runs. |
| Notifications (`POST_NOTIFICATIONS`) | Show that notification (recording / transcribing). |
| Foreground service data sync | Unrelated to the bubble: on-device model downloads. |

## Findings and changes

1. **Broader than needed:** `flagReportViewIds` (removed); password fields (now excluded).
2. **Consent was a plain OK dialog** with no record. Replaced (see below).
3. **Service description was vague** about reading text and did not mention passwords or audio. Rewritten.
4. **Service enabled straight from Android settings** (skipping the app) would have shown the bubble. The
   bubble now stays hidden until the current disclosure has been agreed to in the app.
5. Residual: Android itself lists the service as able to "observe your actions and retrieve window
   content", which is standard wording for any accessibility service and cannot be changed.
6. Residual: window-state events name the front app. The per-app list uses this; nothing is kept.

## Consent flow now

Floating-button settings → "Accessibility service" row. If consent for the current version exists (or the
service is already on) the app opens Android settings directly. Otherwise it shows the disclosure with
**I agree** / **No thanks**. Outside-tap and Back do nothing. *I agree* stores the time and version
(`floatingButtonConsentAt`, `floatingButtonConsentVersion`) and then opens Android settings. *No thanks*
closes the dialog and goes nowhere. Raising `AccessibilityConsent.CURRENT_VERSION` after editing the
text shows it again and hides the bubble until re-agreed. The keyboard's own button is untouched.

New English strings only; other languages show English for these until translated. The older
`dictate__floating_button_disclosure_*` strings are now unused.
