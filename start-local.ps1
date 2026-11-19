# Keep one launcher: configuration, build and Flyway behavior live in start-be.bat.
& (Join-Path $PSScriptRoot 'start-be.bat') @args
exit $LASTEXITCODE