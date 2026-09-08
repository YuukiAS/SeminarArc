package com.yuukias.seminararc.domain.export

import com.yuukias.seminararc.domain.model.ExportReferenceItem
import com.yuukias.seminararc.domain.model.SeminarExportDocument
import java.util.Locale
import javax.inject.Inject

class SeminarBibliographyRenderer @Inject constructor() {
    fun renderBibTeX(document: SeminarExportDocument): String {
        val references = document.brief?.references.orEmpty()
        if (references.isEmpty()) return ""
        val usedKeys = mutableMapOf<String, Int>()
        return references.joinToString(separator = "\n\n", postfix = "\n") { reference ->
            reference.toBibTeXEntry(usedKeys)
        }
    }

    fun renderRis(document: SeminarExportDocument): String {
        val references = document.brief?.references.orEmpty()
        if (references.isEmpty()) return ""
        return references.joinToString(separator = "\n") { reference ->
            reference.toRisEntry()
        }
    }

    private fun ExportReferenceItem.toBibTeXEntry(usedKeys: MutableMap<String, Int>): String {
        val type = bibTeXType()
        val key = uniqueCitationKey(usedKeys)
        val fields = buildList {
            add("title" to title)
            authorsForExport().takeIf { it.isNotEmpty() }?.let { add("author" to it.joinToString(" and ")) }
            publicationYear?.let { add("year" to it.toString()) }
            val venueValue = sourceTitle?.takeIf { it.isNotBlank() } ?: venue
            venueValue?.takeIf { it.isNotBlank() }?.let {
                add((if (type == "article") "journal" else "howpublished") to it)
            }
            doi?.takeIf { it.isNotBlank() }?.let { add("doi" to it) }
            landingPageUrl?.takeIf { it.isNotBlank() }?.let { add("url" to it) }
            note?.takeIf { it.isNotBlank() }?.let { add("note" to it) }
        }
        return buildString {
            appendLine("@$type{$key,")
            fields.forEachIndexed { index, (name, value) ->
                val suffix = if (index == fields.lastIndex) "" else ","
                appendLine("  $name = {${value.bibTeXValue()}}$suffix")
            }
            append("}")
        }
    }

    private fun ExportReferenceItem.toRisEntry(): String = buildString {
        appendLine("TY  - ${risType()}")
        appendRisLine("TI", title)
        authorsForExport().forEach { appendRisLine("AU", it) }
        publicationYear?.let { appendRisLine("PY", it.toString()) }
        val journalOrSource = sourceTitle?.takeIf { it.isNotBlank() } ?: venue
        appendRisLine("JO", journalOrSource)
        appendRisLine("DO", doi)
        appendRisLine("UR", landingPageUrl)
        appendRisLine("N1", note)
        appendLine("ER  -")
    }

    private fun ExportReferenceItem.authorsForExport(): List<String> {
        if (authors.isNotEmpty()) return authors.filter { it.isNotBlank() }
        return authorsText
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun ExportReferenceItem.bibTeXType(): String {
        val type = publicationType.orEmpty().lowercase(Locale.US)
        return if ("journal" in type || "article" in type) "article" else "misc"
    }

    private fun ExportReferenceItem.risType(): String {
        val type = publicationType.orEmpty().lowercase(Locale.US)
        return when {
            "journal" in type || "article" in type -> "JOUR"
            "conference" in type || "proceeding" in type -> "CONF"
            else -> "GEN"
        }
    }

    private fun ExportReferenceItem.uniqueCitationKey(usedKeys: MutableMap<String, Int>): String {
        val authorPart = authorsForExport().firstOrNull()?.lastToken()?.citationToken().orEmpty()
        val yearPart = publicationYear?.toString().orEmpty()
        val titlePart = title
            .split(Regex("\\s+"))
            .firstOrNull { it.any(Char::isLetterOrDigit) }
            ?.citationToken()
            .orEmpty()
        val base = listOf(authorPart, yearPart, titlePart)
            .joinToString("")
            .ifBlank { "reference" }
        val next = (usedKeys[base] ?: 0) + 1
        usedKeys[base] = next
        return if (next == 1) base else "$base$next"
    }

    private fun String.lastToken(): String = trim()
        .split(Regex("\\s+"))
        .lastOrNull()
        .orEmpty()

    private fun String.citationToken(): String = lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "")

    private fun String.bibTeXValue(): String = replace("\r", " ")
        .replace("\n", " ")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .trim()

    private fun String.risValue(): String = replace("\r", " ")
        .replace("\n", " ")
        .trim()

    private fun StringBuilder.appendRisLine(tag: String, value: String?) {
        value?.risValue()?.takeIf { it.isNotBlank() }?.let { appendLine("$tag  - $it") }
    }
}
