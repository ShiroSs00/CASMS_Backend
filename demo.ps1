<#
.SYNOPSIS
    One-command Windows local demo manager for CASMS (Backend + isolated casms_demo PostgreSQL DB).
.DESCRIPTION
    Safely resets ONLY the dedicated local `casms_demo` database, runs Flyway migrations +
    DemoCatalogSeeder (`casms-85-catalog.sql`), starts CASMS Backend in `demo` profile with real
    email disabled, seeds Scenarios A/B/C/D via real business APIs, and prints URLs, role accounts,
    business codes, and direct UI jump links.

    Your existing development database is NEVER touched.

.EXAMPLE
    .\demo.ps1 -Reset
    .\demo.ps1 -Reset -Build
    .\demo.ps1 -Verify
    .\demo.ps1 -SimulatePay DEMO-PAY-01
    .\demo.ps1 -Info
    .\demo.ps1 -Stop
#>
[CmdletBinding()]
param(
    [switch]$Reset,
    [switch]$Build,
    [switch]$Verify,
    [switch]$Info,
    [switch]$Stop,
    [switch]$StartFrontend,
    [string]$SimulatePay = '',
    [string]$EnvFile = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Ensure UTF-8 console output on Windows PowerShell
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

$RepoRoot = $PSScriptRoot
$StateFile = Join-Path $RepoRoot '.casms-demo-state.json'
$PidFile   = Join-Path $RepoRoot '.casms-demo-be.pid'
$LogOut    = Join-Path $RepoRoot 'logs\demo-backend.stdout.log'
$LogErr    = Join-Path $RepoRoot 'logs\demo-backend.stderr.log'

# Default to -Info if no action flag was supplied
if (-not ($Reset -or $Build -or $Verify -or $Info -or $Stop -or $SimulatePay -or $StartFrontend)) {
    $Info = $true
}

function Read-EnvConfig {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Thiếu file cấu hình demo: '$Path'. Hãy sao chép từ '.env.demo.example' sang '.env.demo' và điền cấu hình PostgreSQL local trước khi chạy."
    }
    $cfg = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $k = $matches[1]
            $v = $matches[2].Trim()
            if ($v.Length -ge 2 -and (($v.StartsWith('"') -and $v.EndsWith('"')) -or ($v.StartsWith("'") -and $v.EndsWith("'")))) {
                $v = $v.Substring(1, $v.Length - 2)
            }
            $cfg[$k] = $v
        }
    }
    return $cfg
}

