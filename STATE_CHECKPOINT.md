# ZoteroHelperNative - State Checkpoint

*Data dell'ultimo aggiornamento: 1 Giugno 2026*

## Obiettivo Globale
Porting dell'app web (React/Capacitor) `Zotero Helper` in una **App Android Nativa** (Kotlin + Jetpack Compose) per risolvere in modo definitivo i problemi di precisione delle evidenziazioni nei PDF multi-colonna.

## Architettura del Progetto
- **Path Progetto**: `c:\Users\neves\Desktop\Personale\Apps\ZoteroHelperNative`
- **UI/UX**: Jetpack Compose con tema Dark-Mode nativo (Stylus-first) ispirato alle linee guida *Impeccable*. (File di tema completati in `ui/theme/`).
- **Data Layer (Zotero Sync)**: 
  - `ZoteroModels.kt` e `ZoteroApiService.kt`: Mappatura DTO e chiamate API Retrofit al server Zotero v3.
  - `SettingsRepository.kt`: Gestione delle credenziali e preferenze usando `DataStore` (sostituisce idb-keyval/Zustand persist).
  - `WebDavClient.kt`: Fetching degli allegati PDF da WebDAV.
- **Annotazioni PDF**:
  - `CoordinateConverter.kt`: Sistema che mappa il box `[x1, y1, x2, y2]` estratto dall'API Zotero nei pixel del Canvas Android (DPI-aware).
  - `PdfPageView.kt`: Rendering dell'immagine (bitmap estratta da MuPDF) + un livello `Canvas` Jetpack Compose per disegnare evidenziazioni custom.
  - Libreria PDF: **MuPDF** (`com.artifex.mupdf:fitz:1.23.0`).

## Componenti UI Sviluppati
- `RadialMenu.kt`: Menu radiale contestuale, animato con `spring` physics.
- `Type.kt`, `Theme.kt`, `Color.kt`: Base solida.

## Stato Attuale del Porting (Task Completati)
- Inizializzazione Gradle e rimozione Room (uso DataStore).
- `ReaderViewModel.kt`, `LibraryViewModel.kt` e `SettingsViewModel.kt` implementati.
- `SettingsScreen.kt`: Modale per l'inserimento credenziali Zotero API e WebDAV.
- `LibraryScreen.kt`: Browser collezioni implementato.
- `ReaderScreen.kt`: Master layout con Canvas PDF, Menu Radiale e Sidebar connessi.

## Prossimi Passi Necessari
1. **Completamento MuPDF Canvas**: Legare il touch-drag del Canvas in `PdfPageView` alla creazione di una vera annotazione e al suo caricamento su server.
2. Sincronizzazione background reale (WorkManager).

*Se riapri una nuova chat con questo file come prompt, l'IA saprà esattamente a che punto siamo.*
