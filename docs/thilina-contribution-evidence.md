# Thilina contribution evidence — FINORA

Audit date: 2026-11-22. Purpose: final report/viva evidence, not a commit-count competition.
AI tools assisted implementation and QA; Thilina remains responsible for understanding, explaining and validating
the work. Team approvals, Sevindu's API/database foundation and Sahan's Android work are separate contributions.

## Attribution findings

- Local author configuration names ThilinaW-LK. All 25 observed Thilina-authored commits across existing local refs
  at the start of this audit used one author email, matching the configured email. The address is intentionally omitted.
- GitHub REST commit objects link representative commits 4ea18dd (T50), 72ec247 (pipeline), 5900d3b and ac78ed1
  (T54) to **ThilinaW-LK** as author. No attribution mismatch was observed. These checks do not read private email settings.
- Before new T55 commits, 20 of those authored commits were reachable from upstream origin/main, including 16
  non-merge commits; 5 were still confined to pending T52/T54 branches. These are repository-history counts, not
  a measured profile contribution-calendar count. Unfetched/deleted refs are outside this local inventory.
- Upstream merge 89b6e7c has two parents and is authored by the lead/web-flow; original pipeline commit 72ec247
  remains in upstream history with Thilina's attribution. Other observed source commits also remain visible.
  A merge commit credited to the lead does not transfer authorship of the preserved implementation commits.
- PRs #10/#11 were open, with passing API and Android CI and no submitted reviews when checked. Work still only
  on fork feature branches is not yet default-branch commit evidence. No history/email/authorship changes were made.

GitHub documents that commit email must be associated with the account, commits must reach the default/gh-pages
branch, fork-only commits do not count, and qualifying updates may take up to 24 hours:
[Troubleshooting missing contributions](https://docs.github.com/en/account-and-profile/how-tos/contribution-settings/troubleshooting-missing-contributions).
These rules explain plausible timing/branch effects; the exact displayed graph count was not measured here.
The observed author mapping already supports correct account linkage. Do not rewrite merged history or manufacture
commits to increase counts. If eligible older commits remain missing after refresh, inspect the account privately or
ask GitHub Support with commit links; changing teammates' attribution is not a remedy.

## Report / viva evidence map

| Specific contribution | Commit / review evidence | What to demonstrate and limitations |
|---|---|---|
| T50 analytics boundary/isolation support | 4ea18dd, 36db18a; T50 tracker | Explain the 9 added tests and analytics cases. Sevindu leads the overall backend test platform. |
| T21 analytics corrections | ea5f8ae, merged PR #5 | Cross-month payday windows and default Savings classification; explain regression cases. |
| T23 approved health model | b0e8284, merged PR #6 | BigDecimal D20 calculation, missing/N/A handling and owner declarations; decisions are team-owned, not solely authored policy. |
| T25/T26 rules and AI boundary | 79021a0, merged PR #7 | Six fact-backed actions, numeric provenance, private-field whitelist and adversarial fixtures. |
| T27–T29 preparatory pipeline | 72ec247, ed33c41, merged PR #8 | Mocked providers, backup/rule fallback, exact provisional verifier and bounded outcomes. Historical 253-test evidence; no live-provider pass rate. |
| T53 authentication/logging QA | 8d38725, 1ed683f, merged PR #9 | UTC/JPA test-fixture diagnosis, disabled-refresh/wrong-signature tests, safe diagnostics. Deployment/privacy/scans remain pending. |
| T52 financial response privacy | b6be682, 142c8bc, PR #10 | Four tests over existing STAFF/ADMIN denials, private error shape and unchanged rows; real ADMIN/STAFF modules remain dependencies. |
| T54 correctness journeys | 5900d3b, ac78ed1, PR #11 | Six hand-calculated months plus two real HTTP/service update journeys; 8 focused / 265 full passing on that branch, not the current T55 test count. |
| T55 reproducible dataset | Current T55 review branch and dataset tracker | Explain 826 invented transactions, hand totals, safeguards, rollback and limitations. Not real bank/SMS or performance evidence. |

For the viva, show a requirement → implementation → hand-calculated oracle → genuine test output → blocker chain.
Use task/PR scope and demonstrated reasoning as evidence; never equate commit totals or lines changed with academic quality.
