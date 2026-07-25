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
import com.example.mallar.data.PlaceRepository
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.mallar.data.StartupCoordinator
import com.example.mallar.data.StartupState
import com.example.mallar.ui.navigation.*
import com.example.mallar.ui.splash.SplashScreen
import com.example.mallar.ui.auth.*
import com.example.mallar.ui.profile.*
import com.example.mallar.ui.parking.*
import com.example.mallar.ui.home.*
import com.example.mallar.ui.destination.DestinationSelectionScreen
import com.example.mallar.ui.destination.DestinationSearchScreen
import com.example.mallar.ui.destination.DestinationCategoryScreen
import com.example.mallar.ui.localization.*
import com.example.mallar.ui.theme.MallARTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        // Apply saved language preference before inflation
        val prefs = newBase.getSharedPreferences("mallar_app_prefs", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = java.util.Locale(lang)
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
        // 1. Install System Splash (Dismisses immediately into Compose Splash)
        installSplashScreen()
        
        super.onCreate(savedInstanceState)

        setContent {
            val startupState by StartupCoordinator.state.collectAsState()
            val intentData = remember { intent?.data }
            
            MallARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MallARNavGraph(this, startupState, intentData)
                }
            }
        }
    }
}

@Composable
fun MallARNavGraph(context: Context, startupState: StartupState, initialIntentData: Uri?) {

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
            context,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun markNotFirstLaunch() {
        isFirstLaunch.value = false
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    // Handle initial intent for deep linking after splash
    val navigateAfterSplash: () -> Unit = {
        val destination = if (isFirstLaunch.value) "welcome" else "home"
        
        // If we have deep link data, we ensure the standard transition occurs correctly.
        // Deep links are typically handled by the NavHost automatically if configured,
        // or we could explicitly route based on initialIntentData here.
        navController.navigate(destination) {
            popUpTo("splash") { inclusive = true }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = "splash"
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable(
            route = "splash",
            exitTransition = {
                fadeOut(tween(300))
            }
        ) {
            SplashScreen(
                startupState = startupState,
                onTimeout = navigateAfterSplash,
                onRetry = {
                    StartupCoordinator.retry(context = context)
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
        composable("home") {
            HomeScreen(
                onMapClick = {
                    navController.navigate("static_map")
                },
                onSavedClick = {
                    navController.navigate("saved_places")
                },
                onDestinationSelected = { place ->
                    NavigationState.selectedPlace = place
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
                onNavigateToNavigation = {
                    navController.navigate("navigation") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onOffersClick = {
                    navController.navigate("offers")
                },
                onVoucherClick = { voucherId ->
                    navController.navigate("voucher/$voucherId")
                },
                onNavigateToDestinationSelection = {
                    navController.navigate("destination_selection")
                }
            )
        }

        composable("destination_selection") {
            DestinationSelectionScreen(
                onBackClick = { navController.popBackStack() },
                onSearchClick = { navController.navigate("destination_search") },
                onCategoryClick = { key, label -> 
                    val encodedKey = if (key.isBlank()) "all" else Uri.encode(key)
                    navController.navigate("destination_category/$encodedKey/${Uri.encode(label)}") 
                },
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

        composable("destination_search") {
            DestinationSearchScreen(
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
            route = "destination_category/{categoryKey}/{categoryLabel}",
            arguments = listOf(
                navArgument("categoryKey") { type = NavType.StringType },
                navArgument("categoryLabel") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val categoryKey = backStackEntry.arguments?.getString("categoryKey").orEmpty()
            val categoryLabel = backStackEntry.arguments?.getString("categoryLabel").orEmpty()
            val effectiveKey = if (categoryKey == "all") "" else categoryKey
            
            DestinationCategoryScreen(
                categoryKey = effectiveKey,
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

        composable("logo_scan_with_dest") {
            LogoScanScreen(
                preselectedDestination = true,
                onBackFromLogo = { navController.popBackStack() },
                onStoreSelected  = { isCameraMode ->
                    NavigationState.startWithAr = isCameraMode
                    navController.navigate("navigation") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        composable("logo_scan") {
            LogoScanScreen(
                onBackFromLogo = { navController.popBackStack() },
                onStoreSelected = { isCameraMode ->
                    NavigationState.startWithAr = isCameraMode
                    navController.navigate("navigation")
                }
            )
        }
        
        composable("navigation") {
            UnifiedNavigationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("static_map") {
            StaticMapScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

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

        composable("profile") {
            ProfileScreen(
                onBackClick   = { navController.popBackStack() },
                onLogoutClick = {
                    isFirstLaunch.value = true
                    prefs.edit().putBoolean("is_first_launch", true).apply()
                    StartupCoordinator.reset()
                    navController.navigate("welcome") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

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
