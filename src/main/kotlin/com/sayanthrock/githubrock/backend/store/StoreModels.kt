package com.sayanthrock.githubrock.backend.store

import kotlinx.serialization.Serializable

val STORE_CATEGORIES = setOf("trending", "new-releases", "most-popular")
val STORE_PLATFORMS = setOf("android", "windows", "macos", "linux")

@Serializable
data class StoreRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: StoreOwner,
    val description: String? = null,
    val defaultBranch: String? = null,
    val htmlUrl: String,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val releasesUrl: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
    val latestReleaseDate: String? = null,
    val releaseRecency: Int? = null,
    val releaseRecencyText: String? = null,
    val trendingScore: Double? = null,
)

@Serializable
data class StoreOwner(
    val login: String,
    val avatarUrl: String? = null,
)

@Serializable
data class StoreCatalog(
    val category: String,
    val platform: String,
    val lastUpdated: String? = null,
    val totalCount: Int = 0,
    val repositories: List<StoreRepository> = emptyList(),
)

@Serializable
data class StoreIndexResponse(
    val categories: List<String>,
    val platforms: List<String>,
    val catalogs: List<StoreCatalog>,
    val lastUpdated: String? = null,
)

@Serializable
data class StoreSearchResponse(
    val query: String,
    val category: String? = null,
    val platform: String? = null,
    val totalCount: Int,
    val repositories: List<StoreRepository>,
)
