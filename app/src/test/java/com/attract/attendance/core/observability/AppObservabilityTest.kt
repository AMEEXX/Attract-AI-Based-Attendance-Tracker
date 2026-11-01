package com.attract.attendance.core.observability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppObservabilityTest {

    @Test
    fun initialization_doesNotThrowWhenFirebaseNotInitialized() {
        // In JVM test environment, FirebaseApp is not initialized
        // AppObservability must gracefully catch Throwable and not crash
        AppObservability.initialize("1.0.0-test")
    }

    @Test
    fun logScreenTransition_doesNotThrowInUnitTestEnvironment() {
        AppObservability.logScreenTransition(
            screenName = "ClassWorkspace",
            metadata = mapOf("class_id" to 10L, "origin_tab" to 0)
        )
    }

    @Test
    fun logSessionState_doesNotThrowInUnitTestEnvironment() {
        AppObservability.logSessionState(
            stateName = "READY",
            metadata = mapOf("class_id" to 5L, "session_id" to 42L, "attempt_id" to 1L)
        )
    }

    @Test
    fun recordNonFatal_doesNotThrowInUnitTestEnvironment() {
        AppObservability.recordNonFatal(
            throwable = IllegalStateException("Test exception"),
            context = "UnitTestContext"
        )
    }

    @Test
    fun piiScrubbing_dropsPiiKeysAndRetainsSafeKeys() {
        val method = AppObservability::class.java.getDeclaredMethod("scrubMetadata", Map::class.java)
        method.isAccessible = true

        val unsafeMetadata = mapOf(
            "student_name" to "Alice Smith",
            "roll_number" to "CS101",
            "teacher_name" to "Dr. Bob",
            "pin_hash" to "abcd1234efgh",
            "face_embedding" to floatArrayOf(0.1f, 0.2f),
            "user_email" to "alice@example.com",
            "class_id" to 42L,
            "session_id" to 101L,
            "attempt_id" to 3L,
            "item_count" to 25
        )

        @Suppress("UNCHECKED_CAST")
        val scrubbed = method.invoke(AppObservability, unsafeMetadata) as Map<String, Any>

        // Verify PII keys are dropped
        assertFalse("student_name should be scrubbed", scrubbed.containsKey("student_name"))
        assertFalse("roll_number should be scrubbed", scrubbed.containsKey("roll_number"))
        assertFalse("teacher_name should be scrubbed", scrubbed.containsKey("teacher_name"))
        assertFalse("pin_hash should be scrubbed", scrubbed.containsKey("pin_hash"))
        assertFalse("face_embedding should be scrubbed", scrubbed.containsKey("face_embedding"))
        assertFalse("user_email should be scrubbed", scrubbed.containsKey("user_email"))

        // Verify non-PII keys are preserved
        assertTrue("class_id should be preserved", scrubbed.containsKey("class_id"))
        assertTrue("session_id should be preserved", scrubbed.containsKey("session_id"))
        assertTrue("attempt_id should be preserved", scrubbed.containsKey("attempt_id"))
        assertTrue("item_count should be preserved", scrubbed.containsKey("item_count"))
    }
}
