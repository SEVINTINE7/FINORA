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

## Execution 31 exact runtime inventory

Frozen baseline: `15c4ce267d988deb9924b4f858d92358bee9009c`. The rows below come from Spring MVC
mapping discovery in `EndpointAuthTest.discoveryFindsAllControllers`, deduplicated by method and path.
There were **102 product mappings** in the local test profile, including **94 protected** and **8
authentication-protocol** mappings. The application security matrix starts at `/api/v1`.
Actuator, OpenAPI, framework error routes and test probes are outside this inventory.

### Credential expectations for every row

| Row policy | Anonymous or malformed bearer | Expired signed bearer | Disabled valid bearer | Owner USER or other USER | STAFF | ADMIN |
|---|---|---|---|---|---|---|
| USER | 401 UNAUTHORIZED | 401 UNAUTHORIZED | 403 ACCOUNT_DISABLED | Allow routing for either USER. Handler enforces ownership as listed below. | 403 FORBIDDEN | 403 FORBIDDEN |
| STAFF | 401 UNAUTHORIZED | 401 UNAUTHORIZED | 403 ACCOUNT_DISABLED | 403 FORBIDDEN for either USER | Allow routing. Assignee or workflow rules still apply. | 403 FORBIDDEN |
| ADMIN | 401 UNAUTHORIZED | 401 UNAUTHORIZED | 403 ACCOUNT_DISABLED | 403 FORBIDDEN for either USER | 403 FORBIDDEN | Allow routing. Global resource or workflow scope applies. |
| Protocol | NOT_APPLICABLE as a bearer gate. Uses its own request credentials or validation. | NOT_APPLICABLE as a bearer gate. Refresh or activation expiry is protocol-specific. | Evaluate the credential subject in login or refresh. Activation expects pending STAFF. | NOT_APPLICABLE as a bearer role gate | NOT_APPLICABLE as a bearer role gate | NOT_APPLICABLE as a bearer role gate |
| Consent | A valid Consent grant is required. See dedicated contract. | An expired Consent grant is rejected. A bearer cannot substitute for the grant. | Grant validity and account scope apply. See dedicated cases. | NOT_APPLICABLE as a bearer role gate | NOT_APPLICABLE as a bearer role gate | NOT_APPLICABLE as a bearer role gate |

For all 94 protected rows, `EndpointAuthTest.noTokenGives401Everywhere`,
`badTokenGives401Everywhere`, `wrongRoleGives403Everywhere`, and
`disabledAccountsReturn403Everywhere` are direct dispatcher assertions. The new
`expiredAccessTokenIsRejectedOnEveryProtectedRoute` adds **282 signed-expired requests**
(three original roles per route), each asserting HTTP 401 and `UNAUTHORIZED`.
`RestrictedConsentAuthorizationTest.restrictedGrantAndWithdrawnAccessCannotReachOrdinaryProtectedRoutes`
adds a distinct 282-probe baseline for restricted grants and withdrawn sessions.
`normalRolesAndInvalidCredentialsCannotUseRecoveryEndpoints` and
`twoRestrictedExportsAreBoundToGrantOwnersNotRequestedIds` cover recovery-specific denial,
owner-scoped export and grant consumption. The bearer denial cases never serve as authorized
positive evidence.

### Exact route rows

The allowed column identifies the direct feature-test entry point for the corresponding method.
The boundary column distinguishes scoped cases, explicit N/A reasons, and follow-up gaps.
A referenced feature test demonstrates the described scenario, not all input variants.

