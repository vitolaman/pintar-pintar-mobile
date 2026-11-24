# Pintar Pintar mobile clone status

Scope: clone the website's mobile features, excluding the internal dashboard,
payment selection, processing and post-payment flows. This is a work in progress,
not feature parity.

## Implemented, subject to device and end-to-end testing

- Public catalog: featured home sections and testimonials, search, nested category/level/file-format filters, sorting, pagination, product details and merchant storefronts; public and merchant-specific bundle browsing, details and cart additions. Signed-in learners can review owned products, vote helpful and reply when permitted.
- Learner account: email sign-in and registration, existing password-reset request, profile viewing/editing and avatar upload/removal, resumable role and skills onboarding, securely persisted session, email notification preferences, password change, ending other sessions and explicitly confirmed account deletion.
- Learning: list owned products and progress; play uploaded videos in native Android/iOS players and YouTube embeds in app (other link providers open externally). Uploaded videos are marked complete on playback end; manual completion is also available. View chapters, meeting details and attendance, answer multiple-choice and essay quizzes, upload or resubmit PDF/DWG/ZIP assignments, view grades and certificate status, and participate in class discussions. Signed video, resource and certificate links are refreshed before use; an uploaded assignment asset can be reused when final submission is retried.
- Shopping: API-backed cart and wishlist, product links, wishlist-to-cart, removal, pagination and available-item selection.
- Checkout preparation: discover and claim public vouchers, and preview selected cart items, applied/rejected codes and totals using the backend preview endpoint. No order is created; payment remains excluded.
- Career: public job-board search, pagination and listing details; signed-in users can save jobs, upload a CV and apply, and browse application and interview status.
- Promotions and community: promotional product listings and vouchers; paginated group and thread browsing, group creation and membership, owner join decisions, text threads, likes, replies and author deletion.
- Help: public FAQs and signed-in feedback submission.
- Merchant: registration, role-gated dashboard summary, recent activity, paginated class/digital-product/customer lists, searchable sales, analytics summary, basic class creation/editing and chapter/video-link creation and removal.
- Mentor: registration with CV and skills certificate uploads, profile editing, role-gated dashboard summary, upcoming sessions, class progress, recent messages and teaching class list.
- Visual/accessibility foundations: consistent high-contrast theme and surfaces; screen-reader headings and live status messages on key catalog, account, shopping and learning screens; labeled checkbox, switch and radio rows; contextual action labels; search keyboard action and collapsible catalog filters. These changes are not a full accessibility or visual-parity audit.

## Remaining

- Account: remaining profile/settings and device-level testing. Social sign-in and password-reset confirmation/deep links are excluded from the current request. Account deletion, notification preferences, security/session management, avatar picker/upload and onboarding require real-account verification.
- Catalog: remaining website home/merchant sections and visual parity; bundle/cart and authenticated review behavior need real-account verification.
- Shopping: remaining cart edge cases and pre-payment order preparation; voucher eligibility and claims need real-account verification. Payment and post-payment remain excluded.
- Learning: verify in-app video playback (including YouTube embedding), automatic completion, attendance, discussions, certificate downloads, quizzes and assignment uploads with real accounts on Android and iOS devices. Arbitrary third-party video links open externally, matching the website fallback.
- Other learner features: non-payment order views and remaining community features, including attachment/product sharing and reply likes. Promotions, job applications, group moderation, FAQ and feedback need real-account verification.
- Merchant and mentor: full class workspace and content/assignment management, digital-product and bundle management, job posting and applicant management, vouchers/discounts, remaining analytics charts, merchant settings, mentor documents and remaining dashboard sections. Existing registration, class editing and summary views require real-account verification. The internal dashboard is excluded.
- Remaining visual polish and accessibility across all screens, large-font/responsive layouts, screen-reader and keyboard testing, navigation/back handling, localization, integration tests and device-level end-to-end tests.

## Verification

- The latest check passed **83 unit tests**, including color-contrast and accessibility-semantics checks; backend responses are mocked.
- Android debug assembly and iOS simulator and device shared-code compilation
  passed. These checks do not establish playback or device-level behavior.
- The configured development hostname `api-dev.pintarpintar.id` did not resolve
  from the development machine. Live API behavior and authenticated workflows
  have not been verified against a real account.
- No TalkBack, VoiceOver or visual device audit was completed. The connected Android
  device already had the app installed, and iOS Xcode package resolution timed out.
- Production readiness requires completing the remaining flows, platform testing,
  and end-to-end validation.
