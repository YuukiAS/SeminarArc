package com.yuukias.seminararc.data.reference

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> = this[key]?.jsonArray.orEmpty()

internal fun JsonObject.stringArray(key: String): List<String> = array(key).mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }

internal fun minimalPayload(
    title: String?,
    doi: String?,
    year: Int?,
    venue: String?,
    url: String?,
): String = buildJsonObject {
    title?.let { put("title", it) }
    doi?.let { put("doi", it) }
    year?.let { put("year", it) }
    venue?.let { put("venue", it) }
    url?.let { put("url", it) }
}.toString()
