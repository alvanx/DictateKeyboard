# Privacy Policy for Voice Mode

> **Draft. Do not publish as is.** Fill in the operator's details and every point marked
> **[TO CONFIRM]** first. Everything else describes the App and the server in this repository as
> they are today.

**Effective date:** [DATE OF FIRST RELEASE]
**Last updated:** 15 October 2026

This policy explains how **Voice Mode** (the "App", application ID `com.placeholder.voicemode`)
handles your information. The App is made by **[OPERATOR NAME]** ("we", "us"), [OPERATOR ADDRESS].
We are the data controller for the few things described below.

Questions? Write to **[SUPPORT EMAIL]**.

---

## 0. Two ways to use the App, and only one involves us

Voice Mode turns your speech into text. You can set that up in two ways, and they handle your data
very differently.

**The free way does not involve us at all.** You bring your own API key from a provider you choose,
or you use on-device recognition. Your audio goes from your phone straight to that provider, or never
leaves the phone. We run no server in that path. We receive nothing and store nothing about you.

**Plus is optional.** Plus is a subscription you buy through Google Play. Only if you subscribe do
your recordings pass through a server we run, called Voice Mode Cloud. Section 4 describes exactly
what happens then.

---

## 1. The short version

- **Unless you subscribe to Plus, we receive none of your data.** No server of ours in your path, no
  database about you, no analytics.
- To turn speech into text, the App sends your recording to **an AI provider you choose** (for
  example OpenAI, Groq, or your own server). It goes **directly from your phone to that provider**,
  never through us.
- **On-device recognition** needs no internet connection and no provider at all.
- **Plus is the one exception.** It is optional, it only starts with a subscription you buy
  yourself, and section 4 says what it does.
- Your API keys, prompts and settings are stored **on your phone**, and we never receive them. Copies
  can exist in two other places, and only if you use them: Android's own backup, where they are
  end-to-end encrypted (section 2.6), and your watch, if you use the Wear OS app (section 3.5).
- The App has **no ads, no tracking, no telemetry and no crash-reporting tools**.
- The App is a keyboard, but it **does not log your keystrokes** or collect what you type in other
  apps. It only handles audio you record on purpose, by pressing the dictation button.
- **You can check all of this.** The App and the Voice Mode Cloud server are open source (section 8).

---

## 2. What the App handles

### 2.1 Voice recordings
When you dictate, the App records audio with your microphone. The recording is saved to a temporary
file in the App's private storage and **deleted automatically** once the transcript is back. To get a
transcript, the App sends the audio to the provider you chose, or processes it on your phone if you
use on-device recognition.

If a recording cannot be sent because you have no connection, the App keeps it and sends it once you
are back online. If you turn on the "resend / retry" option, the latest recording is kept so you can
send it again. It is deleted when you start a new dictation, when you clear it, or when the App
removes it.

### 2.2 Text you transcribe or reword
The transcript goes to the provider you set up. So does any text you ask the App to reword or format.
The provider sends the result back to your phone, and the App types it into the field you are in.

### 2.3 Dictation history
The App keeps a history of your dictations **on your phone**, so you can insert, review or
re-transcribe them again. It is **on by default** and holds your latest 50 dictations, unless you
change that. You can search it, pin entries, and delete one entry or all of them in the App's
settings.

- **Transcripts** are stored in a private database on your phone.
- **The audio is not kept**, unless you turn that on separately. It is **off by default**. One
  exception: when a transcription fails, its audio is kept so you can try again. It is deleted with
  the entry.
- **Nothing is saved from password fields, PIN fields or incognito mode.** This is true whether you
  dictate with the keyboard, the floating button, or Voice Mode's voice input in another keyboard.
  One case is different: when another app asks Android's speech recognition for text, it does not
  say what the text is for. Such a dictation is saved like any other.
- None of this is uploaded. It stays on your phone until you clear the history or uninstall the App.

### 2.4 API keys and provider settings
To use a provider, you enter your own API key (or, for a server of your own such as Ollama, its
address). The App stores these on your phone, in its private storage. It sends a key **only** to the
provider it belongs to, to sign in your own requests. We never receive your keys.

Copies can exist in two other places: Android's own backup, if you use it (section 2.6), and your
watch, if you use the Wear OS app (section 3.5).

### 2.5 Prompts, words and settings
Your rewording prompts, style settings, custom words, language choices and other settings are stored
on your phone, in the App's private settings and a local database. They are only sent anywhere as part
of a request to your provider, when needed. For example, a prompt goes along when you ask for a
rewording.

