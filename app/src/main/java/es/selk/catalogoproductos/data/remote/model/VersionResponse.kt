package es.selk.catalogoproductos.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VersionResponse(
    @Json(name = "version") val version: String,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "changes_count") val changesCount: Int
)