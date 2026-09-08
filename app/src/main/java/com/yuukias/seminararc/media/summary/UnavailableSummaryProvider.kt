package com.yuukias.seminararc.media.summary

import com.yuukias.seminararc.domain.summary.SummaryProvider
import com.yuukias.seminararc.domain.summary.SummaryRequest
import com.yuukias.seminararc.domain.summary.SummaryResult
import javax.inject.Inject

class UnavailableSummaryProvider @Inject constructor() : SummaryProvider {
    override val providerId: String = "unavailable-summary"
    override val providerVersion: String = "0.4-local-boundary"

    override suspend fun draft(request: SummaryRequest): SummaryResult {
        return SummaryResult.Failed(
            message = "Live summary provider is not configured.",
            isRetryable = false,
        )
    }
}