### 2.6 Android's own backup
If Android's built-in backup is on for your phone (the backup to your Google account, or moving to a
new phone), it includes the App's settings. That way a new phone starts where the old one left off.
The backup covers your API keys, your Voice Mode Cloud access key and recovery code, the proxy
password, your prompts and settings, your personal dictionary and the keyboard's own files.

- On Android 12 and later, the App joins the backup to your Google account **only when that backup
  is end-to-end encrypted** with your screen lock. That means Google cannot read it.
- On Android 8 to 11, the App cannot set that condition. Whether the backup is encrypted depends on
  your phone.
- Your **dictation history, its audio and the clipboard are never included.**
- This backup belongs to Android, not to us, and we never receive it. You can turn it off in
  Android's settings. The App's own "Back up & Restore" lets you leave your keys and passwords out of
  a backup file you make yourself.

### 2.7 The floating dictation button
The floating button is **optional and off until you turn it on**. It works through an Android
accessibility service. That is the kind of permission that lets an app see what is on the screen, so
here is exactly what the App does with it:

- It finds the text field you are in, and whether it is a password field.
- It types your dictation into that field.
- It reads the text in that field when you apply a prompt to it.
- It notes which app is open, so the button shows up in the right place and stays out of the way.

It reads nothing else on the screen and does not record your typing. Nothing it reads is stored.
Nothing leaves your phone, except text you ask a prompt to reword, which goes to your provider like
any other rewording. You can turn the service off at any time in Android's accessibility settings.

### 2.8 Scanning and translating text
**Scan text** reads printed text from a photo you take or pick. **The translate bar** translates what
you type. Both run entirely on your phone. The photo and the text are never uploaded. The translation
models are downloaded once, when you add a language (section 3.4).

Scan text uses Google's ML Kit library. On its own, that library sends Google usage statistics about
the feature. The App turns that off.

---

## 3. Other services

### 3.1 AI providers you choose
For transcription and rewording, the App connects to a provider **you choose and sign in to with your
own account**. When you use such a feature, the provider handles your audio or text under **its own**
privacy policy and terms. It decides what happens to that data, not us. We have no access to it and no
control over it.

Providers you can choose in the App:

| Provider | Privacy policy |
| --- | --- |
| OpenAI | https://openai.com/policies/privacy-policy |
| Groq | https://groq.com/privacy-policy/ |
| OpenRouter | https://openrouter.ai/privacy |
| Google Gemini | https://policies.google.com/privacy and the Gemini API terms, https://ai.google.dev/gemini-api/terms |
| Together AI | https://www.together.ai/privacy |
| DeepInfra | https://deepinfra.com/privacy |
| Mistral AI | https://mistral.ai/terms/#privacy-policy |
| Scaleway | https://www.scaleway.com/en/privacy-policy/ |
| OVHcloud | https://www.ovhcloud.com/en-ie/personal-data-protection/ |
| xAI (Grok) | https://x.ai/legal/privacy-policy |
| DeepSeek | https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html |
| SiliconFlow | https://docs.siliconflow.cn/cn/legals/privacy-policy (in Chinese) |
| Anthropic | https://www.anthropic.com/legal/privacy |
| Deepgram | https://deepgram.com/privacy |
| AssemblyAI | https://www.assemblyai.com/legal/privacy-policy |
| ElevenLabs | https://elevenlabs.io/privacy |
| Soniox | https://soniox.com/privacy |
| Microsoft Azure Speech | https://privacy.microsoft.com/privacystatement |
| Ollama (local) | Runs on your own device or server. No third party involved. |
| On-device recognition | Runs entirely on your phone. No third party involved. |
| Voice Mode Cloud | Our own service, for Plus subscribers. See section 4. |

Some differences are worth knowing before you choose:

- **Google Gemini:** on Google's free tier, Google may use what you send to improve its products, and
  people at Google may read it. On the paid tier it does not. Which tier applies depends on your
  Google account, not on the App.
- **SiliconFlow** is run from mainland China, and what you send it is processed there.
- **Scaleway and OVHcloud** are European providers and process in the EU. **Soniox** and
  **OpenRouter** offer EU regions, which you can pick in the App.
- **Microsoft Azure Speech** uses your own Azure resource. The audio goes to the address and region
  of the resource you enter.

You can also set up **any other OpenAI-compatible server**. Then your data goes to the server you
enter, and how it handles that data is up to that server.

**Please read a provider's privacy policy before you use it.** Your data is sent with your own API
key, so the provider may keep or process it under your account terms with them (for example, to run
the service or to check for abuse).

