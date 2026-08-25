package com.sayanthrock.githubrock.backend.service

import com.sayanthrock.githubrock.backend.config.AppConfig
import com.sayanthrock.githubrock.backend.store.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StoreCatalogService(
    private val client: HttpClient,
    private val config: AppConfig,
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CachedCatalog>()
    private val cacheTtlMillis = 23 * 60 * 60 * 1000L

    suspend fun catalog(category: String, platform: String): StoreCatalog {
        require(category in STORE_CATEGORIES) { "Unsupported store category" }
        require(platform in STORE_PLATFORMS) { "Unsupported store platform" }
        val key = "$category/$platform"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.loadedAt < cacheTtlMillis }?.let { return it.catalog }

        return mutex.withLock {
            cache[key]?.takeIf { System.currentTimeMillis() - it.loadedAt < cacheTtlMillis }?.let { return@withLock it.catalog }
            try {
                val loaded = client.get(config.storeDataBaseUrl.trimEnd('/') + "/$category/$platform.json").body<StoreCatalog>()
                cache[key] = CachedCatalog(loaded, System.currentTimeMillis())
                loaded
            } catch (error: Exception) {
                cache[key]?.catalog ?: throw IllegalStateException("Store data is temporarily unavailable", error)
            }
        }
    }

    suspend fun index(): StoreIndexResponse {
        val catalogs = STORE_CATEGORIES.flatMap { category ->
            STORE_PLATFORMS.mapNotNull { platform ->
                runCatching { catalog(category, platform) }.getOrNull()
            }
        }
        return StoreIndexResponse(
            categories = STORE_CATEGORIES.toList(),
            platforms = STORE_PLATFORMS.toList(),
            catalogs = catalogs,
            lastUpdated = catalogs.mapNotNull { it.lastUpdated }.maxOrNull(),
        )
    }

    suspend fun search(query: String, category: String?, platform: String?, limit: Int): StoreSearchResponse {
        require(query.isNotBlank()) { "Search query is required" }
        category?.let { require(it in STORE_CATEGORIES) { "Unsupported store category" } }
        platform?.let { require(it in STORE_PLATFORMS) { "Unsupported store platform" } }

        val categories = category?.let(::listOf) ?: STORE_CATEGORIES.toList()
        val platforms = platform?.let(::listOf) ?: STORE_PLATFORMS.toList()
        val normalized = query.trim().lowercase()
        val repositories = categories.flatMap { cat ->
            platforms.flatMap { plat -> runCatching { catalog(cat, plat).repositories }.getOrDefault(emptyList()) }
        }.distinctBy { it.id }
            .filter { repository ->
                listOf(repository.name, repository.fullName, repository.description.orEmpty(), repository.language.orEmpty(), repository.topics.joinToString(" "))
                    .joinToString(" ").lowercase().contains(normalized)
            }
            .take(limit.coerceIn(1, 100))

        return StoreSearchResponse(query.trim(), category, platform, repositories.size, repositories)
    }

    private data class CachedCatalog(val catalog: StoreCatalog, val loadedAt: Long)
}
