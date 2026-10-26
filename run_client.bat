@echo off
chcp 65001 >nul
pushd "%~dp0"
if not exist "bin\client\ClientMain.class" (
    call "%~dp0compile.bat"
    if errorlevel 1 (
        popd
        exit /b 1
    )
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run_java.ps1" client
set "BATTLESHIP_EXIT=%ERRORLEVEL%"
popd
exit /b %BATTLESHIP_EXIT%
