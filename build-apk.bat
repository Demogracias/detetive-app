@echo off
title Detetive - Build APK
echo ============================================
echo  Detetive - Build Android APK (RELEASE)
echo ============================================
echo.
echo A compilar APK assinado... (pode demorar alguns minutos)
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0build-android.ps1"
echo.
echo Se o build foi bem-sucedido, o ficheiro Detetive.apk
echo esta na pasta "ver final".
echo.
pause
