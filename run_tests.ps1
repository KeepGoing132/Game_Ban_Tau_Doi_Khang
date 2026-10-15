param([switch]$RealTimer, [switch]$Gui, [switch]$SelfTestFailure, [switch]$Visual, [switch]$Chat, [switch]$ChatGui)
$ErrorActionPreference = 'Stop'
$testBuild = Join-Path $env:TEMP ('battleship-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testBuild | Out-Null
Push-Location $PSScriptRoot
try {
    $sources = @((Get-ChildItem src,tests -Recurse -Filter *.java).FullName)
    $version = (& javac -version 2>&1 | Out-String)
    $flags = @('-encoding', 'UTF-8', '-d', $testBuild)
    if ($version -notmatch 'javac 1\.8\.') { $flags += @('--release', '8', '-Xlint:-options') }
    & javac @flags @sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $javaFlags = @('-Dfile.encoding=UTF-8','-Dbattleship.idle_ms=2500','-Dbattleship.heartbeat_ms=500','-Dbattleship.invite_ms=700','-Dbattleship.rematch_ms=1500')
    if (-not $RealTimer) { $javaFlags += '-Dbattleship.turn_ms=700' }
    $testArgs = @('-cp', ('"' + $testBuild + ';' + (Join-Path $PSScriptRoot 'lib/*') + '"'), 'tests.TestRunner')
    if ($ChatGui) { $testArgs += 'chatgui' } elseif ($Chat) { $testArgs += 'chat' } elseif ($Visual) { $testArgs += 'visual' } elseif ($Gui) { $testArgs += 'gui' }
    if ($SelfTestFailure) { $testArgs += 'fail' }
    $process = Start-Process java -ArgumentList ($javaFlags + $testArgs) -PassThru -NoNewWindow
    $processHandle = $process.Handle
    if (-not $process.WaitForExit(120000)) { $process.Kill(); throw 'Test timeout (120 seconds)' }
    $process.WaitForExit()
    $process.Refresh()
    $testExit = $process.ExitCode
    if ($null -eq $testExit) { throw 'Cannot determine test exit code' }
    exit $testExit
} finally {
    Pop-Location
}
