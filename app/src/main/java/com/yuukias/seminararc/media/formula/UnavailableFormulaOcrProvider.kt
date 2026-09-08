package com.yuukias.seminararc.media.formula

import com.yuukias.seminararc.domain.formula.FormulaOcrProvider
import com.yuukias.seminararc.domain.formula.FormulaOcrRequest
import com.yuukias.seminararc.domain.formula.FormulaOcrResult
import javax.inject.Inject

class UnavailableFormulaOcrProvider @Inject constructor() : FormulaOcrProvider {
    override val providerId: String = "formula-ocr-unavailable"
    override val providerVersion: String = "1"

    override suspend fun recognize(request: FormulaOcrRequest): FormulaOcrResult {
        return FormulaOcrResult.Failed(
            message = "Formula OCR provider is not configured.",
            isRetryable = false,
        )
    }
}
