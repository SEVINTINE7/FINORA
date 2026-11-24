# CSV import template (T12, D25)

Users can import income and expense records from a CSV file. The app shows a preview first and saves only the rows the user confirms.

## File

- UTF-8 text (a byte-order mark is allowed), comma-separated, `LF` or `CRLF` line ends.
- Maximum **1 MB** and **2000 data rows** (header not counted). Blank lines are skipped.
- The first line must be exactly this header (case and spaces around names do not matter):

```csv
date,type,amount,category,account,merchant,note
```

- Cells that contain a comma, a quote or a line break must be wrapped in double quotes; a quote inside is written twice (`"Keells ""Super"""`).

## Columns

| Column | Required | Format | Example |
|---|---|---|---|
| `date` | yes | `YYYY-MM-DD` (real calendar date) | `2026-11-05` |
| `type` | yes | `INCOME` or `EXPENSE` (any case) | `EXPENSE` |
| `amount` | yes | LKR, more than 0, at most 2 decimals and 13 whole digits, no sign or currency symbol. Thousand separators are allowed only in quoted cells (`"1,250.00"`) | `1250.00` |
| `category` | no | Name of a default or own category, same kind as `type` (English or Sinhala name, any case) | `Food` |
| `account` | yes | Name of one of your own accounts that is not archived (any case) | `Cash` |
| `merchant` | no | Up to 100 characters | `Keells` |
| `note` | no | Up to 255 characters | `Weekly groceries` |

## Example

```csv
date,type,amount,category,account,merchant,note
2026-11-01,INCOME,85000.00,Salary,Bank,,November salary
2026-11-05,EXPENSE,1250.75,Food,Cash,Keells,
2026-11-06,expense,"2,400.00",Transport,Cash,PickMe,"Airport, return"
```

## Safety rules

- **Formula injection:** a cell whose first non-space character is `=`, `+`, `-` or `@` (or that starts with a tab or carriage return) is rejected, so a later export opened in a spreadsheet cannot run it as a formula. This is why amounts cannot have a sign.
- The file is parsed in memory and never stored. Nothing is saved by the preview.

## Duplicates

Each valid row gets `dedup_hash` = SHA-256 of `CSV|date|type|amount|accountId|merchant (lowercase)|n`, where `n` is how many times that same row has appeared so far in the file (1, 2, …). Two identical rows in one file are therefore both imported, and importing the same file again marks every row as a duplicate. Duplicate rows are skipped on confirm.

## API

See `docs/api-contract.md`: `POST /api/v1/imports/csv/preview` and `POST /api/v1/imports/csv/confirm`.
