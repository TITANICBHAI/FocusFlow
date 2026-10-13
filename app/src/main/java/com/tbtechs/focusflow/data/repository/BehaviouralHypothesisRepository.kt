package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.BehaviouralHypothesisDao
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import kotlinx.coroutines.CancellationException

class BehaviouralHypothesisRepository(private val dao: BehaviouralHypothesisDao) {
    suspend fun needsColdStart(): Boolean =
        runCatching { dao.getAll().isEmpty() }.getOrDefault(true)

    suspend fun save(hypothesis: BehaviouralHypothesisEntity): Boolean =
        try {
            dao.insert(hypothesis)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    suspend fun getAll(): List<BehaviouralHypothesisEntity> =
        runCatching { dao.getAll() }.getOrDefault(emptyList())

    suspend fun getReflexAppPackage(): String? =
        runCatching { dao.getReflexAppPackage() }.getOrNull()
}