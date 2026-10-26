@echo off
rem Reuse the repository's Maven wrapper while running in login-service's directory.
call "%~dp0..\customer-service\mvnw.cmd" %*
exit /b %ERRORLEVEL%
