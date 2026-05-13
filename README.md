# Bingwa Companion

<p align="center">
  <img src="https://img.shields.io/badge/Android-26%2B-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Android 26+" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-MIT-black?style=for-the-badge" alt="MIT License" />
  <a href="https://github.com/sponsors/gingerbreadsketchy">
    <img src="https://img.shields.io/badge/Support%20the%20Project-GitHub%20Sponsors-EA4AAA?style=for-the-badge&logo=githubsponsors&logoColor=white" alt="Support the project" />
  </a>
</p>

Standalone Android companion app for the Bingwa payment flow. It listens for payment SMS messages, matches them to configured offers, renders the correct USSD code, and helps complete fulfillment on-device with accessibility-assisted automation.

## Why This Exists

This project is a clean-room Android companion built to support a Bingwa fulfillment workflow without modifying or unlocking any paid APK. It is intended to be transparent, auditable, and easy to extend.

## Highlights

- Reads incoming payment SMS messages on-device
- Filters messages by sender name and success keywords
- Extracts the paid amount and recipient phone number
- Matches payments to a local bundle catalog
- Queues fulfillment jobs locally
- Launches USSD automatically
- Uses an accessibility service to observe dialogs and send replies when configured
- Works with dual-SIM slot hints where supported by the device
- Supports swipe navigation across the main app pages
- Lets you clear purchase history from the dashboard
- Keeps fulfillment history cleaner by showing only relevant USSD and status details

## Tech Stack

<p>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Android%20SDK-34A853?style=flat-square&logo=android&logoColor=white" alt="Android SDK" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Material%203-1E88E5?style=flat-square&logo=materialdesign&logoColor=white" alt="Material 3" />
  <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/Serialization-000000?style=flat-square&logo=kotlin&logoColor=white" alt="kotlinx.serialization" />
</p>

## Project Facts

- Package name: `ke.co.bingwa.companion`
- Minimum Android version: `26`
- Target SDK: `35`
- Current release: `v2`
- Persistence: `SharedPreferences` + `kotlinx.serialization`
- Automation path: `BroadcastReceiver` + `ForegroundService` + `AccessibilityService`

## How It Works

1. A payment SMS arrives on the device.
2. The app checks sender filters and success keywords.
3. The parser extracts the amount and recipient phone number.
4. A matching active offer is selected from the local catalog.
5. A fulfillment job is queued.
6. The app launches the generated USSD code.
7. The accessibility service tracks the USSD dialog, filters unrelated screen noise, and can auto-reply where needed.

## Default Offer Support

The project ships with a seeded local catalog and known USSD patterns such as:

- `*180*5*2*pppp*5*1#`
- `*180*5*2*pppp*5*2#`
- `*180*5*2*pppp*5*4#`
- `*180*5*2*pppp*5*5#`
- `*180*5*2*pppp*5*6#`
- `*180*5*2*pppp*5*7#`
- `*180*5*2*pppp*5*8#`

`pppp` is replaced with the normalized recipient phone number before the USSD call is launched.

## Setup

1. Open this folder in Android Studio.
2. Let Gradle sync and install any missing SDK components.
3. Connect a real Android device.
4. Grant `SMS`, `Phone`, and notification permissions.
5. Enable the app accessibility service in Android settings.
6. Configure sender filters, keywords, offers, reply sequences, and preferred SIM slot inside the app.

## Build Locally

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases

Each public release includes a downloadable Android APK.

The current `v2` release includes:

- Swipe navigation between the main pages
- A `Clear History` action for purchase records
- Cleaner purchase history entries and transcript previews
- Improved accessibility logging focused on real USSD dialog content

If you just want to install the app, open the Releases page and download the latest APK asset.

A checksum file is also included with each release for anyone who wants to verify the download.

## Contributing

Contributions are open.

- Fork the repository
- Create a feature branch
- Make focused changes
- Test on a real device where possible
- Open a pull request with a clear description

See [CONTRIBUTING.md](CONTRIBUTING.md) for the working agreement.

## Support

If this project saves you time or helps your workflow, you can support ongoing maintenance here:

- GitHub Sponsors: https://github.com/sponsors/gingerbreadsketchy

You can also open issues, fork the repo, improve it, and send pull requests.

## Credits

Created and maintained by **gingerbreadsketchy**.

If you publish forks or derivative improvements, keep attribution in place and link back to the original project where appropriate.

## License

Released under the [MIT License](LICENSE).

## Important Limits

- Android background behavior varies by device vendor and OS version
- USSD automation depends on accessibility staying enabled
- SMS parsing depends on the exact payment message format you receive
- Dual-SIM routing extras are OEM-sensitive and may not behave the same on all phones
- This project is intended for lawful and authorized use only
