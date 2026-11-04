# ScreenOS

ScreenOS is an Android TV dashboard for photos, videos, music information and phone-based control.

## Current foundation (0.1)
- Native Android TV app entry point (Leanback + normal launcher).
- Fullscreen dashboard packaged inside the APK.
- Basic TV remote navigation through Android WebView.
- GitHub Actions workflow builds a debug APK artifact.

## Planned next
1. Phone remote served by the TV app on the same Wi-Fi network.
2. USB media selection using Android's Storage Access Framework.
3. Spotify connection settings on the phone remote.
4. Photo slideshow, video playback, clock/weather and dashboard customization.

## Spotify setup design
Spotify will be optional. The phone remote will contain a Spotify verbinden page where the owner can enter their Spotify Client ID and start the authorization flow. The implementation should use Spotify Authorization Code with PKCE for a public client; a client secret must never be requested or stored in the phone browser or APK. After authorization, the TV dashboard can show currently playing metadata and album artwork subject to Spotify API permissions and account/API availability.

No Spotify credentials are included in this repository. The dashboard currently displays a not-connected placeholder.

## Build
GitHub Actions builds app-debug.apk on pushes to main or when manually started from the Actions tab. This first build is a debug APK for testing, not a Play Store release.
