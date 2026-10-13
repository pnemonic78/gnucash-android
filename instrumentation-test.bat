@echo off
setlocal

:: Don't abort if commands exit with non-zero exit code
call gcloud firebase test android run ^
    --app %APPLICATION_APK% ^
    --test %INSTRUMENTATION_APK% ^
    --results-dir %GCLOUD_BUCKET_DIRECTORY% ^
    --device model=%MODEL%,version=%VERSION%

set test_code=%ERRORLEVEL%

set "test_success=false"
set "exit_code=%test_code%"

:: https://firebase.google.com/docs/test-lab/android/command-line#script_exit_codes
if "%test_code%"=="0" (
    set "test_success=true"
) else if "%test_code%"=="10" (
    set "exit_code=0"
)

if defined GITHUB_OUTPUT (
    echo test_success=%test_success%>> "%GITHUB_OUTPUT%"
)

exit /b %exit_code%
