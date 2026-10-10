# Release acceptance

State: incomplete

Do not change this state until every required scenario and feature in
`EQUILY_CODEX_PROMPT.md` has verified evidence and the owner authorizes publication.
The release workflow deliberately fails while this state is incomplete.
See `PROGRESS.md` for actual checks and outstanding work.

## Verified evidence

| Brief requirements | Implementation and evidence |
| --- | --- |
| 2–7, 9–12, 15: native app, dossier, dashboard, expenses, rules, recurrence, payments, history | Compose screens, integer-cent ledger, Room audit and dated snapshots; 38 unit tests and complete offline Android journey |
| 8: autonomous OCR, attachments and review | Bundled pinned FR/NL/EN Tesseract assets; image/text/scanned/mixed PDF, EXIF and invalid-file tests; school invoice review and original in full journey |
| 13: reports with provenance | Monthly/yearly PDF/CSV; annual NL pagination and multi-period allocations tested; exports in full journey |
| 14: complete encrypted backup and clean restore | v1/v2 compatibility, tampering rejection, large archive; actual uninstall/reinstall, restore and persisted reopen |
| 4, 14, 16: localization/layout and privacy | Three layouts × FR/NL/EN × light/dark; secure-window and actual Android credential/SAF test; no INTERNET permission |
| 17–19: delivery infrastructure | CI/release workflows and required documentation present; private stable release key, fingerprint gate; owner authorized publication on 11 October 2026; remote verification pending |

Signed release: apksigner v2 and expected certificate verified, 16 KB ZIP/native alignment checked, manifest minimum 26/target 36/version 0.1.0 code 1, non-debuggable and no INTERNET permission. Fresh-install French onboarding, 50/50 dossier without mandatory private information, zero dashboard and persistence after forced restart verified using native Android UI. Final artifact/commit/checksum are recorded in `artifacts/DELIVERY.md`.

Full-suite evidence: `artifacts/verification/android-api26-20261010T192729Z.txt` (18 passed, none failed/skipped). Details and intermediate failures in `PROGRESS.md`. Synthetic emulator tests do not constitute physical-camera, audible TalkBack or biometric hardware acceptance.

## Pending gates

- Owner must review disclosed physical-device/TalkBack limitations; no successful physical validation is claimed.
- Owner explicitly authorized publication on 11 October 2026. Source push and remote CI may proceed; the tag/publication still require the remaining acceptance gates.
- Independent encrypted signing-key/password backup outside this server not confirmed; follow `RELEASE.md` and verify the restored public fingerprint.

Keep `State: incomplete` while these gates remain unresolved. Do not silently accept or publish on behalf of the owner.
