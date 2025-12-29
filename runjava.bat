@echo off
setlocal enabledelayedexpansion
set EXIT_CODE=0

:: -----------------------------
:: Project directories
:: -----------------------------
set "PROJECT_DIR=%cd%"
set "SRC_PATH=%PROJECT_DIR%\src"
set "BIN_DIR=%PROJECT_DIR%\bin"
set "EXTRA_FLAGS="

:: -----------------------------
:: Process optional -e flag
:: -----------------------------
:parse_args
if "%~1"=="" goto args_done
if "%~1"=="-e" (
    set "EXTRA_FLAGS=%~2"
    shift
    shift
) else (
    shift
)
goto parse_args
:args_done

:: -----------------------------
:: Gather library jars from lib folder
:: -----------------------------
set "LIBS="
if exist "%PROJECT_DIR%\lib\" (
    for %%J in ("%PROJECT_DIR%\lib\*.jar") do (
        if defined LIBS (
            set "LIBS=!LIBS!;%%~fJ"
        ) else (
            set "LIBS=%%~fJ"
        )
    )
)

:: -----------------------------
:: Create bin directory
:: -----------------------------
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

:: -----------------------------
:: Gather all Java sources into sources.txt (forward slashes)
:: -----------------------------
> sources.txt (
    if exist "%SRC_PATH%\" (
        pushd "%SRC_PATH%"
        for /r %%F in (*.java) do (
            set "SRCFILE=%%F"
            setlocal enabledelayedexpansion
            echo !SRCFILE:\=/!
            endlocal
        )
        popd
    )
)

:: Check if sources.txt has any content
for /f %%L in (sources.txt) do goto compile_sources
echo error: no Java source files found
set EXIT_CODE=1
goto goto_exit

:compile_sources
:: -----------------------------
:: Compile Java sources
:: -----------------------------
echo Compiling...
if defined LIBS (
    javac -d "%BIN_DIR%" -cp "%LIBS%" @sources.txt
) else (
    javac -d "%BIN_DIR%" @sources.txt
)
if errorlevel 1 set EXIT_CODE=1 & goto goto_exit

del sources.txt

:: -----------------------------
:: Copy non-Java resources
:: -----------------------------
if exist "%SRC_PATH%\" (
   :: Copy all non-java resources preserving folder structure
    for /R "%SRC_PATH%" %%R in (*) do (
        if /I not "%%~xR"==".java" (
            set "DEST=%BIN_DIR%\%%~pR"
            if not exist "!DEST!" mkdir "!DEST!"
            copy /Y "%%R" "!DEST!" >nul
        )
    )

)

:: -----------------------------
:: Detect main class (fully qualified) reliably
:: -----------------------------
set "MAIN_CLASS="
for /R "%SRC_PATH%" %%F in (*.java) do (
    findstr /C:"public static void main(String" "%%F" >nul
    if not errorlevel 1 (
        set "MAIN_FILE=%%F"
        goto :found_main
    )
)

echo error: Could not determine main class automatically.
set EXIT_CODE=1
goto :goto_exit

:found_main
:: Extract package separately
set "PACKAGE="
for /F "tokens=2 delims= " %%P in ('findstr "^package " "%MAIN_FILE%"') do set "PACKAGE=%%P"
set "PACKAGE=%PACKAGE:~0,-1%"

:: Class name
for %%F in ("%MAIN_FILE%") do set "CLASSNAME=%%~nF"

:: Combine
if defined PACKAGE (
    set "MAIN_CLASS=%PACKAGE%.%CLASSNAME%"
) else (
    set "MAIN_CLASS=%CLASSNAME%"
)


echo Detected main class: %MAIN_CLASS%


:run_main
if not defined MAIN_CLASS (
    echo error: Could not determine main class automatically.
    set EXIT_CODE=1
    goto goto_exit
)

:: -----------------------------
:: Run main class
:: -----------------------------
echo Running %MAIN_CLASS%
echo.
echo ------------- Output -------------
echo.

if defined LIBS (
    java %EXTRA_FLAGS% -cp "%BIN_DIR%;%LIBS%" "%MAIN_CLASS%"
) else (
    java %EXTRA_FLAGS% -cp "%BIN_DIR%" "%MAIN_CLASS%"
)

:: -----------------------------
:: Exit
:: -----------------------------
:goto_exit
echo.
echo ---------- Exit Code: %EXIT_CODE% ----------
exit /b %EXIT_CODE%
