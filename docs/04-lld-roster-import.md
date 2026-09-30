# LLD-04 — CSV/XLSX Roster Import

**Status:** Draft — ready for review and freeze  
**Requirements source:** SDD §§3, 10–11, 72  
**Depends on:** LLD-02, LLD-03

## Goal

Import a teacher-selected CSV or XLSX roster into one class safely. Import is a preview-and-confirm operation: no student row is persisted until the teacher sees validation results and confirms.

## Components

```text
feature/importroster/    File picker, mapping, preview, import result ViewModels/screens
domain/import/           DetectColumns, ValidateRows, BuildImportPlan, CommitImport
data/importexport/       ContentReader, CsvParser, XlsxParser, ImportRepository
```

The Android file picker returns a content URI. `ContentReader` streams it through `ContentResolver`; no path assumptions are made and no original roster is copied to permanent app storage.

## Accepted data and mapping

Required logical columns: **name** and **roll number/student ID**. Optional: serial number.

Recognized normalized headers:

```text
roll number: roll, roll number, student id, id
name: name, student name
serial: sl no, s.no, serial, serial number
```

Header detection lowercases, trims, removes punctuation, and compares aliases. If exactly one valid mapping is found, preselect it. If columns are ambiguous or missing, show a mapping screen; the teacher chooses one source column for each required logical field. A source column cannot map to two logical fields.

CSV uses a standards-compliant parser supporting quoted commas/newlines. XLSX parsing reads cell values only; it does not execute formulas, macros, external links, or embedded objects. Treat formulas as their displayed/cached plain value only if the library provides one safely; otherwise flag the cell for correction.

## Import algorithm

1. Verify MIME/type extension and safely open a bounded input stream.
2. Parse header and rows. Enforce documented implementation limits (initially 5 MB, 2,000 rows, 50 columns); return a clear limit error before memory pressure.
3. Build a row model with original row number and raw values.
4. Normalize name/roll/serial using the LLD-03 rules.
5. Validate required values, maximum lengths, duplicate roll numbers inside the file, and duplicates already in the target class.
6. Produce an immutable `ImportPlan`: valid new rows, duplicate rows, invalid rows, and selected mapping.
7. Preview totals and row-level reasons. Teacher chooses Cancel, Go Back to Mapping, or Confirm.
8. On Confirm, re-check target class and all duplicate rolls inside one Room transaction; insert all rows or none.
9. Display imported count and downloadable/copyable error summary only for rows that were not imported.

The MVP policy is **all-or-nothing**. A plan with any invalid/duplicate row cannot be confirmed until the teacher removes/fixes those rows in the source or explicitly imports a reviewed subset in a later, separately designed enhancement. This avoids accidental partial rosters.

## Errors and privacy

Do not log roster cell contents. User messages identify row numbers and field reasons, for example “Row 14: Roll number is missing.” File read errors offer Retry/Choose another file. A changed or revoked URI permission returns a recoverable “File is no longer available” state.

## Build steps

1. Write parser adapters behind `RosterParser` and use fixture files in tests.
2. Implement header alias normalization and pure row validation.
3. Implement immutable import plan and preview ViewModel.
4. Implement atomic repository commit and concurrency re-validation.
5. Add document picker integration and accessibility review.

## Tests

| Level | Scenario | Expected result |
|---|---|---|
| Unit | aliases such as `S.No`, `Student ID` | Correct mapping |
| Unit | ambiguous/missing headers | Mapping required |
| Unit | CSV quoted comma/newline | One correct cell, no shifted columns |
| Unit | duplicate case/spacing roll values | Duplicate detected |
| Unit | blank required field / overlong value | Row error with source row number |
| Parser | malformed CSV / corrupt XLSX / password-protected XLSX | Safe parse error, no crash |
| Security | XLSX formula, external link, macro payload | Never executed |
| Repository | valid plan commit | All students inserted atomically |
| Repository | one row races with existing roster insert | Entire import rolls back |
| UI | cancellation/back during preview | No students persisted |
| Instrumented | revoked content URI | Recoverable file error |

## Definition of done

The feature safely parses supported files, shows an accurate preview, never creates a partial roster, and handles untrusted file data without code/formula execution.

