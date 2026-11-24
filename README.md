# MobileStack

## Pintar Pintar mobile (work in progress)

The app now starts with a shared Android/iOS learner catalog backed by the
`API_BASE_URL` environment setting. It supports searching,
filtering by class, bootcamp, or digital product, pagination, and retrying failed
requests. Product detail screens display information from the public API,
including descriptions, course material, schedules, and FAQs where available.
Signed-in learners can use cart and wishlist and preview checkout totals and
promo codes; payments are intentionally excluded. Learners can also sign in,
register, request a password reset, view and edit their profile, and see their
learning list, course materials and job listings. Sessions persist in Android encrypted preferences backed by the
Android Keystore and in the iOS Keychain. Website links still require a separate
browser login; signing out removes the session from this app.

The new code is under `shared/src/commonMain/kotlin/com/getmobilestack/kmp/pintar`:
`catalog` contains catalog models, repository and state transitions; `detail`
contains product detail models and repository; `account` contains the account API;
`shopping` contains the cart and wishlist API;
`ui` contains the shared Compose
screens. The API base URL can be supplied to either API repository for staging or tests. Shared unit tests are in
`shared/src/commonTest/kotlin/com/getmobilestack/kmp/pintar`.

This is **not yet a complete website clone**. Native
social login, full checkout preparation and course playback,
and merchant and mentor features still need implementation. The original MobileStack boilerplate remains
in the project but is not the active app screen.
See `docs/pintar-clone-status.md` for the current scope and remaining work.

To run the Android unit tests, install a JDK and Android SDK, configure
`local.properties` with `sdk.dir`, and run `./gradlew :shared:testDebugUnitTest`.
Copy `.env.example` to `.env` to configure `API_BASE_URL` (or supply it as an
environment variable). The value is embedded at build time for Android and iOS;
rebuild after changing it. It must be an HTTPS origin. Do not put secrets in it.
The Android app build requires a Firebase `androidApp/google-services.json`
registered for `id.pintarpintar.dev`. Debug builds use the Android SDK's default
debug keystore; release signing still needs to be configured. The iOS project
retains its existing Firebase setup.

MobileStack is a fully-featured, Kotlin Multiplatform (KMP) boilerplate template for building cross-platform Android/iOS apps with 100% shared UI. It includes authentication, Firestore database, billing (RevenueCat), analytics, OpenAI integration, and camera/image picker functionality - everything you need to ship your mobile app quickly.

# Screenshots

|                                                 |                                               |                                               |                                                             |                                                 |                                                 |                                                 |
|-------------------------------------------------|-----------------------------------------------|-----------------------------------------------|-------------------------------------------------------------|-------------------------------------------------|-------------------------------------------------|-------------------------------------------------|
| ![Welcome Light](screenshots/welcome_light.png) | ![SignIn Light](screenshots/signin_light.png) | ![SignUp Light](screenshots/signup_light.png) | ![ResetPassword Light](screenshots/resetpassword_light.png) | ![Profile Light](screenshots/profile_light.png) | ![ChatGPT Light](screenshots/chatgpt_light.png) | ![Paywall Light](screenshots/paywall_light.png) |
| ![Welcome Dark](screenshots/welcome_dark.png)   | ![SignIn Dark](screenshots/signin_dark.png)   | ![SignUp Dark](screenshots/signup_dark.png)   | ![ResetPassword Dark](screenshots/resetpassword_dark.png)   | ![Profile Dark](screenshots/profile_dark.png)   | ![ChatGPT Dark](screenshots/chatgpt_dark.png)   | ![Paywall Dark](screenshots/paywall_dark.png)   |          |

## Features

- **KMP Targets Android / iOS**: Utilize Kotlin Multiplatform (KMP) to develop apps that run on both
  Android and iOS from a single codebase, ensuring consistent behavior and performance across
  platforms.
- **100% Shared UI**: Write your UI once and share it across Android and iOS, significantly reducing
  development time and effort while maintaining a consistent user experience.
- **Dark Theme**: Built-in support for dark mode, providing a seamless user experience that adapts
  to system-wide theme settings on both Android and iOS.
- **UI Components**: A set of pre-built, customizable UI components designed to help you quickly
  build and prototype your app’s interface with a cohesive look and feel.
- **Analytics**: Integrated Firebase Analytics to track user engagement and behavior, helping you
  make data-driven decisions to improve your app.
- **DB**: Utilize Firebase Firestore for real-time database needs, ensuring fast and reliable data
  storage and retrieval across your app which also handles offline scenarios right out of the box.
- **User Management**: Includes a user table and samples on how to update user data making it easy
  to pair with your favorite auth management tool.

## Installation and Getting Started

See the [Setup Guide](https://github.com/brahyam/mobilestack-kmp#setup) and [Documentation](https://brahyam.github.io/mobilestack-kmp/) for detailed instructions on configuration and usage.

## Support

- If you have a question or need help implementing a feature, please create a GitHub issue or discussion.
- For community support, join our [Discord community](https://discord.gg/mobilestack).

## Third-Party Licenses

This project includes modified versions of the following libraries:
- **Peekaboo** (camera and image-picker modules) - Apache License 2.0
  - Original project: https://github.com/onseok/peekaboo
  - Modifications:
    - Fix for image picker image orientation on iOS.
    - Fix for image compression size.
