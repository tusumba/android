package com.example.tusumba.ui.catalog

import com.example.tusumba.data.catalog.CatalogEvent
import com.example.tusumba.data.catalog.CatalogFilters

data class CatalogUiState(
    val filters: CatalogFilters = CatalogFilters(),
    val content: CatalogContent = CatalogContent.Loading,
)

sealed interface CatalogContent {
    data object Loading : CatalogContent

    data class Events(val events: List<CatalogEvent>) : CatalogContent

    data object Empty : CatalogContent

    data object Error : CatalogContent
}
