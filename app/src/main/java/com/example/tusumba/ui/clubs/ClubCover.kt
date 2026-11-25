package com.example.tusumba.ui.clubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tusumba.BuildConfig
import com.example.tusumba.R
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
internal fun ClubCover(photoUrl: String?, version: String, preview: ByteArray? = null) {
    val context = LocalContext.current
    val url = remember(photoUrl) { photoUrl?.takeIf { it.isNotBlank() }?.let { BuildConfig.BASE_URL.toHttpUrl().resolve(it)?.toString() } }
    val model = remember(context, url, version, preview) {
        if (preview != null) ImageRequest.Builder(context).data(preview).build()
        else url?.let {
            ImageRequest.Builder(context).data(it).memoryCacheKey("$it:$version").diskCacheKey("$it:$version").build()
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)))
            .testTag(if (model == null) "club_default_cover" else "club_photo"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Menu, if (model == null) stringResource(R.string.club_default_cover) else null,
            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (model != null) {
            AsyncImage(model, stringResource(R.string.club_photo), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
