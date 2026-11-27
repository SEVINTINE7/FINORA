# T58.0 visual direction

**Status** Proposed for Sevindu’s approval under D61. This document defines the intended design on the existing Java and XML Views stack.

## Light and dark roles

Pairs are foreground / background. Contrast uses relative luminance of the opaque sRGB values in [finora_design.xml](../android/app/src/main/res/values/finora_design.xml). Every proposed text pair must reach 4.5:1, including small labels and disabled text. Avoid alpha on labels. A colored amount belongs on its paired container with a sign and a text label.

| Role | Light | Dark |
| --- | --- | --- |
| Background / on background | `finora_ink` #142B29 / `finora_surface` #F8FAF6 (14.21:1) | `finora_on_navy` #FFFFFF / `finora_navy` #102529 (15.93:1) |
| Surface / on surface (cards and fields) | `finora_ink` #142B29 / `finora_field` #FFFFFF (14.92:1) | `finora_on_navy` #FFFFFF / `finora_ink` #142B29 (14.92:1) |
| Surface variant / on surface variant (secondary text) | `finora_muted` #4B6159 / `finora_neutral_surface` #EAF0EB (5.76:1) | `finora_mint` #C8F5CC / `finora_ink` #142B29 (12.36:1) |
| Secondary text on base surface | `finora_muted` #4B6159 / `finora_surface` #F8FAF6 (6.34:1) | `finora_mint` #C8F5CC / `finora_navy` #102529 (13.19:1) |
| Primary / on primary (main action and FAB) | `finora_on_navy` #FFFFFF / `finora_green` #155744 (8.47:1) | `finora_navy` #102529 / `finora_mint` #C8F5CC (13.19:1) |
| Primary container / on primary container (selected navigation) | `finora_navy` #102529 / `finora_mint` #C8F5CC (13.19:1) | `finora_navy` #102529 / `finora_mint` #C8F5CC (13.19:1) |
| Secondary / on secondary (outlined or secondary-filled actions) | `finora_on_navy` #FFFFFF / `finora_navy_line` #436D60 (5.84:1) | `finora_navy` #102529 / `finora_success_surface` #E1F2E5 (13.68:1) |
| Secondary container / on secondary container | `finora_green` #155744 / `finora_success_surface` #E1F2E5 (7.28:1) | `finora_on_navy` #FFFFFF / `finora_navy_line` #436D60 (5.84:1) |
| Tertiary / on tertiary (supporting accent) | `finora_on_navy` #FFFFFF / `finora_navy` #102529 (15.93:1) | `finora_navy` #102529 / `finora_neutral_surface` #EAF0EB (13.77:1) |
| Tertiary container / on tertiary container | `finora_ink` #142B29 / `finora_neutral_surface` #EAF0EB (12.91:1) | `finora_mint` #C8F5CC / `finora_ink` #142B29 (12.36:1) |
| Error / on error (filled emphasis) | `finora_on_navy` #FFFFFF / `finora_error` #982C30 (7.67:1) | `finora_on_navy` #FFFFFF / `finora_error` #982C30 (7.67:1) |
| Error container / on error container (inline errors and expense label) | `finora_error` #982C30 / `finora_error_surface` #FCEDEA (6.74:1) | `finora_error` #982C30 / `finora_error_surface` #FCEDEA (6.74:1) |
| Income and success status | `finora_green` #155744 / `finora_success_surface` #E1F2E5 (7.28:1) | `finora_navy` #102529 / `finora_mint` #C8F5CC (13.19:1) |
| Disabled foreground / container | `finora_disabled_ink` #42574D / `finora_disabled` #DFE9E1 (6.25:1) | `finora_disabled_ink` #42574D / `finora_disabled` #DFE9E1 (6.25:1) |
| Pressed primary foreground / container | `finora_on_navy` #FFFFFF / `finora_pressed` #104536 (10.90:1) | `finora_navy` #102529 / `finora_success_surface` #E1F2E5 (13.68:1) |
| Inverse surface / inverse text | `finora_on_navy` #FFFFFF / `finora_navy` #102529 (15.93:1) | `finora_navy` #102529 / `finora_surface` #F8FAF6 (15.16:1) |

