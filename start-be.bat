@echo off
setlocal
chcp 65001 >nul
set "CASMS_BE_LAUNCHER=%~f0"
set "CASMS_BE_ROOT=%~dp0"
set "CASMS_BE_CHECK=%~1"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$source = Get-Content -LiteralPath $env:CASMS_BE_LAUNCHER -Raw -Encoding UTF8; & ([scriptblock]::Create(($source -split '(?m)^# BEGIN_POWERSHELL\r?\n', 2)[1]))"
set "CASMS_BE_EXIT=%ERRORLEVEL%"
if /I "%~1"=="--check" exit /b %CASMS_BE_EXIT%
if /I "%~1"=="--no-pause" exit /b %CASMS_BE_EXIT%
echo.
pause
exit /b %CASMS_BE_EXIT%
# BEGIN_POWERSHELL
$ErrorActionPreference = 'Stop'
try {
    $repositoryRoot = $env:CASMS_BE_ROOT.TrimEnd([char[]]'\/')
    Set-Location -LiteralPath $repositoryRoot
    $config = if ($env:CASMS_BE_ENV_FILE) { $env:CASMS_BE_ENV_FILE } else { Join-Path $repositoryRoot '.env.local' }
    if (!(Test-Path -LiteralPath $config)) {
        throw 'Thiếu .env.local trong thư mục BE. Cần cấu hình DB_URL, DB_USERNAME và DB_PASSWORD.'
    }
    $configuredKeys = @{}
    foreach ($line in Get-Content -LiteralPath $config -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            $configuredKeys[$key] = $true
        }
    }
    $java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin/java.exe'
    } else { (Get-Command java.exe -ErrorAction Stop).Source }
    $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
    $maven = if ($env:MAVEN_HOME -and (Test-Path -LiteralPath (Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'))) {
        Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'
    } elseif ($env:M2_HOME -and (Test-Path -LiteralPath (Join-Path $env:M2_HOME 'bin/mvn.cmd'))) {
        Join-Path $env:M2_HOME 'bin/mvn.cmd'
    } elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) {
        (Get-Command mvn.cmd).Source
    } elseif (Test-Path -LiteralPath "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd") {
        "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd"
    } else {
        (Get-Command mvn.cmd -ErrorAction Stop).Source
    }
    $git = (Get-Command git.exe -ErrorAction Stop).Source
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
    if (!$configuredKeys.ContainsKey('DB_URL') -or !$configuredKeys.ContainsKey('DB_USERNAME') -or !$configuredKeys.ContainsKey('DB_PASSWORD') -or !$env:DB_URL -or !$env:DB_USERNAME -or !$env:DB_PASSWORD) {
        throw '.env.local chưa có đủ DB_URL, DB_USERNAME và DB_PASSWORD.'
    }
    if (!$env:DB_URL.StartsWith('jdbc:postgresql://')) { throw 'DB_URL phải là địa chỉ JDBC PostgreSQL.' }
    # The selected env file wins over Spring variables left by an earlier QA session.
    $env:SPRING_DATASOURCE_URL = $env:DB_URL
    $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME
    $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
    $databaseUri = [Uri]$env:DB_URL.Substring(5)
    if (!$databaseUri.Host -or !$databaseUri.AbsolutePath.Trim('/')) { throw 'DB_URL phải có host và tên database.' }
    $databasePort = if ($databaseUri.Port -gt 0) { $databaseUri.Port } else { 5432 }
    $socket = New-Object Net.Sockets.TcpClient
    try {
        $connection = $socket.BeginConnect($databaseUri.Host, $databasePort, $null, $null)
        if (!$connection.AsyncWaitHandle.WaitOne(3000)) { throw 'timeout' }
        $socket.EndConnect($connection)
    } catch { throw 'Không kết nối được PostgreSQL. Hãy bật dịch vụ PostgreSQL local và kiểm tra DB_URL.' }
    finally { $socket.Dispose() }
    $backendPort = 8080
    if ($env:SERVER_PORT -and (![int]::TryParse($env:SERVER_PORT, [ref]$backendPort) -or $backendPort -lt 1 -or $backendPort -gt 65535)) {
        throw 'SERVER_PORT phải là số từ 1 đến 65535.'
    }
    $migrationFiles = @(& $git -C $repositoryRoot ls-files -- 'src/main/resources/db/migration/*.sql')
    if ($LASTEXITCODE -ne 0 -or $migrationFiles.Count -eq 0) { throw 'Không đọc được migration do Git quản lý.' }
    $migrationVersions = @{}
    foreach ($migrationFile in $migrationFiles) {
        if (!(Test-Path -LiteralPath $migrationFile)) { throw ('Thiếu migration: ' + $migrationFile) }
        if ([IO.Path]::GetFileName($migrationFile) -notmatch '^V([0-9]+)__.+\.sql$') { throw ('Tên migration không được hỗ trợ: ' + $migrationFile) }
        $migrationVersion = [int]$matches[1]
        if ($migrationVersions.ContainsKey($migrationVersion)) { throw ('Trùng version migration V' + $migrationVersion) }
        $migrationVersions[$migrationVersion] = $migrationFile
    }
    $latestMigration = ($migrationVersions.Keys | Measure-Object -Maximum).Maximum
    $listener = New-Object Net.Sockets.TcpClient
    try {
        $probe = $listener.BeginConnect('127.0.0.1', $backendPort, $null, $null)
        $occupied = $probe.AsyncWaitHandle.WaitOne(1000) -and $listener.Connected
    } finally { $listener.Dispose() }
    if ($occupied) { throw ('Cổng ' + $backendPort + ' đang được sử dụng. Hãy dừng BE đang chạy trước khi mở file này.') }
    Write-Host ('Database: ' + $databaseUri.Host + ':' + $databasePort + '/' + $databaseUri.AbsolutePath.Trim('/'))
    Write-Host ('Migration từ Git: ' + $migrationFiles.Count + ' file, mới nhất V' + $latestMigration)
    if ($env:CASMS_BE_CHECK -eq '--check') {
        Write-Host 'Preflight OK: Java 21, Maven, Git, cấu hình, cổng DB/BE và danh sách migration. Chưa chạy Flyway hoặc thay đổi database.' -ForegroundColor Green
        exit 0
    }
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    Write-Host 'Đang biên dịch backend...' -ForegroundColor Cyan
    & $maven -B '-Dmaven.test.skip=true' clean package
    if ($LASTEXITCODE -ne 0) { throw 'Biên dịch BE thất bại. Xem lỗi phía trên.' }
    # Keep stale target SQL and untracked historical repair scripts out of Flyway.
    $migrationDirectory = Join-Path $repositoryRoot ('target/start-be-migrations-' + [Guid]::NewGuid().ToString('N'))
    [void](New-Item -ItemType Directory -Path $migrationDirectory)
    foreach ($migrationFile in $migrationFiles) { Copy-Item -LiteralPath $migrationFile -Destination $migrationDirectory }
    $migrationLocation = 'filesystem:' + $migrationDirectory.Replace('\', '/')
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'target') -Filter '*.jar' -File)
    if ($jars.Count -ne 1) { throw 'Không xác định được duy nhất một JAR sau khi build.' }
    Write-Host 'Flyway sẽ validate và áp dụng migration còn thiếu; Hibernate chỉ validate schema.' -ForegroundColor Cyan
    Write-Host ('Đang chạy BE: http://localhost:' + $backendPort + '/api') -ForegroundColor Green
    Write-Host 'Nhấn Ctrl+C để dừng.'
    & $java -jar $jars[0].FullName "--server.port=$backendPort" "--spring.flyway.locations=$migrationLocation" '--spring.flyway.target=latest' '--spring.flyway.enabled=true' '--spring.flyway.validate-on-migrate=true' '--spring.jpa.hibernate.ddl-auto=validate' '--spring.jpa.show-sql=false' '--logging.level.org.hibernate.SQL=WARN' '--logging.level.com.casms=INFO'
    exit $LASTEXITCODE
} catch {
    Write-Host ('Lỗi: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