function Assert-SafeDemoConfig {
    param([hashtable]$Cfg)

    $required = @('DEMO_DB_NAME', 'DEMO_PG_MODE', 'DEMO_PG_HOST', 'DEMO_PG_PORT', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SEPAY_API_KEY')
    foreach ($key in $required) {
        if (-not $Cfg.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($Cfg[$key])) {
            throw "Thiếu cấu hình bắt buộc '$key' trong file cấu hình demo. Dừng để bảo vệ database."
        }
    }

    $dbUrl = $Cfg['DB_URL']
    if (-not $dbUrl.StartsWith('jdbc:postgresql://')) {
        throw "DB_URL phải bắt đầu bằng 'jdbc:postgresql://' (giá trị hiện tại: '$dbUrl')."
    }

    $uri = [Uri]$dbUrl.Substring(5)
    $urlHost = $uri.Host.ToLowerInvariant()
    $urlPort = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
    $urlDb   = $uri.AbsolutePath.Trim('/')

    # 1. Host MUST be strictly local loopback (never remote Supabase/AWS/etc.)
    $allowedLocalHosts = @('localhost', '127.0.0.1')
    $cfgHost = $Cfg['DEMO_PG_HOST'].ToLowerInvariant()
    if ($urlHost -notin $allowedLocalHosts -or $cfgHost -notin $allowedLocalHosts) {
        throw "SAFETY ABORT: Chỉ cho phép instance PostgreSQL local (localhost / 127.0.0.1). Phát hiện host '$urlHost' / '$cfgHost'."
    }
    if ($urlPort -ne [int]$Cfg['DEMO_PG_PORT']) {
        throw "SAFETY ABORT: Cổng trong DB_URL ($urlPort) không khớp DEMO_PG_PORT ($($Cfg['DEMO_PG_PORT']))."
    }

    # 2. Database name MUST be strictly casms_demo (refuse dev/prod/system DBs)
    $demoDb = $Cfg['DEMO_DB_NAME']
    if ($demoDb -ne 'casms_demo' -or $urlDb -ne 'casms_demo') {
        throw "SAFETY ABORT: Script demo chỉ được phép thao tác trên database 'casms_demo'. Phát hiện DEMO_DB_NAME='$demoDb', DB_URL database='$urlDb'."
    }

    # 3. Validate PostgreSQL mode & local instance reachability
    $pgMode = $Cfg['DEMO_PG_MODE'].ToLowerInvariant()
    if ($pgMode -notin @('docker', 'psql')) {
        throw "DEMO_PG_MODE phải là 'docker' hoặc 'psql' (hiện tại: '$pgMode')."
    }
    if ($pgMode -eq 'docker') {
        if (-not $Cfg.ContainsKey('DEMO_PG_CONTAINER') -or [string]::IsNullOrWhiteSpace($Cfg['DEMO_PG_CONTAINER'])) {
            throw "DEMO_PG_MODE=docker yêu cầu khai báo DEMO_PG_CONTAINER."
        }
        $container = $Cfg['DEMO_PG_CONTAINER']
        $inspect = (& docker inspect -f '{{.State.Running}}' $container 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($inspect | Out-String).Trim() -ne 'true') {
            throw "PostgreSQL container '$container' chưa chạy. Hãy khởi động Docker container local trước khi chạy demo.ps1."
        }
    } else {
        if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
            throw "DEMO_PG_MODE=psql nhưng không tìm thấy lệnh 'psql' trong PATH."
        }
    }

    # Verify TCP connectivity to local PostgreSQL port
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $tcp.BeginConnect('127.0.0.1', $urlPort, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne(3000) -or -not $tcp.Connected) {
            throw "timeout"
        }
    } catch {
        throw "Không kết nối được PostgreSQL local tại 127.0.0.1:$urlPort."
    } finally {
        $tcp.Dispose()
    }

    return @{
        DbName      = $urlDb
        PgMode      = $pgMode
        PgContainer = $Cfg['DEMO_PG_CONTAINER']
        PgHost      = $cfgHost
        PgPort      = $urlPort
        DbUrl       = $dbUrl
        DbUser      = $Cfg['DB_USERNAME']
        DbPassword  = $Cfg['DB_PASSWORD']
        ServerPort  = if ($Cfg.ContainsKey('SERVER_PORT') -and $Cfg['SERVER_PORT']) { [int]$Cfg['SERVER_PORT'] } else { 8080 }
        SepayApiKey = $Cfg['SEPAY_API_KEY']
        WebBooking  = if ($Cfg.ContainsKey('WEB_BOOKING_URL') -and $Cfg['WEB_BOOKING_URL']) { $Cfg['WEB_BOOKING_URL'].TrimEnd('/') } else { 'http://localhost:5173' }
        WebAdmin    = if ($Cfg.ContainsKey('WEB_ADMIN_URL') -and $Cfg['WEB_ADMIN_URL']) { $Cfg['WEB_ADMIN_URL'].TrimEnd('/') } else { 'http://localhost:5174' }
        FrontendDir = if ($Cfg.ContainsKey('FRONTEND_DIR') -and $Cfg['FRONTEND_DIR']) { $Cfg['FRONTEND_DIR'] } else { '..\CASMS_Frontend' }
    }
}

function Invoke-AdminPsql {
    param([hashtable]$Conf, [string]$TargetDb, [string]$Sql)
    if ($Conf.PgMode -eq 'docker') {
        $out = $Sql | & docker exec -i $Conf.PgContainer psql -U $Conf.DbUser -d $TargetDb -v ON_ERROR_STOP=1 2>&1
        if ($LASTEXITCODE -ne 0) { throw "psql (docker/$($Conf.PgContainer)) failed: $($out | Out-String)" }
        return ($out | Out-String)
    } else {
        $prev = $env:PGPASSWORD
        $env:PGPASSWORD = $Conf.DbPassword
        try {
            $out = $Sql | & psql -h $Conf.PgHost -p $Conf.PgPort -U $Conf.DbUser -d $TargetDb -v ON_ERROR_STOP=1 2>&1
            if ($LASTEXITCODE -ne 0) { throw "psql (local) failed: $($out | Out-String)" }
            return ($out | Out-String)
        } finally {
            $env:PGPASSWORD = $prev
        }
    }
}

function Find-Java21 {
    $java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        (Get-Command java.exe -ErrorAction Stop).Source
    }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '-version'
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardError = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $ver = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($ver -notmatch 'version "(2[1-9]|[3-9]\d)\.') {
        throw "Cần Java 21 trở lên để chạy CASMS Backend (phát hiện: $ver)."
    }
    return $java
}

