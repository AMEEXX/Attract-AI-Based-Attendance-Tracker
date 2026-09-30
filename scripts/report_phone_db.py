"""Read-only Room proof report for the phone acceptance DB pull."""
import sqlite3, sys

path = sys.argv[1] if len(sys.argv) > 1 else r"C:\tmp\phone_acceptance\dbF\attract.db"
con = sqlite3.connect(path)
con.row_factory = sqlite3.Row
cur = con.cursor()

def rows(q):
    try:
        return [dict(r) for r in cur.execute(q).fetchall()]
    except Exception as e:
        return [{"error": str(e)}]

print("== SESSIONS ==")
for s in rows("SELECT id,class_id,session_date,status,mode FROM attendance_sessions ORDER BY id"):
    print(s)

print("\n== STUDENTS (active) ==")
for s in rows("SELECT id,class_id,name,roll_number,enrollment_status FROM students WHERE archived=0 ORDER BY id"):
    print(s)

print("\n== TEMPLATES (dim check) ==")
for t in rows("SELECT id,student_id,model_version,embedding_dim,length(encrypted_embedding) AS blob,active FROM face_templates WHERE active=1 ORDER BY student_id,id"):
    print(t)

print("\n== ATTENDANCE RECORDS ==")
for a in rows("""SELECT ar.id,ar.session_id,ar.student_id,s.name,ar.status,ar.attendance_method,
                        ar.match_confidence,ar.recognition_metadata
                 FROM attendance_records ar JOIN students s ON s.id=ar.student_id
                 ORDER BY ar.session_id,ar.id"""):
    print(a)

print("\n== EXPECTATION CHECKS ==")
sess = rows("SELECT id,status FROM attendance_sessions")
recs = rows("SELECT session_id,student_id,attendance_method,COUNT(*) c FROM attendance_records GROUP BY session_id,student_id,attendance_method")
for r in recs:
    flag = "DUPLICATE!" if r["c"] > 1 else "ok"
    print(f"session={r['session_id']} student={r['student_id']} source={r['attendance_method']} count={r['c']} {flag}")
