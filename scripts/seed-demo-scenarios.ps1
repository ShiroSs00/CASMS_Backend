<#
.SYNOPSIS
    Seeds deterministic, time-relative CASMS demo scenarios (A, B, C, D) via real Backend APIs.
.DESCRIPTION
    Called automatically by demo.ps1 -Reset against a fresh casms_demo database
    (after Flyway migrations and DemoCatalogSeeder / casms-85-catalog.sql have run).
    Never run against production or development databases.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiBase,

    [Parameter(Mandatory = $true)]
    [string]$SepayApiKey,

    [Parameter(Mandatory = $true)]
    [string]$DbName,

    [Parameter(Mandatory = $true)]
    [string]$PgMode,

    [string]$PgContainer = 'casms-postgres',
    [string]$PgHost = 'localhost',
    [int]$PgPort = 5432,
    [string]$PgUser = 'postgres',
    [string]$PgPassword = '',
    [string]$WebBookingUrl = 'http://localhost:5173',
    [string]$WebAdminUrl = 'http://localhost:5174',
    [string]$StateFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.casms-demo-state.json')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Defensive check: refuse non-demo DB name even if script is called directly
if ($DbName -ne 'casms_demo') {
    throw "Safety abort: seed-demo-scenarios.ps1 only supports database 'casms_demo' (got '$DbName')."
}

function Invoke-DemoSql {
    param([Parameter(Mandatory = $true)][string]$Sql)
    if ($PgMode -eq 'docker') {
        $result = $Sql | & docker exec -i $PgContainer psql -U $PgUser -d $DbName -v ON_ERROR_STOP=1 -t -A 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "SQL execution failed in container '$PgContainer': $result"
        }
        return ($result | Out-String).Trim()
    } else {
        $prevPwd = $env:PGPASSWORD
        $env:PGPASSWORD = $PgPassword
        try {
            $result = $Sql | & psql -h $PgHost -p $PgPort -U $PgUser -d $DbName -v ON_ERROR_STOP=1 -t -A 2>&1
            if ($LASTEXITCODE -ne 0) {
                throw "SQL execution failed on ${PgHost}:${PgPort}/${DbName}: $result"
            }
            return ($result | Out-String).Trim()
        } finally {
            $env:PGPASSWORD = $prevPwd
        }
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [string]$Token = '',
        [object]$Body = $null,
        [hashtable]$ExtraHeaders = @{}
    )
    $uri = "$($ApiBase.TrimEnd('/'))$Path"
    $headers = @{ 'Accept' = 'application/json' }
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] }

    $params = @{
        Method      = $Method
        Uri         = $uri
        Headers     = $headers
        TimeoutSec  = 30
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 15 -Compress
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }

    try {
        $webResp = Invoke-WebRequest @params -UseBasicParsing
        $rawText = ''
        if ($webResp.RawContentStream -and $webResp.RawContentStream.Length -gt 0) {
            $webResp.RawContentStream.Position = 0
            $reader = New-Object System.IO.StreamReader($webResp.RawContentStream, [System.Text.Encoding]::UTF8)
            $rawText = $reader.ReadToEnd()
        } else {
            $rawText = $webResp.Content
        }
        if ([string]::IsNullOrWhiteSpace($rawText)) { return $null }
        $resp = $rawText | ConvertFrom-Json
        if ($null -ne $resp -and $resp.PSObject.Properties.Name -contains 'data') {
            return $resp.data
        }
        return $resp
    } catch {
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
                $raw = $reader.ReadToEnd()
                if ($raw) { $detail = "$detail | Response: $raw" }
            } catch {}
        }
        throw "API $Method $Path failed: $detail"
    }
}

function Login-Account {
    param([string]$Username, [string]$Password)
    $data = Invoke-Api -Method POST -Path '/auth/login' -Body @{
        username = $Username
        password = $Password
    }
    if (-not $data.accessToken) {
        throw "Login failed for $Username (no accessToken returned)."
    }
    return [string]$data.accessToken
}

function Format-UtcIso {
    param([datetime]$Dt)
    return $Dt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
}

Write-Host '[Demo Seed] 1/7 Authenticating real role accounts...' -ForegroundColor Cyan
$tokens = @{
    crm       = Login-Account 'cruise_manager'     'cruise123'
    ops       = Login-Account 'operations_manager' 'ops123'
    opsMaker  = Login-Account 'ops_manager'        'ops123'
    asm       = Login-Account 'activity_manager'   'admin123'
    ship      = Login-Account 'ship_manager'       'ship123'
    agent     = Login-Account 'booking_agent'      'booking123'
    checkin   = Login-Account 'checkin_staff'      'checkin123'
    finance   = Login-Account 'finance_officer'    'finance123'
    passenger = Login-Account 'passenger'          'passenger123'
    john      = Login-Account 'john.doe'           'passenger123'
    jane      = Login-Account 'jane.smith'         'passenger123'
}

Write-Host '[Demo Seed] 2/7 Resolving seeded ships, ports, and cabin categories...' -ForegroundColor Cyan
# Ship 1: V34 flagship OceVie Paradise of the Seas (14 SUITE cabins)
# Ship 2: casms-85-catalog.sql Demo catalogue ship (4 cabins: 101 INTERIOR, 102 OCEAN_VIEW, 103 BALCONY, 104 SUITE)
$shipParadiseId = Invoke-DemoSql "SELECT ship_id FROM `"Ship`" WHERE ship_code = 'SH-PARADISE-01' LIMIT 1;"
$shipCatalogId  = Invoke-DemoSql "SELECT ship_id FROM `"Ship`" WHERE ship_code = 'DEMO-CATALOG-SHIP' LIMIT 1;"
if (-not $shipParadiseId) { throw "Missing seeded ship SH-PARADISE-01 from Flyway V34." }
if (-not $shipCatalogId)  { throw "Missing seeded ship DEMO-CATALOG-SHIP from DemoCatalogSeeder (casms-85-catalog.sql)." }

