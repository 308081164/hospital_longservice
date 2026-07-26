# 铂康源材料目录

本目录由 `scripts/rebuild_bokang_from_test_cases.py` 从 `测试用例/` 反向重建。

## 子目录

- `AI账单（原始未处理的）/` — 原始导入格式账单
- `2026年账单(正确的)/` — 人工处理后参考账单
- `特殊价格单/` — 各院 PDF 价目与规则梳理 md
- `建表语句/` — SQL 转储（需从本地备份/U盘放入；`*.sql` 走 Git LFS）
- `参考文件（按照医院）/` — 按院参考包（需从本地备份补充）

## 缺失项

若 `建表语句/` 或 `参考文件（按照医院）/` 为空，请从开发机/U盘复制后：

```bash
git lfs install
git add 铂康/
git commit -m "chore: 补充铂康源材料"
git push
```
