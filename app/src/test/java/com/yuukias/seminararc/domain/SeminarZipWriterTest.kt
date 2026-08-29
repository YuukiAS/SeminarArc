package com.yuukias.seminararc.domain

import com.yuukias.seminararc.domain.export.SeminarZipWriter
import com.yuukias.seminararc.domain.model.ExportMediaAsset
import com.yuukias.seminararc.domain.model.ExportMediaKind
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import com.yuukias.seminararc.domain.model.SeminarExportPackage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeminarZipWriterTest {
    @Test
    fun write_createsExpectedStructureAndSkipsMissingMedia() = runTest {
        val export = SeminarExportPackage(
            document = SeminarExportDocument(
                slug = "seminar-42",
                title = "Seminar",
                speaker = null,
                affiliation = null,
                scheduledAt = null,
                location = null,
                abstractText = null,
                recordingSummary = "No recording.",
                timelineItems = emptyList(),
                mediaAssets = listOf(
                    ExportMediaAsset("photos/a.jpg", "seminar-42/media/photos/a.jpg", ExportMediaKind.PHOTO),
                    ExportMediaAsset("clips/missing.m4a", "seminar-42/media/clips/missing.m4a", ExportMediaKind.CLIP),
                ),
                skippedMedia = listOf("clips/missing.m4a"),
            ),
            markdown = "# Seminar\n",
        )
        val output = ByteArrayOutputStream()

        SeminarZipWriter().write(export, output) { path ->
            if (path == "photos/a.jpg") ByteArrayInputStream("photo".toByteArray()) else null
        }

        assertEquals(
            mapOf(
                "seminar-42/seminar.md" to "# Seminar\n",
                "seminar-42/media/photos/a.jpg" to "photo",
            ),
            output.toZipEntries(),
        )
    }
}

private fun ByteArrayOutputStream.toZipEntries(): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(toByteArray())).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            zip.closeEntry()
        }
    }
    return entries
}
