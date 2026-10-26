# Qur'an

The Qur'an for the Light Phone III, in Arabic with the English translation, and recitation.

- Uthmani Arabic text bundled with the tool, in the Amiri Quran typeface. Readable offline from the first launch.
- English: Marmaduke Pickthall's translation (1930, public domain), bundled with the tool, so it also works offline.
- Recitation ayah by ayah: Alafasy, Husary, Minshawi, Abdul Basit, Sudais. The current ayah is highlighted and followed, playback continues with the screen off, and any ayah can be repeated for memorisation. Optionally continues into the next surah when one ends (off by default). Audio is cached as you listen.
- Juz index, bookmarks, continue where you left off, and search in English or Arabic (Arabic search ignores diacritics).
- Show Arabic, English or both, in three Arabic text sizes.

## Privacy

No account, no tracking, no analytics, no ads. The text and translation are on the phone; the tool only goes online to stream recitation from `cdn.islamic.network` while you listen. Bookmarks and your reading position stay on the phone.

## Sources and licenses

The code is MIT licensed (see `LICENSE`). Bundled content keeps its own terms, detailed in `NOTICE`:

- Arabic text: [Tanzil Project](https://tanzil.net), Uthmani v1.1, CC BY 3.0, distributed verbatim with its copyright notice.
- English: Marmaduke Pickthall, *The Meaning of the Glorious Koran* (1930), public domain; Tanzil's digital edition, which lightly normalizes spelling and some wording (see `NOTICE`).
- Font: [Amiri Quran](https://github.com/aliftype/amiri), SIL Open Font License 1.1 (`tool/src/main/assets/OFL-AmiriQuran.txt`).
- Surah names: [alquran.cloud](https://alquran.cloud).
- Recitation: streamed from the Islamic Network CDN, not bundled; recordings belong to the reciters, licensed to Islamic Network for free non-commercial use.

## Build

To build it yourself, copy `tool/` into a checkout of [light-sdk](https://github.com/lightphone/light-sdk) and run `./gradlew :tool:assembleRelease`.
