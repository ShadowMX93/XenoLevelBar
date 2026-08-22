@echo off
setlocal EnableExtensions

set "GRADLE_VERSION=9.7.1"
set "PROJECT_DIR=%~dp0"
set "DIST_ROOT=%PROJECT_DIR%.gradle-bootstrap"
set "GRADLE_HOME=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
set "ZIP_FILE=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto runGradle

if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
echo Gradle %GRADLE_VERSION% is not cached; downloading it...

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%ZIP_FILE%'"
if errorlevel 1 (
  echo Error: failed to download Gradle %GRADLE_VERSION%.
  exit /b 1
)

if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%DIST_ROOT%' -Force"
if errorlevel 1 (
  echo Error: failed to extract Gradle %GRADLE_VERSION%.
  exit /b 1
)

del /q "%ZIP_FILE%" >nul 2>&1

:runGradle
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