| Runtime method and path | Policy | Allowed path evidence | Ownership, reference, or protocol evidence |
|---|---|---|---|
| `DELETE /api/v1/accounts/{id}` | USER | AccountControllerTest.createListGetUpdateDelete | AccountControllerTest.otherUsersAccountGives404 |
| `DELETE /api/v1/admin/categories/{id}` | ADMIN | AdminDependencyControllerTest.defaultNamesAreUniqueAndDeletingInUseCategoryOnlyDeactivatesIt | Global ADMIN target; owner USER N/A; check non-default/missing target and no mutation |
| `DELETE /api/v1/admin/sms-formats/NSB/revisions/{revision}` | ADMIN | AdminSmsFormatControllerTest.onlyAdminCanDiscardUnpublishedDraftsWithoutChangingActiveFormat | Global ADMIN target; owner USER N/A |
| `DELETE /api/v1/bills/{id}` | USER | BillControllerTest.createGetUpdateDelete | BillControllerTest.otherUsersBillGives404 |
| `DELETE /api/v1/bills/{id}/paid` | USER | BillControllerTest.markUnpaid | Md05FinancialPrivacyTest.foreignFinancialIdsAreRejectedWithoutChangingEitherUser |
| `DELETE /api/v1/budgets/{id}` | USER | BudgetControllerTest.createListUpdateDelete | BudgetControllerTest.otherUsersBudgetGives404 |
| `DELETE /api/v1/categories/{id}` | USER | CategoryControllerTest.createGetUpdateDeleteOwn | CategoryControllerTest.otherUsersCategoryGives404 |
| `DELETE /api/v1/goals/{id}` | USER | GoalControllerTest.createGetUpdateDelete | GoalControllerTest.otherUsersGoalGives404 |
| `DELETE /api/v1/goals/{id}/contributions/{contributionId}` | USER | GoalControllerTest.progressAfterAddAndDeleteOfContributions | Md05FinancialPrivacyTest.foreignFinancialIdsAreRejectedWithoutChangingEitherUser |
| `DELETE /api/v1/me` | USER | DataRightsControllerTest.exportAndDeletionStayWithinTheCallerAndLeaveNoCredentialMaterial | NOT_APPLICABLE — session owner; same test verifies other owner survives |
| `DELETE /api/v1/transactions/{id}` | USER | TransactionControllerTest.createGetUpdateDelete | TransactionControllerTest.otherUsersTransactionGives404 |
| `GET /api/v1/accounts` | USER | AccountControllerTest.createListGetUpdateDelete | Own list; `AccountControllerTest.createListGetUpdateDelete` |
| `GET /api/v1/accounts/{id}` | USER | AccountControllerTest.createListGetUpdateDelete | AccountControllerTest.otherUsersAccountGives404 |
| `GET /api/v1/admin/categories` | ADMIN | Md05FinancialPrivacyTest.populatedFinanceNeverLeaksThroughAuthorizedOperationsResponses | NOT_APPLICABLE — global ADMIN operation; check non-default/missing target and no mutation |
| `GET /api/v1/admin/sms-diagnostics/escalations` | ADMIN | SmsDiagnosticControllerTest.staffFlagsGroupOnceAndOnlyAdminCanAdvanceIt | NOT_APPLICABLE — global ADMIN operation |
| `GET /api/v1/admin/sms-formats/NSB/revisions` | ADMIN | Md05FinancialPrivacyTest.populatedFinanceNeverLeaksThroughAuthorizedOperationsResponses | NOT_APPLICABLE — global ADMIN operation |
| `GET /api/v1/admin/sms-formats/NSB/revisions/{revision}` | ADMIN | NEW AdminSmsFormatControllerTest.adminCanReadOnlyUnpublishedDraftAndDeactivatePublishedRevision | Global ADMIN target; owner USER N/A |
| `GET /api/v1/admin/staff` | ADMIN | Md05FinancialPrivacyTest.populatedFinanceNeverLeaksThroughAuthorizedOperationsResponses | NOT_APPLICABLE — global ADMIN operation |
| `GET /api/v1/admin/stats` | ADMIN | AdminStatsControllerTest.onlyAdminGetsCountsWithNoFinancialFields | NOT_APPLICABLE — global ADMIN operation |
| `GET /api/v1/admin/users` | ADMIN | AdminDependencyControllerTest.userManagementRevokesCredentialsAndAuditsWithoutExposingFinance | NOT_APPLICABLE — global ADMIN operation |
| `GET /api/v1/advice` | USER | AdviceDeliveryControllerTest.publishesCanonicalRulesAndReconcilesOneRating | Same test compares two callers |
| `GET /api/v1/bills` | USER | BillControllerTest.listIsSortedByDueDay | NOT_APPLICABLE — caller scope |
| `GET /api/v1/bills/summary` | USER | BillControllerTest.summaryAndMonthlyTotal | NOT_APPLICABLE — caller scope |
| `GET /api/v1/bills/{id}` | USER | BillControllerTest.createGetUpdateDelete | BillControllerTest.otherUsersBillGives404 |
| `GET /api/v1/budgets` | USER | BudgetControllerTest.createListUpdateDelete | NOT_APPLICABLE — caller scope |
| `GET /api/v1/budgets/{id}` | USER | FinancialJourneyTest.budgetCentBoundaryUpdatesAnalysisAndRejectsUngroundedAdvice | BudgetControllerTest.otherUsersBudgetGives404 |
| `GET /api/v1/categories` | USER | CategoryControllerTest.listShowsDefaultsPlusOwnOnly | CategoryControllerTest.listShowsDefaultsPlusOwnOnly |
| `GET /api/v1/categories/{id}` | USER | CategoryControllerTest.createGetUpdateDeleteOwn | CategoryControllerTest.otherUsersCategoryGives404 |
| `GET /api/v1/dashboard` | USER | DashboardControllerTest.totalsEqualSumOfRawTransactions | DashboardControllerTest.otherUsersDataIsNeverCounted |
| `GET /api/v1/goals` | USER | GoalControllerTest.createGetUpdateDelete | NOT_APPLICABLE — caller scope |
| `GET /api/v1/goals/{id}` | USER | GoalControllerTest.createGetUpdateDelete | GoalControllerTest.otherUsersGoalGives404 |
| `GET /api/v1/goals/{id}/contributions` | USER | GoalControllerTest.progressAfterAddAndDeleteOfContributions | Md05FinancialPrivacyTest.foreignFinancialIdsAreRejectedWithoutChangingEitherUser |
| `GET /api/v1/history` | USER | HistoryControllerTest.usesColomboMonthBoundaryAndReadsExactSnapshotFacts | NOT_APPLICABLE — caller/month scope; ownership evidence review |
| `GET /api/v1/insights` | USER | InsightControllerTest.findsPatternsAndSavesThem | InsightControllerTest.onlyOwnDataIsUsed |
| `GET /api/v1/me` | USER | MeControllerTest.getMeReturnsOwnProfile | NOT_APPLICABLE — session owner |
| `GET /api/v1/me/export` | USER | DataRightsControllerTest.exportIncludesEveryOwnedDataFamilyAndExcludesForeignRows | Same test, two owners and all populated families |
| `GET /api/v1/score` | USER | ScoreControllerTest.explicitDeclarationsGiveNormalizedScoreAndOnlyOneInputRow | ScoreControllerTest.scoreInputsAndComputedFactsAreOwnerIsolated |
| `GET /api/v1/score-inputs/{month}` | USER | ScoreControllerTest.scoreInputsAndComputedFactsAreOwnerIsolated | Month is a period, not an owner ID; same test |
| `GET /api/v1/sms-formats` | USER | SmsFormatControllerTest.userGetsOnlyApprovedActiveSnapshot | NOT_APPLICABLE — shared approved template |
| `GET /api/v1/staff/reply-catalog` | STAFF | NEW allowlist in SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Shared catalog; self-review restricted in the same test |
| `GET /api/v1/staff/sms-diagnostics` | STAFF | SmsDiagnosticControllerTest.aggregatesOnlyMetadataAndDeduplicatesWithinOneUserDay | Metadata-only; no financial resource ID |
| `GET /api/v1/staff/stats` | STAFF | StaffStatsControllerTest.staffSeesOnlyOwnUnresolvedAndUnassignedQueueCounts | Assignee scope in same test |
| `GET /api/v1/staff/tickets` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `GET /api/v1/support/tickets` | USER | SupportTicketControllerTest.optionalErrorCodeIsAllowlistedAndVisibleOnlyAfterClaim | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `GET /api/v1/support/tickets/{id}/replies` | USER | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `GET /api/v1/transactions` | USER | TransactionControllerTest.listFiltersSortsAndPages | NEW query account/category: TransactionControllerTest.foreignAccountAndCategoryQueryFiltersDoNotReturnForeignRows |
| `GET /api/v1/transactions/summary` | USER | TransactionControllerTest.summaryIsExactToTheCentAfterCreateEditDelete | TransactionControllerTest.otherUsersTransactionGives404 |
| `GET /api/v1/transactions/{id}` | USER | TransactionControllerTest.createGetUpdateDelete | TransactionControllerTest.otherUsersTransactionGives404 |
| `PATCH /api/v1/admin/sms-diagnostics/escalations/{id}` | ADMIN | SmsDiagnosticControllerTest.staffFlagsGroupOnceAndOnlyAdminCanAdvanceIt | Global ADMIN target; owner USER N/A |
| `PATCH /api/v1/admin/sms-formats/NSB/1` | ADMIN | AdminSmsFormatControllerTest.onlyAdminCanToggleApprovedFormatWithoutChangingUserContract | NOT_APPLICABLE — global ADMIN operation |
| `PATCH /api/v1/admin/staff/{id}` | ADMIN | StaffProvisioningControllerTest.activationIsSingleUseAndDisableThenEnableDoesNotResurrectSessions | Global ADMIN target; owner USER N/A |
| `POST /api/v1/accounts` | USER | AccountControllerTest.createListGetUpdateDelete | NOT_APPLICABLE — caller scope |
| `POST /api/v1/admin/categories` | ADMIN | AdminDependencyControllerTest.renamePreservesSemanticCodeAndReferencesAndCannotAssignItToOtherCategories | NOT_APPLICABLE — global ADMIN operation; check non-default/missing target and no mutation |
| `POST /api/v1/admin/sms-formats/NSB/revisions/{revision}` | ADMIN | AdminSmsFormatControllerTest.publishesAndRollsBackImmutableDeploymentRevisions | Global ADMIN target; owner USER N/A |
| `POST /api/v1/admin/sms-formats/NSB/revisions/{revision}/activate` | ADMIN | AdminSmsFormatControllerTest.publishesAndRollsBackImmutableDeploymentRevisions | Global ADMIN target; owner USER N/A |
| `POST /api/v1/admin/sms-formats/NSB/revisions/{revision}/deactivate` | ADMIN | NEW AdminSmsFormatControllerTest.adminCanReadOnlyUnpublishedDraftAndDeactivatePublishedRevision | Global ADMIN target; owner USER N/A |
| `POST /api/v1/admin/sms-formats/NSB/revisions/{revision}/publish` | ADMIN | AdminSmsFormatControllerTest.publishesAndRollsBackImmutableDeploymentRevisions | Global ADMIN target; owner USER N/A |
| `POST /api/v1/admin/sms-formats/NSB/revisions/{revision}/validate` | ADMIN | NEW AdminSmsFormatControllerTest.adminCanReadOnlyUnpublishedDraftAndDeactivatePublishedRevision | Global ADMIN target; owner USER N/A |
| `POST /api/v1/admin/staff` | ADMIN | StaffProvisioningControllerTest.activationIsSingleUseAndDisableThenEnableDoesNotResurrectSessions | NOT_APPLICABLE — global ADMIN operation |
| `POST /api/v1/admin/staff/{id}/activation` | ADMIN | NEW StaffProvisioningControllerTest.adminReissuesOnlyPendingStaffActivationAndOldCredentialCannotActivate | NEW rejects active STAFF and USER IDs; pending staff allowed |
| `POST /api/v1/advice/{id}/rating` | USER | AdviceDeliveryControllerTest.publishesCanonicalRulesAndReconcilesOneRating | Same test rejects foreign advice ID |
| `POST /api/v1/auth/consent/accept` | Consent | MeControllerTest.recoveryIsRestrictedSingleUseAndRequiresFreshLogin | expiredGrantIsRejectedAndRestrictedDeletionRetainsPasswordCheck; competingAcceptanceConsumesOneGrantAndRecordsOneConsent |
| `POST /api/v1/auth/consent/delete` | Consent | MeControllerTest.expiredGrantIsRejectedAndRestrictedDeletionRetainsPasswordCheck | RestrictedConsentAuthorizationTest.normalRolesAndInvalidCredentialsCannotUseRecoveryEndpoints |
| `POST /api/v1/auth/consent/export` | Consent | RestrictedConsentAuthorizationTest.twoRestrictedExportsAreBoundToGrantOwnersNotRequestedIds | Owner binding and grant consumption |
| `POST /api/v1/auth/login` | Protocol | AuthControllerTest.loginOk | wrongPasswordAndUnknownEmailGiveSameBody; disabledAccountGives403 |
| `POST /api/v1/auth/logout` | Protocol | AuthControllerTest.logoutRevokesRefreshToken | EndpointAuthTest.authEndpointsArePublic; unknown refresh is idempotent |
| `POST /api/v1/auth/refresh` | Protocol | AuthControllerTest.refreshGivesNewPairAndOldTokenCannotBeReused | expiredRefreshTokenCannotIssueNewTokens; disabledAccountCannotRefresh |
| `POST /api/v1/auth/register` | Protocol | AuthControllerTest.registerOk | registerDuplicateEmailGives409; registerUnder18Gives400; registerWeakPasswordGives400 |
| `POST /api/v1/auth/staff/activate` | Protocol | StaffProvisioningControllerTest.activationIsSingleUseAndDisableThenEnableDoesNotResurrectSessions | NEW replacement binding and former credential rejection |
| `POST /api/v1/bills` | USER | BillControllerTest.createGetUpdateDelete | Body category: gap to reconcile |
| `POST /api/v1/bills/{id}/paid` | USER | BillControllerTest.paidIsPerMonthAndNextMonthStartsUnpaid | Md05FinancialPrivacyTest.foreignFinancialIdsAreRejectedWithoutChangingEitherUser |
| `POST /api/v1/budgets` | USER | BudgetControllerTest.createListUpdateDelete | body: BudgetControllerTest.otherUsersCategoryIsRejected (POST evidence; PUT body review open) |
| `POST /api/v1/categories` | USER | CategoryControllerTest.createGetUpdateDeleteOwn | NOT_APPLICABLE — caller scope |
| `POST /api/v1/daily-guide` | USER | DailyGuideControllerTest.ownerBillsOnlyPaidExcludedAndNoWrites | NOT_APPLICABLE — caller context; lastIncomeFallbackIgnoresOtherOwner |
| `POST /api/v1/goals` | USER | GoalControllerTest.createGetUpdateDelete | NOT_APPLICABLE — caller scope |
| `POST /api/v1/goals/{id}/contributions` | USER | GoalControllerTest.progressAfterAddAndDeleteOfContributions | Md05FinancialPrivacyTest.foreignFinancialIdsAreRejectedWithoutChangingEitherUser |
| `POST /api/v1/imports/csv/confirm` | USER | CsvImportControllerTest.validFilePreviewsThenSaves | CsvImportControllerTest.confirmRejectsOtherUsersAccountAndSavesNothing; foreign category audit open |
| `POST /api/v1/imports/csv/preview` | USER | CsvImportControllerTest.validFilePreviewsThenSaves | Owner-scoped name lookup; explicit foreign-name fixture review open |
| `POST /api/v1/me/consent/withdraw` | USER | MeControllerTest.withdrawConsentRevokesRefreshTokens | NOT_APPLICABLE — session owner; stale session checked |
| `POST /api/v1/simulate` | USER | SimulationControllerTest.ownedGoalProducesCorrectHypotheticalResultWithoutWrites | SimulationControllerTest.anotherUsersGoalReturns404; SimulationServiceTest.inaccessibleCategoryIsNotDisclosed (service); HTTP foreign cut open |
| `POST /api/v1/staff/reply-catalog` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Shared catalog; self-review restricted in the same test |
| `POST /api/v1/staff/reply-catalog/{id}/approve` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Shared catalog; self-review restricted in the same test |
| `POST /api/v1/staff/reply-catalog/{id}/retire` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Shared catalog; self-review restricted in the same test |
| `POST /api/v1/staff/sms-diagnostics/escalations` | STAFF | SmsDiagnosticControllerTest.staffFlagsGroupOnceAndOnlyAdminCanAdvanceIt | Metadata-only; no financial resource ID |
| `POST /api/v1/staff/tickets/{id}/claim` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `POST /api/v1/staff/tickets/{id}/close` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `POST /api/v1/staff/tickets/{id}/replies` | STAFF | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `POST /api/v1/support/sms-diagnostics` | USER | SmsDiagnosticControllerTest.aggregatesOnlyMetadataAndDeduplicatesWithinOneUserDay | Metadata-only; no financial resource ID |
| `POST /api/v1/support/tickets` | USER | SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement | Owner/assignee isolation: SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement |
| `POST /api/v1/transactions` | USER | TransactionControllerTest.createGetUpdateDelete | body: TransactionControllerTest.otherUsersAccountOrCategoryIsRejected |
| `POST /api/v1/transactions/batch` | USER | SmsBatchControllerTest.savesOnlyOnceAcrossInboxPasteAndSameBatchRepeats | SmsBatchControllerTest.validatesEveryRowBeforeWritesAndScopesHashesToUser; rejectsForeignCategoryEvenAfterValidRowAndNeverReturnsOtherUsersManualIds |
| `PUT /api/v1/accounts/{id}` | USER | AccountControllerTest.createListGetUpdateDelete | AccountControllerTest.otherUsersAccountGives404 |
| `PUT /api/v1/admin/categories/{id}` | ADMIN | AdminDependencyControllerTest.renamePreservesSemanticCodeAndReferencesAndCannotAssignItToOtherCategories | Global ADMIN target; owner USER N/A; check non-default/missing target and no mutation |
| `PUT /api/v1/admin/sms-formats/NSB/revisions/{revision}` | ADMIN | AdminSmsFormatControllerTest.draftEditsInvalidateEvidenceAndApprovalRemainsSeparateFromFixtures | Global ADMIN target; owner USER N/A |
| `PUT /api/v1/admin/users/{id}/status` | ADMIN | AdminDependencyControllerTest.userManagementRevokesCredentialsAndAuditsWithoutExposingFinance | Global ADMIN target; owner USER N/A |
| `PUT /api/v1/bills/{id}` | USER | BillControllerTest.createGetUpdateDelete | Body category: gap to reconcile; path: BillControllerTest.otherUsersBillGives404 |
| `PUT /api/v1/budgets/{id}` | USER | BudgetControllerTest.createListUpdateDelete | BudgetControllerTest.otherUsersBudgetGives404; body: BudgetControllerTest.otherUsersCategoryIsRejected (POST evidence; PUT body review open) |
| `PUT /api/v1/categories/{id}` | USER | CategoryControllerTest.createGetUpdateDeleteOwn | CategoryControllerTest.otherUsersCategoryGives404 |
| `PUT /api/v1/goals/{id}` | USER | GoalControllerTest.createGetUpdateDelete | GoalControllerTest.otherUsersGoalGives404 |
| `PUT /api/v1/me` | USER | MeControllerTest.putMeChangesNameAndLanguage | NOT_APPLICABLE — session owner |
| `PUT /api/v1/score-inputs/{month}` | USER | ScoreControllerTest.scoreInputsAndComputedFactsAreOwnerIsolated | Month is a period, not an owner ID; same test |
| `PUT /api/v1/transactions/{id}` | USER | TransactionControllerTest.createGetUpdateDelete | TransactionControllerTest.otherUsersTransactionGives404; body: TransactionControllerTest.otherUsersAccountOrCategoryIsRejected |

