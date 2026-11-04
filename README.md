# Two Tone

A two-colour home screen for the Mudita Kompakt (KompaktOS), made for E-ink.
Personal, non-commercial.

## What's in it

- **Home:** clock (pixel, big type or cards) with the weather beside it; a "today" bar
  with the day from 07 to 23 and what's still to come; your list, a page at a time with
  dots; a now-playing bar (swipe it away to stop); battery and weather at the bottom.
- **Own status bar** (optional): time in the middle, notifications on the left, chosen
  icons on the right. Tap the bell for your notifications, the rest for the status page.
- **Sections**, each replaceable by any installed app (Settings > sections):
  phone, music, notes, calendar, weather, coffee, clock, books, news.
  - **music:** click wheel with sound and vibration, playlists, find, album pages with
    dithered covers, scrubbing, repeat, sleep timer, Bluetooth and lock-screen controls,
    picks up where you left off.
  - **clock:** alarms (sound and/or vibration), timer, stopwatch; tap the home clock.
  - **books:** EPUB reader, a page at a time, bookmarks, time left, dictionary.
  - **news:** add sites or feeds; stories shown as plain text; save for later; share a
    page from the browser to read it here.
  - **notes:** Markdown files, in the launcher or a folder of your own (an Obsidian
    vault works); lists you tick in one tap; voice notes; pin, archive, filter, sort,
    find; remind me (makes a calendar event); anything shared to Two Tone can become a note.
  - **weather:** a pixel street that shows the weather, wind, a line about rain, the
    next hours, and the week with range bars.
  - **phone, calendar, coffee** as before.
- **Apps:** search and an A–Z index.
- **Look:** one font for everything (UNSCII recommended), four text sizes, black on
  white or white on black, clean refreshes against ghosting, charging clock at night.

## Fonts

UNSCII is not included in this repository by default. Download `unscii-16.ttf` and
`unscii-8.ttf` from viznut.fi/unscii (public domain) and put them in
`app/src/main/assets/fonts/` before building. Then choose Settings > font > unscii 16.

## Building

GitHub builds it. Every change to this repository runs `.github/workflows/build.yml`,
which publishes `twotone.apk` as the latest release, numbered 1.0.1, 1.0.2 and so on:

    https://github.com/<you>/<repo>/releases/latest/download/twotone.apk

On a computer with Android Studio: open this folder and press Run.

### The signing key

Android only installs an update over an app signed with the same key.
`signing/twotone.keystore` is a fixed key for this personal app, so every GitHub build
installs over the previous one and keeps your data. If you put your computer's Android
Studio key in as `signing/debug.keystore` (see below), that one is used instead, and the
first GitHub build installs over the version you built in Android Studio.

The Android Studio key is at:
- Mac / Linux: `~/.android/debug.keystore`
- Windows: `C:\Users\<you>\.android\debug.keystore`

Either key is only for this app. Don't use them for anything you publish.
