# build_release.ps1

# Устанавливаем кодировку UTF-8 для консоли
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ============================================
# ХЕЛПЕРЫ ДЛЯ ВЫВОДА
# ============================================

# Счётчик этапов — нумерация остаётся сквозной при пропуске шагов
$script:StageNumber = 0

# Заголовок этапа — Cyan, крупная рамка
function Write-StageHeader {
    param([string]$Title)
    $script:StageNumber++
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  ЭТАП $script:StageNumber. $Title" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host ""
}

# Тонкий разделитель между вопросами — DarkGray
function Write-Divider {
    Write-Host "  ──────────────────────────────────────" -ForegroundColor DarkGray
    Write-Host ""
}

# Вопрос к пользователю — Yellow (яркий)
function Write-Question {
    param([string]$Text)
    Write-Host "  ⚠️  $Text" -ForegroundColor Yellow
}

# Результат ответа / подтверждение — DarkYellow (оливковый)
function Write-Result {
    param([string]$Text)
    Write-Host "  ⚠️  $Text" -ForegroundColor DarkYellow
}

# Приглашение ввода — White
function Read-Answer {
    param([string]$Prompt = ">")
    Write-Host "  $Prompt " -ForegroundColor White -NoNewline
    return Read-Host
}

# Системная информация — Gray
function Write-Info {
    param([string]$Text)
    Write-Host "  ℹ️  $Text" -ForegroundColor Gray
}

# Подшаг / действие — DarkGray
function Write-Step {
    param([string]$Text)
    Write-Host "  ➡️  $Text" -ForegroundColor DarkGray
}

# Успех — Green
function Write-Success {
    param([string]$Text)
    Write-Host "  ✅ $Text" -ForegroundColor Green
}

# Предупреждение (проблема, не ответ) — Yellow
function Write-Warn {
    param([string]$Text)
    Write-Host "  ⚠️  $Text" -ForegroundColor Yellow
}

# Ошибка — Red
function Write-Err {
    param([string]$Text)
    Write-Host "  ❌ $Text" -ForegroundColor Red
}

# Критический блок (рамка) — Red
function Write-CriticalBlock {
    param([string]$Text)
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host "  ❌ $Text" -ForegroundColor Red
    Write-Host "==========================================" -ForegroundColor Red
    Write-Host ""
}

# ============================================
# СТАРТ
# ============================================
Write-Host ""
Write-Host "##########################################" -ForegroundColor Magenta
Write-Host "#                                        #" -ForegroundColor Magenta
Write-Host "#  🚀 QUTY.LAUNCH — СБОРКА И ПУБЛИКАЦИЯ  #" -ForegroundColor Magenta
Write-Host "#                                        #" -ForegroundColor Magenta
Write-Host "##########################################" -ForegroundColor Magenta
Write-Host ""

# ============================================
# ЭТАП: ЧТЕНИЕ ТЕКУЩЕЙ ВЕРСИИ
# ============================================
Write-StageHeader -Title "ЧТЕНИЕ ТЕКУЩЕЙ ВЕРСИИ"

