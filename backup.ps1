# Backup all current APKs with timestamps
$root = Split-Path -Parent $PSCommandPath
$backupDir = Join-Path $root "backups"
if (!(Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir | Out-Null }

$ts = Get-Date -Format "yyyy-MM-dd_HHmmss"
$apks = @(
    @{src="Detetive.apk"; name="Detetive_normal_$ts.apk"},
    @{src="Detetive_Market_*.apk"; name=$null}
)

# Backup Detetive.apk (normal build)
$srcNormal = Join-Path $root "Detetive.apk"
if (Test-Path $srcNormal) {
    $dstNormal = Join-Path $backupDir "Detetive_normal_$ts.apk"
    Copy-Item $srcNormal $dstNormal -Force
    Write-Host "Backup: $dstNormal" -ForegroundColor Green
} else {
    Write-Host "Aviso: Detetive.apk n�o encontrado" -ForegroundColor Yellow
}

# Backup latest market build
$marketFiles = Get-ChildItem (Join-Path $root "Detetive_Market_*.apk") | Sort-Object LastWriteTime -Descending
if ($marketFiles) {
    $latestMarket = $marketFiles | Select-Object -First 1
    $dstMarket = Join-Path $backupDir "Detetive_market_$ts.apk"
    Copy-Item $latestMarket.FullName $dstMarket -Force
    Write-Host "Backup: $dstMarket" -ForegroundColor Green
} else {
    Write-Host "Aviso: Nenhum APK Market encontrado" -ForegroundColor Yellow
}

Write-Host "`nBackup conclu�do em: $backupDir" -ForegroundColor Cyan
