package com.example.tusumba.ui.clubs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.tusumba.R
import com.example.tusumba.ui.components.ContentError
import com.example.tusumba.ui.components.ContentLoading

@Composable
internal fun ClubsPagination(
    hasMore: Boolean,
    loading: Boolean,
    error: Boolean,
    @StringRes errorMessage: Int,
    onLoadMore: () -> Unit,
) {
    when {
        loading -> ContentLoading()
        error -> ContentError(errorMessage, onLoadMore)
        hasMore -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clubs_load_more))
        }
    }
}
