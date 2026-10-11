## Full app checkup — 11 October 2026

Walked every screen at phone size in Light and Dark mode with real-looking data: Home (Study, Alarms, Schedule), alarm menu, alarm editor, mission picker and panels, all 10 missions while ringing, sound pickers, Start studying, Focus settings, study timer, break picker, break, End early, exit questions and rejection, Plan (timetable, courses), Progress, See all sheets, recap, every Settings sheet, Phone setup and the Guide. No script errors, no screen scrolling sideways and no text running off the edge. Fixed:

- "Test the alarm" buttons still used a ▶️ emoji; now a play icon.
- Guide → Focus Mode described the old break and streak rules; rewritten for the break-time pool, the return after breaks, the automatic never-resetting streak, recaps, and the stricter early exit.
- Progress → By subject cut subject names very short ("Structural A…"); names now get more room and wrap onto two lines.
- Course titles in Plan → Courses were cut off with no "…"; now ellipsised.
- Messages (toasts) could only use half the screen width and wrapped into narrow 4-line boxes; they now use the full width.
- The third early-exit question was styled as a small uppercase heading; now matches the other two questions.
- Privacy policy now explains the Accessibility cover and screen-timeout use, Display over other apps, and study-time Do Not Disturb.

## Phone setup checklist and build upkeep — 11 October 2026

