$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts/java_env.ps1')
Use-BattleshipJava -Compiler
Push-Location $PSScriptRoot
try {
    foreach ($tool in @('java','javac','jar')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "Missing $tool. Install a full JDK (Temurin 21 recommended), then reopen the terminal. https://adoptium.net/temurin/releases/?version=21"
        }
    }
    New-Item -ItemType Directory -Force -Path bin | Out-Null
    New-Item -ItemType Directory -Force -Path dist | Out-Null
    $sources = @((Get-ChildItem src -Recurse -Filter *.java).FullName)
    $flags = @('-encoding', 'UTF-8', '-d', (Join-Path $PSScriptRoot 'bin'))
    $version = (& javac -version 2>&1 | Out-String)
    if ($version -notmatch 'javac 1\.8\.') { $flags += @('--release','8','-Xlint:-options') }
    & javac @flags @sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & jar cfe dist/battleship-client.jar client.ClientMain -C bin client -C bin common
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    New-Item -ItemType Directory -Force -Path dist/lib | Out-Null
    Copy-Item -Path lib/*.jar,lib/*LICENSE* -Destination dist/lib -Force
    Copy-Item -LiteralPath THIRD_PARTY_NOTICES.md -Destination dist -Force
    $serverManifest = Join-Path $env:TEMP ('battleship-manifest-' + [guid]::NewGuid() + '.mf')
    [System.IO.File]::WriteAllText($serverManifest, "Main-Class: server.ServerMain`nClass-Path: lib/sqlite-jdbc-3.53.4.0.jar`n`n", [System.Text.Encoding]::ASCII)
    & jar cfm dist/battleship-server.jar $serverManifest -C bin server -C bin common
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host 'Build OK (Java 8 API compatibility).'
    Write-Host 'Portable JARs: dist/battleship-client.jar and dist/battleship-server.jar'
    exit 0
} finally { Pop-Location }
