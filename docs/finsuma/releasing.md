# Releasing Finsuma

What to build, where each file goes, and what each store asks for. Signing keys live outside the
repo in `C:\Users\camer\finsuma-signing` (back this folder up; losing a key means installed copies
can never be updated).

| Store | File | Key |
| --- | --- | --- |
| GitHub Releases | `Finsuma-android.apk` (no Google services), `Finsuma-android-google-cast.apk`, Windows `.exe`/`.zip`, Linux `.AppImage`/`.deb`/`.tar.xz` | `finsuma-release.jks` |
| IzzyOnDroid | the GitHub `Finsuma-android.apk` (Izzy pulls it from GitHub Releases) | `finsuma-release.jks` |
| F-Droid | built by F-Droid from source (they sign it themselves) | F-Droid's key |
| Google Play | `Finsuma-play.aab` (no YouTube/SoundCloud downloader) | `finsuma-play-upload.jks` (upload key) |

## Building

Android (in `mobile/`, with `keystore.properties` / `keystore-play.properties` copied from the
signing folder):

```sh
./gradlew assembleDegoogledRelease assembleTempusRelease   # GitHub / IzzyOnDroid APKs
./gradlew bundleTempusRelease -PfinsumaPlay                # Google Play bundle (.aab)
```

Desktop (repo root): `pnpm run build`, then `electron-builder --win nsis zip --x64 --publish never`
on Windows and `electron-builder --linux --x64 --publish never` on Linux. Move `.env.local` aside
first so no home address is baked in.

## Google Play

- **Account:** one-time $25 developer registration. New personal accounts must run a closed test
  with at least 12 testers for 14 days before going public.
- **App signing:** choose "Play App Signing" and upload `Finsuma-play.aab`; Google keeps the
  app-signing key, you keep the upload key.
- **App name:** Finsuma. **Short description:** see `mobile/fastlane/metadata/android/en-US/short_description.txt`.
  **Full description:** the same folder; remove the "Optional: Finsuma server" paragraph's
  YouTube/SoundCloud sentence for Play.
- **Category:** Music & Audio. **Tags:** music player, streaming.
- **Contact email:** required (public). **Website:** https://github.com/CameronStorer/finsuma
- **Privacy policy URL:** https://github.com/CameronStorer/finsuma/blob/main/PRIVACY.md
- **Ads:** No. **In-app purchases:** No (unless you charge for the app).
- **Content rating questionnaire:** Music app; no violence, sexual content, gambling, or user
  interaction features. Note: lyrics/songs come from the user's own server.
- **Target audience:** 13+ (not designed for children).
- **Data safety:** "No data collected" and "No data shared". Data is sent only to the user's own
  server; encrypted in transit when the server uses HTTPS. Users can delete data by uninstalling.
- **Permissions declarations:** foreground service type "media playback" (playing music in the
  background); Bluetooth connect (start playback when a car or headphones connect).
- **Graphics:** icon 512×512 (`mobile/fastlane/.../images/icon.png`), feature graphic 1024×500
  (still needed), 2–8 phone screenshots (`images/phoneScreenshots`).
- **Countries:** all. **Price:** free or paid; paid needs a payments profile.

## F-Droid

- Open a merge request to https://gitlab.com/fdroid/fdroiddata adding `metadata/com.finsuma.android.degoogled.yml`
  (template below). Store text, icon and screenshots come from `mobile/fastlane/metadata`.
- Expect the **NonFreeNet** anti-feature (YouTube/SoundCloud) and possibly **NonFreeDep** review of
  dependencies; the `degoogled` flavor has no Google libraries.

```yaml
Categories:
  - Multimedia
License: GPL-3.0-only
AuthorName: CameronStorer
SourceCode: https://github.com/CameronStorer/finsuma
IssueTracker: https://github.com/CameronStorer/finsuma/issues
AutoName: Finsuma
RepoType: git
Repo: https://github.com/CameronStorer/finsuma.git
AntiFeatures:
  NonFreeNet:
    en-US: Can save music from YouTube and SoundCloud via the optional Finsuma server.
Builds:
  - versionName: 1.0.0
    versionCode: 52
    commit: v1.0.0
    subdir: mobile/app
    gradle:
      - degoogled
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 52
```

## IzzyOnDroid

Open an issue at https://gitlab.com/IzzyOnDroid/repo asking to include the app, linking the GitHub
repo. It picks up `Finsuma-android.apk` from each GitHub release automatically.

## GitHub Releases

Tag `v1.0.0`, title "Finsuma 1.0.0", attach all files from `C:\Users\camer\finsuma-releases`, and
paste the changelog. The desktop app's update check reads these releases.

## Windows

- **winget:** submit a manifest to https://github.com/microsoft/winget-pkgs (PackageIdentifier
  `CameronStorer.Finsuma`, installer URL = the GitHub release `.exe`).
- **Microsoft Store (optional):** free individual developer account; upload the `.exe` installer
  as a "Win32 app" (needs a privacy policy URL, screenshots, description).
- Unsigned installers show a SmartScreen warning until they build reputation; a code-signing
  certificate removes it (paid).

## Linux

- **GitHub Releases:** AppImage, `.deb`, `.tar.xz` (already built).
- **Flathub (optional):** submit a manifest to https://github.com/flathub/flathub; needs an
  AppStream metainfo file (`org.jeffvli.feishin.metainfo.xml` can be adapted) and screenshots.
- **AUR (optional):** a `finsuma-bin` PKGBUILD pointing at the `.tar.xz` release.
