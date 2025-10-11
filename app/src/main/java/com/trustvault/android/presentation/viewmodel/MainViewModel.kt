package com.trustvault.android.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.trustvault.android.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    val isMasterPasswordSet: Flow<Boolean> = preferencesManager.isMasterPasswordSet
}
