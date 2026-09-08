package com.yuukias.seminararc.data.reference

import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceLookupQueryType
import com.yuukias.seminararc.domain.model.ReferenceProviderResponse
import com.yuukias.seminararc.domain.model.ReferenceProviderResult
import com.yuukias.seminararc.domain.repository.ReferenceLookupProvider
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CrossrefReferenceLookupProvider @Inject constructor(
    private val client: HttpReferenceClient,
) : ReferenceLookupProvider {
    override val id: ReferenceLookupProviderId = ReferenceLookupProviderId.CROSSREF

    override suspend fun lookup(query: ReferenceLookupQuery): ReferenceProviderResponse {
        val url = when (query.queryType) {
            ReferenceLookupQueryType.DOI -> {
                val doi = query.doi ?: return ReferenceProviderResponse.Failed("DOI query had no DOI.", retryable = false)
                "$BASE/works/${client.encode(doi)}"
            }
            ReferenceLookupQueryType.BIBLIOGRAPHIC,
            ReferenceLookupQueryType.TITLE_AUTHOR_YEAR,
            -> {
                val clue = listOfNotNull(query.title, query.authors.joinToString(" ").takeIf { it.isNotBlank() }, query.year?.toString(), query.venue)
                    .joinToString(" ")
                    .trim()
                if (clue.isBlank()) return ReferenceProviderResponse.Failed("Bibliographic query had no selected clue.", retryable = false)
                "$BASE/works?rows=5&select=DOI,title,author,published-print,published-online,issued,container-title,type,URL,license,score&query.bibliographic=${client.encode(clue)}"
            }
        }
        val response = client.get(url)
        if (response.networkError != null) return ReferenceProviderResponse.Failed(response.networkError, retryable = true)
        if (response.statusCode == 429) return ReferenceProviderResponse.RateLimited("Crossref rate limited this lookup.", response.retryAfter?.toLongOrNull(), 429)
        if (response.statusCode !in 200..299) return ReferenceProviderResponse.Failed("Crossref returned HTTP ${response.statusCode}.", response.statusCode, retryable = true)
        return runCatching {
            val root = Json.parseToJsonElement(response.body).jsonObject
            val message = root["message"]?.jsonObject ?: JsonObject(emptyMap())
            val items = when (query.queryType) {
                ReferenceLookupQueryType.DOI -> listOf(message)
                else -> message["items"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            }
            ReferenceProviderResponse.Success(items.mapNotNull { it.toCrossrefResult() }, response.statusCode)
        }.getOrElse {
            ReferenceProviderResponse.Failed("Crossref response could not be parsed.", response.statusCode, retryable = true)
        }
    }

    private fun JsonObject.toCrossrefResult(): ReferenceProviderResult? {
        val title = stringArray("title").firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val doi = text("DOI")
        val authors = array("author").mapNotNull { author ->
            val obj = author.jsonObject
            listOfNotNull(obj.text("given"), obj.text("family")).joinToString(" ").takeIf { it.isNotBlank() }
        }
        val year = datePartsYear("published-print") ?: datePartsYear("published-online") ?: datePartsYear("issued")
        val venue = stringArray("container-title").firstOrNull()
        val license = array("license").firstOrNull()?.jsonObject?.text("URL")
        return ReferenceProviderResult(
            provider = ReferenceLookupProviderId.CROSSREF,
            providerWorkId = doi ?: text("URL"),
            doi = doi,
            title = title,
            authors = authors,
            publicationYear = year,
            venue = venue,
            sourceTitle = venue,
            publicationType = text("type"),
            landingPageUrl = text("URL"),
            openAccessUrl = null,
            licenseUrl = license,
            providerRawScore = this["score"]?.jsonPrimitive?.doubleOrNull,
            providerPayloadJson = minimalPayload(title, doi, year, venue, text("URL")),
        )
    }

    private fun JsonObject.datePartsYear(key: String): Int? {
        return this[key]?.jsonObject
            ?.get("date-parts")?.jsonArray
            ?.firstOrNull()?.jsonArray
            ?.firstOrNull()?.jsonPrimitive?.intOrNull
    }

    private companion object {
        const val BASE = "https://api.crossref.org"
    }
}
