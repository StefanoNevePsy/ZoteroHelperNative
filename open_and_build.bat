@echo off
title Zotero Helper Native - Build & Launch

echo ===================================================
echo   Zotero Helper Native - Build & Launch Script
echo ===================================================
echo.

echo [1/3] Pulizia e Preparazione del progetto...
call gradlew clean
if %ERRORLEVEL% neq 0 (
    echo [ERRORE] La pulizia del progetto (clean) e' fallita!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Compilazione in corso (assembleDebug)...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERRORE] La compilazione (build) e' fallita! Controlla i log qui sopra.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Build completata con successo. Avvio di Android Studio...

:: Controlla il percorso standard di installazione di Android Studio su Windows
set STUDIO_PATH="C:\Program Files\Android\Android Studio\bin\studio64.exe"

if exist %STUDIO_PATH% (
    start "" %STUDIO_PATH% "%~dp0"
) else (
    echo.
    echo [ATTENZIONE] Android Studio non e' stato trovato nel percorso standard 
    echo (%STUDIO_PATH%).
    echo Sto aprendo la cartella del progetto, trascinala dentro Android Studio!
    start "" "%~dp0"
)

echo.
echo Finito! Puoi chiudere questa finestra.
timeout /t 5 >nul
