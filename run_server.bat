@echo off
chcp 65001 >nul
pushd "%~dp0"
if not exist "bin\server\ServerMain.class" (
    call "%~dp0compile.bat"
    if errorlevel 1 (
        popd
        exit /b 1
    )
)
set "BATTLESHIP_PORT=8888"
if not "%~1"=="" set "BATTLESHIP_PORT=%~1"
java -Dfile.encoding=UTF-8 -cp "%~dp0bin;%~dp0lib/*" server.ServerMain %BATTLESHIP_PORT%
set "BATTLESHIP_EXIT=%ERRORLEVEL%"
popd
exit /b %BATTLESHIP_EXIT%
