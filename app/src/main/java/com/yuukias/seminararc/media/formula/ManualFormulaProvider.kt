package com.yuukias.seminararc.media.formula

import com.yuukias.seminararc.domain.formula.FormulaOcrProvider
import com.yuukias.seminararc.domain.formula.FormulaOcrProviderAvailability
import com.yuukias.seminararc.domain.formula.FormulaOcrProviderCapabilities
import com.yuukias.seminararc.domain.formula.FormulaOcrProviderStatus
import com.yuukias.seminararc.domain.formula.FormulaOcrRequest
import com.yuukias.seminararc.domain.formula.FormulaOcrResult
import com.yuukias.seminararc.domain.formula.FormulaRecognition
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ManualFormulaProvider @Inject constructor() : FormulaOcrProvider {
    override val providerId: String = "manual-formula"
    override val providerVersion: String = "1"
    override val status: FormulaOcrProviderStatus = FormulaOcrProviderStatus(
        providerId = providerId,
        providerVersion = providerVersion,
        displayName = "Manual LaTeX",
        availability = FormulaOcrProviderAvailability.AVAILABLE,
        message = "User-entered LaTeX can be saved locally.",
        capabilities = FormulaOcrProviderCapabilities(
            supportsImageRecognition = false,
            supportsManualLatex = true,
            requiresCredential = false,
            usesNetwork = false,
        ),
    )

    override suspend fun recognize(request: FormulaOcrRequest): FormulaOcrResult {
        val latex = request.manualLatex?.trim().orEmpty()
        if (latex.isBlank()) {
            return FormulaOcrResult.Failed("Manual LaTeX input is required.", isRetryable = false)
        }
        return FormulaOcrResult.Recognized(
            FormulaRecognition(
                latex = latex,
                confidence = 1f,
                provenanceJson = JsonObject(
                    mapOf(
                        "provider" to JsonPrimitive(providerId),
                        "requestFingerprint" to JsonPrimitive(request.requestFingerprint),
                    ),
                ).toString(),
            ),
        )
    }
}
