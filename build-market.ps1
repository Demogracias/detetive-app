param([switch]$skipCopy)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSCommandPath

$env:ANDROID_HOME   = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME      = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_OPTS    = "-Xmx512m"

# ─── Backup original app.html ─────────────────────────────────────────────
Write-Host ">> Backup do app.html original ..." -ForegroundColor Cyan
$originalPath = Join-Path $root "app.html"
$backupPath = Join-Path $root "app.html.original"
Copy-Item $originalPath $backupPath -Force
Write-Host "   OK" -ForegroundColor Green

# ─── Minify app.html ──────────────────────────────────────────────────────
Write-Host ">> Minificando JS com terser (ofuscacao) ..." -ForegroundColor Cyan
Push-Location $root
node minify-html.js "app.html" 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location
Write-Host "   OK" -ForegroundColor Green

# ─── Copy web files → www/ ────────────────────────────────────────────────
if (-not $skipCopy) {
    Write-Host ">> Copiando web files para www/ ..." -ForegroundColor Cyan
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
    Move-Item (Join-Path $www "app.html") (Join-Path $www "index.html") -Force
    Write-Host "   OK" -ForegroundColor Green
}

# ─── Sync Capacitor ────────────────────────────────────────────────────────
Write-Host ">> npx cap sync android ..." -ForegroundColor Cyan
Push-Location $root
npx.cmd cap sync android 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Build Market APK ──────────────────────────────────────────────────────
Write-Host ">> Compilando APK Market (assembleMarket) ..." -ForegroundColor Cyan
Push-Location (Join-Path $root "android")
cmd /c gradlew.bat assembleMarket --no-daemon 2>&1 | ForEach-Object { Write-Host $_ }
Pop-Location

# ─── Restore original app.html ─────────────────────────────────────────────
Write-Host ">> Restaurando app.html original ..." -ForegroundColor Cyan
if (Test-Path $backupPath) {
    Copy-Item $backupPath $originalPath -Force
    Remove-Item $backupPath -Force
    Write-Host "   OK" -ForegroundColor Green
}

# ─── Sync keystore ─────────────────────────────────────────────────────────
$ksSrc = Join-Path $root "detetive-release.jks"
$ksDst = Join-Path $root "android\detetive-release.jks"
if (Test-Path $ksSrc) { Copy-Item $ksSrc $ksDst -Force }

# ─── Copy APK ──────────────────────────────────────────────────────────────
$apk = Get-ChildItem (Join-Path $root "android\app\build\outputs\apk\market") -Filter "*.apk" | Select-Object -First 1
if ($apk) {
    $ts = Get-Date -Format "yyyy-MM-dd_HHmmss"
    $dest = Join-Path $root "Detetive_Market_$ts.apk"
    Copy-Item $apk.FullName $dest -Force
    $backupDir = Join-Path $root "backups"
    if (!(Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir | Out-Null }
    Copy-Item $apk.FullName (Join-Path $backupDir "Detetive_market_$ts.apk") -Force
    Write-Host "`n================================================" -ForegroundColor Green
    Write-Host " APK MARKET: $dest" -ForegroundColor Green
    Write-Host " Tamanho: $([math]::Round((Get-Item $dest).Length/1MB,1)) MB" -ForegroundColor Green
    Write-Host " ProGuard: ativado (R8 minify + obfuscation)" -ForegroundColor Green
    Write-Host " JS: minificado com terser" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
} else {
    Write-Host "`nERRO: APK Market nao encontrado!" -ForegroundColor Red
}
