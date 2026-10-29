$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/casms_local_20261025?stringtype=unspecified'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '12345'
$env:SPRING_PROFILES_ACTIVE = 'dev'
& docker start casms-postgres
if ($LASTEXITCODE -ne 0) { throw 'Cannot start local PostgreSQL container.' }
& mvn.cmd -B '-Dmaven.test.skip=true' '-Dmaven.jar.forceCreation=true' package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
# Use the repository migrations for this new database. Keep the older, untracked
# V24 repair script intact without mixing it with the repository's V24 migration.
$migrationDirectory = Join-Path $PSScriptRoot ('target/local-migrations-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $migrationDirectory | Out-Null
$migrationFiles = @(git ls-files -- 'src/main/resources/db/migration/*.sql')
if ($LASTEXITCODE -ne 0 -or $migrationFiles.Count -eq 0) { throw 'Cannot resolve repository migrations.' }
foreach ($migrationFile in $migrationFiles) { Copy-Item -LiteralPath $migrationFile -Destination $migrationDirectory }
$migrationLocation = 'filesystem:' + $migrationDirectory.Replace('\', '/')
& "$env:JAVA_HOME\bin\java.exe" -jar target/casms-0.0.1-SNAPSHOT.jar "--spring.flyway.locations=$migrationLocation" --spring.jpa.show-sql=false --logging.level.org.hibernate.SQL=WARN --logging.level.com.casms=INFO