function Find-Maven {
    if ($env:MAVEN_HOME -and (Test-Path -LiteralPath (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'))) {
        return (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd')
    }
    if ($env:M2_HOME -and (Test-Path -LiteralPath (Join-Path $env:M2_HOME 'bin\mvn.cmd'))) {
        return (Join-Path $env:M2_HOME 'bin\mvn.cmd')
    }
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if (Test-Path -LiteralPath "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd") {
        return "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd"
    }
    throw "Không tìm thấy Maven (mvn.cmd). Kiểm tra MAVEN_HOME hoặc PATH."
}

function Stop-DemoBackend {
    param([int]$Port)
    $stopped = $false
    if (Test-Path -LiteralPath $PidFile) {
        $savedPid = (Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($savedPid -and ($savedPid -match '^\d+$')) {
            $proc = Get-Process -Id ([int]$savedPid) -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -match 'java') {
                Write-Host "Stopping previous demo backend (PID $savedPid)..." -ForegroundColor Yellow
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                $stopped = $true
            }
        }
        Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    }

    # Also stop any Java process holding casms-0.0.1-SNAPSHOT.jar or listening on $Port
    $javaProcs = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'casms-0\.0\.1-SNAPSHOT\.jar' })
    foreach ($jp in $javaProcs) {
        Write-Host "Stopping CASMS Java process (PID $($jp.ProcessId))..." -ForegroundColor Yellow
        Stop-Process -Id $jp.ProcessId -Force -ErrorAction SilentlyContinue
        $stopped = $true
    }

    if ($stopped) { Start-Sleep -Seconds 2 }

    # Verify port is free
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $tcp.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($ar.AsyncWaitHandle.WaitOne(500) -and $tcp.Connected) {
            throw "Cổng $Port đang bị tiến trình khác (không phải CASMS JAR) chiếm dụng. Hãy giải phóng cổng $Port trước."
        }
    } finally {
        $tcp.Dispose()
    }
}

