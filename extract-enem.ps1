<#
.SYNOPSIS
    Extrator automático de questões ENEM usando enem-extractor
.DESCRIPTION
    Executa o enem-extractor (Python) em PDFs do ENEM para extrair
    questões, imagens e gabaritos. Gera dados no formato compatível
    com a aba ACADEMIA do app Detetive.

    Baseado em: https://github.com/diaslui/enem-extractor

    Referência científica:
    O enem-extractor usa reconhecimento de layout de PDF para estruturar
    questões — técnica relacionada à extração de conhecimento de documentos
    acadêmicos (Hassan & Baumgartner, 2011; lin et al., 2019).

.PARAMETER PastaPDF
    Pasta contendo os PDFs do ENEM (formato: aplicacao.pdf, gabarito.pdf)
.PARAMETER Ano
    Ano da prova (ex: 2024)
.PARAMETER Dia
    Dia da prova (1 ou 2)
.PARAMETER Output
    Pasta de saída para os dados extraídos (opcional)

.EXAMPLE
    .\extract-enem.ps1 -PastaPDF ".\pdfs\2024" -Ano 2024 -Dia 1

.EXAMPLE
    .\extract-enem.ps1 -PastaPDF "C:\Downloads\ENEM2024" -Ano 2024 -Dia 2

.NOTES
    Requer Python 3.6+ e o pacote 'enem' instalado (pip install enem)
    Para instalar localmente: pip install -e .\enem-1.0.4\
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$PastaPDF,
    [Parameter(Mandatory=$true)]
    [int]$Ano,
    [Parameter(Mandatory=$true)]
    [ValidateSet(1, 2)]
    [int]$Dia,
    [string]$Output = ""
)

# Cores
$VERMELHO = "`e[31m"
$VERDE = "`e[32m"
$AMARELO = "`e[33m"
$AZUL = "`e[34m"
$RESET = "`e[0m"

function Write-Step($msg) { Write-Host "${AZUL}>>>${RESET} $msg" }
function Write-OK($msg) { Write-Host "${VERDE}  OK >${RESET} $msg" }
function Write-Error($msg) { Write-Host "${VERMELHO} ERR >${RESET} $msg" }
function Write-Warn($msg) { Write-Host "${AMARELO} WARN >${RESET} $msg" }

# === Validações ===
Write-Step "Verificando dependencias..."

$pythonCmd = (Get-Command "python" -ErrorAction SilentlyContinue)
if (-not $pythonCmd) { $pythonCmd = (Get-Command "python3" -ErrorAction SilentlyContinue) }
if (-not $pythonCmd) { Write-Error "Python 3.6+ nao encontrado. Instale Python e tente novamente."; exit 1 }
Write-OK "Python encontrado: $($pythonCmd.Source)"

# Verificar se o pacote enem está instalado
$enemCheck = & $pythonCmd.Source -c "import enem; print('ok')" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Warn "Pacote 'enem' nao encontrado. Instalando..."
    # Tenta instalar do diretório local
    $localPkg = Join-Path $PSScriptRoot "enem-1.0.4"
    if (Test-Path $localPkg) {
        & $pythonCmd.Source -m pip install -e "$localPkg" 2>&1 | Out-Null
    } else {
        & $pythonCmd.Source -m pip install enem 2>&1 | Out-Null
    }
    if ($LASTEXITCODE -eq 0) { Write-OK "Pacote enem instalado" }
    else { Write-Error "Falha ao instalar enem. Tente: pip install enem"; exit 1 }
} else { Write-OK "Pacote enem disponivel" }

# Verificar PDFs
$provaPath = Join-Path $PastaPDF "prova.pdf"
$aplicPath = Join-Path $PastaPDF "aplicacao.pdf"
$pdfPath = $null
if (Test-Path $provaPath) { $pdfPath = $provaPath }
elseif (Test-Path $aplicPath) { $pdfPath = $aplicPath }
else {
    $pdfs = Get-ChildItem $PastaPDF -Filter "*.pdf" -ErrorAction SilentlyContinue
    if ($pdfs.Count -eq 0) { Write-Error "Nenhum PDF encontrado em: $PastaPDF"; exit 1 }
    $pdfPath = $pdfs[0].FullName
    Write-Warn "Usando: $($pdfs[0].Name)"
}
Write-OK "PDF: $pdfPath"

$gabaritoPath = Join-Path $PastaPDF "gabarito.pdf"
if (-not (Test-Path $gabaritoPath)) {
    $gabaritoPath = Join-Path $PastaPDF "gabarito.png"
}
if (Test-Path $gabaritoPath) { Write-OK "Gabarito: $gabaritoPath" }
else { Write-Warn "Gabarito nao encontrado (opcional). Use nome 'gabarito.pdf' ou 'gabarito.png'" }

# === Definir pasta de saída ===
if (-not $Output) { $Output = Join-Path $PSScriptRoot "enem-extractor-output" }
$outputDir = Join-Path $Output "$Ano"
if (-not (Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir -Force | Out-Null }
Write-OK "Saida: $outputDir"

# === Executar extração ===
Write-Step "Extraindo questoes do ENEM $Ano - Dia $Dia..."
Write-Host "${VERMELHO}  Isso pode levar varios minutos...${RESET}" -ForegroundColor Yellow

$cmdArgs = @(
    "-m", "enem",
    "-y", $Ano,
    "-d", $Dia,
    "-o", $outputDir
)

if ($gabaritoPath -and (Test-Path $gabaritoPath)) {
    $cmdArgs += "-g", $gabaritoPath
}

$cmdArgs += "`"$pdfPath`""

Write-Host "  Comando: python $($cmdArgs -join ' ')" -ForegroundColor DarkGray

$process = Start-Process -FilePath $pythonCmd.Source -ArgumentList $cmdArgs -NoNewWindow -Wait -PassThru

if ($process.ExitCode -eq 0) {
    Write-OK "Extracao concluida!"
} else {
    Write-Error "Extracao falhou (codigo $($process.ExitCode))."
    Write-Host "  Tente manualmente: python -m enem -y $Ano -d $Dia -o `"$outputDir`" `"$pdfPath`""
    exit 1
}

# === Verificar resultado ===
$outputFile = Join-Path $outputDir "output.json"
if (Test-Path $outputFile) {
    $questoes = (Get-Content $outputFile -Raw | ConvertFrom-Json).data.Count
    Write-OK "Total de questoes extraidas: $questoes"
    Write-OK "Arquivo: $outputFile"

    # === Converter para o formato do app ===
    Write-Step "Convertendo para formato do app Detetive..."
    $nodeScript = Join-Path $PSScriptRoot "generate-enem-data.js"
    if (Test-Path $nodeScript) {
        node $nodeScript
        Write-OK "Dados convertidos! Execute o build-android.ps1 para gerar novo APK"
    } else {
        Write-Warn "Script de conversao nao encontrado: $nodeScript"
        Write-Host "  Copie o output.json manualmente para enem/dados_extrator/" -ForegroundColor Yellow
    }
} else {
    Write-Error "Arquivo de saida nao encontrado: $outputFile"
    exit 1
}

Write-Step "=== EXTRACAO CONCLUIDA ==="
