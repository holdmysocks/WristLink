[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repo 'build\protocol-tests\classes'
$null = New-Item -ItemType Directory -Force -Path $classes
$source = Join-Path $repo 'app\src\main\java\dev\wristlink'
$files = @('AncsParser','AncsAppParser','ActionPolicy','DismissalKey','Obex','OutgoingMessage','ReplyMatcher') | ForEach-Object { Join-Path $source "$_.java" }
$tests = @('AncsParserTest','AncsAppParserTest','NotificationBehaviorTest','ReplyTest','ReplyMatcherTest')
$files += $tests | ForEach-Object { Join-Path $repo "tests\$_.java" }
& javac --release 17 -encoding UTF-8 -d $classes @files
if ($LASTEXITCODE -ne 0) { throw 'Protocol test compilation failed.' }
foreach ($test in $tests) {
    & java -cp $classes $test
    if ($LASTEXITCODE -ne 0) { throw "Protocol test failed: $test" }
}