function Start-DemoBackend {
    param([hashtable]$Conf, [switch]$ForceBuild)

    $java = Find-Java21
    $targetDir = Join-Path $RepoRoot 'target'
    $jarPath = Join-Path $targetDir 'casms-0.0.1-SNAPSHOT.jar'

    if ($ForceBuild -or -not (Test-Path -LiteralPath $jarPath)) {
        $maven = Find-Maven
        Write-Host '[Demo] Building CASMS Backend JAR...' -ForegroundColor Cyan
        Push-Location $RepoRoot
        try {
            & $maven -B '-Dmaven.test.skip=true' package
            if ($LASTEXITCODE -ne 0) { throw "Maven build thất bại (exit code $LASTEXITCODE)." }
        } finally {
            Pop-Location
        }
    }

    # Prepare git-tracked Flyway migrations directory (same safety convention as start-be.bat)
    $git = (Get-Command git.exe -ErrorAction Stop).Source
    $migrationFiles = @(& $git -C $RepoRoot ls-files -- 'src/main/resources/db/migration/*.sql')
    if ($LASTEXITCODE -ne 0 -or $migrationFiles.Count -eq 0) {
        throw "Không đọc được danh sách Flyway migration từ Git."
    }
    $migDir = Join-Path $targetDir 'demo-flyway-migrations'
    if (Test-Path -LiteralPath $migDir) { Remove-Item -LiteralPath $migDir -Recurse -Force }
    [void](New-Item -ItemType Directory -Path $migDir -Force)
    foreach ($mf in $migrationFiles) {
        Copy-Item -LiteralPath (Join-Path $RepoRoot $mf) -Destination $migDir
    }
    $migLocation = 'filesystem:' + $migDir.Replace('\', '/')

    $logDir = Split-Path $LogOut -Parent
    if (-not (Test-Path -LiteralPath $logDir)) { [void](New-Item -ItemType Directory -Path $logDir -Force) }

    # Set process environment variables for the child JVM (no remote DB fallback, real emails OFF)
    $env:SPRING_PROFILES_ACTIVE = 'demo'
    $env:DB_URL = $Conf.DbUrl
    $env:DB_USERNAME = $Conf.DbUser
    $env:DB_PASSWORD = $Conf.DbPassword
    $env:SPRING_DATASOURCE_URL = $Conf.DbUrl
    $env:SPRING_DATASOURCE_USERNAME = $Conf.DbUser
    $env:SPRING_DATASOURCE_PASSWORD = $Conf.DbPassword
    $env:SEPAY_ENABLED = 'true'
    $env:SEPAY_API_KEY = $Conf.SepayApiKey
    $env:CASMS_BOOKING_EMAIL_ENABLED = 'false'
    $env:CASMS_BOOKING_EMAIL_PORTAL_URL = $Conf.WebBooking

    $jvmArgs = @(
        '-jar', $jarPath,
        "--server.port=$($Conf.ServerPort)",
        '--spring.profiles.active=demo',
        "--spring.flyway.locations=$migLocation",
        '--spring.flyway.enabled=true',
        '--spring.flyway.validate-on-migrate=true',
        '--spring.jpa.hibernate.ddl-auto=validate',
        '--casms.booking-email.enabled=false',
        '--casms.employee-activation-email.enabled=false',
        '--casms.staff-assignment-email.enabled=false',
        '--logging.level.com.casms=INFO',
        '--logging.level.org.hibernate.SQL=WARN'
    )

    Write-Host "[Demo] Starting CASMS Backend (profile=demo, db=$($Conf.DbName), email=OFF)..." -ForegroundColor Cyan
    $proc = Start-Process -FilePath $java -ArgumentList $jvmArgs -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $LogOut -RedirectStandardError $LogErr -PassThru -WindowStyle Hidden
    $proc.Id | Set-Content -LiteralPath $PidFile -Encoding ASCII

    # Wait up to 180s for Flyway + DemoCatalogSeeder + Spring Boot startup
    $healthUrl = "http://127.0.0.1:$($Conf.ServerPort)/api/payment/sepay/config"
    $deadline = (Get-Date).AddSeconds(180)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) {
            $errTail = if (Test-Path $LogErr) { (Get-Content $LogErr -Tail 30) -join "`n" } else { '' }
            $outTail = if (Test-Path $LogOut) { (Get-Content $LogOut -Tail 30) -join "`n" } else { '' }
            throw "Backend dừng bất ngờ (exit $($proc.ExitCode)).`n--- STDOUT ---`n$outTail`n--- STDERR ---`n$errTail"
        }
        try {
            $resp = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 -ErrorAction Stop
            if ($resp) { $ready = $true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Backend không sẵn sàng trong 180 giây. Xem log: $LogOut và $LogErr"
    }
    Write-Host "[Demo] Backend READY (PID $($proc.Id)) at http://127.0.0.1:$($Conf.ServerPort)/api" -ForegroundColor Green
}

function Ensure-FrontendServers {
    param([hashtable]$Conf)
    $feRoot = if ([IO.Path]::IsPathRooted($Conf.FrontendDir)) { $Conf.FrontendDir } else { Join-Path $RepoRoot $Conf.FrontendDir }
    if (-not (Test-Path -LiteralPath $feRoot)) {
        Write-Host "[Demo] Không tìm thấy thư mục Frontend tại '$feRoot' (bỏ qua tự động khởi động FE)." -ForegroundColor Yellow
        return
    }
    $apps = @(
        @{ Name = 'WebBooking'; Port = 5173; Flag = '--booking' },
        @{ Name = 'WebAdmin';   Port = 5174; Flag = '--admin' }
    )
    foreach ($app in $apps) {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $up = $false
        try {
            $ar = $tcp.BeginConnect('127.0.0.1', $app.Port, $null, $null)
            $up = $ar.AsyncWaitHandle.WaitOne(500) -and $tcp.Connected
        } finally { $tcp.Dispose() }

        if ($up) {
            Write-Host "[Demo] $($app.Name) đang chạy sẵn tại http://localhost:$($app.Port)" -ForegroundColor DarkGray
        } else {
            Write-Host "[Demo] Đang mở $($app.Name) (http://localhost:$($app.Port))..." -ForegroundColor Cyan
            $bat = Join-Path $feRoot 'start-dev.bat'
            Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "`"$bat`" $($app.Flag)" -WorkingDirectory $feRoot -WindowStyle Minimized
        }
    }
}

function Show-DemoCheatSheet {
    if (-not (Test-Path -LiteralPath $StateFile)) {
        Write-Host "Chưa có dữ liệu demo (.casms-demo-state.json). Hãy chạy: .\demo.ps1 -Reset" -ForegroundColor Yellow
        return
    }
    $s = Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host ''
    Write-Host '========================================================================' -ForegroundColor Cyan
    Write-Host '  CASMS LOCAL DEMO ENVIRONMENT READY  (Database: casms_demo | Email: OFF)' -ForegroundColor Green
    Write-Host '========================================================================' -ForegroundColor Cyan
    Write-Host "  Generated (UTC) : $($s.generatedAtUtc)"
    Write-Host "  Web Booking     : $($s.urls.webBooking)"
    Write-Host "  Web Admin       : $($s.urls.webAdmin)"
    Write-Host "  Backend API     : $($s.urls.api)"
    Write-Host "  Swagger UI      : $($s.urls.swagger)"
    Write-Host ''
    Write-Host '  TAI KHOAN THEO VAI TRO (Role thật trong DB)' -ForegroundColor Yellow
    Write-Host '  - CRUISE_MANAGER           : cruise_manager     / cruise123'
    Write-Host '  - OPERATIONS_MANAGER       : operations_manager / ops123'
    Write-Host '  - ACTIVITY_SERVICE_MANAGER : activity_manager   / admin123'
    Write-Host '  - SHIP_MANAGER             : ship_manager       / ship123'
    Write-Host '  - FINANCE_OFFICER          : finance_officer    / finance123'
    Write-Host '  - BOOKING_AGENT (Check-in) : booking_agent      / booking123'
    Write-Host '  - PASSENGER (Đã TT & C-in) : passenger          / passenger123  (passenger@casms.com)'
    Write-Host '  - PASSENGER (Chờ thanh toán): john.doe          / passenger123  (john.doe@example.com)'
    Write-Host ''
    Write-Host '  MA NGHIEP VU & DUONG DAN NHAY THANG TUNG DOAN DEMO' -ForegroundColor Yellow
    Write-Host '  [A] Lap chuyen & Xet duyet:' -ForegroundColor White
    Write-Host "      - Nhap de CRM gui duyet : DEMO-DRAFT   -> $($s.jumpUrls.A_draft_crm)"
    Write-Host "      - Cho OPS phe duyet     : DEMO-REVIEW  -> $($s.jumpUrls.A_review_ops)"
    Write-Host "        (Chi tiet duyet ngay) :               -> $($s.jumpUrls.A_review_detail)"
    Write-Host '  [B] Mo ban & Dat cabin:' -ForegroundColor White
    Write-Host "      - San sang bam Mo ban   : DEMO-READY   -> $($s.jumpUrls.B_open_sale_ops)"
    Write-Host "      - Dang mo ban (khach)   : DEMO-SALE    -> $($s.jumpUrls.B_book_customer)"
    Write-Host "        (Trang tim chuyen)    :               -> $($s.jumpUrls.B_search_cruises)"
    Write-Host '  [C] Thanh toan (VietQR / SePay mo phong local - KHONG chuyen tien that):' -ForegroundColor White
    Write-Host "      - Cho thanh toan        : DEMO-PAY-01  -> $($s.jumpUrls.C_pay_pending)"
    Write-Host "        Noi dung CK / So tien : $($s.bookings.DEMO_PAY_01.transferContent) | $($s.bookings.DEMO_PAY_01.amountVnd) VND"
    Write-Host "        Mo phong tien vao     : .\demo.ps1 -SimulatePay DEMO-PAY-01"
    Write-Host "      - Da thanh toan (KQ)    : DEMO-PAID-01 -> $($s.jumpUrls.C_pay_paid)"
    Write-Host "      - So cai & Doi soat Fin :              -> $($s.jumpUrls.C_finance_ledger)"
    Write-Host '  [D] Check-in (Cua so 3 gio truoc gio chay):' -ForegroundColor White
    Write-Host "      - Chuyen & Booking      : DEMO-CHECKIN / DEMO-CHECKIN-01"
    Write-Host "      - Man hinh Check-in     :              -> $($s.jumpUrls.D_checkin_staff)"
    Write-Host '        * Jane Smith   : InProgress (da co lien he khan cap -> bam Hoan tat tren UI)'
    Write-Host '        * Nguyen Van A : NotStarted (san sang test API check-in online)'
    Write-Host '========================================================================' -ForegroundColor Cyan
    Write-Host ''
}

function Invoke-ApiRaw {
    param([string]$Base, [string]$Method, [string]$Path, [string]$Token = '', [object]$Body = $null, [hashtable]$ExtraHeaders = @{})
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }
    $p = @{ Method = $Method; Uri = "$($Base.TrimEnd('/'))$Path"; Headers = $headers; TimeoutSec = 20; ErrorAction = 'Stop'; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $p['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
        $p['ContentType'] = 'application/json; charset=utf-8'
    }
    $webResp = Invoke-WebRequest @p
    $rawText = ''
    if ($webResp.RawContentStream -and $webResp.RawContentStream.Length -gt 0) {
        $webResp.RawContentStream.Position = 0
        $reader = New-Object System.IO.StreamReader($webResp.RawContentStream, [System.Text.Encoding]::UTF8)
        $rawText = $reader.ReadToEnd()
    } else {
        $rawText = $webResp.Content
    }
    if ([string]::IsNullOrWhiteSpace($rawText)) { return $null }
    $r = $rawText | ConvertFrom-Json
    if ($null -ne $r -and $r.PSObject.Properties.Name -contains 'data') { return $r.data }
    return $r
}

function Invoke-DemoSimulatePay {
    param([hashtable]$Conf, [string]$BookingRef)
    if (-not (Test-Path -LiteralPath $StateFile)) {
        throw "Chưa có file trạng thái demo. Hãy chạy .\demo.ps1 -Reset trước."
    }
    $s = Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $apiBase = "http://127.0.0.1:$($Conf.ServerPort)/api"

    # Find booking info from state or live lookup
    $entry = $null
    if ($BookingRef -eq 'DEMO-PAY-01') { $entry = $s.bookings.DEMO_PAY_01 }
    if (-not $entry) {
        # Fallback: login as finance_officer and search booking reference
        $finTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'finance_officer'; password = 'finance123' }).accessToken
        $all = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/booking?search=$([Uri]::EscapeDataString($BookingRef))" -Token $finTok
        $items = if ($all.content) { @($all.content) } else { @($all) }
        $match = $items | Where-Object { $_.bookingReference -eq $BookingRef } | Select-Object -First 1
        if (-not $match) { throw "Không tìm thấy booking '$BookingRef' trong môi trường demo." }
        $opts = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/payment/sepay/options/$($match.bookingId)" -Token $finTok
        if (-not $opts.activePendingOrder) {
            throw "Booking '$BookingRef' chưa có lệnh VietQR đang chờ. Hãy bấm Tạo mã QR thanh toán trên UI trước."
        }
        $entry = @{
            bookingId       = $match.bookingId
            transferContent = $opts.activePendingOrder.transferContent
            amountVnd       = [decimal]$opts.activePendingOrder.amountVnd
            paymentId       = $opts.activePendingOrder.paymentId
        }
    }

    $sepayId = [int](Get-Random -Minimum 9910000 -Maximum 9999999)
    $nowVnStr = [System.TimeZoneInfo]::ConvertTimeFromUtc((Get-Date).ToUniversalTime(), [System.TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')).ToString('yyyy-MM-dd HH:mm:ss')
    Write-Host "[SimulatePay] Sending local SePay webhook for $BookingRef ($($entry.transferContent) | $($entry.amountVnd) VND)..." -ForegroundColor Cyan
    $wh = Invoke-ApiRaw -Base $apiBase -Method POST -Path '/payment/sepay/webhook' `
        -ExtraHeaders @{ 'Authorization' = "Apikey $($Conf.SepayApiKey)" } `
        -Body @{
            id              = $sepayId
            gateway         = 'MBBank'
            transactionDate = $nowVnStr
            accountNumber   = '0901452366'
            transferType    = 'in'
            transferAmount  = [decimal]$entry.amountVnd
            content         = [string]$entry.transferContent
            referenceCode   = "DEMO-FT-$sepayId"
            description     = "Local demo simulation for $BookingRef"
        }

    $finTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'finance_officer'; password = 'finance123' }).accessToken
    $bkAfter = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/booking/$($entry.bookingId)" -Token $finTok
    $payStatus = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/payment/sepay/status/$($entry.paymentId)" -Token $finTok

    Write-Host "[SimulatePay] RESULT:" -ForegroundColor Green
    Write-Host "  Webhook success : $($wh.success) | message=$($wh.message)"
    Write-Host "  Booking status  : $($bkAfter.bookingStatus) (ref=$($bkAfter.bookingReference))"
    Write-Host "  Payment status  : $($payStatus.status) (paymentId=$($entry.paymentId))"
}

function Invoke-DemoVerify {
    param([hashtable]$Conf)
    if (-not (Test-Path -LiteralPath $StateFile)) {
        throw "Chưa có file trạng thái demo. Hãy chạy .\demo.ps1 -Reset trước."
    }
    $s = Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $apiBase = "http://127.0.0.1:$($Conf.ServerPort)/api"
    $results = New-Object System.Collections.Generic.List[object]

    function Record-Check([string]$Id, [bool]$Pass, [string]$Detail) {
        $results.Add([pscustomobject]@{ Check = $Id; Result = if ($Pass) { 'PASS' } else { 'FAIL' }; Detail = $Detail })
    }

    $crmTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'cruise_manager'; password = 'cruise123' }).accessToken
    $opsTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'operations_manager'; password = 'ops123' }).accessToken
    $johnTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'john.doe'; password = 'passenger123' }).accessToken
    $paxTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'passenger'; password = 'passenger123' }).accessToken
    $chkTok = (Invoke-ApiRaw -Base $apiBase -Method POST -Path '/auth/login' -Body @{ username = 'booking_agent'; password = 'booking123' }).accessToken

    # A1: DEMO-DRAFT is DRAFT and has 0 blockers
    $draftPlan = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/cruise-plans/$($s.cruises.DEMO_DRAFT.cruiseId)" -Token $crmTok
    Record-Check 'A1: DEMO-DRAFT ready for CRM submit' ($draftPlan.planStatus -eq 'DRAFT' -and @($draftPlan.planBlockers).Count -eq 0) "planStatus=$($draftPlan.planStatus), blockers=$(@($draftPlan.planBlockers).Count)"

    # A2: OPS review queue includes DEMO-REVIEW and excludes DEMO-DRAFT
    $opsQueue = @(Invoke-ApiRaw -Base $apiBase -Method GET -Path '/cruise-plans?includeReviewed=false' -Token $opsTok)
    $hasReview = @($opsQueue | Where-Object { $_.cruiseCode -eq 'DEMO-REVIEW' }).Count -ge 1
    $hasDraft  = @($opsQueue | Where-Object { $_.cruiseCode -eq 'DEMO-DRAFT' }).Count -ge 1
    Record-Check 'A2: OPS queue has DEMO-REVIEW and hides DEMO-DRAFT' ($hasReview -and -not $hasDraft) "hasReview=$hasReview, hasDraft=$hasDraft, queueSize=$($opsQueue.Count)"

    # B1: DEMO-READY is APPROVED + READY and not yet OPEN_FOR_SALE
    $readyPlan = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/cruise-plans/$($s.cruises.DEMO_READY.cruiseId)" -Token $opsTok
    $readySale = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/cruise/$($s.cruises.DEMO_READY.cruiseId)/sale-readiness?saleScope=WHOLE_CRUISE&selectedCruisePackageOfferIds=$($s.cruises.DEMO_READY.offerId)" -Token $opsTok
    Record-Check 'B1: DEMO-READY eligible to open-sale' ($readyPlan.planStatus -eq 'APPROVED' -and $readyPlan.readyStatus -eq 'READY' -and $readySale.ready -eq $true) "planStatus=$($readyPlan.planStatus), readyStatus=$($readyPlan.readyStatus), saleReadiness=$($readySale.ready)"

    # B2: DEMO-SALE is OPEN_FOR_SALE, sellable=true, with available cabins & published packages
    $saleSell = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/cruise/$($s.cruises.DEMO_SALE.cruiseId)/sellability"
    $saleCabins = @(Invoke-ApiRaw -Base $apiBase -Method GET -Path "/booking/available-cabins?cruiseId=$($s.cruises.DEMO_SALE.cruiseId)" -Token $johnTok)
    $salePkgs = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/cruise/$($s.cruises.DEMO_SALE.cruiseId)/cruise-package-offers/available"
    Record-Check 'B2: DEMO-SALE OPEN_FOR_SALE with free cabins & prices' ($saleSell.sellable -eq $true -and $saleSell.saleState -eq 'OPEN_FOR_SALE' -and $saleCabins.Count -ge 1 -and @($salePkgs.offers).Count -ge 1) "sellable=$($saleSell.sellable), state=$($saleSell.saleState), freeCabins=$($saleCabins.Count), offers=$(@($salePkgs.offers).Count)"

    # C1: DEMO-PAY-01 eligible for payment with active VietQR
    $payOpts = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/payment/sepay/options/$($s.bookings.DEMO_PAY_01.bookingId)" -Token $johnTok
    Record-Check 'C1: DEMO-PAY-01 pending with active VietQR' ($payOpts.vietQrEligible -eq $true -and $null -ne $payOpts.activePendingOrder) "vietQrEligible=$($payOpts.vietQrEligible), transfer=$($payOpts.activePendingOrder.transferContent)"

    # C2: DEMO-PAID-01 is Confirmed with Confirmed payment
    $paidBk = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/booking/$($s.bookings.DEMO_PAID_01.bookingId)" -Token $paxTok
    $paidSt = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/payment/sepay/status/$($s.bookings.DEMO_PAID_01.paymentId)" -Token $paxTok
    Record-Check 'C2: DEMO-PAID-01 Confirmed + Payment Confirmed' ($paidBk.bookingStatus -eq 'Confirmed' -and $paidSt.status -eq 'Confirmed') "booking=$($paidBk.bookingStatus), payment=$($paidSt.status)"

    # D1: DEMO-CHECKIN-01 Confirmed, inside 3h window, Jane Smith InProgress ready to complete
    $chkBk = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/booking/$($s.bookings.DEMO_CHECKIN_01.bookingId)" -Token $paxTok
    $janeEntry = @($s.bookings.DEMO_CHECKIN_01.passengers | Where-Object { $_.fullName -eq 'Jane Smith' })[0]
    $janeStatus = Invoke-ApiRaw -Base $apiBase -Method GET -Path "/checkin/passenger/$($janeEntry.bookingPassengerId)/status" -Token $chkTok
    Record-Check 'D1: DEMO-CHECKIN-01 Confirmed & Jane Smith InProgress' ($chkBk.bookingStatus -eq 'Confirmed' -and $janeStatus.checkinStatus -eq 'InProgress') "booking=$($chkBk.bookingStatus), janeCheckin=$($janeStatus.checkinStatus)"

    Write-Host ''
    $results | Format-Table -AutoSize
    $failed = @($results | Where-Object { $_.Result -eq 'FAIL' })
    if ($failed.Count -gt 0) {
        throw "Verification failed ($($failed.Count) checks failed)."
    }
    Write-Host "ALL $($results.Count) DEMO API CHECKS PASSED!" -ForegroundColor Green
}

# ---------------- MAIN EXECUTION ----------------
try {
    $resolvedEnv = if ($EnvFile) { $EnvFile } else { Join-Path $RepoRoot '.env.demo' }
    $rawCfg = Read-EnvConfig -Path $resolvedEnv
    $conf = Assert-SafeDemoConfig -Cfg $rawCfg

    if ($Stop) {
        Stop-DemoBackend -Port $conf.ServerPort
        Write-Host '[Demo] Stopped demo backend.' -ForegroundColor Green
        exit 0
    }

    if ($Reset) {
        Write-Host "[Demo] Target instance : $($conf.PgHost):$($conf.PgPort) (mode=$($conf.PgMode), container=$($conf.PgContainer))" -ForegroundColor Cyan
        Write-Host "[Demo] Target database : $($conf.DbName) (dev DBs untouched)" -ForegroundColor Cyan

        Stop-DemoBackend -Port $conf.ServerPort

        Write-Host "[Demo] Resetting database '$($conf.DbName)' ONLY..." -ForegroundColor Yellow
        $resetSql = @"
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '$($conf.DbName)' AND pid <> pg_backend_pid();
"@
        [void](Invoke-AdminPsql -Conf $conf -TargetDb 'postgres' -Sql $resetSql)
        [void](Invoke-AdminPsql -Conf $conf -TargetDb 'postgres' -Sql "DROP DATABASE IF EXISTS `"$($conf.DbName)`";")
        [void](Invoke-AdminPsql -Conf $conf -TargetDb 'postgres' -Sql "CREATE DATABASE `"$($conf.DbName)`" ENCODING 'UTF8';")
        Write-Host "[Demo] Database '$($conf.DbName)' recreated cleanly." -ForegroundColor Green

        Start-DemoBackend -Conf $conf -ForceBuild:$Build

        $seedScript = Join-Path $RepoRoot 'scripts\seed-demo-scenarios.ps1'
        & $seedScript `
            -ApiBase "http://127.0.0.1:$($conf.ServerPort)/api" `
            -SepayApiKey $conf.SepayApiKey `
            -DbName $conf.DbName `
            -PgMode $conf.PgMode `
            -PgContainer $conf.PgContainer `
            -PgHost $conf.PgHost `
            -PgPort $conf.PgPort `
            -PgUser $conf.DbUser `
            -PgPassword $conf.DbPassword `
            -WebBookingUrl $conf.WebBooking `
            -WebAdminUrl $conf.WebAdmin `
            -StateFile $StateFile

        if ($StartFrontend) {
            Ensure-FrontendServers -Conf $conf
        }
        Show-DemoCheatSheet
    }

    if ($SimulatePay) {
        Invoke-DemoSimulatePay -Conf $conf -BookingRef $SimulatePay
    }

    if ($Verify) {
        Invoke-DemoVerify -Conf $conf
    }

    if ($StartFrontend -and -not $Reset) {
        Ensure-FrontendServers -Conf $conf
    }

    if ($Info -and -not $Reset) {
        Show-DemoCheatSheet
    }

    exit 0
} catch {
    Write-Host ''
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
