@echo off
setlocal
chcp 65001 >nul
set "CASMS_BE_LAUNCHER=%~f0"
set "CASMS_BE_ROOT=%~dp0"
set "CASMS_BE_CHECK=%~1"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$source = Get-Content -LiteralPath $env:CASMS_BE_LAUNCHER -Raw -Encoding UTF8; & ([scriptblock]::Create(($source -split '(?m)^# BEGIN_POWERSHELL\r?\n', 2)[1]))"
set "CASMS_BE_EXIT=%ERRORLEVEL%"
if /I "%~1"=="--check" exit /b %CASMS_BE_EXIT%
echo.
pause
exit /b %CASMS_BE_EXIT%
# BEGIN_POWERSHELL
$ErrorActionPreference = 'Stop'
try {
    Set-Location -LiteralPath $env:CASMS_BE_ROOT
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
    $maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
    $config = Join-Path $env:CASMS_BE_ROOT '.env.local'
    if (!(Test-Path -LiteralPath $config)) {
        throw 'Thiếu .env.local trong thư mục BE. Cần cấu hình DB_URL, DB_USERNAME và DB_PASSWORD.'
    }
    foreach ($line in Get-Content -LiteralPath $config -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    $versionProcess = New-Object Diagnostics.Process
    $versionProcess.StartInfo.FileName = $java
    $versionProcess.StartInfo.Arguments = '-version'
    $versionProcess.StartInfo.UseShellExecute = $false
    $versionProcess.StartInfo.CreateNoWindow = $true
    $versionProcess.StartInfo.RedirectStandardError = $true
    [void]$versionProcess.Start()
    $javaVersion = $versionProcess.StandardError.ReadToEnd()
    $versionProcess.WaitForExit()
    $versionProcess.Dispose()
    if ($javaVersion -notmatch 'version "21\.') { throw 'Backend cần Java 21. Hãy kiểm tra Java trong PATH.' }
    if (!$env:DB_URL -or !$env:DB_USERNAME -or !$env:DB_PASSWORD) {
        throw '.env.local chưa có đủ DB_URL, DB_USERNAME và DB_PASSWORD.'
    }
    if (!$env:DB_URL.StartsWith('jdbc:postgresql://')) { throw 'DB_URL phải là địa chỉ JDBC PostgreSQL.' }
    $databaseUri = [Uri]$env:DB_URL.Substring(5)
    $databasePort = if ($databaseUri.Port -gt 0) { $databaseUri.Port } else { 5432 }
    $socket = New-Object Net.Sockets.TcpClient
    try {
        $connection = $socket.BeginConnect($databaseUri.Host, $databasePort, $null, $null)
        if (!$connection.AsyncWaitHandle.WaitOne(3000)) { throw 'timeout' }
        $socket.EndConnect($connection)
    } catch { throw 'Không kết nối được PostgreSQL. Hãy bật dịch vụ PostgreSQL local và kiểm tra DB_URL.' }
    finally { $socket.Dispose() }
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    if ($env:CASMS_BE_CHECK -eq '--check') {
        Write-Host 'Đã kiểm tra Java 21, Maven, cấu hình và cổng PostgreSQL. Không khởi chạy BE.' -ForegroundColor Green
        exit 0
    }
    $listener = New-Object Net.Sockets.TcpClient
    try {
        $probe = $listener.BeginConnect('127.0.0.1', 8080, $null, $null)
        $occupied = $probe.AsyncWaitHandle.WaitOne(1000) -and $listener.Connected
    } finally { $listener.Dispose() }
    if ($occupied) { throw 'Cổng 8080 đang được sử dụng. Hãy dừng BE đang chạy trước khi mở file này.' }
    Write-Host 'Đang biên dịch backend...' -ForegroundColor Cyan
    & $maven -B '-Dmaven.test.skip=true' '-Dmaven.jar.forceCreation=true' package
    if ($LASTEXITCODE -ne 0) { throw 'Biên dịch BE thất bại. Xem lỗi phía trên.' }
    Write-Host 'Đang chạy BE: http://localhost:8080/api' -ForegroundColor Green
    Write-Host 'Nhấn Ctrl+C để dừng.'
    & $java -jar 'target/casms-0.0.1-SNAPSHOT.jar' '--spring.jpa.show-sql=false' '--logging.level.org.hibernate.SQL=WARN' '--logging.level.com.casms=INFO'
    exit $LASTEXITCODE
} catch {
    Write-Host ('Lỗi: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
