/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Offset-based PagingSource over LibraryRepository.libraryPage (design doc §4.2). */
class LibraryPagingSource(
    private val libraryRepository: LibraryRepository,
    private val libraryId: String,
) : PagingSource<Int, MediaItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val startIndex = params.key ?: 0
        return when (val result = libraryRepository.libraryPage(libraryId, startIndex, params.loadSize)) {
            is SResult.Success -> {
                val page = result.data
                val nextIndex = startIndex + page.items.size
                LoadResult.Page(
                    data = page.items,
                    prevKey = if (startIndex == 0) null else (startIndex - params.loadSize).coerceAtLeast(0),
                    nextKey = if (nextIndex < page.totalCount && page.items.isNotEmpty()) nextIndex else null,
                )
            }
            is SResult.Error -> LoadResult.Error(result.cause ?: RuntimeException(result.error.name))
            SResult.Loading -> LoadResult.Invalid()
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
        state.anchorPosition?.let { anchor ->
            (anchor - state.config.pageSize / 2).coerceAtLeast(0)
        }
}

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val libraryName: String = savedStateHandle["libraryName"] ?: ""
    private val libraryId: String = checkNotNull(savedStateHandle["libraryId"])

    val items: Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
        pagingSourceFactory = { LibraryPagingSource(libraryRepository, libraryId) },
    ).flow.cachedIn(viewModelScope)
}
