# Stability, social, clan economy, and localization implementation plan

1. Add failing PostgreSQL tests for paid clan creation, leader succession, and
   single-member deletion; implement transactional repository operations and
   authoritative engine responses.
2. Add failing tests for the tournament fee selector geometry; reserve stable
   custom-fee space and verify compact/large-font layouts.
3. Add Web input unit/E2E regressions for caret placement, IME composition,
   password selection, and numeric ordering; correct native input ownership.
4. Add navigation/history and lifecycle reconnect regressions; unify Back event
   ownership and clear transient errors only after an authenticated reconnect.
5. Add stable-key/cache regressions for mission claims and frame collection;
   narrow item state and stabilize avatar models.
6. Add header navigation tests; route gold and gems to the matching Shop tab.
7. Add incoming friend-request prompt state and UI tests, including session-only
   defer behavior and authoritative accept/decline handling.
8. Add authoritative Home rank mapping regression and correct the displayed
   ranking metric.
9. Add supported-language tests; expose Vietnamese and English only and migrate
   unsupported saved/browser language choices to English.
10. Add Web safe-area E2E assertions and apply viewport/safe-area padding.
11. Run focused tests after each item, then run protocol, server, shared host,
    Android instrumentation compilation/tests, and Web build/E2E as available.
12. Review the branch, commit, push, merge to master after green CI, and deploy
    the Railway production services.
