package com.example.mallar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.mallar.data.Place

private val LogoPrimary      = Color(0xFF258799)
@Suppress("unused")
private val LogoPrimaryLight = Color(0xFF2fa3b8)

@Composable
fun StoreLogo(
    place: Place,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 10.dp,
    fallbackTextSize: TextUnit = 16.sp,
    maxDecodeSizePx: Int = 256,
) {
    val context = LocalContext.current
    val logoPath = place.logo.orEmpty()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (logoPath.isNotBlank()) {
            val request = remember(place.id, logoPath) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/$logoPath")
                    .crossfade(false)
                    .allowHardware(false)
                    .size(Size(maxDecodeSizePx, maxDecodeSizePx))
                    .memoryCacheKey(logoPath)
                    .build()
            }
            AsyncImage(
                model              = request,
                contentDescription = place.brand,
                // Fit keeps full logo inside the circle — never crops
                contentScale       = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            )
        } else {
            Text(
                text       = place.brand.orEmpty().take(2).uppercase(),
                color      = LogoPrimary,
                fontWeight = FontWeight.Bold,
                fontSize   = fallbackTextSize
            )
        }
    }
}

@Composable
fun StoreLogoContainer(
    place: Place,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 6.dp,
    fallbackTextSize: TextUnit = 16.sp,
    cornerRadius: Dp = 13.dp,
    maxDecodeSizePx: Int = 256,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(cornerRadius))
            .border(1.dp, LogoPrimary.copy(alpha = 0.15f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        StoreLogo(
            place            = place,
            modifier         = Modifier.fillMaxSize(),
            contentPadding   = contentPadding,
            fallbackTextSize = fallbackTextSize,
            maxDecodeSizePx  = maxDecodeSizePx,
        )
    }
}
