@echo off
cd /d "%~dp0"
set ANDROID_HOME=C:\Users\Joaov\AppData\Local\Android\Sdk
set ANDROID_SDK_ROOT=C:\Users\Joaov\AppData\Local\Android\Sdk
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

echo ANDROID_HOME=%ANDROID_HOME%
echo JAVA_HOME=%JAVA_HOME%
java -version 2>&1

echo.
echo ===== Building APK =====
cd android
call gradlew.bat assembleDebug
if %ERRORLEVEL% == 0 (
    echo.
    echo ===== BUILD SUCCESSFUL =====
    dir /b app\build\outputs\apk\debug\*.apk 2>nul
) else (
    echo.
    echo ===== BUILD FAILED (error level %ERRORLEVEL%) =====
)
pause
