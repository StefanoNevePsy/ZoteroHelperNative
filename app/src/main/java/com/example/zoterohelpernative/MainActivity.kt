package com.example.zoterohelpernative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.zoterohelpernative.theme.ZoteroHelperNativeTheme
import com.example.zoterohelpernative.theme.AppTheme
import com.example.zoterohelpernative.data.SettingsRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    val settingsRepository = SettingsRepository(applicationContext)
    // Read the saved theme before the first frame so the app never flashes the
    // wrong appearance; DataStore's first read is a small local file.
    val initialTheme = runBlocking { settingsRepository.appTheme.first() }

    setContent {
      val themeId by settingsRepository.appTheme.collectAsState(initial = initialTheme)
      ZoteroHelperNativeTheme(theme = AppTheme.fromId(themeId)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() }
      }
    }
  }
}
