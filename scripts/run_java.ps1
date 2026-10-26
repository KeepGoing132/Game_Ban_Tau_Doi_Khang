param([ValidateSet('client','server')][string]$Mode, [int]$Port = 8888)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java_env.ps1')
Use-BattleshipJava
$projectDirectory = Split-Path $PSScriptRoot -Parent
Push-Location $projectDirectory
try {
    if ($Mode -eq 'client') {
        & java '-Dfile.encoding=UTF-8' -cp (Join-Path $projectDirectory 'bin') client.ClientMain
    } else {
        & java '-Dfile.encoding=UTF-8' -cp ((Join-Path $projectDirectory 'bin') + ';' + (Join-Path $projectDirectory 'lib/*')) server.ServerMain $Port
    }
    exit $LASTEXITCODE
} finally { Pop-Location }