### 3.2 GIF search
If you use the GIF panel, the words you search for are sent to **KLIPY**
(https://klipy.com/privacy-policy) to fetch results, using an API key you add yourself. The panel does
not work until you add that key. Nothing is sent to KLIPY unless you open the panel and search.

### 3.3 Google Play
**Google** sells and bills the Plus subscription. Google handles the payment and decides what happens
to that data. We never see your payment details. See https://policies.google.com/privacy.

### 3.4 Downloads from GitHub
Some files are too big to ship inside the App, so it downloads them from **GitHub** (a Microsoft
company):

- on-device recognition models and translation models, when you tap to download one;
- the word lists for swipe typing and suggestions, and the Chinese Pinyin pack, when you add a
  keyboard language or first type in it;
- the community prompt library, each time you open it.

**For now, these files come from the GitHub repository of the original author of the open-source app
Voice Mode is built on** (https://github.com/DevEmperor/DictateKeyboard). We do not run that
repository.

These are plain file downloads. Nothing about you, your dictations or your typing is sent with them.
As with any download, GitHub sees your IP address and which file was requested. See
https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement.

### 3.5 The Wear OS watch app
If you use Voice Mode on a Wear OS watch, your phone sends the watch what it needs to dictate:

- your chosen providers and models, the style prompt, your custom words and your rewording prompts;
- **your API keys** for those providers, so the watch can dictate without the phone. You can turn
  this off in the App's Wear OS settings, on the phone or on the watch. The watch then always goes
  through the phone.

When you dictate on the watch, the recording goes to your phone, which transcribes it as described
above. If the phone is not around and the keys are on the watch, the watch sends the recording
straight to your provider instead.

Phone and watch exchange all of this through Google Play services. That happens over Bluetooth, or
through Google's servers when Bluetooth is not available. Google states that this route is
end-to-end encrypted.

---

## 4. Plus and Voice Mode Cloud (optional subscription)

**This section only applies if you subscribe to Plus.** If you use your own API key or on-device
recognition, none of it applies to you.

Plus is for people who do not want an account with an AI provider. You subscribe through Google Play.
Your recordings then go to our server, Voice Mode Cloud, which has them transcribed by **Groq** and
sends back the text. Rewording and one-tap cleanup work the same way.

### 4.1 What we handle

**Recordings and text.** Your recording is sent to our server, **not stored there**, and passed
straight on to Groq. The same goes for text you reword or clean up. We keep none of it: no audio, no
transcript, no prompt.

**At Groq.** [TO CONFIRM BEFORE LAUNCH: how long Groq keeps API inputs and outputs, if at all, the
data-retention setting chosen for our account, and that they are not used to train models. State
this only once Groq's terms and the account setting say so.]

**Your Voice Mode Cloud account.** Subscribing creates an account on our server. It holds a random
ID, a hash of your recovery code and hashes of your access keys. It is not an account in the usual
sense: we hold **no name, no email address and no Google account**.

**Device names.** So you can see in the App which devices use your account, we store a label for
each one, such as "Pixel 8 · Android 16 · 1.0.0" (phone model, Android version, App version), and
when it was last used.

**IP address.** Like any server on the internet, ours sees your IP address when the App connects. We
do not store it in our database. It is used briefly in two places. When someone enters a recovery
code, the server counts attempts from that address for one minute, to stop guessing. And Cloudflare,
which runs our server, keeps short technical request logs that we use to fix problems. [TO CONFIRM
BEFORE LAUNCH: which request details Cloudflare's Workers Logs keep, IP address included, and for how
long on our plan.]

**Your subscription.** We store what Google tells us about your subscription: Google's purchase
token, the plan (monthly or yearly), any free-trial offer, its status, when the current period ends,
whether it renews, and the latest order number. We also keep track of how much of today's allowance
you have used. We never see payment details.

**Usage log.** For each request we store the time, which of your devices sent it, the kind (dictation,
rewording or cleanup), the provider and model used, the seconds billed, token counts, cost, a status
code and how long it took. For a cleanup we also note whether the text was turned into a list. **These
are numbers only, never content.** Each row is deleted after **90 days**. What remains are daily
totals with no link to any account.

**Abuse warnings.** If our automatic checks spot unusual use, the warning they raise can name an
account. These warnings are deleted when the account is.

**A hash of your previous account.** If you subscribe while a Voice Mode Cloud account already exists
on your phone, the App attaches that account's random ID to the purchase, and Google sends it back to
us with the subscription. We store only a **SHA-256 hash** of it. It is not a Google ID and says
nothing about your Play account, your name or your email address. Its only use is to recognise
repeated refunds. A first subscription, or one made after you deleted your account, carries nothing.

### 4.2 Legal bases

| Purpose | Basis |
| --- | --- |
| Providing the service and handling your subscription | Art. 6(1)(b) GDPR (contract) |
| Detecting abuse, keeping the service secure, alerts | Art. 6(1)(f) GDPR (legitimate interests) |
| Keeping subscription and order records | Art. 6(1)(c) GDPR, with the tax law that applies [TO CONFIRM] |

### 4.3 Who else receives the data, and where it is

- **Cloudflare, Inc.**, USA, runs our server and database. This rests on Cloudflare's data
  processing addendum, which is part of its terms. The transfer relies on the European Commission's
  **standard contractual clauses**.
- **Groq, Inc.**, USA, transcribes your recordings and rewords and cleans up your text. [TO CONFIRM
  BEFORE LAUNCH: Groq's data processing addendum and the transfer mechanism it relies on.]

**Where your data is.** Our database and your account are kept in the **European Union** (Western
Europe). We chose that on purpose.

**That does not cover transcription and rewording.** Those run at Groq, in the United States. We
would rather say so plainly.

Keeping the database in Europe is not the whole answer anyway. Cloudflare is a US company, and access
from the United States counts as a transfer under data protection law, even when the data sits in
Europe. What covers this are the standard contractual clauses above, and the fact that **we keep no
recordings or text**, so there is nothing of that kind to reach.

### 4.4 How long we keep it

| What | How long |
| --- | --- |
| Recordings, transcripts, prompts | Not at all, on our side. At Groq: [TO CONFIRM BEFORE LAUNCH] |
| Usage log (one row per request) | 90 days |
| IP address for the recovery-code limit | 1 minute |
| Request logs at Cloudflare | [TO CONFIRM BEFORE LAUNCH] |
| Daily totals | Indefinitely, with no link to an account |
| Account, access keys, device names | Until you delete the account |
| Subscription and order records | As long as tax law requires [TO CONFIRM: the period, for example 10 years under § 147 AO in Germany] |
| Hash of a previous account | 24 months after you delete the account |

### 4.5 Deleting your Voice Mode Cloud account

You can delete it **in the App**, under Settings → Voice Mode Cloud, or **in a browser** at
**https://api.voicemode.example/delete** if the App is no longer installed. Deleting removes your
account, your recovery code, every signed-in device, your usage log and any abuse warnings about it.

**Deleting the account does not cancel Plus.** Only Google can cancel a subscription. If yours is
still running, the App and the web page tell you so and link to Google Play to cancel it. Cancel there
first if you want the payments to stop.

Your subscription and order records stay for the period above. They hold an order number, a plan and
dates. Once the account is gone, nothing in them leads back to you. Because those records point to the
account, the account entry is emptied rather than removed. What is left is the random ID, the dates
it was created and deleted, and usage totals. No recovery code, no device, nothing anyone can sign in
with.

**One exception, which we would rather name than hide.** The hash described in section 4.1 is kept
for **24 months** after deletion, then erased. Its only use is to recognise repeated refunds. Without
it, deleting the account would reset that count. It is a random ID of ours and cannot be turned back
into an account, an email address or a name. The basis is Art. 6(1)(f) GDPR, our legitimate interest
in preventing payment abuse.

### 4.6 Identifying you

We store no name, no email address and no Google account, so **we cannot identify you**. If you ask us
for a copy of your data or to erase it, we need your **recovery code** or the account ID shown in the
App. Without one of those, we cannot connect a request to any data. Under Art. 11 GDPR we do not have
to collect more information just to be able to do that (Art. 12(2)).

---

## 5. Permissions and why the App uses them

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | To record your voice when you press the dictation button. |
| `INTERNET` | To send audio or text to the provider you chose and get the result back. |
| `ACCESS_NETWORK_STATE` | To tell whether the phone is online, so a recording made offline is kept and transcribed once the connection is back. It only says whether a network is available, never which one or where. |
| `MODIFY_AUDIO_SETTINGS` / `BLUETOOTH` | To record through the right microphone, including Bluetooth headsets. |
| `VIBRATE` | Optional vibration feedback. |
| `POST_NOTIFICATIONS` | To show status notifications, such as transcription progress, on Android 13 and later. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | To keep recording while you use the floating button over another app, and to finish a transcription for a connected watch. |
| `FOREGROUND_SERVICE_DATA_SYNC` | To keep downloading an on-device model when you leave the App. |
| `com.android.vending.BILLING` | To subscribe to Plus through Google Play, only if you choose to. |
| Accessibility service | The optional floating dictation button. See section 2.7. |

The App asks for the microphone only for dictation, and only uses it while you are recording. **The
App does not ask for access to your contacts.**

You can still teach the App a contact's name so it stops autocorrecting it, without giving it your
address book. Either pick one contact in Android's own contact picker, which hands over that one
contact and nothing else, or pick a contacts file you exported yourself. The App takes only the name,
never numbers, addresses or anything else. It stores the name in your personal dictionary on your
phone, like any other word you add. You can see and delete these names in the App's dictionary
settings.

---

## 6. Where your data is kept, and for how long

- **On your phone:** API keys, settings, prompts and your dictation history stay on your phone until
  you delete them in the App or uninstall it. If you use Android's own backup, it holds a copy of the
  settings (section 2.6). Temporary recordings are deleted automatically after transcription.
- **With your provider:** how long your audio or text is kept is up to that provider's policy, not us.
- **With us:** nothing, unless you subscribe to Plus. Then section 4.4 applies.

To erase everything stored on your phone, clear the App's keys, history and settings in its settings,
or uninstall the App.

---

## 7. We do not collect what you type

Voice Mode is a keyboard, but it does **not** record, log, store or send your typing. It only handles
audio you record on purpose for dictation, and only to give you a transcript. Password fields, PIN
fields and incognito mode are kept out of the dictation history. Section 2.3 names the one case where
the App cannot tell what kind of field it is.

---

## 8. You do not have to take our word for it

The App is open source, and so is the Voice Mode Cloud server. Both are in the same public repository:

**<https://github.com/alvanx/voice-mode>**. The App is under `app/` and `lib/`, the server under
`cloud/`.

You can read every line, including the parts this policy describes: what is sent to a provider and
when, that recordings pass through the server without being saved, how an account is deleted, and
what is kept afterwards. The App is licensed under the Apache License 2.0, so you can build it
yourself instead of installing ours. The server code is source-available under the Functional Source
License: anyone can read and check it, but not resell it as a competing service.

Two honest limits:

- **Published code is not proof of what runs.** Reading `cloud/` tells you what the server is built
  from. It cannot prove the running server matches it exactly. Nobody can check that from outside,
  here or anywhere else.
- **Two things are deliberately not in the repository.** Passwords and keys, which live in
  Cloudflare's secret store and are never committed. And one settings file with an alert address and
  the limits our abuse checks use. Publishing those would show people exactly how to get around the
  checks. Nothing this policy describes depends on them.

---

## 9. Data security

Data sent to AI providers and to Voice Mode Cloud travels over encrypted HTTPS connections. The one
exception is a plain `http://` address you enter yourself for a server of your own. The App allows
it so a server on your home network works, and it warns you under the address field that such a
connection is not encrypted.

Your keys and settings are kept in the App's private storage, which other apps on Android cannot
read. On the Voice Mode Cloud server, recovery codes and access keys are stored only as SHA-256
hashes, so even someone with a copy of the database could not sign in with them. No way of sending or
storing data is 100% secure.

---

## 10. Children's privacy

The App is not meant for children under 13 (or the minimum age where you live), and we do not
knowingly handle children's data. AI providers may set their own age limits.

---

## 11. Data sent to other countries

The providers you choose may run servers in other countries, for example the United States. When you
send them audio or text, it may be processed where they operate, under their policies. SiliconFlow
processes in mainland China. Scaleway and OVHcloud, and the EU regions of Soniox and OpenRouter, keep
processing in the EU (section 3.1).

For Voice Mode Cloud, see section 4.3.

---

## 12. Your choices and rights

- You decide whether to use an AI provider and which one, or none at all with on-device recognition.
- You can change or delete your API keys at any time in the App's settings.
- You can delete your dictation history and your prompts, and reset your settings, in the App.
- You can delete your Voice Mode Cloud account (section 4.5).
- You can uninstall the App to remove everything stored on your phone.
- Under the GDPR you have the right to access, correct, erase, restrict, move and object to the
  processing of your data (Art. 15 to 21), and the right to complain to a data protection authority.
  For Voice Mode Cloud, please read section 4.6 first: we can only act on a request we can connect to
  an account.
- For data a provider has already received, please contact that provider directly. It decides what
  happens to that data.

---

## 13. Changes to this policy

We may update this policy. When we make a real change, we update the "Last updated" date above and
publish the new version here. If you keep using the App after an update, the new version applies.

---

## 14. Contact

Questions or requests about this policy:

**[OPERATOR NAME]**, [SUPPORT EMAIL]
[OPERATOR ADDRESS]

Project repository: https://github.com/alvanx/voice-mode
