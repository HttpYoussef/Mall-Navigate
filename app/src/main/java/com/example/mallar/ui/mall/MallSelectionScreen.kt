package com.example.mallar.ui.mall

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.data.Mall
import com.example.mallar.data.StartupState

// ── String resource helpers ───────────────────────────────────────────────────

/** Returns the display-name string resource ID for the given [mall]. */
@StringRes
fun mallNameRes(mall: Mall): Int = when (mall) {
    Mall.CITY_STARS          -> R.string.mall_city_stars_name
    Mall.CITY_CENTRE_ALMAZA  -> R.string.mall_city_centre_almaza_name
    Mall.MALL_OF_EGYPT       -> R.string.mall_of_egypt_name
}

/** Returns the location string resource ID for the given [mall]. */
@StringRes
private fun mallLocationRes(mall: Mall): Int = when (mall) {
    Mall.CITY_STARS          -> R.string.mall_city_stars_location
    Mall.CITY_CENTRE_ALMAZA  -> R.string.mall_city_centre_almaza_location
    Mall.MALL_OF_EGYPT       -> R.string.mall_of_egypt_location
}

// ── Design tokens (kept local — no shared theme dependency needed) ────────────

private val NavyBg         = Color(0xFF06131A)
private val CardBg         = Color(0xFF0E2231)
private val AccentPurple   = Color(0xFF9D50FF)
private val TextMain       = Color(0xFFFFFFFF)
private val TextSub        = Color(0xFFB0C4D8)
private val ComingSoonBadge = Color(0xFF9D50FF)

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Mall selection screen.
 *
 * Shown on every cold launch, directly after splash.  The user must pick one
 * of the listed malls before proceeding.  Only [Mall.CITY_STARS] is currently
 * selectable; the others render as "Coming soon" and are inert.
 *
 * @param startupState Current state from [StartupCoordinator].  Selection is
 *   blocked while data is loading; errors surface a Retry button.
 * @param onMallSelected Called exactly once, with the chosen [Mall], when the
 *   user taps a selectable card and startup has succeeded.
 * @param onRetry Called when the user taps Retry after a startup error.
 *   Wire to [StartupCoordinator.retry] in the nav graph — same as SplashScreen.
 */
@Composable
fun MallSelectionScreen(
    startupState: StartupState,
    onMallSelected: (Mall) -> Unit,
    onRetry: () -> Unit,
) {
    // Respect dark-mode preference the same way sibling screens do.
    val isDarkMode by com.example.mallar.data.AppPreferences.isDarkMode.collectAsState()

    val bg     = if (isDarkMode) NavyBg  else Color(0xFFF4F6FA)
    val cardBg = if (isDarkMode) CardBg  else Color.White
    val titleColor  = if (isDarkMode) TextMain else Color(0xFF06131A)
    val subtitleColor = if (isDarkMode) TextSub else Color(0xFF6B7C93)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.mall_select_title),
                color = titleColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(8.dp))

            // ── Subtitle ──────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.mall_select_subtitle),
                color = subtitleColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(40.dp))

            // ── Cards — one per Mall.entries ──────────────────────────────────
            val dataLoading = startupState == StartupState.Loading || startupState == StartupState.Idle
            Mall.entries.forEach { mall ->
                MallCard(
                    mall          = mall,
                    startupState  = startupState,
                    isDarkMode    = isDarkMode,
                    cardBg        = cardBg,
                    titleColor    = titleColor,
                    subtitleColor = subtitleColor,
                    onMallSelected = onMallSelected
                )
                // Spinner sits directly under the only selectable card while its
                // data is still loading (spec §3).
                if (mall == Mall.CITY_STARS && dataLoading) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentPurple,
                        strokeWidth = 2.5.dp
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Startup error → Retry ────────────────────────────────────────
            if (startupState == StartupState.Error) {
                val retryLabel = stringResource(R.string.mall_retry)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        AccentPurple.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.semantics { contentDescription = retryLabel }
                ) {
                    Text(
                        text = retryLabel,
                        color = if (isDarkMode) Color.White else AccentPurple
                    )
                }
            }
        }
    }
}

// ── Mall card ─────────────────────────────────────────────────────────────────

@Composable
private fun MallCard(
    mall: Mall,
    startupState: StartupState,
    isDarkMode: Boolean,
    cardBg: Color,
    titleColor: Color,
    subtitleColor: Color,
    onMallSelected: (Mall) -> Unit,
) {
    val available = mall.hasNavigationData
    // Tappable only when the card is available AND startup has loaded.
    val tappable  = available && startupState == StartupState.Success

    val alpha     = if (available) 1f else 0.45f
    val borderColor = if (isDarkMode) {
        if (available) AccentPurple.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f)
    } else {
        if (available) AccentPurple.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.08f)
    }

    val mallName     = stringResource(mallNameRes(mall))
    val mallLocation = stringResource(mallLocationRes(mall))
    val comingSoon   = stringResource(R.string.mall_coming_soon)

    // Merged semantics node for TalkBack
    val semanticsDescription = buildString {
        append(mallName)
        append(", ")
        append(mallLocation)
        if (!available) {
            append(", ")
            append(comingSoon)
        }
    }

    Card(
        onClick = { if (tappable) onMallSelected(mall) },
        enabled = tappable,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDescription
                if (!available) {
                    disabled()
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor    = cardBg,
            disabledContainerColor = cardBg,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Generic storefront icon, decorative (no contentDescription per spec)
            Icon(
                imageVector = Icons.Outlined.Storefront,
                contentDescription = null, // decorative
                tint = if (isDarkMode) AccentPurple.copy(alpha = 0.8f) else AccentPurple,
                modifier = Modifier.size(36.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mallName,
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mallLocation,
                    color = subtitleColor,
                    fontSize = 13.sp
                )
            }

            // "Coming soon" badge for unavailable malls
            if (!available) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .background(
                            ComingSoonBadge.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            ComingSoonBadge.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = comingSoon,
                        color = ComingSoonBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
