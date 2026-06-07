$src = "C:\Users\Joaov\Downloads\modulos do aplicativo\sistema monolitico"
$indexFile = Join-Path $src "index.html"

$map = @{
    'icone.txt' = 'icone.png'
    'icone2.txt' = 'icone2.png'
    'icone3.txt' = 'icone3.png'
    'icone4.txt' = 'icone4.png'
    'icone5.txt' = 'icone5.png'
    'icone6.txt' = 'icone6.png'
    'sound1.txt' = 'sound1.mp3'
    'sound2.txt' = 'sound2.mp3'
    'sound3.txt' = 'sound3.mp3'
    'sound4.txt' = 'sound4.mp3'
    'sound5.txt' = 'sound5.mp3'
    'sound6.txt' = 'sound6.mp3'
    'sound7.txt' = 'sound7.mp3'
    'sound8.txt' = 'sound8.mp3'
    'sound9.txt' = 'sound9.mp3'
    'sound10.txt' = 'sound10.mp3'
    'sound11.txt' = 'sound11.mp3'
    'softclick.txt' = 'softclick.mp3'
    'hardclick.txt' = 'hardclick.mp3'
    'returnbell.txt' = 'returnbell.mp3'
    'oldtelephone.txt' = 'oldtelephone.mp3'
    'cassetsound.txt' = 'cassetsound.mp3'
    'livro1.txt' = 'livro1.txt'
    'livro2.txt' = 'livro2.txt'
    'livro3.txt' = 'livro3.txt'
    'livro4.txt' = 'livro4.txt'
    'livro5.txt' = 'livro5.txt'
    'livro6.txt' = 'livro6.txt'
    'livro7.txt' = 'livro7.txt'
    'livro8.txt' = 'livro8.txt'
    'livro9.txt' = 'livro9.txt'
    'livro10.txt' = 'livro10.txt'
}

function Extract-Base64 {
    param([string]$content)
    # Remove ALL whitespace/control chars (\s in .NET = space, tab, \n, \r, \f, \v)
    $flat = $content -replace '\s', ''
    
    # Case 1: HTML audio/img tag wrapper
    if ($flat -match 'src="data:[^;]+;base64,([^"]+)"') {
        return $matches[1]
    }
    
    # Case 2: Data URI prefix (data:audio/mpeg;base64,... or data:image/png;base64,...)
    if ($flat -match '^data:[^;]+;base64,([A-Za-z0-9+/=]+)$') {
        return $matches[1]
    }
    
    # Case 3: Pure base64
    if ($flat -match '^[A-Za-z0-9+/=]+$') {
        return $flat
    }
    
    # Fallback: raw content (not base64) - base64-encode it for storage
    # This preserves the original content in the ASSETS object
    $rawBytes = [System.Text.Encoding]::UTF8.GetBytes($content)
    $rawB64 = [Convert]::ToBase64String($rawBytes)
    Write-Host "AVISO: $($kv.Name) - nao e base64, codificado ($($rawB64.Length) chars)" -ForegroundColor Yellow
    return $rawB64
}

Write-Host "Lendo arquivos e montando objeto ASSETS..." -ForegroundColor Cyan

$sb = New-Object System.Text.StringBuilder
$null = $sb.AppendLine("const ASSETS = {")
$first = $true
$totalLength = 0
foreach ($kv in $map.GetEnumerator() | Sort-Object Name) {
    $txtFile = Join-Path $src $kv.Name
    $assetKey = $kv.Value
    if (-not (Test-Path $txtFile)) {
        Write-Host "AVISO: $($kv.Name) nao encontrado!" -ForegroundColor Yellow
        continue
    }
    $raw = [System.IO.File]::ReadAllText($txtFile)
    $b64 = Extract-Base64 -content $raw
    
    if (-not $first) { $null = $sb.AppendLine(",") }
    $null = $sb.Append("  '$assetKey': `"$b64`"")
    $first = $false
    $totalLength += $b64.Length
    Write-Host "  + $($kv.Name) -> $assetKey ($($b64.Length) chars)" -ForegroundColor Green
}
$null = $sb.AppendLine("")
$null = $sb.AppendLine("};")
$jsContent = $sb.ToString()

$totalMB = [math]::Round($totalLength / 1MB, 2)
Write-Host "Total base64 data: $totalLength chars ($totalMB MB)" -ForegroundColor Cyan

Write-Host "Lendo index.html..." -ForegroundColor Cyan
$html = [System.IO.File]::ReadAllText($indexFile)

# Find and replace the existing ASSETS block (or the placeholder)
$startMarker = "const ASSETS = {"
$endMarker = "};"

$startIdx = $html.IndexOf($startMarker)
if ($startIdx -lt 0) {
    Write-Host "ERRO: '$startMarker' nao encontrado no index.html!" -ForegroundColor Red
    exit 1
}

# Find the matching end marker (first standalone }; after the start)
$searchFrom = $startIdx + $startMarker.Length
$endIdx = $html.IndexOf("`n" + $endMarker, $searchFrom)
if ($endIdx -lt 0) {
    $endIdx = $html.IndexOf($endMarker, $searchFrom)
}
if ($endIdx -lt 0) {
    Write-Host "ERRO: '$endMarker' nao encontrado depois do ASSETS!" -ForegroundColor Red
    exit 1
}

$endIdx = $endIdx + $endMarker.Length  # include the }; in the match

$before = $html.Substring(0, $startIdx)
$after = $html.Substring($endIdx)

$newHtml = $before + $jsContent + $after

[System.IO.File]::WriteAllText($indexFile, $newHtml)

Write-Host ""
Write-Host "SUCESSO! Assets incorporados no index.html ($totalMB MB)" -ForegroundColor Green
