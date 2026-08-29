package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.SeminarExportPackage
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class SeminarZipWriter @Inject constructor() {
    suspend fun write(
        export: SeminarExportPackage,
        output: OutputStream,
        sourceResolver: suspend (String) -> InputStream?,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("${export.document.slug}/seminar.md"))
            zip.write(export.markdown.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            export.document.mediaAssets.forEach { asset ->
                val input = sourceResolver(asset.sourceRelativePath) ?: return@forEach
                input.use {
                    zip.putNextEntry(ZipEntry(asset.exportRelativePath))
                    it.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }
}
