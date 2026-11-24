package org.manhwahub.mangadex

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Small, read-only MangaDex API client.
 *
 * This is intentionally separate from Aniyomi's extension API. It can be
 * tested independently and then adapted to the exact source-api version
 * used by the user's Aniyomi fork.
 */
class MangaDexApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val api = "https://api.mangadex.org"

    data class Manga(
        val id: String,
        val title: String,
        val description: String,
        val coverFileName: String?
    )

    data class Chapter(
        val id: String,
        val chapter: String,
        val title: String,
        val pages: Int,
        val language: String
    )

    data class ChapterPages(
        val baseUrl: String,
        val hash: String,
        val pageFiles: List<String>
    ) {
        /** MangaDex at-home page URL; use the original-quality `data` files. */
        fun imageUrls(): List<String> =
            pageFiles.map { "$baseUrl/data/$hash/$it" }
    }

    private fun get(path: String): JsonObject {
        val request = Request.Builder()
            .url("$api$path")
            .header("User-Agent", "ManhwaHub/0.1 (personal Aniyomi source project)")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("MangaDex API returned HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("MangaDex API returned an empty response")
            return JsonParser.parseString(body).asJsonObject
        }
    }

    fun search(query: String, limit: Int = 20): List<Manga> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val q = enc(query)
        val json = get(
            "/manga?title=$q&limit=${limit.coerceIn(1, 100)}" +
                "&availableTranslatedLanguage[]=en&includes[]=cover_art&order[relevance]=desc"
        )
        return json.getAsJsonArray("data").map { item ->
            val obj = item.asJsonObject
            val attrs = obj.getAsJsonObject("attributes")
            val title = pickEnglish(attrs.getAsJsonObject("title"))
            val desc = attrs.getAsJsonObject("description")
                ?.let { pickEnglish(it) }.orEmpty()
            val cover = obj.getAsJsonArray("relationships")
                ?.firstOrNull {
                    it.asJsonObject.get("type")?.asString == "cover_art"
                }?.asJsonObject?.getAsJsonObject("attributes")
                ?.get("fileName")?.asString
            Manga(obj.get("id").asString, title, desc, cover)
        }
    }

    fun chapters(mangaId: String, limit: Int = 100): List<Chapter> {
        val json = get(
            "/manga/${enc(mangaId)}/feed?limit=${limit.coerceIn(1, 500)}" +
                "&translatedLanguage[]=en&order[chapter]=asc&order[volume]=asc"
        )
        return json.getAsJsonArray("data").map { item ->
            val obj = item.asJsonObject
            val attrs = obj.getAsJsonObject("attributes")
            Chapter(
                id = obj.get("id").asString,
                chapter = attrs.get("chapter")?.takeUnless { it.isJsonNull }?.asString ?: "",
                title = attrs.get("title")?.takeUnless { it.isJsonNull }?.asString ?: "",
                pages = attrs.get("pages")?.asInt ?: 0,
                language = attrs.get("translatedLanguage")?.asString ?: "en"
            )
        }
    }

    fun pages(chapterId: String): ChapterPages {
        val json = get("/at-home/server/${enc(chapterId)}")
        val chapter = json.getAsJsonObject("chapter")
        val files = chapter.getAsJsonArray("data").map { it.asString }
        return ChapterPages(
            baseUrl = json.get("baseUrl").asString,
            hash = chapter.get("hash").asString,
            pageFiles = files
        )
    }

    private fun pickEnglish(obj: JsonObject): String {
        return obj.get("en")?.asString
            ?: obj.entrySet().firstOrNull()?.value?.asString
            ?: ""
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun usage(): Nothing {
    error(
        """
        Usage:
          search "title"
          chapters <manga-uuid>
          pages <chapter-uuid>
        """.trimIndent()
    )
}

fun main(args: Array<String>) {
    if (args.isEmpty()) usage()
    val api = MangaDexApi()
    when (args[0]) {
        "search" -> {
            val query = args.drop(1).joinToString(" ").trim().trim('"')
            if (query.isBlank()) usage()
            api.search(query).forEach {
                println("${it.id}\t${it.title}\t${it.coverFileName.orEmpty()}")
            }
        }
        "chapters" -> {
            val id = args.getOrNull(1) ?: usage()
            api.chapters(id).forEach {
                println("${it.id}\t${it.chapter}\t${it.title}\t${it.pages}")
            }
        }
        "pages" -> {
            val id = args.getOrNull(1) ?: usage()
            api.pages(id).imageUrls().forEach(::println)
        }
        else -> usage()
    }
}
