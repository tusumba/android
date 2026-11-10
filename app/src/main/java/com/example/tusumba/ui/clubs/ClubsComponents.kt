package com.example.tusumba.ui.clubs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tusumba.R

@Composable
internal fun ClubsLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ClubsFeedback(
    @StringRes message: Int,
    onRetry: (() -> Unit)? = null,
    @StringRes action: Int = R.string.clubs_retry,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(action)) }
        }
    }
}

@Composable
internal fun ClubsPagination(
    hasMore: Boolean,
    loading: Boolean,
    error: Boolean,
    @StringRes errorMessage: Int,
    onLoadMore: () -> Unit,
) {
    when {
        loading -> ClubsLoading()
        error -> ClubsFeedback(errorMessage, onLoadMore)
        hasMore -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clubs_load_more))
        }
    }
}
