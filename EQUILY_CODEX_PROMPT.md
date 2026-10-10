# EQUILY — Complete Codex Development Brief

You are a senior Android engineer, architect, product designer, QA engineer and DevOps engineer. Build, test, sign and, where authorized, publish the first real Android release of **Equily**.

**Repository:** https://github.com/Jhamende/Equily  
**Package:** `be.equily.app` (unless an existing project identifier requires preserving compatibility)  
**Initial version:** `0.1.0` (`versionCode=1`)  
**Git tag:** `v0.1.0`  
**Distribution:** GitHub Releases, signed APK, marked pre-release.

## 1. Mission and execution rules

Deliver a genuinely usable, polished, offline-first Android app for people in Belgium managing financial obligations after separation. This is not a prototype. Inspect the development server and repository, plan briefly, then implement. Make routine technical decisions independently. Do not claim features, tests or publication succeeded unless verified. Do not bypass access controls or invent credentials. Ask for intervention only when required for permissions, signing secrets or other sensitive operations. If blocked, complete independent work and document the blocker.

Use the existing repository as source of truth; preserve relevant files, commit logical changes and push only when authorized. Do not change unrelated infrastructure. Do not commit secrets. If the project exceeds one session, leave a buildable state with an actionable progress record.

## 2. Product principles

- Equily tracks shared expenses, payments and reimbursements between former partners, with or without children.
- **Solo-first:** the application must work even if only one person uses it. No account, email or invitation is required.
- **Privacy:** salaries, new partners' incomes, private debts and personal finances are never required. Optional benefits, insurance reimbursements and other inflows may be recorded when relevant.
- **Neutrality:** an entry recorded by one party is not automatically accepted by the other or a legally established debt.
- **Belgian context:** EUR, Belgian/European dates and numbers, French/Dutch/English; customizable agreements rather than hardcoded legal rules.
- **Offline autonomy:** no shared database, backend, cloud sync, tracking, advertising or mandatory Internet access in v0.1.0.

## 3. Technical architecture

Build a native Android application using Kotlin, Jetpack Compose, customized Material 3, Navigation Compose, ViewModel, Coroutines/Flow, Room/SQLite, DataStore and appropriate dependency injection (Hilt if justified). Use Gradle Kotlin DSL and the Gradle Wrapper. Select current stable, mutually compatible versions after inspecting the environment.

Use clean presentation/domain/data boundaries, repository interfaces, stable entity IDs and documented schema migrations. Avoid unnecessary modules or premature cloud implementation. Monetary values must use integer minor units (cents) or exact decimal arithmetic, **never binary floating point**. Document rounding and ledger semantics. Support Android versions chosen and documented based on actual library compatibility.

Inspect OS, Java, Android SDK, Gradle, Git, GitHub CLI/authentication, disk, network, emulator/device availability before installing dependencies. Keep installations scoped and reproducible.

## 4. Premium visual design

Create an ultra-modern, bespoke, mobile-first fintech interface using Compose, **not legacy Android XML layouts** or an unmodified stock Android UI. Develop a consistent design system: deep indigo/navy, restrained mint/turquoise accent, light/dark themes, excellent contrast, modern typography, refined spacing, cards, icons, edge-to-edge screens, responsive layouts, tasteful micro-interactions, accessible controls and one-handed navigation. Create original Equily branding, launcher/adaptive icon and splash. Avoid visual clutter, unnecessary dialogs, or misleading color semantics. Support French, Dutch and English with externalized strings; French default.

## 5. Onboarding and dossier

Provide polished onboarding: explain purpose, create a local separation dossier, optional first names/aliases of the two parties, optional separation date, optional children/aliases, default sharing percentages, then dashboard. Never demand income or an email. Allow a user to begin tracking years after separation, with a clearly labeled optional opening balance and tracking start date.

## 6. Dashboard

Display selected month and cumulative overview: shared expenses, each party's allocated share, amounts paid by each source, transfers, reimbursements, outstanding amounts, pending and disputed entries, and recent activity. Clearly separate **declared**, **accepted**, **paid**, **outstanding** and **contested** figures. Provide quick actions: add expense, scan invoice, add payment, view history. Add useful, restrained charts and empty states. Never show fabricated financial figures in normal use.

## 7. Manual expenses

