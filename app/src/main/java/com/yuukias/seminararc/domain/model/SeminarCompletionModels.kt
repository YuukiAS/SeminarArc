package com.yuukias.seminararc.domain.model

sealed interface CompleteSeminarResult {
    data class Completed(val seminarId: Long) : CompleteSeminarResult
    data class AlreadyCompleted(val seminarId: Long) : CompleteSeminarResult
    data class NotActive(val seminarId: Long, val status: SeminarStatus) : CompleteSeminarResult
    data class NotFound(val seminarId: Long) : CompleteSeminarResult
    data class LostUpdate(val seminarId: Long) : CompleteSeminarResult
}
