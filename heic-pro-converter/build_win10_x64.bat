@echo off
setlocal
cd /d "%~dp0"

echo ===============================================
echo   HEIC Pro Converter V6 - Windows 10 x64 Build
echo ===============================================

where py >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Python Launcher ^(py.exe^) not found.
  echo Install Python 3.8 x64 from python.org and enable the launcher.
  pause
  exit /b 1
)

for /f "delims=" %%A in ('py -3.8 -c "import struct; print(struct.calcsize('P')*8)" 2^>nul') do set PYBITS=%%A
if not defined PYBITS (
  echo [ERROR] Python 3.8 x64 was not found.
  echo Install Python 3.8 64-bit and try again.
  pause
  exit /b 1
)
if not "%PYBITS%"=="64" (
  echo [ERROR] Python is not 64-bit. This package targets Windows 10 x64.
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo Creating virtual environment...
  py -3.8 -m venv .venv
  if errorlevel 1 goto :fail
)

call .venv\Scripts\activate.bat
python -m pip install --upgrade "pip==24.3.1"
if errorlevel 1 goto :fail
python -m pip install -r requirements.txt
if errorlevel 1 goto :fail

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist

python -m PyInstaller --noconfirm --clean --onefile --windowed ^
  --name "HEIC-Pro-Converter-v6-Silver-Studio" ^
  --collect-all pillow_heif ^
  --collect-all customtkinter ^
  --hidden-import pillow_heif ^
  --hidden-import tkinter ^
  src\app.py
if errorlevel 1 goto :fail

echo.
echo [OK] Build completed successfully.
echo Output: %CD%\dist\HEIC-Pro-Converter-v6-Silver-Studio.exe
start "" "%CD%\dist"
pause
exit /b 0

:fail
echo.
echo [ERROR] Build failed. Review the messages above.
pause
exit /b 1
