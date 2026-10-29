package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.InternaAppShell
import com.example.ui.InternaViewModel
import com.example.ui.theme.InternaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: InternaViewModel = viewModel()
            val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()

            InternaTheme(themeMode = prefs?.themeMode ?: "DARK") {
                InternaAppShell(viewModel = viewModel)
            }
        }
    }
}
