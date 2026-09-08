package com.yuukias.seminararc.data.reference

import com.yuukias.seminararc.domain.model.ReferenceLookupProviderId
import com.yuukias.seminararc.domain.model.ReferenceLookupQuery
import com.yuukias.seminararc.domain.model.ReferenceProviderResponse
import com.yuukias.seminararc.domain.model.ReferenceProviderResult
import com.yuukias.seminararc.domain.repository.ReferenceLookupProvider
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAlexReferenceLookupProvider @Inject constructor(
    private val client: HttpReferenceClient,
) : ReferenceLookupProvider {
    override val id: ReferenceLookupProviderId = ReferenceLookupProviderId.OPENALEX

    override suspend fun lookup(query: ReferenceLookupQuery): ReferenceProviderResponse {
        val clue = query.doi ?: listOfNotNull(query.title, query.authors.joinToString(" ").takeIf { it.isNotBlank() }, query.year?.toString())
            .joinToString(" ")
            .trim()
        if (clue.isBlank()) return ReferenceProviderResponse.Failed("OpenAlex query had no selected clue.", retryable = false)
        val url = "$BASE/works?per-page=5&select=id,doi,title,display_name,authorships,publication_year,primary_location,type,cited_by_count,open_access&search=${client.encode(clue)}"
        val response = client.get(url)
        if (response.networkError != null) return ReferenceProviderResponse.Failed(response.networkError, retryable = true)
        if (response.statusCode == 429) return ReferenceProviderResponse.RateLimited("OpenAlex rate limited this lookup.", response.retryAfter?.toLongOrNull(), 429)
        if (response.statusCode !in 200..299) return ReferenceProviderResponse.Failed("OpenAlex returned HTTP ${response.statusCode}.", response.statusCode, retryable = true)
        return runCatching {
            val results = Json.parseToJsonElement(response.body).jsonObject["results"]?.jsonArray.orEmpty()
            ReferenceProviderResponse.Success(results.mapNotNull { it.jsonObject.toOpenAlexResult() }, response.statusCode)
        }.getOrElse {
            ReferenceProviderResponse.Failed("OpenAlex response could not be parsed.", response.statusCode, retryable = true)
        }
    }

    private fun JsonObject.toOpenAlexResult(): ReferenceProviderResult? {
        val title = text("display_name") ?: text("title") ?: return null
        val location = this["primary_location"]?.jsonObject
        val source = location?.get("source")?.jsonObject
        val authors = this["authorships"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["author"]?.jsonObject?.text("display_name")
        }
        return ReferenceProviderResult(
            provider = ReferenceLookupProviderId.OPENALEX,
            providerWorkId = text("id"),
            doi = text("doi")?.removePrefix("https://doi.org/"),
            title = title,
            authors = authors,
            publicationYear = this["publication_year"]?.jsonPrimitive?.intOrNull,
            venue = source?.text("display_name"),
            sourceTitle = source?.text("display_name"),
            publicationType = text("type"),
            landingPageUrl = text("id"),
            openAccessUrl = this["open_access"]?.jsonObject?.text("oa_url"),
            licenseUrl = null,
            providerRawScore = this["cited_by_count"]?.jsonPrimitive?.doubleOrNull,
            providerPayloadJson = minimalPayload(title, text("doi"), this["publication_year"]?.jsonPrimitive?.intOrNull, source?.text("display_name"), text("id")),
        )
    }

    private companion object {
        const val BASE = "https://api.openalex.org"
    }
}
