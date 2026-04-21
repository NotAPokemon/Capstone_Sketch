@echo off

if exist internal_config.json (
    del /f internal_config.json
)

if exist Korgi\config.json (
    move Korgi\config.json config.json
)

if exist Korgi\ (
    rmdir /s /q Korgi
)

call runjava.bat

if exist config.json (
    move config.json Korgi\config.json
)

call runjava.bat