# Ensure ship_manager manages both demo ships so cabin-category mapping confirmation runs via real API
[void](Invoke-DemoSql "UPDATE `"Ship`" SET created_by_ship_manager_id = 'a0000000-0000-0000-0000-000000000003' WHERE ship_id IN ('$shipParadiseId', '$shipCatalogId') AND created_by_ship_manager_id IS NULL;")

# Resolve active Vietnamese ports seeded by Flyway V28/V33/V36
$portsPage = Invoke-Api -Method GET -Path '/ports?size=50' -Token $tokens.crm
$allPorts = if ($portsPage.content) { @($portsPage.content) } else { @($portsPage) }
$activeVnPorts = @($allPorts | Where-Object { $_.status -eq 'ACTIVE' -and $_.countryCode -eq 'VN' })
if ($activeVnPorts.Count -lt 2) {
    $activeVnPorts = @($allPorts | Where-Object { $_.status -eq 'ACTIVE' })
}
if ($activeVnPorts.Count -lt 2) {
    throw "Need at least 2 ACTIVE ports from Flyway migrations (found $($activeVnPorts.Count))."
}
$depPort = ($activeVnPorts | Where-Object { $_.portCode -match 'HPH|HLG|HAN' } | Select-Object -First 1)
if (-not $depPort) { $depPort = $activeVnPorts[0] }
$retPort = ($activeVnPorts | Where-Object { $_.portId -ne $depPort.portId -and $_.portCode -match 'DAD|NHA|SGN' } | Select-Object -First 1)
if (-not $retPort) { $retPort = ($activeVnPorts | Where-Object { $_.portId -ne $depPort.portId } | Select-Object -First 1) }

Write-Host "    Ships: SH-PARADISE-01 ($shipParadiseId), DEMO-CATALOG-SHIP ($shipCatalogId)" -ForegroundColor DarkGray
Write-Host "    Ports: $($depPort.portCode) ($($depPort.portName)) -> $($retPort.portCode) ($($retPort.portName))" -ForegroundColor DarkGray

Write-Host '[Demo Seed] 3/7 Creating reusable approved Trip & published Ship Package Templates via API...' -ForegroundColor Cyan
# 3a. Create Trip (3 days / 2 nights domestic VN route) + finalize Schedule Version 1
$tripCreated = Invoke-Api -Method POST -Path '/trips' -Token $tokens.crm -ExtraHeaders @{ 'Idempotency-Key' = "demo-trip-$([Guid]::NewGuid().ToString('N'))" } -Body @{
    name             = 'Hải trình Di sản Hạ Long - Đà Nẵng (Demo)'
    description      = 'Lịch trình mẫu phục vụ trình diễn hệ thống CASMS.'
    durationGroup    = 'LONG'
    durationDays     = 3
    durationNights   = 2
    earliestDeparture = '08:00'
    latestDeparture  = '10:00'
    tripType         = 'ONE_WAY'
    plannedScope     = 'DOMESTIC'
    domesticRegions  = @('BAC_BO', 'TRUNG_BO')
}
$tripId = [string]$tripCreated.tripId
$firstVer = @($tripCreated.versions | Select-Object -First 1)[0]
$versionId = [string]$firstVer.versionId
$versionRev = [long]$firstVer.revision

$savedContent = Invoke-Api -Method PUT -Path "/trip-schedule-versions/$versionId/content" -Token $tokens.crm -Body @{
    expectedRevision = $versionRev
    days             = @(
        @{ dayNumber = 1; dayType = 'PORT_DAY'; departurePortId = $depPort.portId; plannedDepartureTime = '08:00:00'; shipTimeZoneId = 'Asia/Ho_Chi_Minh'; title = "Khởi hành từ $($depPort.portName)"; description = 'Đón khách và xuất bến.' },
        @{ dayNumber = 2; dayType = 'SEA_DAY'; shipTimeZoneId = 'Asia/Ho_Chi_Minh'; title = 'Hải trình trên biển'; description = 'Trải nghiệm ẩm thực, spa và giải trí trên tàu.' },
        @{ dayNumber = 3; dayType = 'PORT_DAY'; shipTimeZoneId = 'Asia/Ho_Chi_Minh'; title = "Cập cảng $($retPort.portName)"; description = 'Kết thúc hải trình.' }
    )
    stops            = @(
        @{
            clientKey            = 'demo-stop-1'
            portId               = $depPort.portId
            stopOrder            = 1
            arrivalDayNumber     = 1
            plannedArrivalTime   = '09:30:00'
            departureDayNumber   = 1
            plannedDepartureTime = '11:30:00'
            callType             = 'NORMAL'
            arrivalMethod        = 'BERTH'
            allAboardDayNumber   = 1
            allAboardTime        = '11:00:00'
        },
        @{
            clientKey            = 'demo-stop-2'
            portId               = $retPort.portId
            stopOrder            = 2
            arrivalDayNumber     = 3
            plannedArrivalTime   = '08:00:00'
            departureDayNumber   = 3
            plannedDepartureTime = '11:00:00'
            callType             = 'NORMAL'
            arrivalMethod        = 'BERTH'
            allAboardDayNumber   = 3
            allAboardTime        = '10:30:00'
            isDisembarkation     = $true
        }
    )
    entries          = @()
}
$finalizedVer = Invoke-Api -Method POST -Path "/trip-schedule-versions/$versionId/finalize" -Token $tokens.crm -Body @{
    expectedRevision = [long]$savedContent.revision
}
$sourceVersionId = $finalizedVer.versionId
$sourceVersionRev = [long]$finalizedVer.revision
Write-Host "    Approved Trip Schedule Version: $sourceVersionId (rev $sourceVersionRev)" -ForegroundColor DarkGray

# 3b. Create & publish Ship Package Templates via real API (ASM), then confirm cabin mapping (SHIP_MANAGER)
$demoBenefits = @()

function New-PublishedPackageTemplate {
    param([string]$Code, [string]$Name, [string]$ShipId, [string[]]$CabinTypes)
    $created = Invoke-Api -Method POST -Path '/cruise-package-templates/with-version' -Token $tokens.asm -Body @{
        code    = $Code
        version = @{
            expectedTemplateRevision = 0
            name                     = $Name
            description              = 'Gói tiêu chuẩn dùng cho bộ dữ liệu demo CASMS.'
            sourceShipId             = $ShipId
            cabinTypes               = $CabinTypes
            benefits                 = $demoBenefits
        }
    }
    $verId = [string]$created.version.id
    $verRev = [long]$created.version.revision
    $pub = Invoke-Api -Method POST -Path "/cruise-package-templates/versions/$verId/publish" -Token $tokens.asm -Body @{
        expectedRevision = $verRev
    }
    $refs = @($pub.logicalCategoryReferences)
    $suiteRef = ($refs | Where-Object { $_.cabinType -eq 'SUITE' } | Select-Object -First 1)
    if (-not $suiteRef) { $suiteRef = $refs[0] }
    return @{
        versionId     = [string]$pub.version.id
        suiteCategory = $suiteRef
    }
}

function Confirm-ShipCabinMapping {
    param([string]$ShipId)
    $mapView = Invoke-Api -Method GET -Path "/ships/$ShipId/cruise-package-cabin-mapping" -Token $tokens.ship
    $assignments = @()
    foreach ($cat in @($mapView.categories)) {
        $matchingCabins = @($mapView.cabins | Where-Object { $_.cabin_type -eq $cat.cabinType } | ForEach-Object { [string]$_.cabin_id })
        if ($matchingCabins.Count -gt 0) {
            $assignments += @{
                logicalCategoryId = [string]$cat.id
                cabinIds          = $matchingCabins
            }
        }
    }
    if ($assignments.Count -gt 0) {
        $expectedMappingId = if ($mapView.confirmation) { [string]$mapView.confirmation.id } else { $null }
        [void](Invoke-Api -Method POST -Path "/ships/$ShipId/cruise-package-cabin-mapping/confirm" -Token $tokens.ship -Body @{
            expectedMappingId           = $expectedMappingId
            expectedStructureFingerprint = [string]$mapView.structureFingerprint
            assignments                 = $assignments
        })
    }
}

$tplParadise = New-PublishedPackageTemplate -Code 'DEMO-PKG-PARADISE' -Name 'Gói Suite Tiêu Chuẩn OceVie (Demo)' -ShipId $shipParadiseId -CabinTypes @('SUITE')
$tplCatalog  = New-PublishedPackageTemplate -Code 'DEMO-PKG-CATALOG'  -Name 'Gói Cabin Tiêu Chuẩn Demo' -ShipId $shipCatalogId  -CabinTypes @('INTERIOR', 'OCEAN_VIEW', 'BALCONY', 'SUITE')
Confirm-ShipCabinMapping -ShipId $shipParadiseId
Confirm-ShipCabinMapping -ShipId $shipCatalogId
$tplParadiseVerId = $tplParadise.versionId
$tplCatalogVerId  = $tplCatalog.versionId
$paradiseSuiteCat = $tplParadise.suiteCategory
$catalogSuiteCat  = $tplCatalog.suiteCategory
Write-Host "    Published Package Templates & Confirmed Cabin Mappings: Paradise=$tplParadiseVerId, Catalog=$tplCatalogVerId" -ForegroundColor DarkGray

# 3c. Seed active Operational Regulations (3 GLOBAL + 2 CRUISE) BEFORE building cruises so readiness fingerprints stay 100% valid
$regNowUtc = (Get-Date).ToUniversalTime()
$regFrom   = Format-UtcIso ($regNowUtc.AddDays(-30))
$regTo     = Format-UtcIso ($regNowUtc.AddDays(365))

function New-ActiveRegulation {
    param([string]$Name, [string]$Desc, [string]$Type, [string]$Scope, [string]$Value, [string]$Unit)
    $reg = Invoke-Api -Method POST -Path '/operational-regulations' -Token $tokens.ops -Body @{
        name           = $Name
        description    = $Desc
        regulationType = $Type
        scope          = $Scope
        value          = $Value
        unit           = $Unit
        effectiveFrom  = $regFrom
        effectiveTo    = $regTo
    }
    $regId = [string]$reg.regulationId
    [void](Invoke-Api -Method PATCH -Path "/operational-regulations/$regId/activate" -Token $tokens.ops)
    return $regId
}

[void](New-ActiveRegulation -Name 'Tốc độ hành trình an toàn tối đa' -Desc 'Giới hạn tốc độ di chuyển an toàn trong vùng biển ven bờ và khu vực luồng cảng.' -Type 'SPEED' -Scope 'GLOBAL' -Value '18' -Unit 'KNOT')
[void](New-ActiveRegulation -Name 'Dự phòng áo phao cứu sinh bắt buộc' -Desc 'Số lượng áo phao đạt chuẩn trên tàu phải đạt tối thiểu 110% tổng số hành khách và thuyền viên.' -Type 'SAFETY' -Scope 'GLOBAL' -Value '110' -Unit 'PERCENT')
[void](New-ActiveRegulation -Name 'Chuẩn bảo quản thực phẩm kho lạnh bếp tàu' -Desc 'Nhiệt độ kho lạnh thực phẩm tươi sống duy trì không vượt quá 4 độ C xuyên suốt hải trình.' -Type 'HEALTH' -Scope 'GLOBAL' -Value '4' -Unit 'CELSIUS')

$regWeatherId = New-ActiveRegulation -Name 'Giới hạn độ cao sóng tuyến Hạ Long – Đà Nẵng' -Desc 'Tạm hoãn hoặc điều chỉnh tốc độ hành trình khi độ cao sóng dự báo vượt ngưỡng 3 mét.' -Type 'WEATHER' -Scope 'CRUISE' -Value '3' -Unit 'METER'
$regEnvId     = New-ActiveRegulation -Name 'Cam kết không xả thải nhựa vùng di sản vịnh' -Desc 'Thu gom 100% rác thải nhựa tái chế tại cảng đích, nghiêm cấm xả thải xuống vùng biển di sản.' -Type 'ENVIRONMENT' -Scope 'CRUISE' -Value '100' -Unit 'PERCENT'
Write-Host "    Active Operational Regulations: 3 GLOBAL + 2 CRUISE ($regWeatherId, $regEnvId)" -ForegroundColor DarkGray

# Helper: build a cruise through the exact real mainflow up to a target stage
# Stages: DRAFT | SUBMITTED | READY | OPEN_FOR_SALE
function New-DemoCruise {
    param(
        [string]$BusinessCode,
        [string]$CruiseName,
        [string]$ShipId,
        [string]$TemplateVersionId,
        [string]$CabinType,
        [object]$LogicalCategory,
        [datetime]$DepartureUtc,
        [datetime]$BookingOpensUtc,
        [datetime]$BookingClosesUtc,
        [decimal]$PriceVnd,
        [int]$ExpectedPassengers,
        [ValidateSet('DRAFT', 'SUBMITTED', 'READY', 'OPEN_FOR_SALE')]
        [string]$TargetStage
    )

    Write-Host "  -> Creating cruise $BusinessCode ($TargetStage)..." -ForegroundColor White
    $created = Invoke-Api -Method POST -Path '/cruise' -Token $tokens.crm -ExtraHeaders @{ 'Idempotency-Key' = "demo-cruise-$BusinessCode-$([Guid]::NewGuid().ToString('N'))" } -Body @{
        cruiseName                    = $CruiseName
        shipId                        = $ShipId
        expectedPassengerCount        = $ExpectedPassengers
        departureDatetime             = (Format-UtcIso $DepartureUtc)
        sourceVersionId               = $sourceVersionId
        expectedSourceVersionRevision = $sourceVersionRev
        itineraryMode                 = 'FIXED'
        plannedScope                  = 'DOMESTIC'
    }
    $cruiseId = [string]$created.cruiseId

    # Assign deterministic business code BEFORE submit/fingerprint computation so cryptographic hashes stay 100% valid
    [void](Invoke-DemoSql "UPDATE `"Cruise`" SET cruise_code = '$BusinessCode' WHERE cruise_id = '$cruiseId';")

    # Assign 2 CRUISE-scoped operational regulations while still DRAFT so readiness fingerprints stay valid
    [void](Invoke-Api -Method POST -Path "/cruises/$cruiseId/regulations" -Token $tokens.crm -Body @{ cruiseId = $cruiseId; regulationId = $regWeatherId })
    [void](Invoke-Api -Method POST -Path "/cruises/$cruiseId/regulations" -Token $tokens.crm -Body @{ cruiseId = $cruiseId; regulationId = $regEnvId })

    # CRM sets & submits operating budget -> OPS approves operating budget (required before work handoff)
    $budget = Invoke-Api -Method GET -Path "/cruise/$cruiseId/preparation/operating-budget" -Token $tokens.crm
    $budget = Invoke-Api -Method PUT -Path "/cruise/$cruiseId/preparation/operating-budget" -Token $tokens.crm -Body @{
        expectedRevision = [long]$budget.revision
        amountVnd        = '25000000'
    }
    $budget = Invoke-Api -Method POST -Path "/cruise/$cruiseId/preparation/operating-budget/submit" -Token $tokens.crm -Body @{
        expectedRevision       = [long]$budget.revision
        expectedReviewRevision = [long]$budget.reviewRevision
    }
    [void](Invoke-Api -Method POST -Path "/cruise/$cruiseId/preparation/operating-budget/review" -Token $tokens.ops -Body @{
        expectedReviewRevision = [long]$budget.reviewRevision
        decision               = 'APPROVE'
        reason                 = "Phê duyệt ngân sách vận hành cho chuyến demo $BusinessCode"
    })

    # CRM sends work handoff to ASM -> ASM accepts so ASM has authorized scope on this cruise
    $panel = Invoke-Api -Method POST -Path "/cruise-work/$cruiseId/ASM" -Token $tokens.crm -Body @{
        request = "Chuẩn bị gói du thuyền và giá bán chuẩn cho chuyến demo $BusinessCode."
    }
    $asmTask = @($panel.tasks | Where-Object { $_.group -eq 'ASM' } | Select-Object -First 1)[0]
    $asmRev = if ($asmTask) { [long]$asmTask.revision } else { 0L }
    $acceptedTask = Invoke-Api -Method PUT -Path "/cruise-work/$cruiseId/ASM" -Token $tokens.asm -Body @{
        action   = 'ACCEPT'
        revision = $asmRev
        response = ''
    }

    # CRM sets booking & guidance windows FIRST so package salesStart/salesEnd fall cleanly inside booking window
    $plan = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId" -Token $tokens.crm
    $guidanceOpens = $DepartureUtc.AddHours(-3)
    $guidanceCloses = $DepartureUtc.AddMinutes(-30)
    $plan = Invoke-Api -Method PUT -Path "/cruise-plans/$cruiseId/windows" -Token $tokens.crm -Body @{
        expectedRevision = [long]$plan.windows.expectedRevision
        bookingOpensAt   = (Format-UtcIso $BookingOpensUtc)
        bookingClosesAt  = (Format-UtcIso $BookingClosesUtc)
        guidanceOpensAt  = (Format-UtcIso $guidanceOpens)
        guidanceClosesAt = (Format-UtcIso $guidanceCloses)
    }

    # Configure Cruise Package Offer + VND Price + Submit (ASM)
    # Note: offer publish happens AFTER OPS approves plan & price (FARE_OFFER_INELIGIBLE requires approved price)
    $offerView = Invoke-Api -Method POST -Path "/cruise/$cruiseId/cruise-package-offers" -Token $tokens.asm -Body @{
        templateVersionId = $TemplateVersionId
        logicalCategory   = $LogicalCategory.name
        logicalCategoryId = $LogicalCategory.id
        proposedCabinType = $CabinType
        salesStart        = (Format-UtcIso $BookingOpensUtc)
        salesEnd          = (Format-UtcIso $BookingClosesUtc)
    }
    $offerId = [string]$offerView.offer.id
    $offerRev = [long]$offerView.offer.revision

    $offerView = Invoke-Api -Method POST -Path "/cruise/$cruiseId/cruise-package-offers/$offerId/prices" -Token $tokens.asm -Body @{
        expectedOfferRevision = $offerRev
        amount                = $PriceVnd
    }
    $priceRow = @($offerView.prices | Select-Object -Last 1)[0]
    $priceId = [string]$priceRow.id
    $priceRev = [long]$priceRow.revision

    $offerView = Invoke-Api -Method POST -Path "/cruise/$cruiseId/cruise-package-offers/$offerId/prices/$priceId/submit" -Token $tokens.asm -Body @{
        expectedRevision = $priceRev
        reason           = 'Đề xuất mức giá demo chuẩn VND'
    }
    $approvedPriceId = $priceId

    # ASM completes handoff back to CRM
    [void](Invoke-Api -Method PUT -Path "/cruise-work/$cruiseId/ASM" -Token $tokens.asm -Body @{
        action   = 'COMPLETE'
        revision = [long]$acceptedTask.revision
        response = "Đã phát hành gói du thuyền và đề xuất giá $PriceVnd VND cho $BusinessCode."
    })

    # CRM selects the package offer in the cruise plan
    $pkgSel = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId/cruise-packages" -Token $tokens.crm
    [void](Invoke-Api -Method PUT -Path "/cruise-plans/$cruiseId/cruise-packages" -Token $tokens.crm -Body @{
        expectedSelectionRevision = [long]$pkgSel.selectionRevision
        selections                = @(
            @{ offerId = $offerId; priceRevisionId = $approvedPriceId }
        )
    })

    # CRM requests sale terms setup -> ops_manager (maker) saves tax + port fee so operations_manager (checker) can approve without SELF_APPROVAL_FORBIDDEN!
    [void](Invoke-Api -Method POST -Path "/cruise-plans/$cruiseId/sale-terms-request" -Token $tokens.crm)
    $saleTerms = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId/commercial-terms/sale" -Token $tokens.opsMaker
    [void](Invoke-Api -Method PUT -Path "/cruise-plans/$cruiseId/commercial-terms/sale" -Token $tokens.opsMaker -Body @{
        expectedRevision = [long]$saleTerms.revision
        terms            = @{
            tax     = @{ applicability = 'APPLICABLE'; value = 0.10; reason = 'Thuế GTGT 10% theo quy định demo'; evidence = 'DEMO-TAX-2026' }
            portFee = @{ applicability = 'APPLICABLE'; value = 350000; reason = 'Phí cảng hành khách nội địa'; evidence = 'DEMO-PORT-2026' }
        }
    })

    $plan = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId" -Token $tokens.crm

    if ($TargetStage -eq 'DRAFT') {
        return @{
            cruiseId        = $cruiseId
            cruiseCode      = $BusinessCode
            cruiseName      = $CruiseName
            offerId         = $offerId
            priceRevisionId = $approvedPriceId
            planStatus      = $plan.planStatus
            readyStatus     = $plan.readyStatus
            departureUtc    = (Format-UtcIso $DepartureUtc)
            planBlockers    = @($plan.planBlockers)
        }
    }

    # CRM submits plan + coordinator dossier sections
    $prep = Invoke-Api -Method GET -Path "/cruise/$cruiseId/preparation" -Token $tokens.crm
    $prepRevs = @{}
    foreach ($sec in @($prep.sections)) {
        if ($sec.sectionCode -in @('HOTEL_CABIN', 'ENTERTAINMENT_SHORE')) {
            $prepRevs[$sec.sectionCode] = [long]$sec.revision
        }
    }
    $plan = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId" -Token $tokens.crm
    $plan = Invoke-Api -Method POST -Path "/cruise-plans/$cruiseId/submit" -Token $tokens.crm -Body @{
        expectedRevision     = [long]$plan.revision
        reason               = "Gửi hồ sơ kế hoạch $BusinessCode xét duyệt vận hành"
        fingerprint          = [string]$plan.fingerprint
        preparationEvidence  = "Hồ sơ chuẩn bị đầy đủ cho chuyến demo $BusinessCode"
        preparationRevisions = $prepRevs
    }

    if ($TargetStage -eq 'SUBMITTED') {
        return @{
            cruiseId        = $cruiseId
            cruiseCode      = $BusinessCode
            cruiseName      = $CruiseName
            offerId         = $offerId
            priceRevisionId = $approvedPriceId
            planStatus      = $plan.planStatus
            readyStatus     = $plan.readyStatus
            departureUtc    = (Format-UtcIso $DepartureUtc)
        }
    }

    # OPS approves plan (unified review also handles price; approve price explicitly if still SUBMITTED)
    $plan = Invoke-Api -Method POST -Path "/cruise-plans/$cruiseId/approve" -Token $tokens.ops -Body @{
        expectedRevision = [long]$plan.revision
        reason           = "OPS phê duyệt kế hoạch $BusinessCode"
        fingerprint      = [string]$plan.fingerprint
    }

    $offersAfter = @(Invoke-Api -Method GET -Path "/cruise/$cruiseId/cruise-package-offers" -Token $tokens.ops)
    $myOffer = ($offersAfter | Where-Object { $_.offer.id -eq $offerId } | Select-Object -First 1)
    if ($myOffer) {
        $pRow = ($myOffer.prices | Where-Object { $_.id -eq $approvedPriceId } | Select-Object -First 1)
        if ($pRow -and $pRow.status -eq 'SUBMITTED') {
            $myOffer = Invoke-Api -Method POST -Path "/cruise/$cruiseId/cruise-package-offers/$offerId/prices/$approvedPriceId/approve" -Token $tokens.ops -Body @{
                expectedRevision = [long]$pRow.revision
                reason           = 'Phê duyệt giá mở bán demo'
            }
        }
        if ($myOffer.offer.status -ne 'PUBLISHED') {
            [void](Invoke-Api -Method POST -Path "/cruise/$cruiseId/cruise-package-offers/$offerId/publish" -Token $tokens.asm -Body @{
                expectedRevision = [long]$myOffer.offer.revision
            })
        }
    }

    # OPS confirms READY (Step 2)
    $plan = Invoke-Api -Method GET -Path "/cruise-plans/$cruiseId" -Token $tokens.ops
    $plan = Invoke-Api -Method POST -Path "/cruise-plans/$cruiseId/ready" -Token $tokens.ops -Body @{
        expectedRevision = [long]$plan.revision
        reason           = "Xác nhận sẵn sàng mở bán cho $BusinessCode"
        fingerprint      = [string]$plan.fingerprint
    }

    if ($TargetStage -eq 'READY') {
        return @{
            cruiseId        = $cruiseId
            cruiseCode      = $BusinessCode
            cruiseName      = $CruiseName
            offerId         = $offerId
            priceRevisionId = $approvedPriceId
            planStatus      = $plan.planStatus
            readyStatus     = $plan.readyStatus
            departureUtc    = (Format-UtcIso $DepartureUtc)
        }
    }

    # OPS opens for sale (Step 3) — pass identical saleScope & selectedCruisePackageOfferIds to sale-readiness so fingerprint matches
    $saleReady = Invoke-Api -Method GET -Path "/cruise/$cruiseId/sale-readiness?saleScope=WHOLE_CRUISE&selectedCruisePackageOfferIds=$offerId" -Token $tokens.ops
    [void](Invoke-Api -Method POST -Path "/cruise/$cruiseId/open-sale" -Token $tokens.ops -Body @{
        expectedRevision       = [long]$saleReady.cruiseRevision
        saleScope              = 'WHOLE_CRUISE'
        packageIds             = @()
        cruisePackageOfferIds  = @($offerId)
        fingerprint            = [string]$saleReady.fingerprint
        eventIdentity          = "demo-open-sale-$BusinessCode"
    })

    # Assign booking_agent and checkin_staff to OPEN_FOR_SALE cruises so both roles have operational scope in API & WebAdmin UI
    $assignStaffSql = @"
INSERT INTO "CruiseStaff" (cruise_staff_id, cruise_id, user_id, role_in_cruise, status, assigned_by_user_id)
VALUES
  (gen_random_uuid(), '$cruiseId', 'a0000000-0000-0000-0000-000000000006', 'BOOKING_AGENT', 'ACTIVE', 'a0000000-0000-0000-0000-000000000001'),
  (gen_random_uuid(), '$cruiseId', 'a0000000-0000-0000-0000-000000000007', 'CHECKIN_STAFF', 'ACTIVE', 'a0000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;
"@
    [void](Invoke-DemoSql $assignStaffSql)

    return @{
        cruiseId        = $cruiseId
        cruiseCode      = $BusinessCode
        cruiseName      = $CruiseName
        offerId         = $offerId
        priceRevisionId = $approvedPriceId
        planStatus      = 'APPROVED'
        readyStatus     = 'READY'
        saleState       = 'OPEN_FOR_SALE'
        departureUtc    = (Format-UtcIso $DepartureUtc)
    }
}

# Dynamic reference clock (Asia/Ho_Chi_Minh aligned morning departure 09:00 VN = 02:00 UTC)
$nowUtc = (Get-Date).ToUniversalTime()
$baseDayUtc = [datetime]::new($nowUtc.Year, $nowUtc.Month, $nowUtc.Day, 2, 0, 0, [DateTimeKind]::Utc)

Write-Host '[Demo Seed] 4/7 Building Scenarios A & B cruises via real lifecycle APIs...' -ForegroundColor Cyan

# Scenario A1: DRAFT (CRM edits and submits directly; MUST NOT appear in OPS review queue)
$cruiseDraft = New-DemoCruise `
    -BusinessCode 'DEMO-DRAFT' `
    -CruiseName 'Hải trình Hạ Long - Đà Nẵng (Bản nháp chờ gửi)' `
    -ShipId $shipParadiseId `
    -TemplateVersionId $tplParadiseVerId `
    -CabinType 'SUITE' `
    -LogicalCategory $paradiseSuiteCat `
    -DepartureUtc ($baseDayUtc.AddDays(44)) `
    -BookingOpensUtc ($nowUtc.AddDays(-1)) `
    -BookingClosesUtc ($baseDayUtc.AddDays(42)) `
    -PriceVnd 12500000 `
    -ExpectedPassengers 12 `
    -TargetStage 'DRAFT'

# Scenario A2: SUBMITTED (Appears in OPS /admin/cruise-plan-reviews ready to approve live)
$cruiseReview = New-DemoCruise `
    -BusinessCode 'DEMO-REVIEW' `
    -CruiseName 'Hải trình Hạ Long - Đà Nẵng (Chờ OPS xét duyệt)' `
    -ShipId $shipParadiseId `
    -TemplateVersionId $tplParadiseVerId `
    -CabinType 'SUITE' `
    -LogicalCategory $paradiseSuiteCat `
    -DepartureUtc ($baseDayUtc.AddDays(34)) `
    -BookingOpensUtc ($nowUtc.AddDays(-1)) `
    -BookingClosesUtc ($baseDayUtc.AddDays(32)) `
    -PriceVnd 13200000 `
    -ExpectedPassengers 12 `
    -TargetStage 'SUBMITTED'

# Scenario B1: READY (Approved + Sale Terms + Ready -> OPS clicks "Mở bán" live)
$cruiseReady = New-DemoCruise `
    -BusinessCode 'DEMO-READY' `
    -CruiseName 'Hải trình Hạ Long - Đà Nẵng (Sẵn sàng mở bán)' `
    -ShipId $shipParadiseId `
    -TemplateVersionId $tplParadiseVerId `
    -CabinType 'SUITE' `
    -LogicalCategory $paradiseSuiteCat `
    -DepartureUtc ($baseDayUtc.AddDays(24)) `
    -BookingOpensUtc ($nowUtc.AddDays(-1)) `
    -BookingClosesUtc ($baseDayUtc.AddDays(22)) `
    -PriceVnd 14000000 `
    -ExpectedPassengers 12 `
    -TargetStage 'READY'

# Scenario B2 + C: OPEN_FOR_SALE (Publicly bookable, 14 days out, 12+ cabins free after 2 demo bookings)
$cruiseSale = New-DemoCruise `
    -BusinessCode 'DEMO-SALE' `
    -CruiseName 'Hải trình Hạ Long - Đà Nẵng OceVie (Đang mở bán)' `
    -ShipId $shipParadiseId `
    -TemplateVersionId $tplParadiseVerId `
    -CabinType 'SUITE' `
    -LogicalCategory $paradiseSuiteCat `
    -DepartureUtc ($baseDayUtc.AddDays(14)) `
    -BookingOpensUtc ($nowUtc.AddDays(-1)) `
    -BookingClosesUtc ($baseDayUtc.AddDays(12)) `
    -PriceVnd 11500000 `
    -ExpectedPassengers 12 `
    -TargetStage 'OPEN_FOR_SALE'

Write-Host '[Demo Seed] 5/7 Building Scenario C bookings (DEMO-PAY-01 pending VietQR & DEMO-PAID-01 paid)...' -ForegroundColor Cyan

# Pick 2 distinct available cabins on SH-PARADISE-01 for DEMO-SALE
$saleCabins = @(Invoke-Api -Method GET -Path "/booking/available-cabins?cruiseId=$($cruiseSale.cruiseId)" -Token $tokens.john)
if ($saleCabins.Count -lt 3) {
    throw "Expected at least 3 available cabins on DEMO-SALE (found $($saleCabins.Count))."
}
$cabinPay01  = $saleCabins[0].cabinId
$cabinPaid01 = $saleCabins[1].cabinId

function New-DemoBooking {
    param(
        [string]$BusinessRef,
        [string]$Token,
        [string]$CruiseId,
        [string]$OfferId,
        [string]$PriceRevisionId,
        [array]$Passengers,
        [string]$Notes
    )
    $quotePassengers = @(
        foreach ($p in $Passengers) {
            @{
                cabinId                    = $p.cabinId
                farePackageOfferId         = $p.farePackageOfferId
                farePackagePriceRevisionId = $p.farePackagePriceRevisionId
                cabinSelectionMode         = $p.cabinSelectionMode
            }
        }
    )
    $quote = Invoke-Api -Method POST -Path '/booking/quotes' -Token $Token -Body @{
        cruiseId    = $CruiseId
        currency    = 'VND'
        paymentPlan = 'FULL'
        passengers  = $quotePassengers
    }
    $created = Invoke-Api -Method POST -Path '/booking' -Token $Token -ExtraHeaders @{ 'Idempotency-Key' = "demo-booking-$BusinessRef-$([Guid]::NewGuid().ToString('N'))" } -Body @{
        cruiseId    = $CruiseId
        quoteId     = [string]$quote.quoteId
        currency    = 'VND'
        paymentPlan = 'FULL'
        notes       = $Notes
        passengers  = $Passengers
    }
    $bookingId = [string]$created.bookingId
    # Set memorable business reference BEFORE VietQR checkout so transferContent matches cleanly
    [void](Invoke-DemoSql "UPDATE `"Booking`" SET booking_reference = '$BusinessRef' WHERE booking_id = '$bookingId';")
    # Accept hard-copy contract via real API as booking_agent (BOOKING_CONTRACT_MANAGE)
    [void](Invoke-Api -Method POST -Path "/booking/$bookingId/contract/accept" -Token $tokens.agent -Body @{
        documentReference = "DOC-CTR-$BusinessRef-DEMO"
    })
    return (Invoke-Api -Method GET -Path "/booking/$bookingId" -Token $Token)
}

# C1: DEMO-PAY-01 (john.doe) -> Contract Accepted + Active VietQR Checkout ready for live payment demo
$bookingPay = New-DemoBooking `
    -BusinessRef 'DEMO-PAY-01' `
    -Token $tokens.john `
    -CruiseId $cruiseSale.cruiseId `
    -OfferId $cruiseSale.offerId `
    -PriceRevisionId $cruiseSale.priceRevisionId `
    -Notes 'Đặt chỗ demo sẵn sàng thanh toán VietQR' `
    -Passengers @(
        @{
            fullName                   = 'John Doe'
            dateOfBirth                = '1990-01-01'
            nationality                = 'USA'
            documentType               = 'Passport'
            documentNumber             = 'P12345678'
            email                      = 'john.doe@example.com'
            phoneNumber                = '+84901234001'
            cabinId                    = $cabinPay01
            farePackageOfferId         = $cruiseSale.offerId
            farePackagePriceRevisionId = $cruiseSale.priceRevisionId
            cabinSelectionMode         = 'PHYSICAL_CABIN'
        }
    )

$checkoutPay = Invoke-Api -Method POST -Path '/payment/sepay/checkout' -Token $tokens.john -Body @{
    bookingId   = $bookingPay.bookingId
    paymentType = 'FULL'
    currency    = 'VND'
}

# C2: DEMO-PAID-01 (passenger) -> Contract Accepted + VietQR Checkout + Settled via real SePay Webhook
$bookingPaid = New-DemoBooking `
    -BusinessRef 'DEMO-PAID-01' `
    -Token $tokens.passenger `
    -CruiseId $cruiseSale.cruiseId `
    -OfferId $cruiseSale.offerId `
    -PriceRevisionId $cruiseSale.priceRevisionId `
    -Notes 'Đặt chỗ demo đã thanh toán hoàn tất qua SePay' `
    -Passengers @(
        @{
            fullName                   = 'Nguyen Van A'
            dateOfBirth                = '1995-05-15'
            nationality                = 'VNM'
            documentType               = 'NationalID'
            documentNumber             = '012345678901'
            email                      = 'passenger@casms.com'
            phoneNumber                = '+84901234567'
            cabinId                    = $cabinPaid01
            farePackageOfferId         = $cruiseSale.offerId
            farePackagePriceRevisionId = $cruiseSale.priceRevisionId
            cabinSelectionMode         = 'PHYSICAL_CABIN'
        }
    )

$checkoutPaid = Invoke-Api -Method POST -Path '/payment/sepay/checkout' -Token $tokens.passenger -Body @{
    bookingId   = $bookingPaid.bookingId
    paymentType = 'FULL'
    currency    = 'VND'
}

$nowVnStr = [System.TimeZoneInfo]::ConvertTimeFromUtc((Get-Date).ToUniversalTime(), [System.TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')).ToString('yyyy-MM-dd HH:mm:ss')
$webhookPaid = Invoke-Api -Method POST -Path '/payment/sepay/webhook' -ExtraHeaders @{ 'Authorization' = "Apikey $SepayApiKey" } -Body @{
    id              = 9900001
    gateway         = 'MBBank'
    transactionDate = $nowVnStr
    accountNumber   = '0901452366'
    transferType    = 'in'
    transferAmount  = [decimal]$checkoutPaid.amountVnd
    content         = [string]$checkoutPaid.transferContent
    referenceCode   = 'DEMO-FT-PAID-01'
    description     = 'Demo local SePay settlement for DEMO-PAID-01'
}
$bookingPaidAfter = Invoke-Api -Method GET -Path "/booking/$($bookingPaid.bookingId)" -Token $tokens.passenger
Write-Host "    DEMO-PAY-01:  status=$($bookingPay.bookingStatus), transferContent=$($checkoutPay.transferContent), amountVnd=$($checkoutPay.amountVnd)" -ForegroundColor DarkGray
Write-Host "    DEMO-PAID-01: status=$($bookingPaidAfter.bookingStatus), webhookSuccess=$($webhookPaid.success), paymentId=$($checkoutPaid.paymentId)" -ForegroundColor DarkGray

Write-Host '[Demo Seed] 6/7 Building Scenario D cruise (DEMO-CHECKIN) & booking (DEMO-CHECKIN-01) inside 3-hour check-in window...' -ForegroundColor Cyan
# Departure = now + 2 hours (strictly inside ONLINE_CHECKIN_LEAD = 3 hours AND still in the future!)
# Create with booking window open right now so booking creation succeeds via real API.
$checkinDepUtc = $nowUtc.AddHours(2)
$cruiseCheckin = New-DemoCruise `
    -BusinessCode 'DEMO-CHECKIN' `
    -CruiseName 'Hải trình Khởi hành Sắp xuất bến (Demo Check-in)' `
    -ShipId $shipCatalogId `
    -TemplateVersionId $tplCatalogVerId `
    -CabinType 'SUITE' `
    -LogicalCategory $catalogSuiteCat `
    -DepartureUtc ($baseDayUtc.AddDays(5)) `
    -BookingOpensUtc ($nowUtc.AddDays(-2)) `
    -BookingClosesUtc ($baseDayUtc.AddDays(4)) `
    -PriceVnd 9500000 `
    -ExpectedPassengers 4 `
    -TargetStage 'OPEN_FOR_SALE'

$checkinCabins = @(Invoke-Api -Method GET -Path "/booking/available-cabins?cruiseId=$($cruiseCheckin.cruiseId)" -Token $tokens.passenger | Where-Object { $_.cabinType -eq 'SUITE' })
if ($checkinCabins.Count -lt 1) {
    throw "No available SUITE cabin on DEMO-CHECKIN."
}
$cabinCheckin = $checkinCabins[0].cabinId

$bookingCheckin = New-DemoBooking `
    -BusinessRef 'DEMO-CHECKIN-01' `
    -Token $tokens.passenger `
    -CruiseId $cruiseCheckin.cruiseId `
    -OfferId $cruiseCheckin.offerId `
    -PriceRevisionId $cruiseCheckin.priceRevisionId `
    -Notes 'Đặt chỗ demo đủ điều kiện làm thủ tục check-in' `
    -Passengers @(
        @{
            fullName                   = 'Nguyen Van A'
            dateOfBirth                = '1995-05-15'
            nationality                = 'VNM'
            documentType               = 'NationalID'
            documentNumber             = '012345678901'
            email                      = 'passenger@casms.com'
            phoneNumber                = '+84901234567'
            cabinId                    = $cabinCheckin
            farePackageOfferId         = $cruiseCheckin.offerId
            farePackagePriceRevisionId = $cruiseCheckin.priceRevisionId
            cabinSelectionMode         = 'PHYSICAL_CABIN'
        },
        @{
            fullName                   = 'Jane Smith'
            dateOfBirth                = '1992-07-20'
            nationality                = 'GBR'
            documentType               = 'Passport'
            documentNumber             = 'GB98765432'
            email                      = 'jane.smith@example.com'
            phoneNumber                = '+84901234568'
            cabinId                    = $cabinCheckin
            farePackageOfferId         = $cruiseCheckin.offerId
            farePackagePriceRevisionId = $cruiseCheckin.priceRevisionId
            cabinSelectionMode         = 'PHYSICAL_CABIN'
        }
    )

# Settle DEMO-CHECKIN-01 via real SePay checkout + webhook so booking becomes Confirmed
$checkoutCheckin = Invoke-Api -Method POST -Path '/payment/sepay/checkout' -Token $tokens.passenger -Body @{
    bookingId   = $bookingCheckin.bookingId
    paymentType = 'FULL'
    currency    = 'VND'
}
[void](Invoke-Api -Method POST -Path '/payment/sepay/webhook' -ExtraHeaders @{ 'Authorization' = "Apikey $SepayApiKey" } -Body @{
    id              = 9900002
    gateway         = 'MBBank'
    transactionDate = $nowVnStr
    accountNumber   = '0901452366'
    transferType    = 'in'
    transferAmount  = [decimal]$checkoutCheckin.amountVnd
    content         = [string]$checkoutCheckin.transferContent
    referenceCode   = 'DEMO-FT-CHECKIN-01'
    description     = 'Demo local SePay settlement for DEMO-CHECKIN-01'
})

# Move DEMO-CHECKIN departure into the live 3-hour check-in window (now + 2h) after booking is Confirmed
# Keep booking_closes_at & guidance windows consistent with check constraints (booking_opens < booking_closes <= departure)
$depSql = (Format-UtcIso $checkinDepUtc)
$retSql = (Format-UtcIso ($checkinDepUtc.AddDays(2)))
$bkCloseSql = (Format-UtcIso ($checkinDepUtc.AddHours(-1)))
$gdOpenSql  = (Format-UtcIso ($checkinDepUtc.AddHours(-3)))
$gdCloseSql = (Format-UtcIso ($checkinDepUtc.AddMinutes(-30)))
[void](Invoke-DemoSql "UPDATE `"Cruise`" SET departure_datetime = '$depSql'::timestamptz, return_datetime = '$retSql'::timestamptz, booking_closes_at = '$bkCloseSql'::timestamptz, guidance_opens_at = '$gdOpenSql'::timestamptz, guidance_closes_at = '$gdCloseSql'::timestamptz WHERE cruise_id = '$($cruiseCheckin.cruiseId)';")

$bookingCheckinAfter = Invoke-Api -Method GET -Path "/booking/$($bookingCheckin.bookingId)" -Token $tokens.passenger
$paxList = @($bookingCheckinAfter.passengers)
$paxNguyen = ($paxList | Where-Object { $_.fullName -eq 'Nguyen Van A' } | Select-Object -First 1)
$paxJane   = ($paxList | Where-Object { $_.fullName -eq 'Jane Smith' } | Select-Object -First 1)

# Pre-start Jane Smith with emergency contact via real API as booking_agent (BOOKING_AGENT holds CHECKIN_CREATE/READ/UPDATE)
# Keep Nguyen Van A at NotStarted so presenter can ALSO demo passenger online check-in start + complete via API.
if ($paxJane) {
    $encName = [Uri]::EscapeDataString('Tran Minh Quan')
    $encPhone = [Uri]::EscapeDataString('0909888777')
    [void](Invoke-Api -Method POST -Path "/checkin/passenger/$($paxJane.bookingPassengerId)/start?emergencyContactName=$encName&emergencyContactPhone=$encPhone" -Token $tokens.agent)
}

Write-Host '[Demo Seed] 6b/7 Seeding sample Operational Issue on DEMO-SALE via API...' -ForegroundColor Cyan
[void](Invoke-Api -Method POST -Path '/operational-issues' -Token $tokens.ops -Body @{
    cruiseId    = $cruiseSale.cruiseId
    shipId      = $shipParadiseId
    issueType   = 'TECHNICAL'
    severity    = 'MEDIUM'
    description = 'Kiểm tra định kỳ cảm biến điều hòa khu vực sảnh đón tiếp trước ngày xuất bến DEMO-SALE.'
})

Write-Host '[Demo Seed] 7/7 Saving demo state manifest...' -ForegroundColor Cyan
$state = [ordered]@{
    generatedAtUtc = (Format-UtcIso (Get-Date).ToUniversalTime())
    database       = $DbName
    urls           = [ordered]@{
        api        = $ApiBase
        swagger    = "$ApiBase/swagger-ui.html"
        webBooking = $WebBookingUrl
        webAdmin   = $WebAdminUrl
    }
    accounts       = [ordered]@{
        cruise_manager     = @{ username = 'cruise_manager';     password = 'cruise123';    role = 'CRUISE_MANAGER' }
        operations_manager = @{ username = 'operations_manager'; password = 'ops123';       role = 'OPERATIONS_MANAGER' }
        activity_manager   = @{ username = 'activity_manager';   password = 'admin123';     role = 'ACTIVITY_SERVICE_MANAGER' }
        ship_manager       = @{ username = 'ship_manager';       password = 'ship123';      role = 'SHIP_MANAGER' }
        finance_officer    = @{ username = 'finance_officer';    password = 'finance123';   role = 'FINANCE_OFFICER' }
        booking_agent      = @{ username = 'booking_agent';      password = 'booking123';   role = 'BOOKING_AGENT (Đặt chỗ & Check-in quầy)' }
        passenger          = @{ username = 'passenger';          password = 'passenger123'; role = 'PASSENGER'; email = 'passenger@casms.com' }
        john_doe           = @{ username = 'john.doe';           password = 'passenger123'; role = 'PASSENGER'; email = 'john.doe@example.com' }
    }
    cruises        = [ordered]@{
        DEMO_DRAFT   = $cruiseDraft
        DEMO_REVIEW  = $cruiseReview
        DEMO_READY   = $cruiseReady
        DEMO_SALE    = $cruiseSale
        DEMO_CHECKIN = [ordered]@{
            cruiseId     = $cruiseCheckin.cruiseId
            cruiseCode   = 'DEMO-CHECKIN'
            cruiseName   = $cruiseCheckin.cruiseName
            departureUtc = $depSql
            windowNote   = 'Khởi hành sau 2 giờ (trong cửa sổ check-in online 3 giờ)'
        }
    }
    bookings       = [ordered]@{
        DEMO_PAY_01     = [ordered]@{
            bookingId        = [string]$bookingPay.bookingId
            bookingReference = 'DEMO-PAY-01'
            account          = 'john.doe / passenger123'
            email            = 'john.doe@example.com'
            bookingStatus    = [string]$bookingPay.bookingStatus
            paymentId        = [string]$checkoutPay.paymentId
            amountVnd        = [decimal]$checkoutPay.amountVnd
            transferContent  = [string]$checkoutPay.transferContent
            bookingUiUrl     = "$WebBookingUrl/my-bookings/$($bookingPay.bookingId)"
        }
        DEMO_PAID_01    = [ordered]@{
            bookingId        = [string]$bookingPaidAfter.bookingId
            bookingReference = 'DEMO-PAID-01'
            account          = 'passenger / passenger123'
            email            = 'passenger@casms.com'
            bookingStatus    = [string]$bookingPaidAfter.bookingStatus
            paymentId        = [string]$checkoutPaid.paymentId
            amountVnd        = [decimal]$checkoutPaid.amountVnd
            transferContent  = [string]$checkoutPaid.transferContent
            bookingUiUrl     = "$WebBookingUrl/my-bookings/$($bookingPaidAfter.bookingId)"
        }
        DEMO_CHECKIN_01 = [ordered]@{
            bookingId        = [string]$bookingCheckinAfter.bookingId
            bookingReference = 'DEMO-CHECKIN-01'
            cruiseId         = $cruiseCheckin.cruiseId
            cruiseCode       = 'DEMO-CHECKIN'
            account          = 'passenger / passenger123'
            email            = 'passenger@casms.com'
            bookingStatus    = [string]$bookingCheckinAfter.bookingStatus
            passengers       = @(
                if ($paxNguyen) { @{ fullName = $paxNguyen.fullName; bookingPassengerId = [string]$paxNguyen.bookingPassengerId; initialCheckinStatus = 'NotStarted' } }
                if ($paxJane)   { @{ fullName = $paxJane.fullName;   bookingPassengerId = [string]$paxJane.bookingPassengerId;   initialCheckinStatus = 'InProgress' } }
            )
            checkinUiUrl     = "$WebAdminUrl/admin/staff/checkin?cruiseId=$($cruiseCheckin.cruiseId)"
        }
    }
    jumpUrls       = [ordered]@{
        A_draft_crm      = "$WebAdminUrl/admin/cruises/$($cruiseDraft.cruiseId)"
        A_review_ops     = "$WebAdminUrl/admin/cruise-plan-reviews"
        A_review_detail  = "$WebAdminUrl/admin/cruises/$($cruiseReview.cruiseId)?work=review&step=review"
        B_open_sale_ops  = "$WebAdminUrl/admin/cruises/$($cruiseReady.cruiseId)?work=review&stage=sale&section=checks"
        B_book_customer  = "$WebBookingUrl/cruises/$($cruiseSale.cruiseId)"
        B_search_cruises = "$WebBookingUrl/cruises"
        C_pay_pending    = "$WebBookingUrl/my-bookings/$($bookingPay.bookingId)"
        C_pay_paid       = "$WebBookingUrl/my-bookings/$($bookingPaidAfter.bookingId)"
        C_finance_ledger = "$WebAdminUrl/admin/staff/finance"
        D_checkin_staff  = "$WebAdminUrl/admin/staff/checkin?cruiseId=$($cruiseCheckin.cruiseId)"
    }
}

$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $StateFile -Encoding UTF8
Write-Host "[Demo Seed] Completed cleanly! State saved to $StateFile" -ForegroundColor Green
