package com.example.tusumba.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tusumba.R

internal data class ContentStateAction(@StringRes val label: Int, val onClick: () -> Unit)

internal data class ContentStateIllustration(@StringRes val title: Int, val icon: ImageVector)

@Composable
internal fun ContentLoading(
    modifier: Modifier = Modifier,
    @StringRes label: Int = R.string.content_loading,
    indicator: @Composable () -> Unit = {
        CircularProgressIndicator(Modifier.padding(24.dp).clearAndSetSemantics {})
    },
) {
    val description = stringResource(label)
    Box(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center,
    ) {
        indicator()
    }
}

@Composable
internal fun ContentEmpty(
    @StringRes message: Int,
    modifier: Modifier = Modifier,
    action: ContentStateAction? = null,
    illustration: ContentStateIllustration? = null,
) {
    ContentFeedback(message, modifier, action, illustration)
}

@Composable
internal fun ContentError(
    @StringRes message: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: ContentStateIllustration? = null,
) {
    ContentFeedback(message, modifier, ContentStateAction(R.string.content_retry, onRetry), illustration)
}

@Composable
private fun ContentFeedback(
    @StringRes message: Int,
    modifier: Modifier,
    action: ContentStateAction?,
    illustration: ContentStateIllustration?,
) {
    val padding = if (illustration == null) PaddingValues(vertical = 16.dp)
        else PaddingValues(horizontal = 18.dp, vertical = 48.dp)
    Column(
        modifier = modifier.fillMaxWidth().padding(padding)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (illustration == null) Arrangement.spacedBy(12.dp) else Arrangement.Center,
    ) {
        if (illustration != null) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(illustration.icon, null, Modifier.padding(20.dp).size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(illustration.title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = stringResource(message),
            style = if (illustration == null) MaterialTheme.typography.bodyMedium else LocalTextStyle.current,
            color = if (illustration == null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            if (illustration == null) {
                OutlinedButton(onClick = action.onClick) { Text(stringResource(action.label)) }
            } else {
                Spacer(Modifier.height(22.dp))
                Button(onClick = action.onClick, shape = RoundedCornerShape(11.dp)) {
                    Text(stringResource(action.label))
                }
            }
        }
    }
}
