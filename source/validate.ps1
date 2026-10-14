param([Parameter(Mandatory=$true)][string]$GameDirectory)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDirectory).Path
$manifest=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') | ConvertFrom-Json
function Assert-File([string]$Path,[string]$Expected){
    if(-not(Test-Path -LiteralPath $Path -PathType Leaf)){throw "星际舰队文件缺失：$Path"}
    if((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Expected){throw "星际舰队文件版本不符：$Path"}
}
Assert-File (Join-Path $root 'Unciv.jar') $manifest.gameSha256
$agent=Join-Path $PSScriptRoot 'stellar-fleet-agent.jar'
Assert-File $agent $manifest.agentSha256
foreach($item in $manifest.assetFiles.PSObject.Properties){Assert-File (Join-Path $root $item.Name) $item.Value}
$atlas=Join-Path $root 'mods/Stellar Fleet/game.atlas'
$canonical=((Get-Content -LiteralPath $atlas -Raw -Encoding UTF8) -split "`r?`n" | ForEach-Object {$_.Trim()} | Where-Object {$_ -ne '' -and -not $_.StartsWith('filter:')}) -join "`n"
$algorithm=[System.Security.Cryptography.SHA256]::Create()
try{$hash=[BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical))).Replace('-','').ToLowerInvariant()}finally{$algorithm.Dispose()}
if($hash -ne $manifest.atlasRegionsSha256){throw '星际舰队图集区域或坐标不符。'}
$atlasList=(Get-Content -LiteralPath (Join-Path $root 'mods/Stellar Fleet/Atlases.json') -Raw -Encoding UTF8).Replace('"','') -replace '\s',''
if($atlasList -ne '[game]'){throw '星际舰队图集列表不符。'}
Write-Output $agent
