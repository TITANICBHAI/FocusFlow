package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.ClarifyingQuestionDao
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.temporal.ChronoUnit

class ClarifyingQuestionRepository(private val dao: ClarifyingQuestionDao) {
    private companion object {
        private const val TAG = "ClarifyingQuestionRepo"
        private const val RATE_LIMIT_DAYS = 14L
    }

    suspend fun getPending(): ClarifyingQuestionEntity? =
        runCatching { dao.getUnanswered() }.getOrNull()

    suspend fun submitIfAllowed(question: ClarifyingQuestionEntity): Boolean =
        runCatching {
            val since = Instant.now().minus(RATE_LIMIT_DAYS, ChronoUnit.DAYS).toString()
            if (dao.countAskedSince(since) > 0) return@runCatching false
            dao.insert(question)
            true
        }.onFailure { Log.e(TAG, "submitIfAllowed failed", it) }.getOrDefault(false)

    suspend fun answer(id: String, response: String): Boolean =
        try {
            dao.answer(id, response, Instant.now().toString())
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "answer($id) failed", error)
            false
        }

    suspend fun pruneOldAnswered() {
        runCatching {
            dao.deleteAnsweredBefore(
                Instant.now().minus(90, ChronoUnit.DAYS).toString(),
            )
        }.onFailure { Log.e(TAG, "pruneOldAnswered failed", it) }
    }
}