@echo off
setlocal
cd /d "%~dp0"

where py >nul 2>nul
if errorlevel 1 (
  echo Python Launcher not found.
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  py -3.8 -m venv .venv
  if errorlevel 1 goto :fail
)

call .venv\Scripts\activate.bat
python -m pip install -r requirements.txt
if errorlevel 1 goto :fail
python src\app.py
exit /b 0

:fail
echo Failed to prepare or run the source project.
pause
exit /b 1
