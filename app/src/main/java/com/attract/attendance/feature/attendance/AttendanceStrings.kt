package com.attract.attendance.feature.attendance

/**
 * Centralized guidance copy for the attendance and enrollment screens.
 * Clean, professional text without emojis; state color and iconography convey status.
 */
object AttendanceStrings {
    // Attendance mode
    const val ATTENDANCE_READY_TITLE = "Look straight at the camera"
    const val ATTENDANCE_READY_SUBTITLE = "Tap the round button when ready"

    // Standalone / inline enrollment mode
    const val ENROLL_STEP1_TITLE = "Look straight at camera"
    const val ENROLL_STEP1_SUBTITLE = "Step 1 of 3 · Keep face centered in good lighting"

    const val ENROLL_STEP2_TITLE = "Now turn left slowly"
    const val ENROLL_STEP2_SUBTITLE = "Step 2 of 3 · Turn head until profile shows"

    const val ENROLL_STEP3_TITLE = "Now turn right slowly"
    const val ENROLL_STEP3_SUBTITLE = "Step 3 of 3 · Turn head until profile shows"

    const val ENROLL_ALL_CAPTURED_TITLE = "All poses captured"
    const val ENROLL_ALL_CAPTURED_SUBTITLE = "Tap SUBMIT to complete enrollment"

    const val ENROLL_SAVING_TITLE = "Saving face profile…"
    const val ENROLL_SAVING_SUBTITLE = "Encrypting and storing biometrics"

    // Verification steps
    const val PROCESSING_TITLE = "Checking your face…"
    const val PROCESSING_SUBTITLE = "Analyzing biometric features"

    const val NEED_MORE_FRAMES_TITLE = "Almost there — hold still"
    const val NEED_MORE_FRAMES_SUBTITLE = "One more clear look to confirm it's you"

    // Quality feedbacks
    const val QUALITY_DARK_TITLE = "Too dark"
    const val QUALITY_DARK_SUBTITLE = "Move toward the light and try again"

    const val QUALITY_OVEREXPOSED_TITLE = "Too bright or glare"
    const val QUALITY_OVEREXPOSED_SUBTITLE = "Adjust lighting and avoid direct glare"

    const val QUALITY_BLUR_TITLE = "Hold the phone still for a moment"
    const val QUALITY_BLUR_SUBTITLE = "Hold steady and try again"

    const val QUALITY_TOO_SMALL_TITLE = "Move closer"
    const val QUALITY_TOO_SMALL_SUBTITLE = "Step closer to the camera"

    const val QUALITY_OFF_CENTER_TITLE = "Center your face"
    const val QUALITY_OFF_CENTER_SUBTITLE = "Align your face inside the frame"

    const val QUALITY_POSE_TITLE = "Keep face level"
    const val QUALITY_POSE_SUBTITLE = "Avoid tilting head up or down"

    const val NO_FACE_TITLE = "No face visible"
    const val NO_FACE_SUBTITLE = "Position your face inside the frame"

    const val MULTIPLE_FACES_TITLE = "One person at a time"
    const val MULTIPLE_FACES_SUBTITLE = "Step back so only your face shows"

    const val UNKNOWN_FACE_TITLE = "Face not recognized"
    const val UNKNOWN_FACE_SUBTITLE = "Select your name below to set up face check-in"

    const val MATCH_TITLE = "Marked present"
    const val ALREADY_PRESENT_TITLE = "Already checked in"

    const val SESSION_INIT_TITLE = "Starting attendance session…"
    const val SESSION_INIT_SUBTITLE = "Initializing secure session, please wait"
    const val SESSION_STARTING = SESSION_INIT_TITLE

    const val QUALITY_NO_FACE_TITLE = NO_FACE_TITLE
    const val QUALITY_NO_FACE_SUBTITLE = NO_FACE_SUBTITLE

    // Doc 22 WP-C: ACTIVE_CHECK_REQUIRED guidance (liveness not yet proven — NOT a spoof verdict)
    const val LIVENESS_TURN_HEAD_TITLE = "Turn your head slightly"
    const val LIVENESS_TURN_HEAD_SUBTITLE = "Slowly look a little to the left, then to the right"
    const val LIVENESS_CHALLENGE_TITLE = "One quick check"
    const val LIVENESS_TEACHER_ASSIST_TITLE = "Teacher assistance"
    const val LIVENESS_TEACHER_ASSIST_SUBTITLE = "Having trouble verifying? Tap below for Teacher Assistance"
    const val LIVENESS_SPOOF_TITLE = "Liveness check failed"
    const val LIVENESS_SCREEN_TITLE = "Screen replay detected"
    const val LIVENESS_PRINT_TITLE = "Printed photo detected"
    const val LIVENESS_STATIC_TITLE = "Liveness check failed — static image"
}
