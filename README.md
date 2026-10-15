# Voice Mode

> **Working name.** "Voice Mode" is a placeholder. See [`docs/rebrand.md`](docs/rebrand.md) for what to
> change when the real name is chosen.

<br>

## 📲 Installation

Not published yet. Build it yourself with Android Studio or `./gradlew assembleDebug`; the
application ID is set once in [`gradle.properties`](gradle.properties) (`appId`).

<br>

## ✨ What is it?

**Voice Mode** is an expressive dictation tool for Android that you shape to your liking. Speak,
and clean, formatted text appears in any app: with punctuation, in over a hundred languages,
online or entirely offline. Hold the mic like a voice message and let go to send, or use the
floating button to dictate into apps while another keyboard is open.

Make it yours. Hand your words to a rewording prompt to make them more formal, translated or
summarised, say a command word to give the AI a freeform instruction, fix recurring words with
find-and-replace rules, and pick the look, the button style and the languages. It works with
leading AI models from OpenAI, Google Gemini and many other providers.

A full keyboard is included if you want it: glide typing, next-word prediction and an autocorrect
that reads your fingers rather than your typos. Use it alone, or keep your own keyboard and
dictate through Voice Mode.

There are four ways to power it, and you pick one during setup: **your own API key** with the
provider of your choice, **a server of your own**, **a model on the device** with no network at
all, or **prepaid credit** if you would rather not deal with any of that. The first three need nothing
from us and are not going anywhere.

<br>

<br>

## 🎤 Features

