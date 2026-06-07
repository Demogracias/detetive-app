# PowerShell script for automatic backup of app.html
$source = "C:\Users\Joaov\Desktop\ver final\app.html"
$backupDir = "C:\Users\Joaov\Desktop\ver final\backups"

# Create backup directory if it doesn't exist
if (-not (Test-Path -LiteralPath $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}

# Generate timestamp
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path -Path $backupDir -ChildPath "app_$timestamp.html"

# Copy the file
Copy-Item -LiteralPath $source -Destination $backupFile -Force

Write-Output "Backup created: $backupFile"

# Keep only last 20 backups (delete oldest)
$backups = Get-ChildItem -LiteralPath $backupDir -Filter "app_*.html" | Sort-Object Name -Descending
if ($backups.Count -gt 20) {
    $backups | Select-Object -Skip 20 | Remove-Item -Force
    Write-Output "Cleaned up old backups (kept 20 most recent)"
}
