# Changes

## 1.6
- Clock: a world clock (+ add a place; each shows its time, how far ahead or behind,
  and "tomorrow" / "yesterday"); hold one to remove it.
- Focus timer (clock > focus): set focus and break minutes; while you focus the phone
  goes to Do not disturb (if allowed) or vibrate, and comes back after. Counts today's.
- Trains (new section): your stations; live NS departures with delays, platform
  changes, cancellations and what it runs via, updated every minute while you look.
  Needs your free key from apiportal.ns.nl ("Ns-App").
- Coffee with your Coffee app: "+ log a brew" (beans from Coffee's list, its recipe
  filled in, the day set to today or earlier) and "brews by day". Brews are kept in
  coffee-brews.json beside coffee-beans.json, so the Coffee app can show them too.

## 1.5
- The lock-screen player is gone again (KompaktOS keeps its lock screen on top). The
  music notification now shows on the lock screen, like other players'.
- Shuffle: on now playing (tap "shuffle"), in the music menu and in music settings.
  The song playing carries on; turning shuffle off puts the queue back in order.
  Repeat (off / all / one song) is next to it.
- Notes: in a list, Enter adds the item and leaves the box ready for the next one. In
  text view, Enter after "- [ ] milk" starts "- [ ] " (also "- " and "1. "); Enter on
  an empty item ends the list.

## 1.4.1
- Music on the lock screen: the player now arrives through a silent full-screen
  notification (as alarm clocks do), so it covers the lock screen and can be used,
  instead of sitting behind it.

## 1.4
- Music on the lock screen (Settings > music > on the lock screen): while music plays,
  turning the screen off puts the player on top of the lock screen. The power button
  wakes the phone straight into it; back returns to the lock screen, and "unlock"
  unlocks and opens music in the launcher. Needs "Display over other apps", once.

