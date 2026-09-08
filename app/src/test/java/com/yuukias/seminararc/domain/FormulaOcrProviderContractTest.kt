package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.formula.FormulaLanguageHint
import com.yuukias.seminararc.domain.formula.FormulaOcrProvider
import com.yuukias.seminararc.domain.formula.FormulaOcrRequest
import com.yuukias.seminararc.domain.formula.FormulaOcrResult
import com.yuukias.seminararc.domain.formula.FormulaRecognition
import com.yuukias.seminararc.domain.formula.FormulaRegionCrop
import com.yuukias.seminararc.media.formula.ManualFormulaProvider
import com.yuukias.seminararc.media.formula.UnavailableFormulaOcrProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FormulaOcrProviderContractTest {
    @Test
    fun fakeProviderReturnsLatexWithProvenance() = runTest {
        val provider = FakeFormulaOcrProvider(
            FormulaOcrResult.Recognized(
                FormulaRecognition(
                    latex = "\\int_0^1 x^2 dx",
                    confidence = 0.93f,
                    provenanceJson = """{"fixture":"formula-contract"}""",
                ),
            ),
        )
        val request = request()

        val result = provider.recognize(request) as FormulaOcrResult.Recognized

        assertEquals("fake-formula-ocr", provider.providerId)
        assertEquals(request, provider.lastRequest)
        assertEquals("\\int_0^1 x^2 dx", result.recognition.latex)
        assertEquals(0.93f, result.recognition.confidence)
        assertEquals("""{"fixture":"formula-contract"}""", result.recognition.provenanceJson)
    }

    @Test
    fun manualProviderReturnsTrimmedLatexWithoutNetworkCredential() = runTest {
        val provider = ManualFormulaProvider()

        val result = provider.recognize(
            request(
                manualLatex = "  E = mc^2  ",
                requestFingerprint = "manual-region-v1",
            ),
        ) as FormulaOcrResult.Recognized

        assertEquals("manual-formula", provider.providerId)
        assertEquals("E = mc^2", result.recognition.latex)
        assertEquals(1f, result.recognition.confidence)
        assertEquals(
            """{"provider":"manual-formula","requestFingerprint":"manual-region-v1"}""",
            result.recognition.provenanceJson,
        )
    }

    @Test
    fun manualProviderFailsWithoutManualLatex() = runTest {
        val provider = ManualFormulaProvider()

        val result = provider.recognize(request(manualLatex = " ")) as FormulaOcrResult.Failed

        assertEquals("Manual LaTeX input is required.", result.message)
        assertEquals(false, result.isRetryable)
    }

    @Test
    fun unavailableProviderFailsWithoutRetry() = runTest {
        val result = UnavailableFormulaOcrProvider().recognize(request()) as FormulaOcrResult.Failed

        assertEquals("Formula OCR provider is not configured.", result.message)
        assertEquals(false, result.isRetryable)
    }

    @Test
    fun requestRejectsInvalidIdentityAndFingerprint() {
        assertThrows(IllegalArgumentException::class.java) {
            request(seminarId = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(regionId = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(sourceAssetId = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            request(requestFingerprint = " ")
        }
    }

    @Test
    fun cropAndRecognitionRejectInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            FormulaRegionCrop(
                normalizedX = 0.8f,
                normalizedY = 0.1f,
                normalizedWidth = 0.3f,
                normalizedHeight = 0.2f,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FormulaRecognition(latex = " ", confidence = 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FormulaRecognition(latex = "x", confidence = 1.2f)
        }
    }

    private fun request(
        seminarId: Long = 1L,
        regionId: Long = 2L,
        sourceAssetId: Long = 3L,
        requestFingerprint: String = "formula-region-v1",
        manualLatex: String? = null,
    ): FormulaOcrRequest {
        return FormulaOcrRequest(
            seminarId = seminarId,
            regionId = regionId,
            sourceAssetId = sourceAssetId,
            sourcePhoto = File("seminars/1/photos/formula.jpg"),
            crop = FormulaRegionCrop(
                normalizedX = 0.1f,
                normalizedY = 0.2f,
                normalizedWidth = 0.3f,
                normalizedHeight = 0.4f,
            ),
            rotationDegrees = 0,
            languageHint = FormulaLanguageHint.MIXED,
            contextHint = "Euler-Lagrange",
            requestFingerprint = requestFingerprint,
            manualLatex = manualLatex,
        )
    }
}

private class FakeFormulaOcrProvider(
    private val result: FormulaOcrResult,
) : FormulaOcrProvider {
    override val providerId: String = "fake-formula-ocr"
    override val providerVersion: String = "1"
    var lastRequest: FormulaOcrRequest? = null
        private set

    override suspend fun recognize(request: FormulaOcrRequest): FormulaOcrResult {
        lastRequest = request
        return result
    }
}
