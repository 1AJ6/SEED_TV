/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.IoDispatcher
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.common.ScrobbleSink
import com.sayertv.mobile.core.jellyfin.model.CollectionKind
import com.sayertv.mobile.core.jellyfin.model.HomeSections
import com.sayertv.mobile.core.jellyfin.model.ItemMappers.toLibraryView
import com.sayertv.mobile.core.jellyfin.model.ItemMappers.toMediaItem
import com.sayertv.mobile.core.jellyfin.model.LatestSection
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.SeriesDetail
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/** Paged result for library grids (consumed by Paging 3 in :feature:library). */
data class ItemPage(val items: List<MediaItem>, val totalCount: Int)

/**
 * Library browsing (design doc §4.2). Network-only in v1 — Room caches only
 * what sync features need. Every call maps SDK DTOs to domain models before
 * returning; 401s invalidate the session (→ onboarding).
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val scrobbleSink: dagger.Lazy<ScrobbleSink>,
    private val snapshotFactory: dagger.Lazy<ScrobbleSnapshotFactory>,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val defaultFields = listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.TAGS,
        ItemFields.PROVIDER_IDS,
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.MEDIA_STREAMS,
    )

    suspend fun homeSections(): SResult<HomeSections> = guarded { session ->
        val api = session.api
        coroutineScope {
            val viewsDeferred = async { api.userViewsApi.getUserViews().content }
            val resumeDeferred = async {
                api.itemsApi.getResumeItems(limit = 12, fields = defaultFields).content
            }
            val nextUpDeferred = async {
                api.tvShowsApi.getNextUp(limit = 12, fields = defaultFields).content
            }

            val libraries = viewsDeferred.await().items.orEmpty()
                .map { it.toLibraryView(session) }
                .filter { it.collectionType != CollectionKind.OTHER }

            val latest = libraries
                .filter { it.collectionType == CollectionKind.MOVIES || it.collectionType == CollectionKind.TVSHOWS }
                .map { library ->
                    async {
                        val items = api.userLibraryApi.getLatestMedia(
                            parentId = UUID.fromString(library.id),
                            limit = 12,
                            fields = defaultFields,
                        ).content
                        LatestSection(library, items.map { it.toMediaItem(session) })
                    }
                }
                .map { it.await() }
                .filter { it.items.isNotEmpty() }

            HomeSections(
                libraries = libraries,
                continueWatching = resumeDeferred.await().items.orEmpty().map { it.toMediaItem(session) },
                nextUp = nextUpDeferred.await().items.orEmpty().map { it.toMediaItem(session) },
                latest = latest,
            )
        }
    }

    /** One page of a library grid (movies+series for mixed, recursive). */
    suspend fun libraryPage(
        libraryId: String,
        startIndex: Int,
        limit: Int,
    ): SResult<ItemPage> = guarded { session ->
        val result = session.api.itemsApi.getItems(
            parentId = UUID.fromString(libraryId),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = limit,
            fields = defaultFields,
        ).content
        ItemPage(
            items = result.items.orEmpty().map { it.toMediaItem(session) },
            totalCount = result.totalRecordCount ?: 0,
        )
    }

    suspend fun item(itemId: String): SResult<MediaItem> = guarded { session ->
        session.api.userLibraryApi.getItem(itemId = UUID.fromString(itemId))
            .content.toMediaItem(session)
    }

    suspend fun seriesDetail(seriesId: String): SResult<SeriesDetail> = guarded { session ->
        coroutineScope {
            val seriesDeferred = async {
                session.api.userLibraryApi.getItem(itemId = UUID.fromString(seriesId)).content
            }
            val seasonsDeferred = async {
                session.api.tvShowsApi.getSeasons(
                    seriesId = UUID.fromString(seriesId),
                    fields = defaultFields,
                ).content
            }
            SeriesDetail(
                series = seriesDeferred.await().toMediaItem(session),
                seasons = seasonsDeferred.await().items.orEmpty().map { it.toMediaItem(session) },
            )
        }
    }

    suspend fun episodes(seriesId: String, seasonId: String): SResult<List<MediaItem>> =
        guarded { session ->
            session.api.tvShowsApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                seasonId = UUID.fromString(seasonId),
                fields = defaultFields,
            ).content.items.orEmpty().map { it.toMediaItem(session) }
        }

    /** The episode that follows [currentItemId] in its series, or null at series end. */
    suspend fun nextEpisode(seriesId: String, currentItemId: String): SResult<MediaItem?> =
        guarded { session ->
            val episodes = session.api.tvShowsApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                adjacentTo = UUID.fromString(currentItemId),
                fields = defaultFields,
            ).content.items.orEmpty()
            val index = episodes.indexOfFirst { it.id.toString() == currentItemId }
            episodes.getOrNull(index + 1)?.toMediaItem(session)
        }

    /** The episode that precedes [currentItemId] in its series, or null at series start. */
    suspend fun previousEpisode(seriesId: String, currentItemId: String): SResult<MediaItem?> =
        guarded { session ->
            val episodes = session.api.tvShowsApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                adjacentTo = UUID.fromString(currentItemId),
                fields = defaultFields,
            ).content.items.orEmpty()
            val index = episodes.indexOfFirst { it.id.toString() == currentItemId }
            if (index > 0) episodes[index - 1].toMediaItem(session) else null
        }

    suspend fun search(query: String): SResult<List<MediaItem>> = guarded { session ->
        if (query.isBlank()) return@guarded emptyList()
        session.api.itemsApi.getItems(
            searchTerm = query,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
            limit = 40,
            fields = defaultFields,
        ).content.items.orEmpty().map { it.toMediaItem(session) }
    }

    /**
     * Manual watched toggle. Per Ethan Sayer's decision #3, marking watched
     * must also scrobble to AniList — the ScrobbleEngine (M4) subscribes to
     * [manualPlayedEvents] so the wiring point already exists.
     */
    suspend fun setPlayed(itemId: String, played: Boolean): SResult<MediaItem> = guarded { session ->
        val api = session.api
        val dto = if (played) {
            api.playStateApi.markPlayedItem(itemId = UUID.fromString(itemId)).content
        } else {
            api.playStateApi.markUnplayedItem(itemId = UUID.fromString(itemId)).content
        }
        val refreshed = session.api.userLibraryApi.getItem(itemId = UUID.fromString(itemId))
            .content.toMediaItem(session)
        // Product decision #3: manual mark-watched scrobbles to AniList too.
        // Snapshot is series-enriched (alpha10 fix: anime markers live on the series).
        if (played) {
            runCatching {
                val runtime = refreshed.runtimeMs ?: 0
                snapshotFactory.get().create(refreshed, runtime, runtime)
                    ?.let { scrobbleSink.get().onMarkedWatched(it) }
            }
        }
        dto.let { }
        refreshed
    }

    /** All Jellyfin traffic runs on Dispatchers.IO — the SDK executes HTTP on the
     *  calling thread, and callers are usually on Main (NetworkOnMainThreadException). */
    private suspend fun <T> guarded(block: suspend (Session) -> T): SResult<T> = withContext(io) {
        val session = sessionManager.current()
            ?: return@withContext SResult.Error(AppError.UNAUTHORIZED)
        try {
            SResult.Success(block(session))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InvalidStatusException) {
            if (e.status == 401) {
                sessionManager.invalidate()
                SResult.Error(AppError.UNAUTHORIZED, e)
            } else {
                SResult.Error(AppError.NETWORK, e)
            }
        } catch (e: ApiClientException) {
            SResult.Error(AppError.NETWORK, e)
        } catch (e: Throwable) {
            SResult.Error(AppError.UNKNOWN, e)
        }
    }
}
