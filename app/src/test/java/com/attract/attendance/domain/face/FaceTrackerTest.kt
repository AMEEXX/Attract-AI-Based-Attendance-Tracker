package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FaceTrackerTest {

    @Test
    fun faceTracker_driftingBox_maintainsTrackId() {
        val tracker = FaceTracker()
        var time = 1_000_000_000L
        val box1 = FaceBox(100f, 100f, 300f, 300f)
        val track1 = tracker.update(listOf(box1), time)
        assertNotNull(track1)
        val initialId = track1!!.id

        // Drift 5 pixels
        time += 33_000_000L // 33ms later
        val box2 = FaceBox(105f, 105f, 305f, 305f)
        val track2 = tracker.update(listOf(box2), time)
        assertNotNull(track2)
        assertEquals(initialId, track2!!.id)
        assertEquals(2, track2.hits)
    }

    @Test
    fun faceTracker_jumpToOtherSide_allocatesNewTrackId() {
        val tracker = FaceTracker()
        var time = 1_000_000_000L
        val box1 = FaceBox(100f, 100f, 300f, 300f)
        val track1 = tracker.update(listOf(box1), time)

        // Jump to completely different coordinates (IoU = 0)
        time += 33_000_000L
        val boxFar = FaceBox(800f, 800f, 1000f, 1000f)
        val track2 = tracker.update(listOf(boxFar), time)

        assertNotNull(track2)
        assertNotEquals(track1!!.id, track2!!.id)
    }

    @Test
    fun faceTracker_largeTimeGap_allocatesNewTrackId() {
        val tracker = FaceTracker()
        var time = 1_000_000_000L
        val box1 = FaceBox(100f, 100f, 300f, 300f)
        val track1 = tracker.update(listOf(box1), time)

        // 500 ms gap (> 400ms threshold)
        time += 500_000_000L
        val track2 = tracker.update(listOf(box1), time)

        assertNotNull(track2)
        assertNotEquals(track1!!.id, track2!!.id)
    }
}