Implement creation, editing, viewing, archiving and audit history. Fields: description, amount, expense date, optional due date, category, optional child/person concerned, payer, payment source (user personal, other personal, shared account, unknown), split rule, payment status, notes, optional recurrence and attachments. The author of an entry is not necessarily its payer. An invoice does not prove payment.

Default customizable categories: children (medical, school, childcare, extracurricular, clothing, other), housing (mortgage, insurance, property tax, maintenance, repairs, utilities, occupancy compensation), financial transactions, and general expenses. **Do not model transfers or reimbursements as ordinary expenses.**

## 8. Offline invoice OCR and attachments

Support camera photo, image and PDF import with preview. Prefer extracting embedded text from text PDFs, and rendering/scanning image PDFs locally. Use on-device OCR with **bundled offline recognition assets** (e.g. compatible ML Kit bundled text recognition); no invoice text sent to an external service. Extract candidate supplier, invoice date/number, total including VAT, reference and category. Handle comma decimals, European thousands separators, VAT subtotals and multiple amounts. Show candidate values and confidence/uncertainty; **require user review** before saving. Allow corrections and graceful errors. Detect possible duplicates using file hashes and invoice metadata.

Store originals in private app storage with safe names and stable IDs; support multiple attachments per expense, viewing and removal. Use Android Photo Picker / Storage Access Framework rather than broad storage permissions. Validate untrusted files and avoid sensitive logs.

## 9. Sharing rules and agreements

Support 50/50, 60/40, 70/30, 100/0 and arbitrary valid custom splits, category defaults and expense overrides. Record effective dates and snapshots of rules used; never silently retroactively recompute historical entries. Record financial agreements and optional supporting documents. For a solo-only app, accepted/contested status is **user-recorded**, not verified bilateral approval. Future two-party approval must remain architecturally possible without being falsely represented today.

## 10. Recurring obligations

Create monthly, quarterly and yearly templates for mortgage, occupancy compensation, insurance, childcare, etc. Distinguish **expected due** from **actually paid**. Generate missed periods deterministically when app reopens, without duplicates. Support pause/end/edit with effective dates. A recurring charge must never be auto-marked paid without evidence.

## 11. Financial ledger and payments

Model separately: expense, expense allocation, payer/source, payment, transfer, refund/credit, allocation of payment to one or more expenses, agreement, evidence and status. Support user-to-other payments, payments received, contributions to a shared bank account, payments from that account, direct supplier payments, partial payments and partial refunds. Prevent double counting: depositing money into a shared account is not automatically a reimbursement for a specific expense. Where contributions to the shared account are unknown, show a qualified balance rather than inventing who owes whom.

Support optional family allowances, mutual-insurance reimbursements, insurance refunds, tax refunds and other shared credits, only affecting a balance when explicitly configured. No mandatory salaries or new-partner information. Document balance formulas and edge cases. Preserve correction history and transaction integrity.

## 12. Status and history

Statuses: draft, declared, accepted (user-recorded), contested (user-recorded), partially settled, settled, archived. Make source and verification status visible. Implement searchable/filterable chronological history by period, category, payer, child and status, with monthly/yearly summaries.

## 13. Reports

Generate local PDF and CSV reports showing period, expense details, sharing rules, payments, reimbursements, outstanding and disputed balances, and provenance of manually recorded statuses. Reports must explicitly state that they reflect entered information and are not independently verified legal determinations. No server upload.

## 14. Backup, restore and privacy

A **complete, tested offline backup** is mandatory: database, agreements, recurring templates, attachments and metadata. Use a versioned archive format, encrypted with a user-provided passphrase using a well-reviewed authenticated encryption/KDF approach. Do not store the passphrase inside the archive. Validate backup integrity and version compatibility, and restore atomically without silent data loss. Test restoration on a clean installation. Explain that uninstalling or changing phones without backup can lose data.

Private app storage; minimum permissions; optional biometric/device-credential app lock; no advertising, analytics, sensitive telemetry or secrets in logs. Do not claim blanket encryption at rest unless implemented and tested. Document GDPR/privacy considerations for a locally stored family/financial data app, without presenting legal conclusions as established advice.

## 15. Required screens