- **Phone setup** (Settings → Phone setup, and shown once after the first-run tour): one checklist with a green tick for each phone setting Rexi Rise depends on — notifications, alarms & reminders, full-screen alarms, Battery Unrestricted / background restriction, Rexi Rise accessibility, Do Not Disturb access, Display over other apps, and Auto-start on phones that have it (marked Done by you, since Android can't report it). A progress bar shows how many are left; ticks refresh when you return from the phone's settings. The Settings row shows a badge with the number still to fix.
- **Build:** GitHub Actions updated to supported versions (checkout v6, setup-java v5, setup-gradle v5, upload-artifact v6, action-gh-release v3; all Node 24) and the runner pinned to Ubuntu 24.04, ahead of GitHub retiring Node 20 actions and moving ubuntu-latest to Ubuntu 26.

## Alarms after a restart and missed-alarm notice — 11 October 2026

- **Ring after a restart, before unlocking:** alarms are kept in Android's device-protected storage and the alarm receiver/ringing service are direct-boot aware, so if the phone restarts overnight (update, crash, battery died and was plugged in) Rexi Rise alarms ring even before you unlock the phone. Existing alarms are moved over automatically after updating. Before the first unlock the alarm rings with sound and its notification; the mission screen opens once you unlock.
- **Missed while off:** after the phone is switched back on, an alarm missed in the last 15 minutes rings straight away; older ones (up to 36 hours) are reported in a "Missed alarm" notification, e.g. "Your 6:00 AM (Morning Alarm) alarm didn't ring because your phone was switched off." Study reminders are not reported.
- An alarm cannot ring while the phone is fully switched off or the battery is flat; Android only allows that for some phone makers' own clock apps.

## Study page follows the phone's sleep timer — 11 October 2026

- **Alarm reliability:** each scheduled alarm now records when it is due. If it rings more than 2 minutes late (the phone held the app back while it was closed — common on Tecno, Infinix, itel, Xiaomi, Oppo, Vivo), the Alarms tab shows "Your 6:00 AM alarm rang 23 min late" with Fix buttons for Battery → Unrestricted and auto-start, and a tip not to swipe the app away. The setup card now also flags Android's "background restricted" setting. Alarms are re-registered whenever the Rexi Rise service restarts, in case the phone cleared them.

- During a focus session the planner now enforces the phone's own Screen timeout: after that long without touching the study page, the screen turns off exactly like pressing the power button (via the Rexi Rise Accessibility service, Android 9+). Wake and unlock the phone and the study timer is still on screen; the countdown, lock and break alerts keep running while the screen is off. "Never" or timeouts over 30 minutes are respected (no auto-off), and ringing alarms are never turned off.

## Milestone study recap — 11 October 2026

- Reaching 7, 14, 30 and 60 study days opens a full-screen, Wayk-style recap you tap through: total study time and sessions, longest session (subject and date), average session, most-studied subject, best day, the time of day you study best, goals reached, the level unlocked, and a confetti finish. It waits until the study timer is closed, and filled milestone circles on the Progress page reopen it any time.
- The old "Level unlocked" pop-up was removed; its level and reward now appear inside the recap.
- Level slide redesigned (option A): a soft circle with a level icon (sprout for Rookie, flame for Dedicated, crown for Master, rocket for Unstoppable), the level description and reward, and a 7d / 14d / 30d / 60d progress row.

## Automatic streak that never resets — 11 October 2026

- A streak day is added only automatically, when you reach a study session goal. The "Mark today" button, tapping today's circle and undo were removed.
- Missing a day no longer resets the streak to 0; your count keeps building from where it was. One day is added per date at most.
- The streak milestone rings (7, 14, 30, 60) use the brand colour instead of yellow.

## Alarm screens follow the Light theme — 11 October 2026

- One button colour everywhere: main buttons and selected options (Start session, Save Alarm, subject chips, durations, segmented tabs, weekdays, days in Plan, difficulty) are black in Light mode and white in Dark mode. Orange-brown stays for links, ticks, switches and progress; the dark study timer and ringing screens keep orange buttons so they stand out.

- The New/Edit Alarm screen, mission picker, verse/affirmation/quiz packs, Wake-up Check, alarm sound pickers and sound credits now use the light colours when Appearance is Light (or System with a light phone), and stay black only in Dark mode. The ringing-alarm and mission screens stay black so they are easy to see when you wake up.

## Fairer Bible/affirmation voice check and greyed-out alarms — 11 October 2026

- **Percentage check:** a Bible verse passes when about 75% of its words are heard in order (affirmations 80%). Extra words ("um", the reference, repeats) no longer count against you.
- **Near-misses accepted:** word endings (strengthen/strengthens), small mishearings (Crist/Christ), numbers (4/four) and sound-alikes (know/no, to/two, there/their, your/you're) match.
- **Read in parts:** words already heard stay counted (shown in green) if the microphone stops at a pause, and the message tells you the percentage done and the word to continue from.
- **Meaning protected:** skipping a "not / no / never / cannot / without / nor" from the text, or adding one, never passes.
- **Microphone:** listens in your region's English (en-NG on Nigerian phones), waits longer through pauses (3 s), uses partial results, and checks the words heard so far when Android stops listening early instead of failing.
- **Alarms:** switched-off alarms are clearly greyed out (grey time, text and icon on a flatter card) in light and dark mode.
- New tests cover the speech scoring, partial reading, negation guard and the native partial-result handling.

## Alarm sounds trimmed and louder; full-name greeting — 10 October 2026

- Removed five sounds that were too soft or calming to wake you: Reawakening, At Rest, Inspired, Stream & birds and Amazing Grace 2011 (20 sounds remain, every category still has at least one). Alarms that used a removed sound now play the default (Happy Alley).
- The eight remaining music clips (Carefree, Life of Riley, Monkeys Spinning Monkeys, Happy Alley, Daily Beetle, Cipher, Motivator, Beachfront Celebration) are about 4 dB louder (≈ −12.5 LUFS, was ≈ −16.5) with a peak limiter so they don't distort. Browser and Android copies, the offline cache, manifest hashes and credits were updated.
- Greetings and spoken alerts use your full saved name ("Hi, Big Daddy", "Good morning, Big Daddy", "Welcome back, Big Daddy") instead of only the first word.

## Icon review — 10 October 2026

- Reviewed every icon. The built-in line icon set was already consistent; the streak flame was redrawn more cleanly.
- Replaced every emoji used as an icon with matching professional line icons (Lucide style, ISC licence): study card and focus screen (book, coffee, pause), focus summary (coffee, lock, ban, unlock), Strict-lock ready / saved (check circle), emergency exit (siren), goal reached (trophy), saved-plan cloud, microphone buttons, mission category chips (mic, brain, book, vibrate), My study questions, and all verse, affirmation and quiz category icons (sun, sunrise, music, flame, shield, waves, bulb, heart, star, dumbbell, sparkles, target, graduation cap, building, moon, paw, microscope, flask, zap, hard hat, scroll, pen). The sun artwork and spoken/toast text are unchanged.
- Alarm editor: removed the "Try it now" button (Save Alarm only). New icons for Mission (puzzle piece), Normal (alarm clock), Scheduled (repeat arrows) and One-time (calendar with tick).

## Random mission difficulty and three mission colours — 10 October 2026

- Only Math asks for a difficulty (Easy / Medium / Hard). Bible verse length, Memory digits, Tap-in-order grid, Unscramble words and Shake count are now picked at random (easy, medium or hard) every round, so their Short/Medium/Long-style selectors were removed. "How many" stays.
- Mission colours cut to three, one per group: Speak (Bible verse, Affirmation) = brand terracotta; Brain (Math, Memory, Tap in order, Unscramble, Type it, Quiz) = bright yellow; Study & Move (Study question, Shake, Surprise me) = green.

## Study-time silence, morning notification, brand colours and a sturdier break return — 10 October 2026

- **Only calls during study time.** New Focus setting "Silence notifications while studying" (on by default). During study time the phone switches to Do Not Disturb that lets through only calls (any caller, including WhatsApp calls and repeat callers) and alarms; other notifications are hidden. Notifications come back on breaks, an emergency pause and when the session ends, and your previous Do Not Disturb setting is restored. It needs one-time "Do Not Disturb access"; the Focus settings and the start sheet show an Allow button. Rexi Rise's own break/goal alerts and wake-up alarms still come through. A session older than 8 hours never keeps the phone silenced.
- **Morning notification:** "Good morning, <your name>" then "Today's focus: <message>" — no quotation marks.
- **Colours:** every yellow/gold accent (switches, selected levels such as Short/Medium/Long, ticks, pack checks, sliders, Add-verse button, sound picks, Morning chip, celebration medal, confetti, mission icon tints, study-cover countdown) now uses the app's terracotta brand colour. Unchanged on purpose: the sun artwork, Google's sign-in logo, and the yellow "medium" difficulty / warning colours.
- **Break return, layered:** (1) the native break alarm switches the session back to study and tries to open the study page; (2) the Accessibility service checks every second, retries the return and shows the full-screen cover over any app until the study page is open; (3) new backup: the study-timer service also checks every second, so the break still ends on time if Android delays the alarm or kills Accessibility, and — with "Display over other apps" allowed (new Allow button) — it can open the study page and show the same cover by itself. The cover now also says to tap the Rexi Rise notification if its button cannot open the app. Cover and Do Not Disturb decisions live in FocusReturnPolicy with new Java tests.

## Wayk-style alarm list: solid mission icons and ⋯ menu — 10 October 2026

- Redesigned all eleven mission icons as one solid, single-colour set (Math, Bible verse, Affirmation, Study question, Memory, Tap in order, Unscramble, Type it, Shake, Quiz, Surprise me). They show grey right after the alarm time, like Wayk, and in each mission's colour in the mission picker. Mission names are not written on alarm cards; the editor's Mission row shows the icon only.
- Removed the "Next … · in …" text beside the Alarms heading. In its place is a ⋯ menu: New Alarm (same as the + button), Sort by Time / Active first / Mission vs Normal (remembered), and Delete All Inactive (asks first; disabled when every alarm is on). Sorting only changes the display order.
- Recent sessions mark a reached study goal with a clean green tick icon (accessible label "Goal reached") instead of the ✅ emoji. Progress lists keep See all on the right of "By subject this week" and "Recent sessions", no per-row delete icons, and a single trash icon on the left of each See all sheet.

## Break pool, stricter early exit and forced return to study — 10 October 2026

- **Break time is a pool inside the session.** Session length × rhythm sets the minutes: 3 h on 50/10 = 30 min, 3 h on 90/15 = 25 min, 1 h on 25/5 = 10 min, Off = no breaks. Each 5/10/15-min break is taken from the pool; coming back early only uses the minutes taken. Only breaks that fit the remaining pool and session time are offered. When the pool is empty a "No more breaks" notification (and on-screen message) appears. The "Break allowance" count setting was removed.
- **End session is no longer under the 5/10/15 picker or on the break screen.** Every early exit goes through the typed sentence (copy/paste blocked) and then three questions: reason, what happened (8+ words, paste blocked) and "can it wait until your session ends?". Bored/chat/tired reasons, "yes, it can wait", distraction words (WhatsApp, TikTok, game, movie, scroll…) or filler text are rejected: the session and lock stay on and End session is locked for 10 minutes.
- **Break over now forces the study page.** Android (especially Tecno/Infinix/Xiaomi) often blocks an app from opening itself from the background, so only the notification appeared. The Accessibility service now draws a full-screen "Break's over — Back to studying" cover over any app until the study page is open. The same cover appears if a blocked app stays open because Android ignored the bounce. No new permission is needed. It never covers calls, a ringing alarm, the lock screen or a sleeping screen.
- The study page confirms it is on screen with retries, so a slow WebView no longer leaves a stale "return to study" request. Returning from a break inside the app no longer leaves a pending return that would cover the screen later.
- **Screen sleeps normally during a study countdown.** The planner's WebView can no longer switch "keep screen on" on by itself (the alarm screen still stays awake while ringing), the window flag is also cleared whenever the planner regains focus, and the beep sound engine pauses 4 seconds after its last sound so an open audio stream can't hold the phone awake. The stale "return to study" request fixed above also stops the app relaunching itself every 1.5 s, which kept some phones awake.
- Validation: all JavaScript and simulated native tests pass (focus tests updated for the break pool and new exit rules); changed Java files parse cleanly and StudyOverlay compiles against Android API stubs. A real Gradle/APK build and phone test are still needed.

## Alarm reference design, mission icons, music and Android compilation fix — 10 October 2026

- Replaced all eleven mission emoji icons with consistent inline SVG artwork. Math, Bible, affirmation and shake follow the supplied glyph references; the remaining missions have matching original symbols. Mission pickers, settings, previews and guides use the same artwork.
- Restyled the alarm editor with black rounded cards, icon-labelled Mission/Normal and Scheduled/One-time controls, three-row time wheels, weekday circles and a white Save Alarm button. Alarm cards show a small mission icon beside the time, schedule above, and alarm name below instead of repeated mission labels.
- Added twelve one-minute music excerpts to the existing thirteen sounds, grouped into the nine requested sound categories with actual counts. Happy Alley is the new-alarm default. Selections save through the nested sound picker. Added source/license credits in the app and SOUND-CREDITS.md.
- Included all twenty-five audio recordings in both browser and Android resources. Added music and the bundled Roboto font to the browser offline cache. GitHub copies the bundled font instead of downloading it during builds.
- Fixed an Android compilation error in AlarmBridge.java by importing java.util.Locale for its existing Locale.ROOT usage. This missing import was present in the original ZIP.
- GitHub now runs alarm/audio regression checks and saves build-diagnostics with verification and Gradle logs. README.md explains extracting the ZIP into the repository root, retaining the signing key and locating build errors.
- Validation passed: all existing JavaScript and simulated native integration tests; twenty-five matching browser/native sound files; error-free audio decoding; source hashes and duration checks for the twelve new tracks; sound selection persistence; all 1,440 time-wheel conversions; offline cache file checks; workflow YAML and shell syntax; Android resource compilation/linking with SDK 34; and compilation of all Android Java sources against SDK 34 and the declared direct authentication dependencies.
- A full Gradle release APK could not be completed in this environment because Gradle could not reach its dependency repositories. The resource and Java checks above are separate compilation checks, not a signed APK build. Physical-phone appearance, ringing, background alarms and audio volume still need testing.

## Early-exit typing protection and break-return recheck — 9 October 2026

- The displayed early-exit sentence disables selection, copying, cutting, dragging and the long-press menu. The answer field blocks paste/drop and full-sentence keyboard clipboard insertions while allowing normal typing, correction and composing input. The protection is installed whenever this sheet is rendered. Other planner fields keep their normal behaviour.
- Rechecked the existing automatic break return using the production native deadline, state-change, launch, blocking and acknowledgement methods against simulated Android services. No additional native lock change was needed: at expiry, the state changes to study, Home becomes blocked in Strict lock, the study activity is launched without tapping a notification, and the pending return is retained until the page is acknowledged. Screen sleep/keyguard defer the launch until unlock; calls defer the launch until they finish. The Accessibility service continues checking deadlines and foreground apps each second.
- The lock resumes according to the selected lock mode; Strict lock blocks Home, while Block apps retains its selected-app list and Off stays off. A working Accessibility service is required for enforcement.
- Validation passed: complete JavaScript syntax, focus/exit checks, clipboard and input event checks, and the native break integration harness. The CI workflow includes the new harness. No APK build or physical handset test was performed here.

## 60 passages and mission review — 9 October 2026

- Expanded the built-in selection to **60 distinct NKJV passages**: six in each of the ten existing categories (61 verse numbers because Psalm 121:1–2 is one passage). All texts were checked against the recorded NKJV sources. The review corrected nine existing passages where a publisher section heading had accidentally been included in the spoken text. Headings, footnotes and verse numbers are excluded from verification text now.
- Rewrote and expanded affirmations from 40 to **64**, eight per category. They encourage practical effort, patience, self-respect and balanced study without guaranteeing results. The fallback pool uses the same improved content.
- Kept the NKJV-first, available-NLT-second mission, custom verse wording and all seven supplied recordings. Voice 5 remains excluded.

| Mission | Review result |
| --- | --- |
| Math | Rejects partial numeric input, decimal-to-integer accidents and words mixed with an answer. Arithmetic generation passed all three difficulty checks. |
| Affirmation | Improved content; spoken matching respects word order, repetitions and negation. Common contractions are accepted. |
| Bible | 60 verified passages; clear short/medium/long bands. A selected category stays selected if its nearest available length must be used. NKJV then cached/downloaded NLT remains intact. |
| Study question | Numeric equivalents such as 1.50 and 1.5 are accepted only when both answers are fully numeric. Missing units or partial fractions no longer pass. |
| Memory | Requires the full digit sequence and refuses answers during the reveal. Back cancels the reveal timer; restart provides a fresh viewing period. |
| Tap in order | Order/reset logic passed. Extra taps during completion cannot reset the solved board. |
| Unscramble | Guarantees jumbled letters differ from the answer while preserving every letter. Avoids consecutive repeated words when alternatives exist. |
| Type it | Existing sentence-matching logic retained after checks. Selection avoids consecutive repeats when alternatives exist. |
| Shake | Counts only during an active mission, once per high/neutral movement pulse. Math fallback appears after five seconds without shakes in each round, including later random rounds. |
| Quiz | Answer-option and difficulty checks passed; prevents extra taps during completion. Clarified the water-temperature, vacuum, surface-gravity and largest-living-animal questions. |
| Surprise me | Filters invalid/unavailable mission types and guarantees a different next type when the selected pool has alternatives. |

Across missions, completion timers and microphone results belong to their original task/request. Old callbacks cannot advance a replacement mission. Back, snooze and stop cancel pending microphone/timer work. The Android recognizer requests English for the English mission text and releases completed/cancelled recognizers.

Validation: JavaScript syntax, all 11 mission types, NKJV source hashes and category selections, NKJV-to-NLT routing, seven voice mappings, simulated native speech callbacks, focus policies and Java break-return/voice policies passed. The build workflow runs the new regression checks. Android SDK/Gradle were not available here: no APK was built and real phone microphone, sensor and UI testing remains necessary.

Recent-session status labels were discussed only. The proposed replacement for the green tick is “Goal reached”; the display has not been changed in this update.

Content wording references for clarified quiz questions: [USGS pure water at 25°C](https://water.usgs.gov/water-resources/software/PHREEQC/documentation/phreeqc3-html/phreeqc3-64.htm), [NIST speed of light in vacuum](https://physics.nist.gov/cuu/Constants/Value/c.html). Recognizer lifecycle reference: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).

## NKJV Bible alarm missions

- Replaced all 45 built-in KJV passages (46 verse numbers) across the existing 10 categories with complete New King James Version wording obtained from Bible Gateway's NKJV text. No API key or runtime NKJV download is required; these passages are embedded for offline use.
- Verse references and category IDs remain the same, preserving existing selections. Previously shortened built-in quotations now use the full referenced NKJV verse text.
- Both mission layouts and verse-selection instructions identify the first translation as NKJV. Publisher copyright acknowledgment appears in the verse-selection instructions and app content terms.
- User-added verses keep their own text and remain labelled as custom verses.
- After acceptance of NKJV, the mission requests available NLT text and plays the existing `bible_nlt.mp3` recording. This second step also appears when the two translations have identical wording. NLT availability still depends on the existing download/cache mechanism.
- `web/nkjv-sources.json` records the source URLs, verification date and SHA-256 text hashes. Source parsing excludes verse numbers, footnote markers and cross-reference annotations while preserving verse wording, punctuation and capitalization.
- `node tests/bible-translation.test.cjs` passed: all 45 source hashes, category selection, custom verse handling, displayed NKJV labels and the NKJV-to-NLT sequence. The voice-alert and focus-policy checks also passed. No Android APK build or phone test was performed in this environment.

## Packaged female voice alerts

Seven user-supplied Nichalia Schwartz recordings are included in `web/voices` and copied into Android assets by the build workflow. They play locally, without an API key or a voice-generation request.

| Purpose | Original recording timestamp | Packaged file |
| --- | --- | --- |
| Study starts | 00_51_29 | study_start.mp3 |
| Break suggested | 00_53_46 | break_suggested.mp3 |
| Break starts | 00_55_02 | break_start.mp3 |
| Automatic break ends | 01_04_58 | break_end.mp3 |
| Study goal reached | 01_06_42 | goal_reached.mp3 |
| Study session completed | 01_07_52 | study_complete.mp3 |
| Bible mission: accepted KJV, request NLT | 01_11_20 | bible_nlt.mp3 |

Session-resumed announcements are removed, including manual return from a break. Study alerts respect the talking-alert setting. The Bible instruction remains enabled independently, as before, and stops before microphone listening begins. The Bible mission still requires available NLT text before offering the second translation.

Native playback uses alarm-volume speech audio and a partial CPU wake lock only while playing, without holding the display on. It releases finished, replaced and cancelled players. Dynamic speech (the voice-setting confirmation and the existing daily-focus notification-dismissal nudge) retains phone TTS. Phone TTS also provides a fallback when a recording cannot be played.

Native timer updates and scheduled events share announcement claims, so foreground goal/break/suggestion updates can play their recordings while native-first events do not duplicate them. The browser uses the same clips, included in the updated offline cache.

Validation: all seven MP3s decoded without errors and their bytes match the identified uploads. JavaScript tests cover focus event routing, voice mute/force rules, KJV-to-NLT acceptance and stopping audio before listening. Java policy tests cover mappings, excluded resume, duplicate event claims and manual/automatic break return. The existing focus and break-return checks also pass. An Android APK build and physical-phone playback, volume, screen-timeout and background tests remain required.

## Study display follows phone timeout

- The planner activity explicitly clears keep-screen-on requests on resume.
- Study-return launches, app-block bounces and focus full-screen notifications are deferred while the device is non-interactive or the keyguard is locked. The pending return remains available after unlock.
- Redundant activity launches stop when the planner is already foreground. Window-focus acknowledgement restores the study page without repeated relaunches.
- The existing focus lock and timer remain configured while the display sleeps. Ringing wake-up alarms retain their separate screen-wake behaviour.
- Display-policy regression tests cover screen off, keyguard locked, already-visible timer and return from Home. Physical-device screen-timeout and unlock testing is still required.

## Automatic return when breaks expire

- Native timer updates preserve a pending return to study until the study page is visibly restored. A WebView-driven break-to-study transition also requests an automatic return.
- Expired break updates are processed immediately, including delayed updates from a background WebView.
- Accessibility checks the last foreground app every second, so a stationary Home screen or blocked app cannot wait for a new window-change event before the configured lock applies.
- Activity resume no longer clears the pending return before the study page is rendered. Pause and new-break transitions cancel pending returns.
- Added dependency-free Java policy regression tests for native/WebView expiry races, acknowledgement, pause, new breaks and new sessions. APK build and phone verification are still required.

## Morning reminders and accessibility readiness

- Morning notifications retain the daily home-screen focus message and display: Good morning, [saved name], followed by Today's Focus and the message without surrounding quotation marks. The full saved name is used. The notification remains scheduled for 7:00 AM.
- Daily message selection now uses the calendar day consistently in the web app and Android, including daylight-saving dates.
- All Android focus-session durations require a connected accessibility service with a recent successful heartbeat and an enabled setting. Starting and resuming fail closed if the native service rejects the update.
- Accessibility lifecycle cleanup resets readiness on unbind/destruction. Runtime failures in window handling and deadline checks are logged instead of escaping the accessibility callbacks; the deadline loop continues after a recoverable failure.
- If Android reports Rexi Rise as malfunctioning, the start dialog explains how to reconnect it by switching it off and on in Accessibility settings.
- Regression tests cover 2h/3h starts across lock modes, native rejection, pause/resume, break allowances, session timing and early exit.

### Validation and device checks

Run `node tests/focus-policy.test.cjs`. Android APK compilation and physical-device verification are still required; this editing environment has no Android SDK/Gradle installation. Use the included GitHub build workflow to build the APK. On a phone, check accessibility disabled, enabled but disconnected, reconnection, 2h/3h starts, pause/resume, app blocking, break return and the morning notification. Device-specific service termination cannot be diagnosed conclusively without Android logs.

# Handwritten-note changes

- Breaks count inside the selected session duration. Actual study time is recorded separately; emergency pauses freeze the session clock.
- Settings controls break rhythm and maximum break count (0–4, default 2). Both are fixed at session start. The 5/10/15-minute picker only shows durations within the chosen rhythm and remaining session time. Off disables breaks. Exhausted allowances are rejected by the handler as well as hidden in the UI.
- End session is hidden during breaks. Early exits from the study screen require the typed commitment followed by a reason check. Health, emergency, or urgent responsibility plus an explanation of at least four words is accepted. Distraction reasons keep the session and its configured lock active. This is an explicit rule check, not automated judgment of the truth of a free-text reason.
- Break expiry resumes the configured blocker, opens the study screen, clears any open exit sheet, and preserves the existing talking alerts and motivational quote. The Accessibility service checks the deadline while the planner is backgrounded and retries returning to study. Calls can finish first. Notification dismissal also triggers a return.
- An expired break is not credited as additional break time when reopening the app late. Background native goal alarms now use total session time.

## Validation

Run `node tests/focus-policy.test.cjs` from the project folder. It checks complete JavaScript syntax, session and study clocks, break quotas, fixed session settings, disabled breaks, emergency pause, typed confirmation, and accepted/rejected exit reasons.

The Android SDK and Gradle are unavailable in the editing environment. No APK was built or device test performed. Use the included GitHub Actions build workflow to build the APK. Test break expiry with the app backgrounded, notification dismissal, allowed apps, calls, and a cold launch on a physical device. Android permissions, Accessibility availability, and manufacturer background restrictions can affect automatic foreground return.

## Progress subject list

The subject list retains at most six distinct subjects studied this week. When a seventh subject appears, the subject studied least recently drops from this list. Studying that subject again makes it recent and eligible to return. Session history, weekly totals, charts, and streaks are retained.

Progress previews three of the retained subjects, ranked by actual study time. See all appears on the right of the heading whenever there is at least one subject and opens a compact sheet with at most six subjects. Existing difficulty colors and bar scale are preserved.

## Recent sessions list and popup style

Recent sessions now previews three entries, with See all on the right whenever there is at least one session. The popup shows only the six newest sessions by completion time, dropping the oldest visible entry as new sessions are completed. Full session history remains saved for totals and charts. Both subject and recent-session See all use the same bottom-sheet component and opening animation as the alarm + button.

## Delete icons

Progress subject and session rows have no individual delete icons, including in See all. Each See all popup has a single red trash icon on the left of its header, a centered title, and Close on the right. See all is available even when the list has only one entry.

The subject popup trash removes all currently listed subjects while preserving study history and totals; a new study session can bring a subject back. The recent sessions popup trash deletes all currently listed sessions (up to six) and updates study totals. Both bulk actions keep the existing confirmation prompt. Older history outside these lists is retained. Alarm header controls are unchanged.
