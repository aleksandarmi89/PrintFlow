@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "POWERSHELL_DIR=%SystemRoot%\System32\WindowsPowerShell\v1.0"

REM Preferred path: Maven Wrapper (requires PowerShell)
if exist "%POWERSHELL_EXE%" (
  where powershell >nul 2>nul
  if errorlevel 1 (
    set "PATH=%POWERSHELL_DIR%;%PATH%"
  )
  call "%ROOT_DIR%mvnw.cmd" %*
  if %errorlevel%==0 exit /b 0
)

REM Fallback for environments without PowerShell (e.g. stripped Windows image)
set "PLEIADES_MVN=C:\Users\%USERNAME%\AppData\Roaming\Code\User\globalStorage\pleiades.java-extension-pack-jdk\maven\latest\bin\mvn.cmd"
if exist "%PLEIADES_MVN%" (
  call "%PLEIADES_MVN%" %*
  exit /b %errorlevel%
)

if defined MAVEN_HOME (
  if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
    exit /b %errorlevel%
  )
)

echo [ERROR] Maven launcher not found.
echo [ERROR] Install PowerShell (for mvnw.cmd) or set MAVEN_HOME, or install Maven.
exit /b 1
