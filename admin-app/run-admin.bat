@echo off
setlocal
set ROOT=%~dp0
if not exist "%ROOT%out" mkdir "%ROOT%out"
javac -encoding UTF-8 -d "%ROOT%out" "%ROOT%src\SchularchivAdmin.java"
if errorlevel 1 exit /b 1
java -cp "%ROOT%out" SchularchivAdmin
