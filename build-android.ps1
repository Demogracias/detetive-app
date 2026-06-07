param([switch]$skipCopy)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSCommandPath

# ─── Android SDK + Java ───────────────────────────────────────────────────
$env:ANDROID_HOME   = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME      = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_OPTS    = "-Xmx512m"

# ─── Copy web files → www/ (if needed) ────────────────────────────────────
if (-not $skipCopy) {
    Write-Host ">> Copiando ficheiros web para www/ ..." -ForegroundColor Cyan
    $www = Join-Path $root "www"

    if (Test-Path $www) { Remove-Item -Recurse -Force $www }
    New-Item -ItemType Directory -Path $www | Out-Null

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
    # Copy all livro*.txt files for the library
    Get-ChildItem (Join-Path $root "livro*.txt") -ErrorAction SilentlyContinue | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $www $_.Name)
    }
    # Copy ENEM data and images
    $enemSrc = Join-Path $root "enem"
    $enemDst = Join-Path $www "enem"
    if (Test-Path $enemSrc) {
        if (!(Test-Path $enemDst)) { New-Item -ItemType Directory -Path $enemDst | Out-Null }
        Copy-Item -Recurse "$enemSrc\*" $enemDst
    }

    $modSrc = Join-Path $root "modulosfitas"
    $modDst = Join-Path $www "modulosfitas"
    if (Test-Path $modSrc) { Copy-Item -Recurse $modSrc $modDst }

    # Use app.html as the main index (canonical file with full features)
    Move-Item (Join-Path $www "app.html") (Join-Path $www "index.html") -Force
    Write-Host "   OK" -ForegroundColor Green
}

# ─── Sync Capacitor ────────────────────────────────────────────────────────
Write-Host ">> npx cap sync android ..." -ForegroundColor Cyan
Push-Location $root
npx.cmd cap sync android 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Build APK ─────────────────────────────────────────────────────────────
Write-Host ">> Compilando APK (assembleRelease assinado) ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "android")
cmd /c gradlew.bat assembleRelease --no-daemon 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Sync keystore to android dir ──────────────────────────────────────────
$ksSrc = Join-Path $root "detetive-release.jks"
$ksDst = Join-Path $root "android\detetive-release.jks"
if (Test-Path $ksSrc) { Copy-Item $ksSrc $ksDst -Force }

# ─── Locate and copy APK ──────────────────────────────────────────────────
$apk = Get-ChildItem (Join-Path $root "android\app\build\outputs\apk\release") -Filter "*.apk" | Select-Object -First 1
if ($apk) {
    $dest = Join-Path $root "Detetive.apk"
    Copy-Item $apk.FullName $dest -Force
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host " APK gerado: $dest" -ForegroundColor Green
    Write-Host " Tamanho: $([math]::Round((Get-Item $dest).Length/1MB,1)) MB" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
} else {
    Write-Host "`nERRO: APK nao encontrado!" -ForegroundColor Red
}
