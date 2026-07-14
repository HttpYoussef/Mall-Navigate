package com.example.mallar.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mallar.R
import com.example.mallar.ui.theme.TextPrimary
import com.example.mallar.ui.theme.TextSecondary
import com.example.mallar.ui.theme.White

private val ScreenBg    = Color(0xFFF5F5FA)
private val ProfileTeal = Color(0xFF1A8C8C)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
    ) {

        // ── Page title ────────────────────────────────────────────────────────
        Text(
            text = "Profile",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = ProfileTeal,
            modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 24.dp)
        )

        // ── Avatar + edit button ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Avatar circle with hardcoded profile photo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.profile_photo),
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                )

                // Delete badge (purely decorative to match UI design)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE0E0E0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove photo",
                        modifier = Modifier.size(13.dp),
                        tint = TextSecondary
                    )
                }
            }

            // Edit icon button (top-end)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFDDE0E8), RoundedCornerShape(10.dp))
                    .background(White)
                    .clickable { /* future: open edit */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit profile",
                    modifier = Modifier.size(18.dp),
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Name
        Text(
            text = "Mohamed",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF255E82),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = "Ibrahim",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF255E82),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Joined 5 weeks ago",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Menu items ────────────────────────────────────────────────────────
        ProfileMenuItem(icon = Icons.Default.LocationOn, label = "Saved Locations", onClick = {})
        ProfileMenuItem(icon = Icons.Default.GridView,   label = "Recent Places",   onClick = {})
        ProfileMenuItem(icon = Icons.Default.Email,      label = "Messages",        onClick = {})
        ProfileMenuItem(icon = Icons.Default.Delete,     label = "Delete Account",  onClick = {})

        Spacer(modifier = Modifier.height(32.dp))

        // ── Sign out button ───────────────────────────────────────────────────
        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.5.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ProfileTeal,
                containerColor = White
            )
        ) {
            Text(
                text = "Sign out",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProfileTeal
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ── Single menu row ───────────────────────────────────────────────────────────
@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = ProfileTeal
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = TextSecondary
        )
    }
}