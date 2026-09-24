@echo off
chcp 65001 >nul
title BATTLESHIP CLIENT
if not exist "bin\client\ClientMain.class" (
    echo Chưa biên dịch! Đang tiến hành biên dịch...
    call compile.bat
)
echo Đang mở giao diện Battleship Client...
start java -Dfile.encoding=UTF-8 -cp bin client.ClientMain
