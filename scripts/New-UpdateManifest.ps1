param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}$')]
    [string]$Version,
    [string]$ReleaseDirectory = (Join-Path $PSScriptRoot '../releases')
)

$ErrorActionPreference = 'Stop'
$folder = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
$names = "PotionsAndDungeons-$Version.apk", "PotionsAndDungeons-$Version-Windows-x64.zip"
$assets = foreach ($name in $names) {
    $file = Get-Item -LiteralPath (Join-Path $folder $name)
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    [ordered]@{ name = $name; size = $file.Length; digest = "sha256:$hash"; state = 'uploaded' }
}
$manifest = [ordered]@{ tag_name = "v$Version"; assets = @($assets) }
$path = Join-Path $folder 'update-manifest.json'
[IO.File]::WriteAllText($path, ($manifest | ConvertTo-Json -Depth 4))
$path
