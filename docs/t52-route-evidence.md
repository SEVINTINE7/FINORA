# T52 authorization coverage inventory

Source baseline: upstream 60aba102bb2935db6be97d14eac6331501429cd6 plus the reconciliation tests.
Paths are relative to /api/v1. EndpointAuthTest discovers Spring MVC mappings by HTTP method and
path for the non-auth routes. It exercises absent, malformed and disabled bearer credentials and
wrong-role denial. Auth and restricted-consent protocol routes have dedicated handling. A dispatcher
denial establishes the role boundary. It does not exercise an authorized business operation.

| Route families | Existing authorized and ownership evidence | Outstanding matrix work |
|---|---|---|
| auth/register, login, refresh, logout | AuthControllerTest covers public validation, token lifecycle and rejection. | Map every protocol case to owner, disabled and invalid-session evidence. |
| auth/consent/{accept,export,delete} | MeControllerTest and RestrictedConsentAuthorizationTest cover withdrawn sessions, ordinary roles, invalid credentials, grant-owner export, consumption, expiry and replay. | Review exception paths and confirm the final scoped contract with Sevindu. |
| me, me/consent, me/export, DELETE me | MeControllerTest, DataRightsControllerTest and Md05FinancialPrivacyTest exercise consent, deletion and 18 populated export families for two owners. | Map allowed status and payload for each exact method and consent state. |
| accounts, categories, transactions, transactions/batch, CSV preview/confirm | FinancialResponsePrivacyTest, Md05FinancialPrivacyTest and feature controller suites cover privileged denial, owner isolation, foreign references and unchanged financial state. | Complete a per-method valid-body and foreign-body-reference checklist. |
| budgets, bills/payment markers, goals/contributions | The two financial privacy suites and feature controller tests cover role denial and selected guessed IDs. Concrete contribution IDs are resolved in the generic privacy suite. | Trace each nested path and parent-child mismatch to a case. |
| dashboard, insights, score, score-inputs, history | FinancialResponsePrivacyTest provides financial denial and no-mutation evidence. Existing finance controllers and category regression provide owner-scoped success cases. | Confirm every method and request variant against the final API contract. |
| advice and rating, daily-guide, simulate | Financial privacy suites plus AdviceDeliveryControllerTest, DailyGuideControllerTest and SimulationControllerTest exercise role and ownership behavior. | Add only uncovered positive or foreign cases after mapping existing tests. |
| sms-formats, admin/sms-formats, admin/categories, admin/user and stats routes | Financial privacy and feature tests cover authorized administrative shape and protected operations. | Review every allowed ADMIN response field against the financial privacy contract. |
| staff provisioning/activation, staff stats, support tickets/replies and SMS diagnostics | MD05 financial privacy and feature tests cover selected authorized privileged response shapes and foreign ticket denial. | Review every allowed STAFF response shape and all assignee/parent references. |

Provenance: 32a1f4b carries the MD05 and restricted-consent tests from a86dc89, 7f8a7b1,
74f7376 and da0236f. The FinancialResponsePrivacyTest change preserves upstream assertions and
fixes unresolved {cid} placeholders, with an assertion requiring concrete paths. T41/T46 and
independent CI acceptance remain open. This is a coverage inventory, not a completed per-route matrix.
