# Rebrand notes

Status: first pass, 2026-10-15. The product name is not chosen yet, so everything below uses
placeholders that are easy to find and replace.

## Placeholders to replace when the name is chosen

| Placeholder | Where it lives | Replace with |
| --- | --- | --- |
| `Voice Mode` (name) | In-app strings in every language (`app/src/main/res/values*/`, `wear/src/main/res/values*/`), theme names in `app/src/main/assets/ime/theme/*/extension.json`, `README.md`, `PRIVACY_POLICY.md`, `SECURITY.md`, `cloud/` operator pages, `ProviderRegistry.kt` (`displayName`) | The final name. Find with `grep -rIl "Voice Mode" .` |
| `com.placeholder.voicemode` (Play package ID) | `gradle.properties` (`appId`), `app/src/androidTest/.../ExampleInstrumentedTest.kt`, `cloud/wrangler.example.jsonc` (`PACKAGE_NAME`),  `PRIVACY_POLICY.md`  | The final package ID. **Permanent once the app exists in the Play Console.** Staging builds use the same ID plus `.staging` |
| `voicemode.example` (domain) | Website, privacy and deletion links in strings (all languages), `AboutScreen.kt`, `ProviderRegistry.kt` (`api.voicemode.example`, the server address), `cloud/wrangler.example.jsonc`, support email `support@voicemode.example` | Our domain. `.example` is reserved and never resolves, so no build can talk to anyone else's server by accident |
| `VoiceMode` (download folder) | `DictateController.kt` (two places) and the strings that say `Downloads/VoiceMode` | The final name, no spaces |
| `[OWNER NAME]` | `NOTICE` | The copyright holder of our changes |
| `[OPERATOR NAME]`, `[OPERATOR ADDRESS]`, `[SUPPORT EMAIL]` |  `PRIVACY_POLICY.md`  | The real operator's details |
| Teal microphone icon | `app/src/main/res/drawable/ic_app_icon_foreground.xml`, `values/colors.xml` (`ic_app_icon_background`), and the same two in `wear/src/main/res/` | Our own mark. Play also needs a 512 px listing icon |

## Changed in this pass

- Application ID is one gradle property (`appId`) used by both the phone app and the Wear app.
- App name, About screen, links, support email, themes, icon and README no longer point at the
  upstream author. Strings changed in all languages; the original author's personal signature was
  replaced by a credit line.
- The Cloud preset points at a placeholder address instead of the upstream author's server.
- Removed: donation nudges and links (including `.github/FUNDING.yml`), the upstream screenshots and
  sponsor list, the About "What's new" list (it replayed the upstream version history).
- `cloud/`: no default Play package name any more (the server refuses to start without
  `PACKAGE_NAME`), brand text updated, upstream launcher icon removed from the operator page.
-  `PRIVACY_POLICY.md`  is now a **draft** with placeholders; do not publish it as is.

## Kept on purpose (Apache-2.0 requires it)

- `LICENSE`, `NOTICE` (our line added on top, upstream notices intact), and the copyright headers
  in source files.
- Credits in the About screen and README: Dictate Keyboard (DevEmperor) and FlorisBoard.
- Kotlin package names (`dev.patrickgold.florisboard...`) and internal identifiers. Renaming them
  adds risk and no value.

## Not done yet

1. **Not compiled.** There is no Android SDK in the environment this was written in. Run
   `./gradlew assembleDebug testDebugUnitTest` before relying on it. The server was type-checked
   (`npx tsc --noEmit` in `cloud/`).
2. ~~**The Cloud option still appears in the app**~~ **Done: hidden behind one switch.** `CloudAvailability.ENABLED`
   (`lib/dictate-core/.../dictate/provider/CloudAvailability.kt`) is `false`. While it is, Cloud is gone from the
   provider lists and pickers, setup, settings search, the credit screen and the "What's new" tour, no request
   goes to the Cloud address, and a saved Cloud choice falls back to another provider at startup
   (`CloudFallback`). The code is kept. **When the server is live (`docs/specs/billing.md`):** set the real address
   in `ProviderRegistry.CLOUD`, then flip the switch to `true`.
3. **Downloads still come from the upstream author's GitHub releases:** on-device models and
   dictionaries (`LocalModelCatalog.kt`, `GlideDictionaryCatalog.kt`, `BigramCatalog.kt`,
   `TrigramCatalog.kt`, `PinyinPackManager.kt`). Mirror them to our own releases before launch; the
   catalogs carry checksums, so only the base URLs change.
4. **Privacy policy** needs a rewrite for the subscription service and the real operator.
5. **Leftover old name in a few strings:** Finnish, Hungarian and Latvian strings that carry the old
   name with a grammatical ending, and the in-app release-note and tour text that quotes upstream
   version names ("Dictate 5.0"). Handle in the translation pass or remove the tours.
6. **Store assets:** listing text, screenshots, feature graphic, 512 px icon.
7. **Signing:** create our own upload key (`keystore.properties.template`).
8. **Licence items** from the plan: some bundled word lists come from GPL-style dictionaries.
