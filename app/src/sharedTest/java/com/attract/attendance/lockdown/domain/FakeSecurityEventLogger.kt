package com.attract.attendance.lockdown.domain

class FakeSecurityEventLogger : SecurityEventLogger {

    val events = mutableListOf<SecurityEvent>()
    var tamperCount = 0
        private set

    override fun log(event: SecurityEvent) {
        events.add(event)
        if (event.category == SecurityEventCategory.TAMPER_DETECTED) {
            tamperCount++
        }
    }

    fun getEventsForCategory(category: SecurityEventCategory): List<SecurityEvent> =
        events.filter { it.category == category }

    fun reset() {
        events.clear()
        tamperCount = 0
    }
}
