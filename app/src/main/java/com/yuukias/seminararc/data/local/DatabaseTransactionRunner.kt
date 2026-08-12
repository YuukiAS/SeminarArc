package com.yuukias.seminararc.data.local

interface DatabaseTransactionRunner {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

