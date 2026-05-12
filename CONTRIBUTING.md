# Contributing to Bingwa Companion

Thanks for contributing.

## Ground Rules

- Keep pull requests focused and small
- Prefer clear fixes over broad refactors
- Preserve existing behavior unless the change is intentional and documented
- Test on a real Android device when the change touches SMS, calls, permissions, foreground services, or accessibility flows
- Do not include secrets, personal data, or real customer SMS logs in commits

## Development Setup

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run `assembleDebug`.
4. Install the debug APK on a test device.
5. Grant the required permissions and enable the accessibility service.

## Branching

- Fork the repository
- Create a branch from `main`
- Use a descriptive branch name such as `fix/sms-parser` or `feat/release-automation`

## Pull Requests

Please include:

- What changed
- Why it changed
- Any device or Android-version assumptions
- Manual test notes
- Screenshots if the UI changed

## Reporting Issues

Useful bug reports include:

- Device model
- Android version
- Expected behavior
- Actual behavior
- Reproduction steps
- Relevant logs with sensitive data removed

## Code Style

- Follow the existing Kotlin style in the repo
- Keep naming direct and descriptive
- Add comments only where the code would otherwise be hard to follow

## License

By contributing, you agree that your contributions will be released under the MIT License used by this project.
