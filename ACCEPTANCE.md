# Release acceptance

State: accepted

Accepted for the initial pre-release on 11 October 2026 after the owner's explicit publication authorization, following disclosure of the local results and untested physical-device/accessibility cases. This does not certify unperformed tests or a completed independent key backup. The mandatory offline scenario and available builds/tests have verified evidence below. The release workflow must still independently pass its verification job before publishing.

## Verified evidence

| Brief requirements | Implementation and evidence |
| --- | --- |
| 2–7, 9–12, 15: native app, dossier, dashboard, expenses, rules, recurrence, payments, history | Compose screens, integer-cent ledger, Room audit and dated snapshots; 38 unit tests and complete offline Android journey |
| 8: autonomous OCR, attachments and review | Bundled pinned FR/NL/EN Tesseract assets; image/text/scanned/mixed PDF, EXIF and invalid-file tests; school invoice review and original in full journey |
| 13: reports with provenance | Monthly/yearly PDF/CSV; annual NL pagination and multi-period allocations tested; exports in full journey |
| 14: complete encrypted backup and clean restore | v1/v2 compatibility, tampering rejection, large archive; actual uninstall/reinstall, restore and persisted reopen |
| 4, 14, 16: localization/layout and privacy | Three layouts × FR/NL/EN × light/dark; secure-window and actual Android credential/SAF test; no INTERNET permission |
| 17–19: delivery infrastructure | Owner authorized publication on 11 October 2026; sources pushed, CI passed on API 35; stable key stored as release-environment secrets restricted to tag v0.1.0 |

Signed release: apksigner v2 and expected certificate verified, 16 KB ZIP/native alignment checked, manifest minimum 26/target 36/version 0.1.0 code 1, non-debuggable and no INTERNET permission. Fresh-install French onboarding, 50/50 dossier without mandatory private information, zero dashboard and persistence after forced restart verified using native Android UI. Final artifact/commit/checksum are recorded in `artifacts/DELIVERY.md`.

Full-suite evidence: `artifacts/verification/android-api26-20261010T192729Z.txt` (18 passed, none failed/skipped). Details and intermediate failures in `PROGRESS.md`. Synthetic emulator tests do not constitute physical-camera, audible TalkBack or biometric hardware acceptance.

GitHub CI evidence: https://github.com/Jhamende/Equily/actions/runs/38090796846 on commit `690705e0399a25a1183d24f6e47ed203ad161889`. Build, unit tests and lint passed; API 35 instrumented report has 18 tests, zero failures and one deliberate skip (real credential test needs the synthetic PIN harness). Thus 17 passed on API 35, not 18; the complete credential/SAF scenario passed separately in the full API 26 suite. No skipped test is misreported as executed.

## Disclosed limitations and owner follow-up

- No successful physical-camera, audible TalkBack or hardware-biometric validation is claimed. These limitations must remain in the public pre-release notes.
- Owner explicitly authorized publication on 11 October 2026 after these limitations were reported. This is authorization for a pre-release, not evidence that the missing tests were performed.
- Independent encrypted signing-key/password backup outside this server not confirmed; follow `RELEASE.md` and verify the restored public fingerprint.

The brief requires disclosure of unavailable tests (§16) and instructions for independent encrypted key backup (§18); it does not require inventing physical hardware or declaring that an owner backup has already occurred. Earlier agent-added gates requiring separate confirmation of these disclosed facts are explicitly corrected here to match the brief and the owner's publication instruction. No technical verification or signature check is bypassed. Record actual publication only after GitHub confirms it.
