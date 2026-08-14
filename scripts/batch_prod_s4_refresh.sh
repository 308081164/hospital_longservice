#!/usr/bin/env bash
# 生产定点 S4 重导（fail_prod_lag 白名单，禁止全量）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
API="${API_BASE:-http://39.102.213.51:8853}"
LOG="/tmp/prod_s4_refresh.log"
: >"$LOG"

HOSPITALS=(
  "黑龙江省医院（南岗院区）"
  "黑龙江省医院（香坊院区）"
  "黑龙江维多利亚妇产医院"
  "黑龙江九洲妇科医院"
  "黑龙江省中医药大学附属第三医院（电力）"
  "呼兰区红十字医院"
  "国药总医院第三院区"
  "哈尔滨市第五医院"
  "新发红十字医院"
  "祖研-黑龙江省中医医院（三辅院区）"
  "南岗区妇产医院"
  "黑龙江省社会康复医院"
  "黑龙江中医药大学附属第二医院（哈南分院）"
  "哈尔滨冰城医疗美容医院"
  "黑龙江省第二医院（南岗院区）"
  "哈尔滨市呼兰区第一人民医院"
  "哈尔滨市红十字妇产医院"
)

echo "== prod S4 refresh API=$API ==" | tee -a "$LOG"
for h in "${HOSPITALS[@]}"; do
  echo ">> $h" | tee -a "$LOG"
  if python3 scripts/batch_june_system_test.py "$h" --api-base "$API" --mode direct >>"$LOG" 2>&1; then
    echo "OK $h" | tee -a "$LOG"
  else
    echo "FAIL $h (exit $?)" | tee -a "$LOG"
  fi
  sleep 2
done
echo "== done ==" | tee -a "$LOG"
