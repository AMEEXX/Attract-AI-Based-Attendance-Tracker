# Sample Data & Roster Templates

This directory contains reference datasets and CSV templates for importing classroom rosters into **Attract**.

---

## 1. Classroom Roster Template (`student_roster.csv`)

Teachers can import class rosters directly via the in-app import feature or via ADB automation scripts.

### Supported CSV Format
```csv
Name,ID
Aaditya Chitale,B123001
Aayansh Yadav,B123002
Abhigyan Dutta,B123003
Abhijeet Raj,B123004
```

### Schema Rules
- **Name** (`String`, Required): Student's full display name (e.g. `Amit Kumar`).
- **ID / Roll Number** (`String`, Required, Unique within Section): Student's institute roll number or unique ID (e.g. `B123013`).
- **Encoding**: UTF-8 without BOM.
- **Header**: First row must contain `Name,ID` or `name,id`.

---

## 2. Usage Instructions

### Method A: Manual Import via App UI
1. Navigate to the desired Class in **Attract**.
2. Tap **Class Options** -> **Import Roster**.
3. Select your CSV file from local device storage or Google Drive.
4. Review student preview and tap **Confirm Import**.

### Method B: Automated ADB Import
Run the PowerShell utility from the project root:
```powershell
.\scripts\phone_import_roster.ps1 -RosterPath "sample-data\student_roster.csv" -ClassId 1
```
