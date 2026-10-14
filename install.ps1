param([Parameter(Mandatory=$true)][string]$GameDirectory,[switch]$ValidateOnly)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDirectory).Path
$manifest=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $PSScriptRoot 'package-manifest.json') | ConvertFrom-Json
if((Get-FileHash -LiteralPath (Join-Path $root 'Unciv.jar')).Hash.ToLowerInvariant() -ne $manifest.gameSha256){throw '版本不符：完整扩展仅适配Unciv 4.22.6-patch1电脑端。'}
$plans=@()
foreach($entry in $manifest.files.PSObject.Properties){
    $relative=$entry.Name
    if($relative -notmatch '^(mods/Stellar Fleet/|extensions/StellarFleet/|[^/]+\.cmd$)'){throw "包内路径不允许：$relative"}
    $source=Join-Path $PSScriptRoot $relative;$target=[IO.Path]::GetFullPath((Join-Path $root $relative))
    if(-not $target.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw '拒绝越界写入。'}
    if((Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant() -ne $entry.Value){throw "包内文件损坏：$relative"}
    $plans+=@{path=$relative;source=$source;target=$target;text=$null}
}
# 只给现有增强入口追加可选、经过校验的末位代理，不写Unciv.json。
foreach($relative in @('extensions/DongfengOptimization/start.ps1','extensions/NuclearOrganization/start.ps1')){
    $target=Join-Path $root $relative
    if(-not(Test-Path -LiteralPath $target)){continue}
    $text=Get-Content -Raw -Encoding UTF8 -LiteralPath $target
    if($text.Contains('# StellarFleet optional extension v1')){continue}
    $anchor='Write-Output "已验证'
    $index=$text.IndexOf($anchor)
    if($index -lt 0 -or -not $text.Contains('$agents')){throw "现有增强入口格式未知，拒绝改写：$relative"}
    $addon=@'
# StellarFleet optional extension v1
$stellarGuard=Join-Path $gameRoot 'extensions/StellarFleet/validate.ps1'
if(Test-Path -LiteralPath $stellarGuard){$agents+=(& $stellarGuard -GameDirectory $gameRoot)}

'@
    $plans+=@{path=$relative;source=$null;target=$target;text=$text.Insert($index,$addon+"`r`n")}
}
if($ValidateOnly){Write-Output "安装预检通过，将安装星际舰队并兼容现有增强入口（$($plans.Count)项）。没有写入或启动游戏。";return}
$backup=Join-Path $root ('extension-backups/StellarFleet-1.0.0-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$records=@()
foreach($plan in $plans){
    $exists=Test-Path -LiteralPath $plan.target -PathType Leaf
    if($exists){$copy=Join-Path $backup $plan.path;New-Item -ItemType Directory -Path (Split-Path $copy) -Force | Out-Null;Copy-Item -LiteralPath $plan.target -Destination $copy}
    New-Item -ItemType Directory -Path (Split-Path $plan.target) -Force | Out-Null
    $temporary=$plan.target+'.stellar-tmp'
    if($null -ne $plan.text){[IO.File]::WriteAllText($temporary,$plan.text,(New-Object Text.UTF8Encoding($true)))}else{Copy-Item -LiteralPath $plan.source -Destination $temporary -Force}
    $records+=@{path=$plan.path;existed=$exists;sha256=(Get-FileHash -LiteralPath $temporary).Hash.ToLowerInvariant()}
    Move-Item -LiteralPath $temporary -Destination $plan.target -Force
}
$records | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backup 'installed-files.json') -Encoding UTF8
Write-Output "星际舰队安装完成；备份：$backup"
Write-Output '没有启停游戏或改写存档。使用现有增强入口或启动星际舰队.cmd，新建对局勾选Stellar Fleet。'
