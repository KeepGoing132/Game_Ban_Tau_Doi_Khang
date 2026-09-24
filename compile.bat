@echo off
chcp 65001 >nul
echo [1/2] Đang tạo thư mục bin...
if not exist "bin" mkdir bin

echo [2/2] Đang biên dịch mã nguồn Java (UTF-8)...
powershell -Command "javac -encoding UTF-8 -d bin (Get-ChildItem -Recurse -Path src -Filter *.java).FullName"

if %ERRORLEVEL% EQU 0 (
    echo =======================================================
    echo   BIÊN DỊCH THÀNH CÔNG RỰC RỠ!
    echo   Tất cả các file .class đã sẵn sàng trong thư mục bin/
    echo =======================================================
) else (
    echo =======================================================
    echo   BIÊN DỊCH THẤT BẠI! Vui lòng kiểm tra lại lỗi ở trên.
    echo =======================================================
)
pause
