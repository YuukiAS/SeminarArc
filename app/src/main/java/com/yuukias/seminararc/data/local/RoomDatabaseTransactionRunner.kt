package com.yuukias.seminararc.data.local

import androidx.room.withTransaction
import javax.inject.Inject

class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction(block)
    }
}

