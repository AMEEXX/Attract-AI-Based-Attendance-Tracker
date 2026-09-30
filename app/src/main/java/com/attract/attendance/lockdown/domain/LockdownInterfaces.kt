package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.flow.Flow

interface DeviceSecurityChecker {
    suspend fun check(): Set<SetupCheck>
}

interface LockTaskController {
    suspend fun start(): LockTaskResult
    suspend fun stop(): Result<Unit>
    fun observeState(): Flow<LockTaskState>
}

interface TeacherAuthenticator {
    suspend fun authenticate(reason: AuthReason): AuthResult
}

interface EmbeddingCipher {
    fun encrypt(studentId: Long, templateId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding
    fun decrypt(studentId: Long, templateId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray
    fun clear()
}