# Проверяем существование файла
if (-not (Test-Path "app/build.gradle.kts")) {
    Write-Err "Файл app/build.gradle.kts не найден!"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

Write-Info "Файл: app/build.gradle.kts"

$gradleFile = Get-Content "app/build.gradle.kts" -Raw -Encoding UTF8

$currentVersionCode = 0
$currentVersionName = ""
$currentVersionSuffix = ""

# Разбираем построчно
foreach ($line in $gradleFile -split "`n") {
    if ($line -match 'versionCode\s*=\s*(\d+)') {
        $currentVersionCode = [int]$matches[1]
    }
    if ($line -match 'versionName\s*=\s*"([^"]+)"' -and $line -notmatch 'versionNameSuffix') {
        $currentVersionName = $matches[1]
    }
    if ($line -match 'versionNameSuffix\s*=\s*"([^"]+)"') {
        $currentVersionSuffix = $matches[1]
    }
}

Write-Success "Текущая версия: $currentVersionName $currentVersionSuffix"
Write-Info "Version code: $currentVersionCode"

# ============================================
# ЭТАП: НАСТРОЙКИ РЕЛИЗА
# ============================================
Write-StageHeader -Title "НАСТРОЙКИ СБОРКИ"

# --- Вопрос 0: Тип сборки ---
Write-Question "Какую сборку делаем?"
Write-Info "  1) release — подписанный APK + публикация на сервер"
Write-Info "  2) debug   — локальная сборка для теста, без публикации"
Write-Host ""

$buildChoice = $null
while ($buildChoice -notin @("1", "2")) {
    $answer = Read-Answer -Prompt "Выберите (1/2) [1]:"
    if ([string]::IsNullOrEmpty($answer)) { $answer = "1" }
    $buildChoice = $answer
}

$isReleaseBuild = ($buildChoice -eq "1")

if ($isReleaseBuild) {
    Write-Success "Режим: RELEASE (с публикацией)"
} else {
    Write-Success "Режим: DEBUG (локальная сборка)"
}

Write-Divider

# --- Вопрос 0.1: Суффикс версии ---
Write-Question "Какой суффикс версии (versionNameSuffix) использовать?"
Write-Info "  1) Alpha   — сборка в стадии разработки (по умолчанию)"
Write-Info "  2) Release — стабильная сборка"
Write-Host ""

$suffixChoice = $null
while ($suffixChoice -notin @("1", "2")) {
    $answer = Read-Answer -Prompt "Выберите (1/2) [1]:"
    if ([string]::IsNullOrEmpty($answer)) { $answer = "1" }
    $suffixChoice = $answer
}

$newVersionSuffix = if ($suffixChoice -eq "2") { "Release" } else { "Alpha" }

if ($newVersionSuffix -eq $currentVersionSuffix) {
    Write-Success "Суффикс: $newVersionSuffix (без изменений)"
} else {
    Write-Success "Суффикс: $currentVersionSuffix → $newVersionSuffix"
}

Write-Divider

if ($isReleaseBuild) {

# --- Вопрос 1: Новая версия ---
Write-Question "Введите новый номер версии (сейчас $currentVersionName):"
$newVersionName = Read-Answer

if ([string]::IsNullOrEmpty($newVersionName)) {
    $newVersionName = $currentVersionName
    Write-Result "Оставлена текущая версия: $newVersionName"
} else {
    Write-Success "Новая версия: $newVersionName"
}

Write-Divider

# --- Вопрос 2: Changelog ---
Write-Question "Введите описание изменений (changelog) для этой версии:"
Write-Info "Несколько строк, для окончания введите пустую строку"
Write-Host ""

$changelog = ""
while ($true) {
    $line = Read-Answer
    if ([string]::IsNullOrEmpty($line)) {
        break
    }
    if ([string]::IsNullOrEmpty($changelog)) {
        $changelog = $line
    } else {
        $changelog = $changelog + "`n" + $line
    }
}

if ([string]::IsNullOrEmpty($changelog)) {
    $changelog = "Исправление багов и улучшение производительности"
    Write-Result "Использован стандартный changelog"
}

Write-Divider

# --- Вопрос 3: Критическое обновление ---
Write-Question "Это критическое обновление? (y/N) [N]:"
$isCriticalInput = Read-Answer
$isCritical = ($isCriticalInput -eq "y" -or $isCriticalInput -eq "Y")
if ($isCritical) {
    Write-Result "Обновление будет помечено как КРИТИЧЕСКОЕ"
} else {
    Write-Success "Обычное обновление"
}

Write-Divider

# --- Вопрос 4: Git tag ---
Write-Question "Создать Git tag для этого релиза в Quty.Launch.Server? (y/N) [N]:"
$createTagInput = Read-Answer
$createTag = ($createTagInput -eq "y" -or $createTagInput -eq "Y")
if ($createTag) {
    Write-Success "Tag v$newVersionName будет создан"
} else {
    Write-Success "Tag создаваться не будет"
}

# Подсчёт нового versionCode
$newVersionCode = $currentVersionCode + 1

} else {
    # DEBUG: версия не меняется, публикация не выполняется
    $newVersionName = $currentVersionName
    $newVersionCode = $currentVersionCode
    $changelog = ""
    $isCritical = $false
    $createTag = $false

    Write-Info "Версия остаётся без изменений: $currentVersionName ($currentVersionCode)"
}

# ============================================
# ЭТАП: ОБНОВЛЕНИЕ BUILD.GRADLE.KTS
# ============================================
$suffixChanged = ($newVersionSuffix -ne $currentVersionSuffix)

if ($isReleaseBuild -or $suffixChanged) {
    Write-StageHeader -Title "ОБНОВЛЕНИЕ BUILD.GRADLE.KTS"

    if ($isReleaseBuild) {
        Write-Step "versionCode: $currentVersionCode → $newVersionCode"
        Write-Step "versionName: $currentVersionName → $newVersionName"
    } else {
        Write-Step "Версия и versionCode не меняются"
    }
    if ($suffixChanged) {
        Write-Step "versionNameSuffix: $currentVersionSuffix → $newVersionSuffix"
    }

    $newContent = @()
    foreach ($line in ($gradleFile -split "`n")) {
        if ($isReleaseBuild -and $line -match 'versionCode\s*=') {
            $newContent += "    versionCode = $newVersionCode"
        } elseif ($isReleaseBuild -and $line -match 'versionName\s*=' -and $line -notmatch 'versionNameSuffix') {
            $newContent += "    versionName = `"$newVersionName`""
        } elseif ($suffixChanged -and $line -match 'versionNameSuffix\s*=') {
            $newContent += "        versionNameSuffix = `"$newVersionSuffix`""
        } else {
            $newContent += $line
        }
    }

    try {
        $contentToWrite = $newContent -join "`n"
        $utf8NoBom = New-Object System.Text.UTF8Encoding $false
        $bytes = $utf8NoBom.GetBytes($contentToWrite)
        [System.IO.File]::WriteAllBytes("app/build.gradle.kts", $bytes)
        Write-Success "build.gradle.kts обновлен"
    } catch {
        Write-Err "Не удалось сохранить build.gradle.kts: $($_.Exception.Message)"
        Write-Host ""
        Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
        Read-Host
        exit 1
    }
}

# ============================================
# ЭТАП: СБОРКА
# ============================================
if ($isReleaseBuild) {
    $gradleTask = "assembleRelease"
    $buildTitle = "СБОРКА RELEASE"
    $apkSourceDir = "app\build\outputs\apk\release"
} else {
    $gradleTask = "assembleDebug"
    $buildTitle = "СБОРКА DEBUG"
    $apkSourceDir = "app\build\outputs\apk\debug"
}

Write-StageHeader -Title $buildTitle

Write-Step "Запуск: gradlew clean $gradleTask"
Write-Info "Это может занять несколько минут..."
Write-Host ""

# Проверяем наличие gradlew
if (-not (Test-Path "gradlew.bat") -and -not (Test-Path "gradlew")) {
    Write-Err "gradlew не найден!"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

if (Test-Path "gradlew.bat") {
    .\gradlew.bat clean $gradleTask
} else {
    ./gradlew clean $gradleTask
}

if ($LASTEXITCODE -ne 0) {
    Write-CriticalBlock "СБОРКА НЕ УДАЛАСЬ! Код ошибки: $LASTEXITCODE"
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

Write-Host ""
Write-Success "Сборка успешно завершена!"

# ============================================
# ЭТАП: КОПИРОВАНИЕ APK
# ============================================
$debugApkPath = $null

if ($isReleaseBuild) {

Write-StageHeader -Title "КОПИРОВАНИЕ APK"

$apkFilename = "Quty.Launch-$newVersionName.apk"
$sourceApk = Join-Path $apkSourceDir "app-release.apk"
$destDir = "..\Quty.Launch.Server\updates\apk\"
$destApk = Join-Path $destDir $apkFilename

Write-Info "Источник:   $sourceApk"
Write-Info "Назначение: $destApk"

if (-not (Test-Path $sourceApk)) {
    Write-Err "APK файл не найден: $sourceApk"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

try {
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
} catch {
    Write-Err "Не удалось создать директорию $destDir"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

try {
    Copy-Item $sourceApk $destApk -Force -ErrorAction Stop
    Write-Success "APK скопирован"
} catch {
    Write-Err "Не удалось скопировать APK: $($_.Exception.Message)"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

# Размер APK
$apkSize = (Get-Item $destApk).Length
$apkSizeHuman = if ($apkSize -gt 1MB) {
    "{0:N2} MB" -f ($apkSize / 1MB)
} elseif ($apkSize -gt 1KB) {
    "{0:N0} KB" -f ($apkSize / 1KB)
} else {
    "{0} B" -f $apkSize
}
Write-Info "Размер APK: $apkSizeHuman"

# ============================================
# ЭТАП: СОЗДАНИЕ VERSION.JSON
# ============================================
Write-StageHeader -Title "СОЗДАНИЕ VERSION.JSON"

$versionJson = "..\Quty.Launch.Server\updates\version.json"
$jsonChangelog = $changelog -replace "`n", "\n"

$isCriticalJson = if ($isCritical) { "true" } else { "false" }

Write-Info "Файл: $versionJson"
Write-Step "isCritical: $isCriticalJson"

$jsonContent = @"
{
  "version": "$newVersionName",
  "versionCode": $newVersionCode,
  "downloadUrl": "https://raw.githubusercontent.com/Sanya2104/Quty.Launch.Server/main/updates/apk/$apkFilename",
  "changelog": "$jsonChangelog",
  "releaseDate": "$(Get-Date -Format dd-MM-yyyy)",
  "isCritical": $isCriticalJson,
  "size": "$apkSizeHuman"
}
"@

try {
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    $bytes = $utf8NoBom.GetBytes($jsonContent)
    [System.IO.File]::WriteAllBytes($versionJson, $bytes)
    Write-Success "version.json создан"
} catch {
    Write-Err "Не удалось создать version.json: $($_.Exception.Message)"
    Write-Host ""
    Write-Host "  Нажмите Enter для выхода..." -ForegroundColor White
    Read-Host
    exit 1
}

# ============================================
# ЭТАП: GIT PUSH
# ============================================
Write-StageHeader -Title "GIT PUSH"

$skipGit = $false

if (-not (Test-Path "..\Quty.Launch.Server\.git")) {
    Write-Warn "Папка ..\Quty.Launch.Server не является Git репозиторием!"
    Write-Warn "Пропускаем Git push"
} else {
    Push-Location "..\Quty.Launch.Server" -ErrorAction SilentlyContinue

    Write-Step "git add ."
    git add .

    Write-Step "git commit -m `"Release $newVersionName`""
    git commit -m "Release $newVersionName"

    # Подтягиваем последние изменения с сервера (GitHub Actions мог обновить
    # store/shells.json пока мы делали релиз), чтобы избежать rejection при push.
    Write-Step "git pull --rebase origin main"
    git pull --rebase origin main

    if ($LASTEXITCODE -ne 0) {
        Write-Err "Ошибка git pull --rebase. Возможно, конфликты — разрешите вручную."
        Write-Warn "Push и создание tag пропущены."
        $skipGit = $true
    } else {
        Write-Step "git push origin main"
        git push origin main

        if ($LASTEXITCODE -eq 0) {
            Write-Success "Изменения отправлены в GitHub"
        } else {
            Write-Err "Ошибка отправки в GitHub (код: $LASTEXITCODE)"
            $skipGit = $true
        }
    }

    Pop-Location
}

# ============================================
# ЭТАП: GIT TAG (если выбрано)
# ============================================
if ($createTag -and -not $skipGit) {
    Write-StageHeader -Title "GIT TAG"

    Push-Location "..\Quty.Launch.Server" -ErrorAction SilentlyContinue

    Write-Step "git tag -a `"v$newVersionName`""
    git tag -a "v$newVersionName" -m "Release $newVersionName"

    Write-Step "git push origin `"v$newVersionName`""
    git push origin "v$newVersionName"

    if ($LASTEXITCODE -eq 0) {
        Write-Success "Tag v$newVersionName создан и отправлен"
    } else {
        Write-Err "Ошибка создания tag (код: $LASTEXITCODE)"
    }

    Pop-Location
} else {
    if ($skipGit) {
        Write-Host ""
        Write-Warn "Git tag не создавался (из-за ошибки на предыдущем этапе)"
    } else {
        Write-Host ""
        Write-Info "Git tag не создавался"
    }
}

} else {

# ============================================
# DEBUG: ЛОКАЛЬНЫЙ APK БЕЗ ПУБЛИКАЦИИ
# ============================================
Write-StageHeader -Title "РЕЗУЛЬТАТ DEBUG-СБОРКИ"

$debugApkPath = Join-Path $apkSourceDir "app-debug.apk"

if (Test-Path $debugApkPath) {
    $apkSize = (Get-Item $debugApkPath).Length
    $apkSizeHuman = if ($apkSize -gt 1MB) {
        "{0:N2} MB" -f ($apkSize / 1MB)
    } elseif ($apkSize -gt 1KB) {
        "{0:N0} KB" -f ($apkSize / 1KB)
    } else {
        "{0} B" -f $apkSize
    }

    Write-Success "APK готов: $debugApkPath"
    Write-Info "Размер: $apkSizeHuman"
    Write-Info "Версия не менялась, на сервер ничего не отправлено"
} else {
    Write-Err "APK не найден: $debugApkPath"
    $apkSizeHuman = "—"
}

} # конец debug-ветки

# ============================================
# ИТОГ
# ============================================
Write-Host ""
Write-Host "##########################################" -ForegroundColor Green
Write-Host "#                                        #" -ForegroundColor Green
Write-Host "#     ✅ ПРОЦЕСС ЗАВЕРШЕН УСПЕШНО!       #" -ForegroundColor Green
Write-Host "#                                        #" -ForegroundColor Green
Write-Host "##########################################" -ForegroundColor Green
Write-Host ""
Write-Host "  🏗️  Режим:        " -NoNewline -ForegroundColor Gray
Write-Host $(if ($isReleaseBuild) { "RELEASE" } else { "DEBUG" }) -ForegroundColor White
Write-Host "  📦 Версия:       " -NoNewline -ForegroundColor Gray
Write-Host "$newVersionName $newVersionSuffix" -ForegroundColor White
Write-Host "  🔢 Version code: " -NoNewline -ForegroundColor Gray
Write-Host "$newVersionCode" -ForegroundColor White
Write-Host "  🧲 Размер:       " -NoNewline -ForegroundColor Gray
Write-Host "$apkSizeHuman" -ForegroundColor White

if ($isReleaseBuild) {
    Write-Host "  🚩  Critical:    " -NoNewline -ForegroundColor Gray
    Write-Host "$isCritical" -ForegroundColor White
    Write-Host "  🏷️  Git tag:     " -NoNewline -ForegroundColor Gray
    Write-Host "$createTag" -ForegroundColor White
    Write-Host ""
    Write-Host "  📄 Changelog:" -ForegroundColor Gray
    Write-Host "$changelog" -ForegroundColor White
    Write-Host ""
    Write-Host "  📁 APK:  " -NoNewline -ForegroundColor Gray
    Write-Host "$destApk" -ForegroundColor White
    Write-Host "  🧩 JSON: " -NoNewline -ForegroundColor Gray
    Write-Host "$versionJson" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "  📁 APK:  " -NoNewline -ForegroundColor Gray
    Write-Host $debugApkPath -ForegroundColor White
    Write-Host ""
    Write-Info "Debug-сборка не публикуется и не меняет версию проекта"
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green

# ============================================
# ВЫХОД
# ============================================
Write-Host ""
Write-Host "  Нажмите Enter для выхода..." -ForegroundColor Cyan
Read-Host
exit 0