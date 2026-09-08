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

class DataCiteReferenceLookupProvider @Inject constructor(
    private val client: HttpReferenceClient,
) : ReferenceLookupProvider {
    override val id: ReferenceLookupProviderId = ReferenceLookupProviderId.DATACITE

    override suspend fun lookup(query: ReferenceLookupQuery): ReferenceProviderResponse {
        val url = if (query.doi != null) {
            "$BASE/dois/${client.encode(query.doi)}"
        } else {
            val clue = listOfNotNull(query.title, query.authors.joinToString(" ").takeIf { it.isNotBlank() }, query.year?.toString()).joinToString(" ")
            if (clue.isBlank()) return ReferenceProviderResponse.Failed("DataCite query had no selected clue.", retryable = false)
            "$BASE/dois?page[size]=5&query=${client.encode(clue)}"
        }
        val response = client.get(url)
        if (response.networkError != null) return ReferenceProviderResponse.Failed(response.networkError, retryable = true)
        if (response.statusCode == 429) return ReferenceProviderResponse.RateLimited("DataCite rate limited this lookup.", response.retryAfter?.toLongOrNull(), 429)
        if (response.statusCode !in 200..299) return ReferenceProviderResponse.Failed("DataCite returned HTTP ${response.statusCode}.", response.statusCode, retryable = true)
        return runCatching {
            val data = Json.parseToJsonElement(response.body).jsonObject["data"]
            val rows = if (data is kotlinx.serialization.json.JsonArray) data.map { it.jsonObject } else listOfNotNull(data?.jsonObject)
            ReferenceProviderResponse.Success(rows.mapNotNull { it.toDataCiteResult() }, response.statusCode)
        }.getOrElse {
            ReferenceProviderResponse.Failed("DataCite response could not be parsed.", response.statusCode, retryable = true)
        }
    }

    private fun JsonObject.toDataCiteResult(): ReferenceProviderResult? {
        val attrs = this["attributes"]?.jsonObject ?: return null
        val title = attrs["titles"]?.jsonArray?.firstOrNull()?.jsonObject?.text("title") ?: return null
        val creators = attrs["creators"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.text("name") }
        val year = attrs["publicationYear"]?.jsonPrimitive?.intOrNull
        return ReferenceProviderResult(
            provider = ReferenceLookupProviderId.DATACITE,
            providerWorkId = text("id"),
            doi = attrs.text("doi"),
            title = title,
            authors = creators,
            publicationYear = year,
            venue = attrs["publisher"]?.jsonPrimitive?.content,
            sourceTitle = attrs["publisher"]?.jsonPrimitive?.content,
            publicationType = attrs["types"]?.jsonObject?.text("resourceTypeGeneral"),
            landingPageUrl = attrs.text("url"),
            openAccessUrl = null,
            licenseUrl = attrs["rightsList"]?.jsonArray?.firstOrNull()?.jsonObject?.text("rightsUri"),
            providerRawScore = null,
            providerPayloadJson = minimalPayload(title, attrs.text("doi"), year, attrs["publisher"]?.jsonPrimitive?.content, attrs.text("url")),
        )
    }

    private companion object {
        const val BASE = "https://api.datacite.org"
    }
}
