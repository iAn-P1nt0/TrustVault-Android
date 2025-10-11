package com.trustvault.android.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trustvault.android.presentation.ui.screens.auth.MasterPasswordSetupScreen
import com.trustvault.android.presentation.ui.screens.auth.UnlockScreen
import com.trustvault.android.presentation.ui.screens.credentials.AddEditCredentialScreen
import com.trustvault.android.presentation.ui.screens.credentials.CredentialListScreen
import com.trustvault.android.presentation.ui.screens.generator.PasswordGeneratorScreen
import com.trustvault.android.presentation.ui.theme.TrustVaultTheme
import com.trustvault.android.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrustVaultTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val mainViewModel: MainViewModel = hiltViewModel()
                    val isMasterPasswordSet by mainViewModel.isMasterPasswordSet.collectAsState(initial = false)

                    val startDestination = if (isMasterPasswordSet) {
                        Screen.Unlock.route
                    } else {
                        Screen.MasterPasswordSetup.route
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
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
                                }
                            )
                        }

                        composable(Screen.PasswordGenerator.route) {
                            PasswordGeneratorScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