Use `finora_navy_line` for functional light borders and `finora_outline` for functional dark borders. Keep `finora_outline` as a subtle separator only in light mode. Use `finora_focus` for focus outlines on light surfaces and `finora_mint` on dark surfaces. Show checked state with shape, icon and label. Charts and progress bars need a text equivalent with value and unit. Missing scores and limits have explicit labels. The window and system bars use the corresponding surface with legible system icons. Apply system-bar and IME insets at the appropriate containers for edge-to-edge layout.

## Typography

Noto Sans for English and LKR figures; Noto Sans Sinhala for Sinhala with adequate line metrics for marks. Use sp, preserve Android text scaling and test both scripts at 130%. Keep labels in sentence case and allow wrapping. Keep financial amounts together where space allows, right-align with tabular figures if supported, and place the amount on its own line on narrow screens.

| Role | Size / line height | Weight |
| --- | --- | --- |
| Balance display | 32 / 44 sp | 600 |
| Screen heading | 24 / 34 sp | 600 |
| Section title | 20 / 29 sp | 600 |
| Card title and amount | 16 / 24 sp | 600 |
| Body and form value | 16 / 25 sp | 400 |
| Button and field labels | 14 / 22 sp | 600 |
| Caption and navigation label | 13 / 21 sp | 500 |

## Spacing and shape

Use the existing 6, 12, 16, 22 and 28 dp steps for icon gaps, row padding, card padding, section spacing and large separation. Use 24 dp outer margins and a 12 dp inset between adjacent cards. Controls are at least 48 × 48 dp with a 56 dp preferred minimum height. Radius is 14 dp for fields, buttons and the FAB, 16 dp for cards and 30 dp for sheets. Use full rounding only for chips and indicators. Use a 1 dp border and a 2 dp focus outline. Allow the layout to grow with text and scroll above navigation and the keyboard.

## Home proposal

```text
+---------------------------------------------------------+
| FINORA                           Nov 2026               |
| +----------------------------------------+              |
| | MONTHLY BALANCE                       |               |
| | LKR 42,650.00                          |              |
| | Income 65,000.00   Expenses 22,350.00  |              |
| +----------------------------------------+              |
| Financial health       ( 72 / 100 ) ring                |
| Budgets                                                 |
| Food       [=======-----] 7,000 / 12,000                |
| Travel     [===---------] 1,500 /  6,000                |
| Upcoming bills                                          |
| Internet bill                 26 Nov                    |
| Recent transactions                                     |
| Groceries                  - LKR 1,250.00               |
| Salary                    + LKR 65,000.00               |
|                         [ + Add transaction ]           |
| [H] Home  [R] Records  [P] Plan  [I] Insights  [M] More |
+---------------------------------------------------------+
```

The letters represent icons with persistent labels. Keep all five destinations in the bottom bar in implementation. The layout preview uses illustrative values. Reuse existing data and state; unavailable sections gracefully use the existing empty or unavailable state.

## Transactions list proposal

```text
+-----------------------------------------------+
| < Transactions                  [Filter]      |
| +----------------------------------------+    |
| | [bag] Groceries              [ ... ]   |    |
| | Food / 19 Nov          - LKR 1,250.00  |    |
| +----------------------------------------+    |
| +----------------------------------------+    |
| | [pay] Salary                 [ ... ]   |    |
| | Income / 18 Nov       + LKR 65,000.00  |    |
| +----------------------------------------+    |
|                         [ + Add transaction ] |
+-----------------------------------------------+
```

Keep the amount aligned to the right edge of the row and give the overflow action a per-row accessible name. Reuse the existing Edit and Delete flows and confirmations. Keep distinct loading, empty and error states and a visible retry where applicable.

## Review and implementation gate

Sevindu reviews the colour roles, type scale, shape and both screen proposals before T58.1. Preserve all existing view IDs, behavior, API calls, validations, BigDecimal money, role guards and FLAG_SECURE. Use ViewBinding when converting code-built views to XML. New UI strings require both EN and SI resources and pending native-speaker review. Later acceptance covers EN/SI, light/dark and large text on emulator with the existing accessibility and navigation checks.
