# 从本地 Docker MySQL 导出 hospital 数据库（在 Windows PowerShell 中执行）
# 前提：本地 docker compose 已启动，容器名 hospital-mysql
#
# 注意：不要用 PowerShell 管道写 mysqldump（会破坏 UTF-8 多字节字符，导致导入报错 Unknown command '\"'）。
# 本脚本在容器内写临时文件，再用 docker cp 拷出，保持原始字节流。

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
$remoteDump = "/tmp/hospital-migration.sql"

Write-Host "Exporting from $container to $outFile ..."

docker exec $container mysqldump `
    -u root "-p$rootPwd" `
    --single-transaction `
    --routines `
    --triggers `
    --databases hospital `
    --result-file=$remoteDump

if ($LASTEXITCODE -ne 0) {
    Write-Error "mysqldump failed with exit code $LASTEXITCODE"
}

docker cp "${container}:${remoteDump}" $outFile
docker exec $container rm -f $remoteDump

$size = (Get-Item $outFile).Length
Write-Host "Done. Size: $([math]::Round($size/1MB, 2)) MB"
Write-Host "Upload to server: /mnt/newdisk/app/Hospital/$(Split-Path $outFile -Leaf)"
