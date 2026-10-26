function Use-BattleshipJava {
    param([switch]$Compiler)
    $candidateBins = @()
    if ($env:JAVA_HOME) { $candidateBins += Join-Path $env:JAVA_HOME 'bin' }
    $resolvedJava = Get-Command java -ErrorAction SilentlyContinue
    if ($resolvedJava -and $resolvedJava.Source -notmatch '\\Oracle\\Java\\javapath\\') {
        $candidateBins += Split-Path $resolvedJava.Source -Parent
    }
    foreach ($installRoot in @((Join-Path $env:ProgramFiles 'Java'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'))) {
        if (Test-Path -LiteralPath $installRoot) {
            $candidateBins += @(Get-ChildItem -LiteralPath $installRoot -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'bin' })
        }
    }
    foreach ($candidate in $candidateBins) {
        $hasJava = Test-Path -LiteralPath (Join-Path $candidate 'java.exe')
        $hasCompiler = (Test-Path -LiteralPath (Join-Path $candidate 'javac.exe')) -and (Test-Path -LiteralPath (Join-Path $candidate 'jar.exe'))
        if ($hasJava -and (-not $Compiler -or $hasCompiler)) {
            $env:PATH = $candidate + [IO.Path]::PathSeparator + $env:PATH
            return
        }
    }
    throw 'Java not found. Install a full JDK, set JAVA_HOME, and reopen the terminal.'
}
