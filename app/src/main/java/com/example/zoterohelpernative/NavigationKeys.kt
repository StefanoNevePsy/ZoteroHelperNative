package com.example.zoterohelpernative

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object LibraryKey : NavKey

@Serializable data class ReaderKey(val itemKey: String) : NavKey

@Serializable data object SettingsKey : NavKey
