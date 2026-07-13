# 从本地 Docker MySQL 导出 hospital 数据库（在 Windows PowerShell 中执行）
# 前提：本地 docker compose 已启动，容器名 hospital-mysql

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $ProjectRoot ".env"

if (-not (Test-Path $EnvFile)) {
    Write-Error "未找到 $EnvFile，请先在项目根目录配置 .env"
}

$vars = @{}
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') {
        $vars[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$rootPwd = $vars["MYSQL_ROOT_PASSWORD"]
if (-not $rootPwd) {
    Write-Error ".env 中缺少 MYSQL_ROOT_PASSWORD"
}

$outFile = Join-Path $PSScriptRoot ("hospital-migration-{0:yyyyMMdd}.sql" -f (Get-Date))
$container = "hospital-mysql"

Write-Host "正在从 $container 导出到 $outFile ..."

docker exec $container mysqldump `
    -u root "-p$rootPwd" `
    --single-transaction `
    --routines `
    --triggers `
    --databases hospital `
    | Out-File -FilePath $outFile -Encoding utf8

$size = (Get-Item $outFile).Length
Write-Host "导出完成，文件大小: $([math]::Round($size/1MB, 2)) MB"
Write-Host "请将 $outFile 上传到服务器 /mnt/newdisk/app/Hospital/ 目录"
