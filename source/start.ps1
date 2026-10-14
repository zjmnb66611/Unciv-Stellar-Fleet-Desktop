param([string]$GameDirectory=(Join-Path $PSScriptRoot '../..'),[switch]$ValidateOnly,[switch]$NuclearOrganization)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $GameDirectory).Path
$stellar=& (Join-Path $PSScriptRoot 'validate.ps1') -GameDirectory $root
$jar=Join-Path $root 'Unciv.jar'
function Assert-Agent([string]$Directory,[string]$JarName){
    $folder=Join-Path $root "extensions/$Directory"
    $manifestPath=Join-Path $folder 'manifest.json'
    if(-not(Test-Path -LiteralPath $manifestPath)){return}
    $manifest=Get-Content -Raw -Encoding UTF8 -LiteralPath $manifestPath | ConvertFrom-Json
    $agent=Join-Path $folder $JarName
    if((Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant() -ne $manifest.gameSha256){throw "$Directory 不适用于当前游戏。"}
    if((Get-FileHash -LiteralPath $agent).Hash.ToLowerInvariant() -ne $manifest.agentSha256){throw "$Directory 文件校验失败。"}
    Write-Output $agent
}
$agents=@()
if($NuclearOrganization){
    $nuclear=Assert-Agent 'NuclearOrganization' 'nuclear-organization-agent.jar'
    if(-not $nuclear){throw '未安装核组织完整扩展；不能默默换成普通规则。'}
    $agents+=$nuclear
}else{$dongfeng=Assert-Agent 'DongfengOptimization' 'dongfeng-agent.jar';if($dongfeng){$agents+=$dongfeng}}
$bulk=Assert-Agent 'BulkPurchase' 'bulk-purchase-agent.jar';if($bulk){$agents+=$bulk}
$agents+=$stellar
Write-Output "已验证星际舰队及 $($agents.Count) 个兼容扩展；核组织模式：$($NuclearOrganization.IsPresent)。"
if($ValidateOnly){Write-Output '只验证文件，没有启动游戏。';return}
$java=Join-Path $root 'jre/bin/javaw.exe'
$running=Get-CimInstance Win32_Process -Filter "Name='Unciv.exe' OR Name='javaw.exe' OR Name='java.exe'" | Where-Object { $_.ExecutablePath -eq (Join-Path $root 'Unciv.exe') -or ($_.ExecutablePath -in @($java,(Join-Path $root 'jre/bin/java.exe')) -and $_.CommandLine -match 'Unciv\.jar') }
if($running){throw '游戏已在运行。请自行保存退出后，再使用星际舰队入口。'}
$logs=Join-Path $PSScriptRoot 'logs';New-Item -ItemType Directory -Path $logs -Force | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$launchArgs=@('-Xmx4G','-Dunciv.packr=true','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8')
foreach($agent in $agents){$launchArgs+=('-javaagent:"'+$agent+'"')}
$launchArgs+=@('-jar',('"'+$jar+'"'))
Start-Process -FilePath $java -ArgumentList $launchArgs -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs "$stamp-output.txt") -RedirectStandardError (Join-Path $logs "$stamp-error.txt")