- **Voice dictation with the engine you choose** — Whisper, Gemini, Deepgram, Soniox, ElevenLabs and more: highly accurate speech-to-text in over a hundred languages, with automatic punctuation. It's so sensitive you can literally *whisper* and still get a clean transcription.
- **Push to talk** — hold the mic key and speak, let go and it's sent, like a voice message. Slide left to throw the recording away, drag up to lock it hands-free. A quick tap still works the way it always did.
- **Use Voice Mode from any keyboard** — Voice Mode registers as a system-wide voice input, so the mic key in other keyboards and apps can transcribe through Voice Mode, with your provider, prompts and on-device models. No accessibility permission needed, so it also works in apps that block it.
- **Real-time transcription** — watch your words appear live as you speak, streaming from OpenAI, Google Gemini, Deepgram, Soniox, AssemblyAI, ElevenLabs or xAI. Deepgram's **Flux** models decide for themselves when a turn has ended instead of waiting out a silence timer.
- **On-device transcription — now live, too** — dictate completely offline with a downloadable model: no internet needed and nothing ever leaves your phone. Streaming models write as you speak in ten languages, and for one-shot accuracy there is Whisper, NVIDIA Parakeet (25 European languages), Canary (English, German, French and Spanish in a third of the space) and models specialised in English, German, Russian or Chinese — German and English among them at around 140 MB, with their own punctuation. **Dolphin** adds 40 Eastern languages — Hindi, Arabic, Persian, Thai, Vietnamese, Bengali, Tamil, Urdu and more — in 105 MB, where Whisper answers Hindi in the wrong script. Hold the send button to run just one dictation locally without switching providers, and models free their memory again when idle. Models keep downloading in the background even if you leave the app.
- **Share a voice message and read it** — Voice Mode is in the share sheet for audio and video, so a voice message from any app can be handed straight to it. A screen opens and starts transcribing by itself; the result is searchable, and long files are handled in pieces rather than turned away.
- **Transcription history** — every dictation is saved to a searchable history you can re-insert, replay, re-transcribe or pin, with full control over how long audio is kept. Point it at a folder and each new dictation is written there as a plain text file the moment it is finished, optionally with its recording — Voice Mode only writes the files, so whatever already syncs that folder (a cloud client, a script, your own tooling) picks them up without an account or a token ever being handed over.
- **Long-form dictation** — speak for as long as you like: long recordings are transcribed in the background in segments, so you get your text sooner and never hit a length limit. An optional on-device Smart Turn model cuts at finished thoughts instead of at every pause.
- **Glide typing, suggestions & autocorrect** — Voice Mode is a complete typing keyboard too: swipe across the keys to type whole words, with dictionaries for over forty languages — Arabic, Bengali, Finnish, Hindi, Indonesian, Tamil and Urdu among them — spell check and an autocorrect that decides from where your fingers actually landed rather than from the finished word. How eagerly it corrects is yours to set, the word the space bar is about to take is marked in your accent colour, and backspace right after a correction gives back exactly what you typed. It offers the next word before you type it — weighing the two words before the cursor, in every language it ships data for, not just English — and any word can be added to your dictionary with a long press. It also **learns the words you type** (on by default, switchable): a name or term no dictionary knows is kept once you have typed it often enough, so it stops being marked as a mistake — on this device only, never from incognito mode, password fields or dictation, and all of it in a list you can read and prune.
- **Classic keyboard-free dictation layout** — bring back the pure, voice-first screen from Dictate 3: lock it in, or keep it just a swipe away from the full keyboard — now with a fully customizable action row (drag & drop), an Enter-key symbol popup and long-form controls.
- **Wear OS keyboard** — dictate straight from your watch, tethered through your phone or fully standalone.
- **Floating dictation button** — dictate straight into **any** app, even when another keyboard is active. Pick from six styles (Pill, Ring, Orb, the audio-reactive Cloud and the new Aurora and Lattice orbs), watch a live waveform while you speak, drag it anywhere with edge-snapping, set its color and size, and long-press for rewording — or for a **freeform voice command**: just say what you want and the AI does it, using any selected text as context.
- **AI rewording & rewriting** — turn a selection into something more formal, casual, translated, summarised, or anything you define with custom prompts, with adjustable reasoning effort. Give it a command word and you stop reaching for a button at all: a dictation that opens with it — “Jarvis, make this more formal” — is the instruction, not the text.
- **Community prompt library** — browse rewording prompts shared by others and install them in a tap, or publish your own.
- **Dictation statistics** — track how much you've dictated and typed, with milestones and a home-screen overview.
- **Cleaner transcripts, cheaper uploads** — long silences are trimmed out of a recording before it is sent, and it can be sped up without your voice going higher: providers bill by audio length, so a recording a third shorter costs a third less. Long dictations are packed rather than refused, and can be split into paragraphs automatically at sentence boundaries.
- **Contact names, without the address book** — put your contacts' names into the personal dictionary so the keyboard stops underlining them and starts suggesting them. Voice Mode never asks for contacts permission: you pick the names through a picker, or import a vCard. Nothing is read that you did not hand over.
- **Find & replace rules** — automatically fix recurring words, names or phrases in every transcript.
- **Single-call multimodal mode** — let one audio-capable AI model transcribe *and* format in a single request, for lower latency and cost.
- **Custom prompts & snippets** — build your own reword actions; reusable text snippets (a prompt written in `[square brackets]`) are inserted instantly without an API call. Give a snippet a typing shortcut and it expands as you type: `r5` plus a space becomes the whole block, and one backspace puts the shortcut back.
- **GIF search** — search and insert GIFs right from the keyboard, powered by [KLIPY](https://klipy.com). Add your own free KLIPY API key (bring-your-own-key, like the AI providers); search terms are only sent while the GIF panel is open.
- **Your own stickers** — point the keyboard at a folder of your own images and insert them straight into a chat. Subfolders become tabs, long-press pins a favourite or deletes the file, and nothing leaves the device. Share a sticker to Voice Mode from WhatsApp, Telegram or anywhere else and it lands in the folder.
- **Scan text** — photograph an IBAN, a serial number or an address on an envelope and tap the part of it you want in the field. Recognition runs on the device, no camera permission is asked for, and the photo is deleted as soon as it has been read.
- **Offline translation** — tap Translate in the Smartbar, type in your language and the translation appears in the text field as you go; press Enter and it is sent. Runs entirely on the phone with Mozilla's Firefox Translations models: 49 languages including Hindi, Japanese and Arabic, downloaded one at a time (30–75 MB each), and nothing you type leaves the device.
- **Searchable settings** — find any option by name and jump straight to it, no digging through menus.
- **Backup & restore** — take your whole setup to a new phone: preferences, rewording prompts, personal dictionary and learned words, themes, layouts. You choose what goes in, and an archive can be made **without** your API keys, passwords or Voice Mode Cloud credit — so it can be handed to someone else or attached to a bug report without leaking anything.
- **Voice Mode Cloud — credit instead of an API key** *(optional)* — buy prepaid minutes through Google Play and skip the provider sign-up entirely. Neither your recordings nor your text are stored on the way through; the server that does it lives in [`cloud/`](cloud/) in this repository, so the privacy claims can be read rather than believed. No name, no email address — just a wallet and a recovery code you can delete from inside the app.
- **Bring your own key & provider** — use your own API key with OpenAI, Google Gemini, Groq, Mistral, OpenRouter, Anthropic, xAI, Soniox, Deepgram, AssemblyAI, ElevenLabs, Azure Speech and other compatible endpoints, so you stay in control of usage and cost. xAI's Grok Voice Transcribe costs ten cents an hour of audio, and twenty streamed live. Azure runs Microsoft's own MAI-Transcribe, which detects across 60 languages and follows a switch mid-sentence. Gemini transcribes with Google's dedicated speech-to-text models rather than a chat model under instruction, and if you speak more than one language you can say which ones instead of picking one and hoping. Where a provider runs its data where you are — Soniox has projects in the EU, Japan and India, OpenRouter an EU entry point — the region is a field in the provider editor, and it moves live streaming with it rather than leaving that one request on another continent. For audio and text that never leave the EU at all, **Scaleway** (Paris) and **OVHcloud** (Gravelines) transcribe and reword in France, under the GDPR and with zero data retention.
- **Self-hosting friendly** — point Voice Mode at a server of your own for transcription, rewording and even live streaming, and let it wake a sleeping GPU machine before the first request arrives.
- **A real, full keyboard** *(courtesy of the FlorisBoard base):*
  - Huge variety of keyboard layouts and easy language/subtype switching, including **phonetic Russian** (ЯШЕРТЫ) beside ЙЦУКЕН
  - **French finds its accents from the plain letters** — `ho` reaches *hôte* and *hôtel*, and an unaccented spelling still gets its accent back
  - **Chinese input** with Pinyin and a candidate row, alongside the Zhengma shape-based method
  - Full theme customization with day/night presets and automatic switching, plus bundled themes of our own: **Glass** and **Glass Clear** (translucent, so the app you are in tints the keyboard), **Sand**, **Deep**, and an **E-Ink** pair whose flat monochrome suits an e-reader panel
  - A **high-contrast** day/night pair behind one switch on the theme screen, for anyone who needs the keyboard to be readable rather than pretty: every letter, icon and hint measured at **7:1 or better** (WCAG AAA for body text) against the surface it is actually drawn on, outlined keys, nothing translucent, and no accent colour allowed to weaken it. The numbers are held by a test, not by an opinion
  - Emoji keyboard with search in **51 languages** — look for "heart", "心" or "قلب" and land on the same emoji — plus **emoji suggestions while you type** in about fifty languages and an optional row of your pinned and recent emojis
  - A searchable clipboard that offers what you just copied, tracking parameters stripped from pasted links, an inline calculator, a selection counter, jumps to the start and end of the field, and an incognito mode that remembers nothing
  - A **text editing panel** — a cursor pad that repeats when held, a *Select* key that turns the arrows into a selection, and cut/copy/paste next to them, so fixing a typo in the middle of a paragraph does not mean aiming at it with a fingertip
  - One-handed / compact mode, a **split keyboard** for two thumbs on a tablet or an unfolded foldable, gesture actions, customizable key sound & haptic feedback
- **Privacy-respecting by design** — no tracking, and your audio goes only to the provider you configure. Choose a key, a server of your own or an on-device model and Voice Mode never talks to us at all; choose Voice Mode Cloud and nothing you say or write is stored on the way through.

<p align="center"><i>Bring your own API key — Voice Mode works with:</i></p>
<p align="center">
  <img alt="OpenAI" src="https://img.shields.io/badge/OpenAI-412991?logo=openai&logoColor=white">
  <img alt="Google Gemini" src="https://img.shields.io/badge/Google%20Gemini-4285F4?logo=googlegemini&logoColor=white">
  <img alt="Groq" src="https://img.shields.io/badge/Groq-F55036">
  <img alt="Deepgram" src="https://img.shields.io/badge/Deepgram-13EF93?labelColor=101820">
  <img alt="AssemblyAI" src="https://img.shields.io/badge/AssemblyAI-5D5DFF">
  <img alt="ElevenLabs" src="https://img.shields.io/badge/ElevenLabs-111111">
  <img alt="Soniox" src="https://img.shields.io/badge/Soniox-2A6DF4">
  <img alt="Azure Speech" src="https://img.shields.io/badge/Azure%20Speech-0078D4?logo=microsoftazure&logoColor=white">
  <img alt="xAI" src="https://img.shields.io/badge/xAI-000000?logo=x&logoColor=white">
  <img alt="Mistral" src="https://img.shields.io/badge/Mistral-FA520F">
  <img alt="OpenRouter" src="https://img.shields.io/badge/OpenRouter-6467F2">
  <img alt="Anthropic" src="https://img.shields.io/badge/Anthropic-D97757?logo=anthropic&logoColor=white">
  <img alt="Ollama" src="https://img.shields.io/badge/Ollama-111111?logo=ollama&logoColor=white">
  <img alt="and more" src="https://img.shields.io/badge/%2B%20more-30B7E6">
</p>

<br>

<br>

## 🖥️ Using your own server

Voice Mode speaks the plain OpenAI API, so any server that does too can handle your dictation —
[Speaches](https://github.com/speaches-ai/speaches), faster-whisper-server, `whisper.cpp`'s server,
vLLM, or something you wrote yourself. Nothing is hardcoded about it:

1. **Settings → AI providers → Add your own server**
2. **Base URL** — the address of your server *including the trailing `/v1/`*, e.g.
   `http://192.168.1.20:8000/v1/`. Voice Mode appends `audio/transcriptions` and `chat/completions`
   to it. Plain `http://` on your own network is fine.
3. **API key** — leave it empty if your server does not ask for one. No `Authorization` header is
   sent then.
4. **Transcription model** — browse your server's `/v1/models`, or just type the model id by hand.
   Servers that expose no catalog, or a non-standard one, work fine that way.
5. Pick it as the active provider for transcription, rewording, or both.

Two things worth knowing: **`localhost` means the phone, not the machine your server runs on** —
use its address on your network. And if your server also speaks the OpenAI *realtime* protocol
under `/v1/realtime`, switch on **Real-time streaming** in the same editor to dictate live.

**Ollama is a special case.** It serves no `/v1/audio/transcriptions`, so it can only reword. Run a
speech server next to it for dictation — or use the on-device engine below and skip servers
entirely.

**No server at all:** Voice Mode can also transcribe **fully on your device** with a downloadable
model (Whisper, Parakeet, FastConformer, Canary, GigaAM, SenseVoice, Dolphin, or a live-typing Kroko model). No account, no
network, no audio leaving the phone — offered right in the setup wizard, and under
*Settings → AI providers → On-device (offline)*.

<br>

<br>

## ☁️ Managed service (in development)

For people who would rather not bring a key, a server or a model, the plan is a subscription that
runs dictation and cleanup on our own server. It is one option among four and the rest of the app
does not depend on it. The server lives in [`cloud/`](cloud/) and is published so that its privacy
claims can be checked: [`cloud/src/meter.ts`](cloud/src/meter.ts) is the only file in it that
writes anything, and it stores numbers (timestamps, durations, token counts, status codes), never
audio or text. An account is an anonymous id with a recovery code; there is no name or email.

The subscription design is in [`docs/specs/billing.md`](docs/specs/billing.md).

<br>

## 🧱 Built on

Voice Mode started from [**Dictate Keyboard**](https://github.com/DevEmperor/DictateKeyboard) by
DevEmperor (Apache-2.0), which provided the voice-dictation and AI-rewording foundation. Dictate Keyboard is in turn a fork of [**FlorisBoard**](https://github.com/florisboard/florisboard),
an open-source, privacy-respecting keyboard created by
[Patrick Goldinger](https://github.com/patrickgold) and
[The FlorisBoard Contributors](https://github.com/florisboard/florisboard/graphs/contributors).
Their work provides the entire keyboard foundation — layouts, theming, gesture handling,
clipboard tools and the IME plumbing — on top of which Voice Mode adds its voice-dictation and
AI-rewording layer.

Huge thanks to the FlorisBoard team. FlorisBoard is licensed under the Apache License 2.0;
see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) for full attribution.

<br>

<br>

## 🤝 Contributing

The best way to help right now is to **[open an issue](https://github.com/alvanx/voice-mode/issues)**
with bug reports, ideas or feedback. Full contribution and community guidelines will be
published as the project matures. Thank you! 🙏

**Found a security problem?** Please don't open a public issue for it — use
[GitHub's private advisory form](https://github.com/alvanx/voice-mode/security/advisories/new).
[`SECURITY.md`](SECURITY.md) says what is in scope, what isn't, and what to expect.

<br>

<br>

## 📄 License & attribution

The keyboard app is released under the terms of the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The Plus subscription server (`cloud/`) and its specs (`docs/specs/`) are **source-available**
under the [Functional Source License](cloud/LICENSE.md) (FSL-1.1-Apache-2.0): you can read, run and
modify them for your own use, but you can't offer them as a competing product or service. Each
version becomes Apache 2.0 two years after it is published.

- This project is built on **Dictate Keyboard** (Copyright © DevEmperor, Apache-2.0), itself a fork of **FlorisBoard** — Copyright © The FlorisBoard Contributors,
  licensed under Apache-2.0.
- See [`LICENSE`](LICENSE) for the full license text and [`NOTICE`](NOTICE) for required
  attribution notices.
- On-device transcription uses [OpenAI Whisper](https://openai.com/index/whisper/) (MIT),
  NVIDIA's [Parakeet](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3),
  [Canary](https://huggingface.co/nvidia/canary-180m-flash) and
  [FastConformer](https://huggingface.co/nvidia/stt_de_fastconformer_hybrid_large_pc) models,
  moondream's [Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra) post-train and the
  primeline German fine-tune (CC-BY-4.0),
  [GigaAM](https://github.com/salute-developers/GigaAM) for Russian (MIT),
  [Dolphin](https://github.com/DataoceanAI/Dolphin) by DataoceanAI and Tsinghua University for 40
  Eastern languages (Apache-2.0),
  and — for live transcription — the [Kroko ASR](https://huggingface.co/Banafo/Kroko-ASR)
  community models by Banafo (CC-BY-SA). They are exported to ONNX by
  [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — except Parakeet Ultra, whose weights are
  substituted into sherpa-onnx's own Parakeet graph, since upstream publishes no ONNX build.
- The Lattice button design is ported from
  [thinking-orbs](https://github.com/Jakubantalik/thinking-orbs) by Jakub Antalik (MIT).
- Offline translation uses Mozilla's [Firefox Translations](https://github.com/mozilla/translations)
  models and its Bergamot engine (MPL-2.0), built on [Marian NMT](https://marian-nmt.github.io) (MIT).
- GIF search is powered by [KLIPY](https://klipy.com); GIFs are served by KLIPY under their terms.

<br>