### Privileged response verification

- `FinancialResponsePrivacyTest.rejectedFinancialReadsExposeOnlyStandardErrorFields` and
  `rejectedFinancialWritesCannotMutateAnyFinancialRows` cover denied financial routes.
- `Md05FinancialPrivacyTest.populatedFinanceNeverLeaksThroughAuthorizedOperationsResponses`
  checks the ADMIN users, stats, staff list, category list and format revisions, plus STAFF stats,
  ticket queue and diagnostic groups. It uses populated finance and compares before/after exports.
- `SmsDiagnosticControllerTest.staffFlagsGroupOnceAndOnlyAdminCanAdvanceIt` now checks
  the ADMIN escalation object field allowlist. The existing STAFF group case uses strict JSON.
- `SupportTicketControllerTest.ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement`
  now checks the STAFF catalog allowlist. The workflow also exercises owner and assignee denials.
- `StaffProvisioningControllerTest.adminReissuesOnlyPendingStaffActivationAndOldCredentialCannotActivate`
  checks ADMIN provision and reissue allowlists. The temporary password is part of the approved
  one-time activation contract. The reissue response requires `Cache-Control: no-store`.
- `AdminSmsFormatControllerTest.adminCanReadOnlyUnpublishedDraftAndDeactivatePublishedRevision`
  checks strict ADMIN draft and validation shapes, 404 on published draft detail, and a successful
  deactivate that the USER sees as an empty template list.
- Empty 204 responses have no financial fields to assess. The relevant feature tests verify
  successful statuses and applicable state transitions. Do not infer valid positive behavior from
  dispatcher tests.

### Remaining acceptance and review

Complete a second-pass audit of each allowed body and query variant, including bill category writes,
budget update category handling, multipart CSV category/account resolution and simulation HTTP
foreign cuts. Confirm no-mutation assertions for every rejected write variant where the original
test only asserted a status. Reconcile missing, wrong-type and workflow-invalid IDs for shared
ADMIN/STAFF resources separately from USER ownership. Confirm whether the required policy for
STAFF catalog retirement is any STAFF or a narrower workflow rule. Current source and existing
tests establish a STAFF role gate, and this audit introduces no new owner restriction.
Obtain independent review of the final matrix and CI results after T41/T46 contract closure.
T52 remains in progress.

### Execution 31 verification

Java 17 and MySQL 8.0.46 on the verified loopback finora_test instance. Providers were disabled.
Targeted runs reported 6/6 and 1/1 methods, each with zero failures, errors or skips.
Integrated Maven verify reported Tests run: 445, Failures: 0, Errors: 0, Skipped: 0.
JaCoCo passed. These are local results on the Execution 31 tree. Independent CI remains outstanding.
