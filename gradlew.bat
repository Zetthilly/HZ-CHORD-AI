@echo off
set DIR=%~dp0
java -jar "%DIR%gradle\wrapper\gradle-wrapper.jar" %*
if errorlevel 1 exit /b %errorlevel%
