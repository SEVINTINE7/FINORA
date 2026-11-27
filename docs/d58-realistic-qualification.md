# D58 realistic synthetic goal-coaching evidence

These November 2026 fixtures use illustrative Sri Lankan LKR amounts for
verification and demonstrations. They are independent of the frozen benchmark.
They are neither population estimates nor participant observations. The recorded
goal contributions predate the fixture month and are separate from November
income and spending. The freelancer also has September and October income
entries of LKR 52,000 and 81,000. The November observation alone drives the
monthly salary plan. The plan does not forecast freelance income.

| Profile and goal | Income | Needs | Wants | Target | Previously saved | Requested months |
|---|---:|---:|---:|---:|---:|---:|
| Entry-level worker, emergency fund | 75,000 | 40,000 | 20,000 | 150,000 | 45,000 | 6 |
| Young adult, laptop for study | 52,000 | 39,000 | 12,000 | 120,000 | 30,000 | 6 |
| Freelancer, education fund | 68,000 | 42,000 | 16,000 | 120,000 | 30,000 | 9 |
| Student, emergency reserve | 36,000 | 31,000 | 8,000 | 75,000 | 15,000 | 12 |
| Young professional, rental deposit | 90,000 | 52,000 | 16,000 | 90,000 | 90,000 | 6 |

## Independent expectations and observed V24 results

Each case uses rent, food and transport as needs and one Shopping bucket for
wants. With no recorded prior Shopping amount, the quarter cut applies in every
case and is below the half cap. Limit wants to the lesser of the post-cut
amount and 40% of income. Divide remaining balance by the planned months,
rounding required monthly savings up to cents. The transfer is bounded by
nonnegative headroom and the required amount. Duration rounds up to whole
months at the planned positive transfer. These are conditional calculations.

| Profile | Cut | Wants limit | Remaining | Required/month | Transfer/month | Buffer | Months at plan |
|---|---:|---:|---:|---:|---:|---:|---:|
| Worker | 5,000 | 15,000 | 105,000 | 17,500 | 17,500 | 2,500 | 6 |
| Laptop | 3,000 | 9,000 | 90,000 | 15,000 | 4,000 | 0 | 23 |
| Freelancer | 4,000 | 12,000 | 90,000 | 10,000 | 10,000 | 4,000 | 9 |
| Student | 2,000 | 6,000 | 60,000 | 5,000 | 0 | 0 | unavailable |
| Completed | 4,000 | 12,000 | 0 | unavailable | unavailable | unavailable | unavailable |

Five disposable users registered and signed in normally. Each case passed its
applicable delivered-fact assertions on a fresh V24 loopback instance with
external providers and Firebase disabled. The zero-transfer case showed shortfall
guidance and no completion estimate. The completed goal was DONE and emitted
no GOAL_PLAN. General salary-plan language for that user was outside the
completed-goal assertions. Repeat advice delivery reused the stored ID, all
five ledgers remained identical, and a foreign focus-goal request returned 404.

The compact API 37 emulator verified the worker and laptop cases through
normal sign-in and account switching. The one connected test passed with ten
logged checkpoints, including the goal, remaining balance, planned transfer and
duration for both cases. Other profiles have API evidence only. The test uses
the same guarded D58 journey with the explicit realistic-fixture flag. For
reproduction, register disposable worker and irregular profiles under the
existing harness fixture aliases, seed the corresponding JSON rows through
normal API operations, and run the guarded test with
`finoraD58Realistic=true`. Transfer credentials through the existing
session-only input mechanism.

Production application APK SHA-256
`016f4aad30b1357dfdcdbfe715094b0ea5385d0137e718739a74767e1b442459`.
Test APK SHA-256
`20d1e62c3dd364d6b29f8d3d7433833f62bfa06179da02dd57bbed54c6f2d085`.
The compact evidence is emulator UI hierarchy evidence. It does not establish
physical-device acceptance, human usability, or live AI accuracy.
