@echo off
SET "JAVA_HOME=C:\Program Files\Java\jdk-21"
SET "JNI_INCLUDE=%JAVA_HOME%\include"
SET "JNI_INCLUDE_WIN=%JAVA_HOME%\include\win32"
SET "SRC_DIR=natives\win\src"
SET "INCLUDE=natives\win\include"
SET "LIB_DIR=natives\win\libs"
SET "BUILD_DIR=natives\win\build"

IF NOT EXIST "%BUILD_DIR%" mkdir "%BUILD_DIR%"


C:\msys64\mingw64\bin\clang -m64 -c "%SRC_DIR%/glad.c" -I"%INCLUDE%" -o glad.o

c:\msys64\mingw64\bin\clang++ -m64 -std=c++17 -shared "%SRC_DIR%/KorgiJNI.cpp" glad.o -Iinclude -I"%INCLUDE%" -I"%JNI_INCLUDE%" -I"%JNI_INCLUDE_WIN%" -L "%LIB_DIR%" -lglfw3 -luser32 -lgdi32 -lkernel32 -lwinmm -lshell32 -lopengl32 -lgdi32 -luser32 -lkernel32 -lwinmm -lshell32 -lole32 -ladvapi32 -o "%BUILD_DIR%\korgikompute-win.dll"

del glad.o

IF %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Build succeeded: "%BUILD_DIR%\korgikompute-win.dll"
