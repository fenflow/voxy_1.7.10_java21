param(
    [string]$Java = '',
    [switch]$GpuTest
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (-not $Java) {
    $prismJava = Join-Path $env:APPDATA 'PrismLauncher\java\java-runtime-epsilon\bin\java.exe'
    $Java = if (Test-Path -LiteralPath $prismJava) { $prismJava } else { (Get-Command java).Source }
}
$cache = Join-Path $env:TEMP 'voxy-forge1710-gradle'
$output = Join-Path $env:TEMP 'voxy-forge1710-output'
$tasks = @('test', 'build')
if ($GpuTest) { $tasks += 'gpuTest' }
Push-Location -LiteralPath $project
try {
    & $Java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain --gradle-user-home $cache "-PvoxyBuildDir=$output" @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    New-Item -ItemType Directory -Force (Join-Path $project 'build\libs') | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $output 'libs') -Filter '*.jar' | Copy-Item -Destination (Join-Path $project 'build\libs') -Force
    Copy-Item -LiteralPath (Join-Path $output 'reports') -Destination (Join-Path $project 'build') -Recurse -Force
} finally { Pop-Location }
