package com.trustvault.android.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trustvault.android.presentation.ui.screens.auth.MasterPasswordSetupScreen
import com.trustvault.android.presentation.ui.screens.auth.UnlockScreen
import com.trustvault.android.presentation.ui.screens.credentials.AddEditCredentialScreen
import com.trustvault.android.presentation.ui.screens.credentials.CredentialListScreen
import com.trustvault.android.presentation.ui.screens.generator.PasswordGeneratorScreen
import com.trustvault.android.presentation.ui.screens.ocr.OcrCaptureScreen
import com.trustvault.android.presentation.ui.screens.settings.SettingsScreen
import com.trustvault.android.presentation.ui.theme.TrustVaultTheme
import com.trustvault.android.presentation.viewmodel.AddEditCredentialViewModel
import com.trustvault.android.presentation.viewmodel.MainViewModel
import com.trustvault.android.security.AutoLockManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var autoLockManager: AutoLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for Android 15 (API 35) compatibility
        enableEdgeToEdge()

        setContent {
            TrustVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = hiltViewModel()
                    // Wait for the Flow to emit value - don't use initial = false
                    // This prevents showing Setup screen when preferences are still loading
                    val isMasterPasswordSet by mainViewModel.isMasterPasswordSet.collectAsState(initial = null)
                    val currentBackStack by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStack?.destination?.route

                    // Track if user is authenticated (not on setup or unlock screens)
                    var isAuthenticated by remember { mutableStateOf(false) }

                    // Update authentication state based on current route
                    LaunchedEffect(currentRoute) {
                        isAuthenticated = when (currentRoute) {
                            Screen.MasterPasswordSetup.route, Screen.Unlock.route -> false
                            else -> true
                        }
                    }

                    // Record activity when user interacts with authenticated screens
                    LaunchedEffect(isAuthenticated, currentRoute) {
                        if (isAuthenticated) {
                            autoLockManager.recordActivity()
                        }
                    }

                    // Monitor database lock state and navigate to unlock screen if locked
                    LaunchedEffect(isAuthenticated) {
                        if (isAuthenticated && !mainViewModel.isDatabaseUnlocked()) {
                            // Database was locked (by AutoLockManager or manual lock)
                            // Navigate back to unlock screen
                            navController.navigate(Screen.Unlock.route) {
                                // Clear back stack to prevent going back to authenticated screens
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    // Determine start destination
                    // If preferences haven't loaded yet (null), show loading screen
                    val startDestination = when (isMasterPasswordSet) {
                        true -> Screen.Unlock.route
                        false -> Screen.MasterPasswordSetup.route
                        null -> Screen.LoadingScreen.route  // Wait for preferences to load
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.LoadingScreen.route) {
                            // Show loading while preferences are being loaded
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            // Auto-navigate once preferences are loaded
                            LaunchedEffect(isMasterPasswordSet) {
                                if (isMasterPasswordSet != null) {
                                    val destination = if (isMasterPasswordSet == true) {
                                        Screen.Unlock.route
                                    } else {
                                        Screen.MasterPasswordSetup.route
                                    }
                                    navController.navigate(destination) {
                                        popUpTo(Screen.LoadingScreen.route) { inclusive = true }
                                    }
                                }
                            }
                        }

                        composable(Screen.MasterPasswordSetup.route) {
                            MasterPasswordSetupScreen(
                                onPasswordCreated = {
                                    navController.navigate(Screen.CredentialList.route) {
                                        popUpTo(Screen.MasterPasswordSetup.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Unlock.route) {
                            UnlockScreen(
                                onUnlocked = {
                                    navController.navigate(Screen.CredentialList.route) {
                                        popUpTo(Screen.Unlock.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.CredentialList.route) {
                            CredentialListScreen(
                                onAddCredential = {
                                    navController.navigate(Screen.AddEditCredential.createRoute())
                                },
                                onCredentialClick = { id ->
                                    navController.navigate(Screen.AddEditCredential.createRoute(id))
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onLockApp = {
                                    mainViewModel.lockDatabase()
                                    navController.navigate(Screen.Unlock.route) {
                                        popUpTo(Screen.CredentialList.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = Screen.AddEditCredential.route,
                            arguments = listOf(
                                navArgument("credentialId") {
                                    type = NavType.StringType
                                    nullable = true
                                }
                            )
                        ) {
                            AddEditCredentialScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToGenerator = {
                                    navController.navigate(Screen.PasswordGenerator.route)
                                },
                                onNavigateToOcrCapture = {
                                    navController.navigate(Screen.OcrCapture.route)
                                }
                            )
                        }

                        composable(Screen.PasswordGenerator.route) {
                            PasswordGeneratorScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.OcrCapture.route) {
                            val parentEntry = remember(it) {
                                navController.getBackStackEntry(Screen.AddEditCredential.route)
                            }
                            val parentViewModel: AddEditCredentialViewModel = hiltViewModel(parentEntry)

                            OcrCaptureScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onCredentialsExtracted = { ocrResult ->
                                    parentViewModel.populateFromOcrResult(ocrResult)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