Splash/initialization; onboarding; dashboard; add/edit expense; scan invoice; OCR review; expense details; history; add payment; payment history; recurring templates; sharing rules/agreements; documents; reports; backup/restore; settings. Use premium bottom navigation for primary destinations and contextual bottom sheets where appropriate. Every visible control must function; no fake buttons or placeholder features claimed as implemented.

## 16. Testing and release acceptance scenario

Create unit tests for financial balances, precise rounding, splits, shared-account transfers, refunds, partial payments, recurrence, rule effective dates, database migrations and backup/restore. Add OCR parsing tests using synthetic examples and Compose UI tests for critical workflows. Run available builds/tests and emulator/device smoke tests when hardware exists; disclose anything not tested.

A user must be able to: install APK; onboard offline; create dossier; configure 50/50; record a €1,070 monthly mortgage without invoice; configure recurrence; import a €125 school invoice; extract amount offline and correct it; attach original; associate child; record payer; compute shares; enter partial reimbursement; see updated balance; export monthly report; create backup; restore it on clean install; and reopen with consistent persisted data. No network required for this scenario.

## 17. GitHub CI/CD

Create GitHub Actions workflows for compilation, tests and appropriate checks on pushes/PRs; produce debug artifacts where useful. On an authorized release tag, build a **signed release APK**, verify signature, compute SHA-256, and publish release notes, checksum and APK at https://github.com/Jhamende/Equily/releases. Follow least-privilege workflow permissions; do not expose signing secrets to untrusted pull requests. Provide changelog, known limitations, installation instructions and minimum Android version.

Initial artifact: `Equily-v0.1.0-release.apk`. Tag: `v0.1.0`. Title: `Equily v0.1.0 — Initial Android Release`. Mark as **pre-release**.

## 18. Signing credentials — critical

Generate a dedicated Android release keystore **once**, with stable alias and long-term valid certificate. Never commit the keystore, password or private keys, and never print secrets in logs. Secure it on the devserver with restrictive permissions; instruct the owner to make an independent encrypted backup. Set GitHub Actions signing secrets through authorized tooling if possible; otherwise give precise manual steps. Never silently regenerate a production key on future releases. Verify the signed APK and report **only public** certificate fingerprint, alias, expiry and secure storage location (not passwords). If secrets/permissions are missing, finish other work and clearly report blocked publication; never publish an unsigned artifact as signed.

## 19. Documentation

Write `README.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md`, `RELEASE.md`, `SECURITY.md`, `PRIVACY.md`, `CHANGELOG.md`. Explain environment setup, builds, tests, local-only limitations, financial calculations, OCR offline guarantees, backup/restore, signing-key recovery risks and future release procedure. Preserve signing and database compatibility for future updates; no destructive production migrations.

## 20. Delivery plan

1. Inspect repository and server.
2. Create concise plan and architecture.
3. Build Android project and design system.
4. Implement local data layer and financial ledger.
5. Implement expenses, payments, splits and recurrence.
6. Implement offline OCR and attachments.
7. Implement reports, backup and restore.
8. Add privacy/security and localization.
9. Run tests, build and resolve failures.
10. Generate/configure secure release signing.
11. Configure GitHub CI/CD.
12. Verify signed APK and checksum.
13. Commit/push authorized source changes.
14. Publish `v0.1.0` if authorized and all required gates pass.
15. Produce a verified final delivery report.

## 21. Final report format

Report repository URL, branch, commit hash, release tag/URL, APK name and SHA-256; implemented versus incomplete features; supported Android versions/languages; build/unit/UI/device/OCR/backup test results; public signing certificate fingerprint; key-backup instructions; known limitations; and next priorities. Clearly distinguish confirmed outcomes from assumptions. Never claim a published release if GitHub did not confirm it.

## 22. Future extensibility (do not implement now)

Optional encrypted multi-device sync, verified bilateral expense approvals, shared documents, mediator access, bank integrations and iOS. Keep v0.1.0 local-only and fully usable without any future subscription.

## FINAL DIRECTIVE

**Start now.** Inspect the actual repository and development environment, then implement the application end-to-end. Do not stop after scaffolding or visual mockups. Prioritize correct accounting, privacy, offline reliability, backup integrity and exceptional UX. Publish the first signed pre-release only when acceptance criteria and permissions are satisfied. If any requirement remains incomplete, be explicit and leave the repository in a clean, reproducible state.
