@echo off
cd /d "%~dp0"
java -Djava.library.path=. -jar lossless.jar
pause
