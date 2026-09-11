@echo off
set DIR=%~dp0
cd /d "%DIR%"
java -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
exit /b %ERRORLEVEL%
