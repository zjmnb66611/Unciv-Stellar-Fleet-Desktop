param([Parameter(Mandatory=$true)][string]$GameDirectory,[Parameter(Mandatory=$true)][string]$BackupDirectory)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDirectory).Path
$backup=(Resolve-Path -LiteralPath $BackupDirectory).Path
if(-not $backup.StartsWith((Join-Path $root 'extension-backups')+'\',[StringComparison]::OrdinalIgnoreCase)){throw '备份路径不属于本游戏。'}
$records=Get-Content -LiteralPath (Join-Path $backup 'installed-files.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($record in $records){
    $target=[IO.Path]::GetFullPath((Join-Path $root $record.path))
    if(-not $target.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw '拒绝越界回退。'}
    if(-not(Test-Path -LiteralPath $target)){continue}
    if((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -ne $record.sha256){Write-Warning "保留安装后修改的文件：$($record.path)";continue}
    if($record.existed){Copy-Item -LiteralPath (Join-Path $backup $record.path) -Destination $target -Force}else{Remove-Item -LiteralPath $target -Force}
}
foreach($relative in @('mods/Stellar Fleet','extensions/StellarFleet')){
    $directory=[IO.Path]::GetFullPath((Join-Path $root $relative))
    if(-not $directory.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw '拒绝越界清理。'}
    if(Test-Path -LiteralPath $directory){
        Get-ChildItem -LiteralPath $directory -Directory -Recurse | Sort-Object {$_.FullName.Length} -Descending | ForEach-Object {if(@(Get-ChildItem -LiteralPath $_.FullName -Force).Count -eq 0){Remove-Item -LiteralPath $_.FullName}}
        if(@(Get-ChildItem -LiteralPath $directory -Force).Count -eq 0){Remove-Item -LiteralPath $directory}
    }
}
Write-Output '回退完成。未启停游戏，也未修改存档。包含星舰或载员的存档仍需保留相应扩展才能继续使用。'
