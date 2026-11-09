package com.example.tusumba.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tusumba.ui.catalog.CatalogBody
import com.example.tusumba.ui.catalog.CatalogHeader
import com.example.tusumba.ui.catalog.CatalogNavigation
import com.example.tusumba.ui.catalog.CatalogTheme
import com.example.tusumba.ui.catalog.CatalogUiState
import com.example.tusumba.ui.catalog.CatalogViewModel

@Composable
fun CatalogScreen(
    onEventClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreenContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onIsFreeChange = viewModel::setIsFree,
        onResetFilters = viewModel::resetFilters,
        onRetry = viewModel::refresh,
        onEventClick = onEventClick,
        onProfileClick = onProfileClick,
    )
}

@Composable
internal fun CatalogScreenContent(
    state: CatalogUiState,
    onQueryChange: (String) -> Unit,
    onIsFreeChange: (Boolean?) -> Unit,
    onResetFilters: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
    onProfileClick: () -> Unit,
) {
    CatalogTheme {
        Scaffold(
            // Системные отступы уже учитывает Scaffold в MainActivity.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { CatalogHeader(onProfileClick) },
            bottomBar = { CatalogNavigation(onProfileClick) },
        ) { padding ->
            CatalogBody(
                state,
                onQueryChange,
                onIsFreeChange,
                onResetFilters,
                onRetry,
                onEventClick,
                Modifier.padding(padding),
            )
        }
    }
}
