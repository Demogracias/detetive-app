param([switch]$skipCopy)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSCommandPath

# ─── Version ──────────────────────────────────────────────────────────────
$versionFile = Join-Path $root "VERSION"
$version = "1.0"
if (Test-Path $versionFile) { $version = (Get-Content $versionFile -Raw).Trim() }

# Increment minor version
$parts = $version.Split('.')
$major = [int]$parts[0]
$minor = [int]$parts[1]
$minor++
$newVersion = "$major.$minor"
[System.IO.File]::WriteAllText($versionFile, $newVersion, [System.Text.UTF8Encoding]::new($false))

# Update Android versionName in build.gradle
$gradleFile = Join-Path $root "android\app\build.gradle"
$gradleContent = [System.IO.File]::ReadAllText($gradleFile) -replace 'versionName "[\d.]+"', "versionName `"$newVersion.0`""
[System.IO.File]::WriteAllText($gradleFile, $gradleContent, [System.Text.UTF8Encoding]::new($false))

Write-Host ">>> BUILD: Detetive$newVersion" -ForegroundColor Cyan

# ─── Android SDK + Java ───────────────────────────────────────────────────
$env:ANDROID_HOME   = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME      = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_OPTS    = "-Xmx512m"

# ─── Copy web files → www/ ────────────────────────────────────────────────
if (-not $skipCopy) {
    Write-Host ">> Copiando ficheiros web para www/ ..." -ForegroundColor Cyan
    $www = Join-Path $root "www"
    if (Test-Path $www) { Remove-Item -Recurse -Force $www }
    New-Item -ItemType Directory -Path $www -Force | Out-Null

    $webFiles = @(
        "app.html", "basedetetive.html", "tailwind.css", "assets.b64.js", "cdn-assets.js",
        "manifest.json", "sw.js", "icon.svg", "jumpscare.txt",
        "iconedoapp_192.png", "iconedoapp_512.png",
        "assets-classicas.js", "vocab-data.js", "lovecraft-texts.js"
    )
    foreach ($f in $webFiles) {
        $src = Join-Path $root $f
        if (Test-Path $src) { Copy-Item $src (Join-Path $www $f) }
    }
    Get-ChildItem (Join-Path $root "livro*.txt") -ErrorAction SilentlyContinue | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $www $_.Name)
    }
    $enemSrc = Join-Path $root "enem"
    $enemDst = Join-Path $www "enem"
    if (Test-Path $enemSrc) {
        if (!(Test-Path $enemDst)) { New-Item -ItemType Directory -Path $enemDst | Out-Null }
        Copy-Item -Recurse "$enemSrc\*" $enemDst
    }
    $modSrc = Join-Path $root "modulosfitas"
    $modDst = Join-Path $www "modulosfitas"
    if (Test-Path $modSrc) { Copy-Item -Recurse $modSrc $modDst }

    $appHtmlDst = Join-Path $www "app.html"
    if (Test-Path $appHtmlDst) { Move-Item $appHtmlDst (Join-Path $www "index.html") -Force }
    Write-Host "   OK" -ForegroundColor Green
}

# ─── Sync Capacitor ────────────────────────────────────────────────────────
Write-Host ">> npx cap sync android ..." -ForegroundColor Cyan
Push-Location $root
npx.cmd cap sync android 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Build APK ─────────────────────────────────────────────────────────────
Write-Host ">> Compilando APK (assembleDebug) ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "android")
cmd /c gradlew.bat assembleDebug --no-daemon 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Locate and copy APK with version ──────────────────────────────────────
$apk = Get-ChildItem (Join-Path $root "android\app\build\outputs\apk\debug") -Filter "*.apk" | Select-Object -First 1
if ($apk) {
    $versionedName = "Detetive$newVersion.apk"
    $dest = Join-Path $root $versionedName
    Copy-Item $apk.FullName $dest -Force
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host " APK: $versionedName ($([math]::Round((Get-Item $dest).Length/1MB,1)) MB)" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green

    # Keep last 2 APKs, delete older ones
    $apks = @(Get-ChildItem (Join-Path $root "Detetive*.apk") | Sort-Object LastWriteTime -Descending)
    if ($apks.Length -gt 2) {
        for ($i = 2; $i -lt $apks.Length; $i++) {
            Remove-Item $apks[$i].FullName -Force
            Write-Host "   (removido antigo: $($apks[$i].Name))" -ForegroundColor DarkYellow
        }
    }
} else {
    Write-Host "`nERRO: APK nao encontrado!" -ForegroundColor Red
}
