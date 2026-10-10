# Rexi Planner (Android)

Semester planner with study reminders and wake-up alarms that need a mission (math, study question, typing or shaking) to stop.

- `web/` – the planner web app (also deployable to Netlify as-is)
- `app/` – the Android shell: WebView + native alarms (AlarmManager, lock-screen alarm screen, alarm-volume ringing)
- Every push to `main` builds a signed APK and publishes it under **Releases**.

## Install this update on GitHub

1. Extract the source ZIP. Copy **its contents** into the repository root, replacing the previous files. Do not upload the ZIP itself as the source code, and do not put everything inside an extra folder.
2. Include `.github/workflows/build.yml`, `web/fonts`, `web/sounds`, `app/src/main/res/raw`, `tests`, and `keystore`. Keep the existing package name and signing key so the APK can update the installed app.
3. Commit to `main`, then open **Actions → Build Android app**. You can also choose **Run workflow** on `main`.
4. After a successful run, download the signed APK from **Releases**. Test a scheduled alarm, a one-time alarm, your chosen music, and each mission on the phone.

The project uses Java 17, Gradle 8.7, Android Gradle Plugin 8.5.2 and Android SDK 34. GitHub installs Gradle automatically; the Roboto font and audio are bundled with the source. Locally, install those build tools, copy `web/index.html`, `web/manifest.json`, `web/icon-192.png`, `web/voices` and `web/fonts` into `app/src/main/assets/www`, then run `gradle assembleRelease`.

If a GitHub build fails, open the first failed step and copy the error above `BUILD FAILED`. The run also uploads a `build-diagnostics` artifact containing the available verification and Gradle logs. Missing write permission for GitHub Actions can prevent publishing a release even when the APK build succeeds; the repository must allow Actions to write its contents.

## Alarm design and sounds

All eleven missions use matching vector icons. Alarm cards show the mission icon beside the time, with the schedule above and alarm name below. The alarm editor and sound categories follow the supplied black-card reference design.

The sound library contains 25 sounds in nine categories. Twelve new musical recordings are one-minute excerpts, and new alarms default to Happy Alley. Existing saved sound choices remain available. Android repeats the selected recording while the alarm rings. Open **Sound credits** in the sound picker or see [SOUND-CREDITS.md](SOUND-CREDITS.md) for source and license details.