## 1.3
- Coffee: can show your Coffee app's beans, read from the coffee-beans.json it keeps in
  its backup folder (coffee > use my coffee app's beans). Adding and editing open Coffee.
- Notes: point "notes kept in" at oboegaki's folder to share the same Markdown notes.
- Backup (Settings > backup): everything in one .zip in a folder you choose, now or
  every day (newest seven kept); put a backup back, and the launcher starts again.
- Apps: hold an app to rename it or hide it from the list. Your names are used on the
  home list and in search. Tap the bottom line of apps to show hidden ones.
- Search everything: swipe down on the first page of the home list. Apps, sections,
  contacts, notes, songs, books and saved news.
- Nothing updates while the screen is off: clocks, status checks and player timers stop,
  and start again when the launcher is back.
- The now-playing bar shows while something plays and for ten minutes after a pause.

## 1.2
- Home: whatever another app is playing (Spotify, podcasts, a browser…) shows in the
  now-playing bar, with its cover dithered, the time left, and previous / play-pause /
  next. Tap the words to open that app; swipe the bar away to pause it and hide it until
  something new plays. Uses the same Notification access as the bell. Two Tone's own
  music still comes first.

## 1.1
- Music: page titles no longer get cut off ("now playing"); pages no longer change size
  when Android's status bar hides or shows.
- Home: no time in the top bar (it's big on the page already); no weather in the bottom
  line when the weather is shown up top.
- Notifications: the bell shows as it should; a new notification shows in the top bar
  for six seconds (tap it to open; can be turned off in Settings > status bar).
- Settings values are in square brackets: font [unscii 16].
- Home list: "+ add to the home list" brings back sections you removed, and adds apps.
- Weather: a pixel canal street with a windmill, under a sky that matches the weather;
  wind with direction and in words; "dry all day" / "rain from 15:00"; the next hours;
  swipe up for the week with low-to-high bars and rain amounts.
- Notes, rebuilt as Markdown files (see README). Old notes are moved over once.
- Sharing to Two Tone asks: read in news, or keep as a note.

# Changes before 1.0

These were the test versions built from Android Studio.

## v0.16
- Notifications: a bell with a count on the left of the top bar (when there are any).
  Tap it for the list: app, time, title and a line of the message. Tap one to open it,
  hold one to dismiss it, or clear all. Needs Notification access for Two Tone, once
  (the list page takes you there). Ongoing ones (music, downloads) are left out.

## v0.15
- News (new section): news > sources > add any website or feed address; its feed is found
  for you. "latest" lists stories from all sources, newest first (• = unread). Opening a
  story fetches the page and keeps only the text, shown a page at a time like books. Tap
  the bottom bar to save it for later (kept on the phone, readable offline), change the
  text size or open it in the browser. "read a link" reads any single article, and
  Share > Two Tone from the browser does the same. Pages built only with JavaScript,
  and paywalls, fall back to the feed's own summary.
  Uses the Jsoup library (downloaded by Android Studio when you build).

## v0.14
- Own status bar (Settings > status bar): Android's bar is hidden while the launcher is
  open (swipe down from the top edge to peek at it). Time in the middle; date, next
  alarm, silent/vibrate, bluetooth, wi-fi, mobile signal and battery icon are each
  optional. Tap it for the status page: notifications, wi-fi, mobile data, bluetooth,
  sound (tap to switch), torch, battery, next alarm, kompakt settings, all settings.
  Switch back to Android's bar in the same place.
- E-ink (Settings > e-ink): clean refresh off / after other apps / also every 10 or 5
  page turns (lists and books). The screen modes themselves are KompaktOS's: the page
  explains which to pick and opens the Kompakt settings app.
- Alarms and the timer: "rings with" sound and vibration, vibration only, or sound only.
- Music: songs > find. Type part of a title, artist or album.
- Books: tap a page's top right corner to bookmark it (the corner folds); bookmarks in the
  reader menu. The footer says how many minutes are left in this part, learnt from your
  own reading speed. Hold a word for its meaning (Wiktionary, needs internet; it picks
  the book's language), or send it to another dictionary app.

## v0.13
- Apps: a # A–Z index down the right edge. Tap a letter, or slide along them, to jump.
- Small text is bigger: next to UNSCII 16 it is drawn in UNSCII 8 at 16 px (2 × 8; 24 px at
  the large text sizes), so it
  stays sharp. Needs unscii-8.ttf in assets/fonts; without it, small text stays at 16 px.
- Books (new section): add a folder of EPUB books once, and new ones in it are picked up
  each time you open books. One page at a time: tap the left third to go back, anywhere
  else to go forward, or use the volume keys. Tap the bar at the bottom for contents,
  text size and font. Your place is saved on every page. Hold a book to remove it.
  To use KOReader instead: Settings > sections > books > opens.

## v0.12
- Weather: when it can't connect, "check the connection" finds out whether the phone is
  offline or only this app is kept off the network, and says what to change.
- Vibration: a longer, full-strength pulse that small motors can actually feel, and
  "test vibration" in Settings > music, which reports what the phone says.

## v0.11
- Rows and the home player keys no longer go solid when pressed; only the music wheel flashes.
- Music: smaller wheel, more room for the list.
- The wheel click plays on the music volume (and in headphones), not the notification volume.
- "vibrate on click" now drives the vibration motor directly.
- Music menu order: songs (with "shuffle all" at the top), artists, albums, playlists,
  settings. "now playing" sits above them while something is queued.

## v0.10
- Music picks up where you left off: after a restart the last queue is back, paused at
  the same place (saved every 15 seconds and whenever you pause or change song).
- Album pages: a big dithered cover with the album, artist, song count and length.
- Scrubbing: on now playing, hold the wheel's middle, then turn: 5 seconds per step.
  Press the middle (or wait 4 seconds) to finish. Song options for what's playing:
  hold "now playing" in the music menu.
- Charging page: a big clock, the date, the charge and the next alarm while the phone
  charges. Settings > charging clock: off / at night (21:00–07:00) / always.
  Tap it to go back to home; it returns the next time you plug in.

## v0.9
- Music feels alive: the part of the wheel you press flashes solid, every step of the
  wheel clicks, rows and player keys go solid while held. The click is a short tick
  the launcher makes itself; vibration can be added (Settings > music).
- Music settings (Settings > music, or "settings" in the music menu): click sound,
  vibrate on click, music folder, repeat (off / all / one song), sleep timer.
- Playlists: music > playlists > + new playlist. Hold the wheel's middle (or hold a song
  row) for a song's options: play now, play next, add to a playlist, remove from this
  playlist. A playlist can be played, shuffled, renamed or deleted.

## v0.8
- One font setting (Settings > font). The launcher sizes everything itself. With a
  UNSCII face every size is an exact pixel multiple: hints 16 px, rows 32 px, home rows
  and titles 48 px, clock 96 px (at "medium"; the text-size setting moves these in steps).
- More UNSCII faces: tall, 8, thin, mcr, fantasy (optional; see assets/fonts).
- Home list turns a page at a time again (swipe up/down), with page dots on the right.

## v0.7
- Music runs as a proper media player: Bluetooth / headset buttons, lock-screen and
  notification controls (previous, play/pause, next, stop), pauses for calls and when
  headphones are unplugged. Album covers are shown as 1-bit dithered pictures.
- Home: swipe the now-playing bar sideways to stop and clear it. The list uses bigger
  type and scrolls (always stopping on a row), with a bar on the right; no page numbers.
- Clock (tap the home clock): alarms that ring on time with stop / snooze, repeat days and
  names, a timer, a stopwatch with laps, and the alarm sound. Another clock app can be
  chosen instead in Settings > sections > clock.
- Weather now uses Open-Meteo (no account, no key). If it can't connect, the page says why.
- Heavier lines: thick rules under the top bar and above the footer, solid section bars
  ("today", "alarms", "tools"), heavier wheel and buttons. Page titles are bigger.

## v0.6
- Text sizes: names, titles and things you tap are large text; hints, counts and details
  are small text, and small text grows only half as much with the text-size setting.
  Fixed spaces (top bar, clock, keypad, calendar grid, wheel) keep their size, so nothing
  wraps or gets cut off. Pixel fonts never go below 16 px.
- Music: now playing fits the space above the wheel; "turn for volume" is in the status line.
- The home clock opens a clock app: the one chosen in Settings > clock opens, otherwise
  the phone's own (found automatically).
- Volume keys stay volume keys on the home page.

## v0.5
- Every page has a top bar: ◂ back, where you are, and the time. Counts sit in a line
  at the bottom ("60 apps", "12 notes").
- Phone: "find:" above the contacts. Tap it to type a name; the keypad steps aside
  until you go back. The keypad still narrows contacts by number or name as before.
- Text size has four steps, all bigger than before: small, medium, large, extra large
  (Settings > text size; tap to step through).

## v0.4
- Home is stacked in bands, divided by rules: time, date and weather; today (heading,
  day bar, what's still to come); your list; the now-playing bar; a status line with
  battery and the weather in words.
- UNSCII 16 and UNSCII 8 are fixed font choices (see below).

## v0.3
- Home: clock with the weather beside it, a strip for today (next event, then a bar of the
  day from 07 to 23: dithered = past, solid = events, notch = now), and a now-playing bar
  while music is queued.
- Coffee section (added to the top of your home list once).
- Phone: contacts above a lettered keypad; typing narrows contacts by number or name
  (5-2-6 finds "Jan"). Call, text or save the number underneath.
- Calendar: month grid. Tap a day to see it, hold a day to add an event on it.
- Music: a much bigger wheel, library counts.
- Separate fonts for large and small text, and "snap to pixel font sizes".

## Using UNSCII (built in from v0.4)
1. Download unscii-16.ttf and unscii-8.ttf from viznut.fi/unscii (public domain).
2. Put both, with those exact names, in  app/src/main/assets/fonts/  inside this project.
3. Press Run. "unscii 16" and "unscii 8" are now choices under both
   Settings > large text font and Settings > small text font, for all text:
   app names, menus, lists, the "big type" and "cards" clocks.
Pixel fonts are snapped to exact multiples of their grid (16 px or 8 px) automatically,
and bold is drawn plain, so the letters stay sharp on E-ink.
Until the files are there, those two rows say "missing".

## Coffee
Log a brew: beans, method, water temperature, grind, dose, water, rating, note. A new
brew starts from the numbers you used last time for the same beans.
Beans > add from a shop link: paste the product page address. The launcher reads what it
can (name, roaster, origin, process, altitude, roast, tasting notes) and opens it for you
to check before saving. Shop pages differ, so expect to fix a field now and then.

## What was in v0.2
- Home: clock (pixel, big type or cards), weather line, next two calendar events,
  then your list. Lists never scroll: swipe up or down, press the volume keys,
  or tap the arrows at the bottom to turn a whole page.
- Sections, each replaceable by any installed app (Settings > sections):
  - phone: keypad and contacts (the call itself uses the phone's own call screen)
  - music: your audio files, a click wheel; stops when the album ends
  - notes: plain text, saved as you type
  - calendar: the next four weeks from the phone's calendars
  - weather: Open-Meteo forecast for a town you search for, or where the phone is
- Fonts: mono, sans, serif, handwriting, or any .ttf/.otf file (Settings > font).

## Click wheel
Turn: move through the list (on now playing: volume).
Middle: select (on now playing: play/pause).
Top: menu, which goes back. Left/right: previous/next song. Bottom: play/pause.

## Permissions
Each section asks for its permission the first time you open it. The only network
use is weather (MET Norway forecasts, Open-Meteo town search).

## Getting your old home screen back
Settings > choose home screen app. Pick any launcher.
