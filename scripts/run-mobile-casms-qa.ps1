param(
    [string] $LogPath = 'docs/audits/mobile-backend-unified/evidence/mobile-casms-final-regression.log'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$localSettings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $repo '.env.local')) {
    if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') {
        $localSettings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
if ($localSettings.DB_URL -notmatch '^jdbc:postgresql://(?:localhost|127\.0\.0\.1):5432/casms(?:\?.*)?$' -or !$localSettings.DB_USERNAME) {
    throw 'Expected .env.local to identify casms on local PostgreSQL port 5432.'
}
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/casms?stringtype=unspecified'
$env:SPRING_DATASOURCE_USERNAME = $localSettings.DB_USERNAME
$env:SPRING_DATASOURCE_PASSWORD = $localSettings.DB_PASSWORD
$env:SPRING_PROFILES_ACTIVE = 'test,mobile-casms-verification'
$env:SPRING_FLYWAY_ENABLED = 'false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:CASMS_PAYMENT_SCHEDULER_ENABLED = 'false'
$env:SEPAY_ENABLED = 'false'
$env:SEPAY_CARD_ENABLED = 'false'
$env:CASMS_FX_AUTO_SYNC_ENABLED = 'false'
$env:CASMS_BOOKING_EMAIL_ENABLED = 'false'
$env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')

# Verify the existing data source before the fixture-scoped test JVM can write.
$postgres = 'C:/Program Files/PostgreSQL/16/bin/psql.exe'
$env:PGPASSWORD = $localSettings.DB_PASSWORD
$env:PGOPTIONS = '-c default_transaction_read_only=on'
$env:PGCONNECT_TIMEOUT = '5'
$identityQuery = "SELECT current_database() || ':' || inet_server_port() || ':' || version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
$serverIdentity = & $postgres -X -w -qAt -h 127.0.0.1 -p 5432 -U $localSettings.DB_USERNAME -d casms -v ON_ERROR_STOP=1 -c $identityQuery
if ($LASTEXITCODE -ne 0 -or $serverIdentity -ne 'casms:5432:141') {
    throw 'Local casms at Flyway V141 is required.'
}
Remove-Item Env:PGPASSWORD
Remove-Item Env:PGOPTIONS

$agent = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar'
$unitTests = @(
    'AuthServiceTest', 'AuthControllerTest', 'PosPrincipalTest', 'BookingServiceTest',
    'BookingRecoveryTest', 'BookingLookupContractTest', 'CheckinServiceTest', 'GuidanceSessionServiceTest',
    'GuestPaymentExperienceServiceTest', 'OnboardFinancialAccessTest', 'InvoiceServiceTest', 'InvoiceControllerTest',
    'InternationalCruisePaymentServiceTest', 'CurrencyExchangeServiceTest', 'ChargeDisputeServiceTest',
    'PaymentServiceTest', 'RefundServiceTest', 'SepayServiceTest', 'PassengerCardServiceTest',
    'PosAccessServicePlacementTest', 'PosRequestValidationTest', 'ServiceRequestServiceTest',
    'ServicepackageControllerTest', 'UxScheduledRequestTest', 'UxServiceContractTest',
    'ActivityRegistrationWorkflowTest', 'UxRegistrationCancellationTest', 'OnboardOperationServiceTest',
    'ActivityCatalogScheduleTest', 'ShoreExcursionServiceTest', 'NotificationServiceTest',
    'MigrationChecksumLockTest'
)
$posMethods = @(
    'passengerAndStaffMobileFlowOverRealHttp',
    'saleChecksReviewedPriceAndCurrencyBeforeAnyFinancialWrites',
    'discoversOnlyAuthorizedTerminalContextAndOwnFinancialScope',
    'recordsUsageAtServerPriceReducesVirtualAllowanceAndCreatesNoPayment',
    'retriesAreIdempotentAndDifferentPayloadCannotReuseKey',
    'simultaneousUseFromTwoDevicesCannotOverspendSharedAllowance',
    'passengerCanCompleteOwnOnlineCheckinWithoutAStaffRole',
    'expiredSessionAndLockedEmployeeCannotUsePos',
    'rejectedAndCancelledPosRequestsRestoreAllowanceThroughOriginalApi',
    'cruiseContextCannotReadOrChargeAnotherCruiseOnSameShip',
    'activityPermissionsInheritAndAuthorizeRegistrationAttendanceAndLifecycle',
    'databaseFailureRollsBackRequestChargeAndUsageTogether'
)
$testSelection = ($unitTests -join ',') + ',PosPostgresIntegrationTest#' + ($posMethods -join '+')
Push-Location -LiteralPath $repo
try {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
    & mvn -o -B "-DargLine=-javaagent:$agent" "-Dtest=$testSelection" '-Dsurefire.forkedProcessTimeoutInSeconds=300' test *> $LogPath
    $result = $LASTEXITCODE
    Get-Content -LiteralPath $LogPath | Select-String -Pattern 'Tests run:|BUILD |\[ERROR\] (Failures:|Errors:|Tests run:|  [A-Z])' |
        Select-Object -Last 40
    exit $result
} finally {
    Pop-Location
}
