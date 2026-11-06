@echo off
REM One-click Shield cleaner for Windows. Put your Shield's IP below, then double-click.
REM Needs adb.exe on PATH (or in this folder).
set SHIELD_IP=192.168.1.50
adb connect %SHIELD_IP%:5555 >nul
echo Clearing all app caches...
adb -s %SHIELD_IP%:5555 shell pm trim-caches 9999G
echo Closing background apps...
adb -s %SHIELD_IP%:5555 shell am kill-all
echo.
adb -s %SHIELD_IP%:5555 shell "grep MemAvailable /proc/meminfo; df -h /data | tail -n 1"
echo Done.
pause
