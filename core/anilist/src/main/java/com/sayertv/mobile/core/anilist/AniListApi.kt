/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.anilist

import com.sayertv.mobile.core.common.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListException(message: String, val status: Int = 0, val retryable: Boolean = true) :
    Exception(message)

data class AniListEntry(
    val mediaId: Int,
    val episodes: Int?,
    val listStatus: String?,   // CURRENT/COMPLETED/... null = not on list
    val listProgress: Int?,
)

data class AniListCandidate(
    val id: Int,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val synonyms: List<String>,
    val year: Int?,
    val episodes: Int?,
    val format: String?,
    val sequelIds: List<Int>,
)

/**
 * Minimal GraphQL client for AniList (design doc §6): OkHttp + kotlinx.json,
 * every call routed through the token-bucket rate limiter and clamped by
 * X-RateLimit headers. All calls run on IO.
 */
@Singleton
class AniListApi @Inject constructor(
    private val authStore: AniListAuthStore,
    private val limiter: AniListRateLimiter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchViewer(token: String): Pair<Int, String>? {
        val data = call("query { Viewer { id name } }", buildJsonObject {}, tokenOverride = token)
        val viewer = data["Viewer"]?.jsonObject ?: return null
        return viewer["id"]!!.jsonPrimitive.int to viewer["name"]!!.jsonPrimitive.contentOrNull.orEmpty()
    }

    suspend fun search(title: String): List<AniListCandidate> {
        val query = """
            query (${'$'}search: String) {
              Page(perPage: 10) {
                media(search: ${'$'}search, type: ANIME) {
                  id
                  title { romaji english }
                  synonyms
                  seasonYear
                  episodes
                  format
                  relations { edges { relationType node { id type } } }
                }
              }
            }
        """.trimIndent()
        val data = call(query, buildJsonObject { put("search", title) })
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().map { m ->
            val obj = m.jsonObject
            AniListCandidate(
                id = obj["id"]!!.jsonPrimitive.int,
                titleRomaji = obj["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.contentOrNull,
                titleEnglish = obj["title"]?.jsonObject?.get("english")?.jsonPrimitive?.contentOrNull,
                synonyms = obj["synonyms"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonPrimitive.contentOrNull },
                year = obj["seasonYear"]?.jsonPrimitive?.intOrNull,
                episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                format = obj["format"]?.jsonPrimitive?.contentOrNull,
                sequelIds = obj["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                    .mapNotNull { edge ->
                        val e = edge.jsonObject
                        if (e["relationType"]?.jsonPrimitive?.contentOrNull == "SEQUEL") {
                            e["node"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                        } else null
                    },
            )
        }
    }

    /** Media core info + the viewer's list entry, one call. */
    suspend fun entry(mediaId: Int): AniListEntry {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id) {
                id
                episodes
                mediaListEntry { status progress }
              }
            }
        """.trimIndent()
        val data = call(query, buildJsonObject { put("id", mediaId) })
        val media = data["Media"]?.jsonObject
            ?: throw AniListException("Media $mediaId not found", retryable = false)
        val entry = media["mediaListEntry"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
        return AniListEntry(
            mediaId = mediaId,
            episodes = media["episodes"]?.jsonPrimitive?.intOrNull,
            listStatus = entry?.get("status")?.jsonPrimitive?.contentOrNull,
            listProgress = entry?.get("progress")?.jsonPrimitive?.intOrNull,
        )
    }

    /** Sequel chain step (for multi-cour/season episode remapping). */
    suspend fun sequels(mediaId: Int): Pair<Int?, List<Int>> {
        val query = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id) {
                episodes
                relations { edges { relationType node { id type } } }
              }
            }
        """.trimIndent()
        val data = call(query, buildJsonObject { put("id", mediaId) })
        val media = data["Media"]?.jsonObject ?: return null to emptyList()
        val episodes = media["episodes"]?.jsonPrimitive?.intOrNull
        val sequelIds = media["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
            .mapNotNull { edge ->
                val e = edge.jsonObject
                if (e["relationType"]?.jsonPrimitive?.contentOrNull == "SEQUEL") {
                    e["node"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                } else null
            }
        return episodes to sequelIds
    }

    suspend fun saveProgress(mediaId: Int, progress: Int, status: String?): Int {
        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                progress
              }
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("mediaId", mediaId)
            put("progress", progress)
            if (status != null) put("status", status)
        }
        val data = call(query, variables)
        return data["SaveMediaListEntry"]?.jsonObject?.get("progress")?.jsonPrimitive?.intOrNull
            ?: progress
    }

    private suspend fun call(
        query: String,
        variables: JsonObject,
        tokenOverride: String? = null,
    ): JsonObject = withContext(io) {
        limiter.withPermit {
            val token = tokenOverride ?: authStore.accessToken
            val body = buildJsonObject {
                put("query", query)
                put("variables", variables)
            }.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                limiter.onResponse(
                    remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull(),
                    retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
                )
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 401 -> {
                        authStore.unlink()
                        throw AniListException("AniList token expired — re-link required", 401, retryable = false)
                    }
                    response.code == 429 -> throw AniListException("Rate limited", 429, retryable = true)
                    !response.isSuccessful ->
                        throw AniListException("AniList HTTP ${response.code}", response.code, retryable = response.code >= 500)
                }
                val parsed = json.parseToJsonElement(text).jsonObject
                parsed["errors"]?.jsonArray?.firstOrNull()?.let { error ->
                    val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "GraphQL error"
                    throw AniListException(message, retryable = false)
                }
                parsed["data"]?.jsonObject
                    ?: throw AniListException("Empty GraphQL response", retryable = true)
            }
        }
    }
}
