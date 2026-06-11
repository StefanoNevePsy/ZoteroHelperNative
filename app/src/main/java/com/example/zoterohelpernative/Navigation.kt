package com.example.zoterohelpernative

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.zoterohelpernative.data.SettingsRepository
import com.example.zoterohelpernative.ui.LibraryViewModel
import com.example.zoterohelpernative.ui.ReaderViewModel
import com.example.zoterohelpernative.data.local.ZoteroDatabase
import com.example.zoterohelpernative.data.sync.ZoteroRepository
import androidx.room.Room
import com.example.zoterohelpernative.ui.main.LibraryScreen
import com.example.zoterohelpernative.ui.main.ReaderScreen
import com.example.zoterohelpernative.ui.main.SettingsScreen
import com.example.zoterohelpernative.ui.SettingsViewModel

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(LibraryKey)
  val context = LocalContext.current

  // Manual instantiation for MVP (Hilt/Dagger would be better)
  val settingsRepository = remember { SettingsRepository(context) }
  
  // Database & Repository
  val database = remember {
      Room.databaseBuilder(
          context.applicationContext,
          ZoteroDatabase::class.java, "zotero-db"
      ).build()
  }
  val zoteroRepository = remember { ZoteroRepository(database, settingsRepository) }

  val libraryViewModel = remember { LibraryViewModel(settingsRepository, zoteroRepository) }
  val readerViewModel = remember { ReaderViewModel(settingsRepository) } // Later we can pass repo here too
  val settingsViewModel = remember { SettingsViewModel(settingsRepository) }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<LibraryKey> {
          LibraryScreen(
            viewModel = libraryViewModel,
            onNavigateToReader = { itemKey -> backStack.add(ReaderKey(itemKey)) },
            onNavigateToSettings = { backStack.add(SettingsKey) },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<ReaderKey> { readerKey ->
          ReaderScreen(
            itemKey = readerKey.itemKey,
            viewModel = readerViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<SettingsKey> {
          SettingsScreen(
            viewModel = settingsViewModel,
            onNavigateBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
