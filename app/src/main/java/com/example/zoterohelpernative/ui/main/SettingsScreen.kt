package com.example.zoterohelpernative.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zoterohelpernative.ui.SettingsViewModel
import com.example.zoterohelpernative.data.Palette
import com.example.zoterohelpernative.ui.components.BackgroundCanvas
import com.example.zoterohelpernative.ui.components.GlassSurface
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color(0x1AFFFFFF),
        unfocusedContainerColor = Color(0x0DFFFFFF),
        focusedBorderColor = Color(0x8060A5FA),
        unfocusedBorderColor = Color(0x33FFFFFF),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xD9FFFFFF),
        focusedLabelColor = Color(0xFF60A5FA),
        unfocusedLabelColor = Color(0xB3FFFFFF)
    )

    val hazeState = remember { dev.chrisbanes.haze.HazeState() }

    androidx.compose.runtime.CompositionLocalProvider(
        com.example.zoterohelpernative.ui.components.LocalHazeState provides hazeState
    ) {
    Box(modifier = modifier.fillMaxSize()) {
        BackgroundCanvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState))

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Impostazioni", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF60A5FA))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Zotero API Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Zotero API", style = MaterialTheme.typography.titleMedium, color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                            
                            OutlinedTextField(
                                value = state.zoteroUserId,
                                onValueChange = viewModel::updateZoteroUserId,
                                label = { Text("Zotero User ID") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )

                            OutlinedTextField(
                                value = state.zoteroApiKey,
                                onValueChange = viewModel::updateZoteroApiKey,
                                label = { Text("Zotero API Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                        }
                    }

                    // WebDAV Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("WebDAV (Koofr)", style = MaterialTheme.typography.titleMedium, color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = state.webdavUrl,
                                onValueChange = viewModel::updateWebdavUrl,
                                label = { Text("WebDAV URL") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )

                            OutlinedTextField(
                                value = state.webdavUser,
                                onValueChange = viewModel::updateWebdavUser,
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )

                            OutlinedTextField(
                                value = state.webdavPass,
                                onValueChange = viewModel::updateWebdavPass,
                                label = { Text("Password (App Password)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                        }
                    }

                    // AI Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("AI (Gemini & NVIDIA)", style = MaterialTheme.typography.titleMedium, color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
                            Text(
                                "Abilita la chat AI sui documenti nel lettore. Gemini: API key gratuita da Google AI Studio (aistudio.google.com). NVIDIA: API key da build.nvidia.com; i modelli disponibili si aggiornano da soli nel selettore della chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xB3FFFFFF)
                            )
                            OutlinedTextField(
                                value = state.geminiApiKey,
                                onValueChange = viewModel::updateGeminiApiKey,
                                label = { Text("Gemini API Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                            OutlinedTextField(
                                value = state.nvidiaApiKey,
                                onValueChange = viewModel::updateNvidiaApiKey,
                                label = { Text("NVIDIA API Key (build.nvidia.com)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors
                            )
                        }
                    }

                    // Palettes Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Palette Personalizzate", style = MaterialTheme.typography.titleMedium, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)

                            var showCreatePaletteDialog by remember { mutableStateOf(false) }
                            var newPaletteName by remember { mutableStateOf("") }

                            if (showCreatePaletteDialog) {
                                AlertDialog(
                                    containerColor = Color(0xFF1E1E24),
                                    titleContentColor = Color.White,
                                    textContentColor = Color(0xD9FFFFFF),
                                    onDismissRequest = { showCreatePaletteDialog = false },
                                    title = { Text("Nuova Palette", fontWeight = FontWeight.Bold) },
                                    text = {
                                        OutlinedTextField(
                                            value = newPaletteName,
                                            onValueChange = { newPaletteName = it },
                                            label = { Text("Nome Palette") },
                                            singleLine = true,
                                            colors = textFieldColors
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (newPaletteName.isNotBlank()) {
                                                viewModel.createCustomPalette(newPaletteName)
                                            }
                                            showCreatePaletteDialog = false
                                        }) {
                                            Text("Crea", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCreatePaletteDialog = false }) {
                                            Text("Annulla", color = Color(0xB3FFFFFF))
                                        }
                                    }
                                )
                            }

                            Button(
                                onClick = {
                                    newPaletteName = ""
                                    showCreatePaletteDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA), contentColor = Color.Black)
                            ) {
                                Text("Crea Nuova Palette", fontWeight = FontWeight.Bold)
                            }   

                            state.customPalettes.values.forEach { palette ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    color = Color(0x1AFFFFFF),
                                    shape = MaterialTheme.shapes.medium,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(palette.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            IconButton(onClick = { viewModel.deleteCustomPalette(palette.id) }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Elimina", tint = Color(0xFFFCA5A5))
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        palette.colors.forEach { mappedColor ->
                                            var showColorEdit by remember { mutableStateOf(false) }
                                            var editHex by remember { mutableStateOf(mappedColor.uiHex) }
                                            
                                            if (showColorEdit) {
                                                AlertDialog(
                                                    containerColor = Color(0xFF1E1E24),
                                                    titleContentColor = Color.White,
                                                    textContentColor = Color(0xD9FFFFFF),
                                                    onDismissRequest = { showColorEdit = false },
                                                    title = { Text("Modifica Colore", fontWeight = FontWeight.Bold) },
                                                    text = {
                                                        OutlinedTextField(
                                                            value = editHex,
                                                            onValueChange = { editHex = it },
                                                            label = { Text("Hex (es. #FF0000)") },
                                                            singleLine = true,
                                                            colors = textFieldColors
                                                        )
                                                    },
                                                    confirmButton = {
                                                        TextButton(onClick = {
                                                            viewModel.updateCustomPaletteColor(palette.id, mappedColor.zoteroHex, editHex)
                                                            showColorEdit = false
                                                        }) {
                                                            Text("Salva", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showColorEdit = false }) {
                                                            Text("Annulla", color = Color(0xB3FFFFFF))
                                                        }
                                                    }
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(mappedColor.name, style = MaterialTheme.typography.bodyMedium, color = Color(0xD9FFFFFF))
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            try {
                                                                Color(android.graphics.Color.parseColor(mappedColor.uiHex))
                                                            } catch(e: Exception) { Color.Gray }
                                                        )
                                                        .clickable {
                                                            editHex = mappedColor.uiHex
                                                            showColorEdit = true
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Input Devices Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Dispositivi di Input", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFACC15), fontWeight = FontWeight.Bold)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Usa solo dito per selezione", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Disabilita la selezione testo con lo stilo.", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = state.fingerSelectionOnly,
                                    onCheckedChange = viewModel::updateFingerSelectionOnly,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFACC15), checkedTrackColor = Color(0x4DFACC15))
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Usa solo penna per evidenziare", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Disabilita l'evidenziazione con il dito. Il dito servirà per scorrere la pagina.", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = state.penHighlightOnly,
                                    onCheckedChange = viewModel::updatePenHighlightOnly,
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFACC15), checkedTrackColor = Color(0x4DFACC15))
                                )
                            }
                        }
                    }

                    // Icon Personalization Section
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x1A000000)
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Personalizzazione Icone", style = MaterialTheme.typography.titleMedium, color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
                            
                            val toolsToConfigure = listOf(
                                "tool_highlighter" to "Evidenziatore",
                                "tool_pen" to "Penna",
                                "tool_eraser" to "Gomma",
                                "tool_palette" to "Palette Colori",
                                "tool_undo" to "Annulla",
                                "tool_square" to "Forma rettangolo",
                                "tool_settings" to "Impostazioni",
                                "tool_menu" to "Menu Rapido",
                                "tool_zoom_in" to "Zoom In",
                                "tool_zoom_out" to "Zoom Out"
                            )

                            var selectedToolKey by remember { mutableStateOf<String?>(null) }
                            
                            if (selectedToolKey != null) {
                                AlertDialog(
                                    containerColor = Color(0xFF1E1E24),
                                    titleContentColor = Color.White,
                                    textContentColor = Color(0xD9FFFFFF),
                                    onDismissRequest = { selectedToolKey = null },
                                    title = { Text("Scegli un'icona", fontWeight = FontWeight.Bold) },
                                    text = {
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(48.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.height(300.dp)
                                        ) {
                                            items(com.example.zoterohelpernative.ui.icons.IconResolver.allIcons.keys.toList()) { iconName ->
                                                val vector = com.example.zoterohelpernative.ui.icons.IconResolver.allIcons[iconName]!!
                                                val isSelected = state.toolIcons[selectedToolKey] == iconName
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(if (isSelected) Color(0x4DC084FC) else Color.Transparent)
                                                        .clickable {
                                                            viewModel.updateToolIcon(selectedToolKey!!, iconName)
                                                            selectedToolKey = null
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = vector,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Color(0xFFC084FC) else Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { selectedToolKey = null }) {
                                            Text("Chiudi", color = Color(0xB3FFFFFF))
                                        }
                                    }
                                )
                            }

                            toolsToConfigure.forEach { (key, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable { selectedToolKey = key },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    val currentIconName = state.toolIcons[key]
                                    val currentIcon = com.example.zoterohelpernative.ui.icons.IconResolver.resolve(currentIconName, Icons.Outlined.Settings)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x1AFFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = currentIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.saveSettings {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399), contentColor = Color.Black)
                    ) {
                        Text("Salva Credenziali", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    }
}
