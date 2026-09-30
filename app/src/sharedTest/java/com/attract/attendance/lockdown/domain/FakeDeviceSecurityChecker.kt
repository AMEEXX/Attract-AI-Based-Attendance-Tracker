package com.attract.attendance.lockdown.domain

class FakeDeviceSecurityChecker(
    private val availableChecks: MutableSet<SetupCheck> = mutableSetOf(),
) : DeviceSecurityChecker {

    override suspend fun check(): Set<SetupCheck> = availableChecks.toSet()

    fun add(check: SetupCheck) {
        availableChecks.add(check)
    }

    fun remove(check: SetupCheck) {
        availableChecks.remove(check)
    }

    fun reset() {
        availableChecks.clear()
    }
}
