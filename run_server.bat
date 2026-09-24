@echo off
chcp 65001 >nul
title BATTLESHIP GAME SERVER - TCP PORT 8888
if not exist "bin\server\ServerMain.class" (
    echo Chưa biên dịch! Đang tiến hành biên dịch...
    call compile.bat
)
echo Đang khởi chạy Battleship Server...
java -Dfile.encoding=UTF-8 -cp bin server.ServerMain 8888
pause
