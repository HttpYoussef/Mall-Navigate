package com.example.mallar

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.mallar.data.MallGraphRepository
import com.example.mallar.data.PlaceRepository
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mallar.data.AppPreferences
import com.example.mallar.data.FavoritesManager
import com.example.mallar.ui.navigation.*
import com.example.mallar.ui.splash.SplashScreen
import com.example.mallar.ui.auth.*
import com.example.mallar.ui.profile.*
import com.example.mallar.ui.parking.*
import com.example.mallar.ui.home.*
import com.example.mallar.ui.localization.*
import com.example.mallar.ui.theme.MallARTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply saved language preference before inflation
        val prefs = newBase.getSharedPreferences("mallar_app_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        newBase.resources.updateConfiguration(config, newBase.resources.displayMetrics)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize preference managers
        AppPreferences.init(this)
        FavoritesManager.init(this)
        com.example.mallar.data.ParkingManager.init(this)

        lifecycleScope.launch(Dispatchers.IO) {
            PlaceRepository.load(applicationContext)
            MallGraphRepository.load(applicationContext)
        }

        setContent {
            MallARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MallARNavGraph(this)
                }
            }
        }
    }
}

@Composable
fun MallARNavGraph(context: Context) {

    val navController = rememberNavController()

    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("mallar_prefs", Context.MODE_PRIVATE)
    }

    val isFirstLaunch = remember {
        mutableStateOf(prefs.getBoolean("is_first_launch", true))
    }

    var verificationId by remember { mutableStateOf("") }

    fun checkPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun markNotFirstLaunch() {
        isFirstLaunch.value = false
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    NavHost(
        navController    = navController,
        startDestination = "splash"
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable("splash") {
            SplashScreen(
                isFirstLaunch = isFirstLaunch.value,
                onStartClick = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // ── Welcome (first-time / sign-in / sign-up) ─────────────────────────
        composable("welcome") {
            WelcomeScreen(
                onSignInClick = {
                    navController.navigate("sign_in")
                },
                onSignUpClick = {
                    navController.navigate("sign_up")
                },
                onSkipClick = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        // ── Sign Up (new) ────────────────────────────────────────────────────
        composable("sign_up") {
            SignUpScreen(
                onBackClick = { navController.popBackStack() },
                onSuccess = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkipClick = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo("sign_up") { inclusive = true }
                    }
                }
            )
        }

        // ── Sign In (unified — phone + OTP on one screen) ────────────────────
        composable("sign_in") {
            SignInScreen(
                onBackClick = { navController.popBackStack() },
                onSuccess = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkipClick = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo("sign_in") { inclusive = true }
                    }
                }
            )
        }

        // ── Phone Auth (kept for backward compat, redirects to sign_in) ──────
        composable("phone_auth") {
            PhoneAuthScreen(
                onBackClick  = { navController.popBackStack() },
                onCodeSent   = { id: String ->
                    verificationId = id
                    navController.navigate("otp_verify")
                },
                onSkipClick  = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo("phone_auth") { inclusive = true }
                    }
                }
            )
        }

        // ── OTP Verify (kept for backward compat) ────────────────────────────
        composable("otp_verify") {
            OtpVerifyScreen(
                verificationId = verificationId,
                onBackClick    = { navController.popBackStack() },
                onSuccess      = {
                    markNotFirstLaunch()
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Permissions ───────────────────────────────────────────────────────
        composable("permissions") {
            PermissionsScreen(
                onContinueClick = {
                    val target =
                        if (NavigationState.selectedPlace != null) "logo_scan_with_dest" else "logo_scan"
                    navController.navigate(target) {
                        popUpTo("permissions") { inclusive = true }
                    }
                }
            )
        }

        // ── HOME ──────────────────────────────────────────────────────────────
        // NEW: First screen after auth. User picks a destination here.
        // After selection → check permissions → logo_scan (to localize) → navigation
        composable("home") {
            HomeScreen(
                onMapClick = {
                    navController.navigate("static_map")
                },
                onSavedClick = {
                    navController.navigate("saved_places")
                },
                onDestinationSelected = { place ->
                    // Save the chosen destination globally
                    NavigationState.selectedPlace = place

                    // Now send user to logo scan to set their start location,
                    // with the destination already known.
                    if (checkPermissionsGranted()) {
                        navController.navigate("logo_scan_with_dest")
                    } else {
                        navController.navigate("permissions")
                    }
                },
                onSettingsClick = {
                    navController.navigate("profile")
                },
                onParkingClick = {
                    navController.navigate("parking_home")
                },
                onScanClick = {
                    if (checkPermissionsGranted()) {
                        navController.navigate("logo_scan")
                    } else {
                        navController.navigate("permissions")
                    }
                },
                onNavigateToNavigation = {
                    navController.navigate("navigation") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onCategoryClick = { categoryKey, categoryLabel ->
                    navController.navigate("category/${Uri.encode(categoryKey)}/${Uri.encode(categoryLabel)}")


                },
                onOffersClick = {
                    navController.navigate("offers")
                },
                onVoucherClick = { voucherId ->
                    navController.navigate("voucher/$voucherId")
                }
            )
        }
        composable(
            route = "offers",
            enterTransition = { slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(320)) }
        ) {
            OffersScreen(
                onBackClick = { navController.popBackStack() },
                onVoucherClick = { voucherId -> navController.navigate("voucher/$voucherId") }
            )
        }

        composable(
            route = "voucher/{voucherId}",
            arguments = listOf(navArgument("voucherId") { type = NavType.StringType }),
            enterTransition = { slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(320)) }
        ) { backStackEntry ->
            val voucherId = backStackEntry.arguments?.getString("voucherId").orEmpty()
            VoucherDetailsScreen(
                voucherId = voucherId,
                onBackClick = { navController.popBackStack() },
                onDestinationSelected = { place ->
                    NavigationState.selectedPlace = place
                    if (checkPermissionsGranted()) {
                        navController.navigate("logo_scan_with_dest")
                    } else {
                        navController.navigate("permissions")
                    }
                }
            )
        }
        composable(
            route = "category/{categoryKey}/{categoryLabel}",
            arguments = listOf(
                navArgument("categoryKey") { type = NavType.StringType },
                navArgument("categoryLabel") { type = NavType.StringType }
            ),
            enterTransition = { slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(320)) }
        ) { backStackEntry ->
            val categoryKey = backStackEntry.arguments?.getString("categoryKey").orEmpty()
            val categoryLabel = backStackEntry.arguments?.getString("categoryLabel").orEmpty()
            CategoryScreen(
                categoryKey = categoryKey,
                categoryLabel = categoryLabel,
                onBackClick = { navController.popBackStack() },
                onDestinationSelected = { place ->
                    NavigationState.selectedPlace = place
                    if (checkPermissionsGranted()) {
                        navController.navigate("logo_scan_with_dest")
                    } else {
                        navController.navigate("permissions")
                    }
                }
            )
        }


        // ── Logo Scan (destination pre-selected from HomeScreen) ──────────────
        // User has already chosen where they want to go.
        // This screen is now only for LOCALIZATION (setting start position).
        // Once localized it auto-navigates.
        composable("logo_scan_with_dest") {
            LogoScanScreen(
                preselectedDestination = true,
                onBackFromLogo = { navController.popBackStack() },

                onStoreSelected  = { isCameraMode ->
                    NavigationState.startWithAr = isCameraMode
                    navController.navigate("navigation") {
                        // Keep home in the back stack so back from navigation → home
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        // ── Logo Scan (standalone — no destination pre-selected) ──────────────
        // Kept for backwards compatibility / direct deep links
        composable("logo_scan") {
            LogoScanScreen(
                onBackFromLogo = { navController.popBackStack() },

                onStoreSelected = { isCameraMode ->
                    NavigationState.startWithAr = isCameraMode
                    navController.navigate("navigation")
                }
            )
        }
        // ── Unified Navigation (Map + AR) ────────────────────────────────────
        composable("navigation") {
            UnifiedNavigationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Static mall map (from Home bottom nav) ────────────────────────────
        composable("static_map") {
            StaticMapScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Saved / favorite places (from Home bottom nav) ────────────────────
        composable("saved_places") {
            SavedPlacesScreen(
                onBackClick = { navController.popBackStack() },
                onPlaceClick = { place ->
                    NavigationState.selectedPlace = place
                    if (checkPermissionsGranted()) {
                        navController.navigate("logo_scan_with_dest")
                    } else {
                        navController.navigate("permissions")
                    }
                }
            )
        }

        // ── Profile (replaces Settings) ──────────────────────────────────────
        composable("profile") {
            ProfileScreen(
                onBackClick   = { navController.popBackStack() },
                onLogoutClick = {
                    isFirstLaunch.value = true
                    prefs.edit().putBoolean("is_first_launch", true).apply()
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── PARKING ───────────────────────────────────────────────────────────
        composable("parking_home") {
            ParkingHomeScreen(
                onBackClick = { navController.popBackStack() },
                onSaveLocationClick = { navController.navigate("parking_camera") },
                onNavigateToCarClick = { navController.navigate("parking_map") },
                onEditLocationClick = { navController.navigate("parking_map") }
            )
        }
        composable("parking_camera") {
            ParkingCameraScreen(
                onBackClick = { navController.popBackStack() },
                onPhotoCaptured = { navController.navigate("parking_scan_result") }
            )
        }
        composable("parking_scan_result") {
            ParkingScanResultScreen(
                onBackClick = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.navigate("parking_home") {
                        popUpTo("parking_home") { inclusive = true }
                    }
                }
            )
        }
        composable("parking_map") {
            ParkingMapScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
