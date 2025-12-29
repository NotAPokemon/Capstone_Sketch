@echo off


set "DIR=%cd%"
set "DIR=%DIR:\=/%"
runjava.bat -e "-Djava.library.path=%DIR%/natives/win/build"