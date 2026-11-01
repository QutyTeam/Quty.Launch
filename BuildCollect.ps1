# BuildCollect.ps1

$StartDir = "app\src\main"
$OutputDir = ".promt"
$Extensions = @(".kt", ".xml", ".gradle", ".kts")
$IgnoreDirs = @("build", ".idea", ".git", "drawable", "mipmap", "assets")

Write-Host ""
Write-Host "==========================================" -ForegroundColor Magenta
Write-Host "  PROJECT CONTEXT BUILDER" -ForegroundColor Magenta
Write-Host "==========================================" -ForegroundColor Magenta
Write-Host ""

# --- Опросник ---
Write-Host "Step 0: What files to collect?" -ForegroundColor Cyan
Write-Host "  1) All files"
Write-Host "  2) main/res + AndroidManifest.xml"
Write-Host "  3) main/java + AndroidManifest.xml"
Write-Host ""

$choice = $null
while ($choice -notin @("1","2","3")) {
    $choice = Read-Host "Enter choice (1/2/3)"
}

if ($choice -eq "1") {
    $mode = "all"
    $OutputFile = Join-Path $OutputDir "project_context_all.txt"
    Write-Host "Selected: ALL files" -ForegroundColor Green
}
elseif ($choice -eq "2") {
    $mode = "res"
    $OutputFile = Join-Path $OutputDir "project_context_res.txt"
    Write-Host "Selected: main/res + AndroidManifest.xml" -ForegroundColor Green
}
elseif ($choice -eq "3") {
    $mode = "java"
    $OutputFile = Join-Path $OutputDir "project_context_java.txt"
    Write-Host "Selected: main/java + AndroidManifest.xml" -ForegroundColor Green
}
else {
    Write-Host "ERROR: invalid choice" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

# --- Создаём выходную папку ---
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
    Write-Host "Created output dir: $OutputDir" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Step 1: Checking folder..." -ForegroundColor Cyan

if (-not (Test-Path $StartDir)) {
    Write-Host "ERROR: Folder '$StartDir' not found!" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

Write-Host "OK: Folder found: $StartDir" -ForegroundColor Green

Write-Host ""
Write-Host "Step 2: Collecting files..." -ForegroundColor Cyan

if (Test-Path $OutputFile) {
    Remove-Item $OutputFile
    Write-Host "Old file removed" -ForegroundColor DarkGray
}

# --- Нормализуем базовые пути один раз ---
$cwd      = (Get-Location).Path
$sep      = [System.IO.Path]::DirectorySeparatorChar
$basePath = [System.IO.Path]::GetFullPath($cwd) + $sep

$mainRoot = [System.IO.Path]::GetFullPath((Join-Path $cwd $StartDir))
$javaRoot = [System.IO.Path]::GetFullPath((Join-Path $mainRoot "java")) + $sep
$resRoot  = [System.IO.Path]::GetFullPath((Join-Path $mainRoot "res"))  + $sep
$manifest = [System.IO.Path]::GetFullPath((Join-Path $mainRoot "AndroidManifest.xml"))

$cmp = [System.StringComparison]::OrdinalIgnoreCase

Write-Host "  basePath: $basePath" -ForegroundColor DarkGray
Write-Host "  mainRoot: $mainRoot" -ForegroundColor DarkGray
Write-Host "  javaRoot: $javaRoot" -ForegroundColor DarkGray
Write-Host "  resRoot : $resRoot"  -ForegroundColor DarkGray
Write-Host "  manifest: $manifest" -ForegroundColor DarkGray
Write-Host "  mode    : $mode"     -ForegroundColor DarkGray
Write-Host ""

$files = Get-ChildItem -Path $StartDir -Recurse -File
$totalCount = 0
$skipCount = 0

foreach ($file in $files) {
    # --- Фильтр по расширению ---
    if ($Extensions -notcontains $file.Extension) {
        $skipCount++
        continue
    }

    # --- Фильтр по игнор-папкам ---
    $skip = $false
    foreach ($ignore in $IgnoreDirs) {
        if ($file.FullName -match "\\$ignore\\") {
            $skip = $true
            break
        }
    }
    if ($skip) {
        $skipCount++
        continue
    }

    # --- Нормализуем путь файла ---
    $full = [System.IO.Path]::GetFullPath($file.FullName)

    # --- Классификация ---
    $isManifest = $full.Equals($manifest, $cmp)
    $inJava     = $full.StartsWith($javaRoot, $cmp)
    $inRes      = $full.StartsWith($resRoot,  $cmp)

    # --- Фильтрация по режиму (явные if, без switch) ---
    $keep = $false

    if ($mode -eq "all") {
        $keep = $true
    }
    elseif ($mode -eq "res") {
        if ($inRes -or $isManifest) { $keep = $true }
    }
    elseif ($mode -eq "java") {
        if ($inJava -or $isManifest) { $keep = $true }
    }

    if (-not $keep) {
        $skipCount++
        continue
    }

    $relativePath = $full.Replace($basePath, "")

    Add-Content -Path $OutputFile -Value "`n`n=================================================="
    Add-Content -Path $OutputFile -Value "FILE: $relativePath"
    Add-Content -Path $OutputFile -Value "=================================================="
    Add-Content -Path $OutputFile -Value "`n"

    try {
        Get-Content -Path $file.FullName -Raw -Encoding UTF8 | Add-Content -Path $OutputFile
        Write-Host "  Added: $relativePath" -ForegroundColor DarkGray
        $totalCount++
    } catch {
        Add-Content -Path $OutputFile -Value "[Read error]"
        Write-Host "  Read error: $relativePath" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "  DONE!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Mode:            $mode" -ForegroundColor White
Write-Host "  File:            $OutputFile" -ForegroundColor White
Write-Host "  Files collected: $totalCount" -ForegroundColor White
Write-Host "  Files skipped:   $skipCount" -ForegroundColor White
Write-Host ""
Write-Host "==========================================" -ForegroundColor Green

Write-Host ""
Read-Host "Press Enter to exit..."
exit 0
