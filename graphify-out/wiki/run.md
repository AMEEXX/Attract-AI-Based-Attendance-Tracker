# .run

> 13 nodes

## Key Concepts

- **.run()** (19 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **RollNumberRepairTest** (16 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`infers a missing roll only when both neighbours pin it down`()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`last row unreadable gets a hint but is not auto-filled`()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`ambiguous extra character is left alone`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`corrects a single wrong character when the sequence agrees`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`duplicated leading letter is dropped`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`duplicates are flagged not dropped`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`fills an illegible character inside the fixed prefix`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`fixes 8 read instead of B`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`fixes letter O read in a digit position`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`restores a lost leading letter`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`
- **.`uses a partially read roll as a wildcard check`()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`

## Relationships

- [RollNumberRepair](RollNumberRepair.md) (5 shared connections)
- [assertTrue](assertTrue.md) (2 shared connections)
- [RawRosterRow](RawRosterRow.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [AdaptiveVerificationEngine](AdaptiveVerificationEngine.md) (1 shared connections)
- [.evaluate](evaluate.md) (1 shared connections)
- [AttractViewModel](AttractViewModel.md) (1 shared connections)

## Source Files

- `app/src/test/java/com/attract/attendance/data/importexport/RollNumberRepairTest.kt`

## Audit Trail

- EXTRACTED: 29 (81%)
- INFERRED: 7 (19%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*