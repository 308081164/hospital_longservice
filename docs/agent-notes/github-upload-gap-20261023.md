> 归档说明：本文原为 Cursor 云端 Agent Store 文件（`bc-3fabfa38-7034-44d3-a6ab-c15e75f34211/files/docs/github-upload-gap.md`），2026-10-23 归档到仓库。内容反映当时核查结论，后续提交可能已覆盖部分条目；文中不含密钥值，SSH 私钥与 `internal/ssh-access-attempt.md` 未归档。

# 账单系统：本机还应上传到 GitHub 的资料

查库日期：2026-10-22 初查；**2026-10-23 00:15（北京时间）按 `origin/main` `bdb43fe9` 复核**。只读，未改仓库、未开 PR、未读密钥。

## 复核结论（2026-10-23）

**可以开始云端开发。** 上一轮 P0 和大部分 P1 权威 Excel 已经在 `bdb43fe9`（2026-10-23 00:08 +0800，`feat(rules): 补齐云端验收所需权威 Excel 与对比脚本输入`）。规则还没有按新表落库，缺原始账的几家仍然跑不了路径 A，生产对版仍要服务器。

已经满足：

- 内勤当前权威 `docs/source/医院内勤规则-20260924.xlsx`（445,117 字节）。`scripts/clerk_rules_import_v2.py` 优先读这一份，`铂康/内勤要求/` 只做回退。
- 10 月参考表 `docs/source/医院内勤规则10.15(1).xlsx` 已入库。`docs/source/README.md` 写明尚未接入导入脚本。无 `(1)` 的 `医院内勤规则10.15.xlsx` 本机未找到，这一项可以关掉。
- 标准收费对照 `docs/source/标准收费部分(1).xlsx` 已入库，`customer_rules_compare.py` 已改读仓库路径。
- 特殊计价新权威 `docs/source/特殊收费(3)-20261022.xlsx`（35,620 字节，45 家，SHA-256 `96a08f20…1c31`）已入库。对比脚本已指向它。
- `.gitignore` 已改成 `铂康/*` 加白名单。内勤三份 xlsx、`铂康/灭菌计费规则.md`、8 份特色价格单梳理 md 已跟踪。密钥、整库 dump、58 万行明细没有进这次提交。`frontend/.env.development` 只是把代理改到 `localhost:8088`，没有口令。

开工前必须知道的三件未完成事项：

1. **新特殊收费表还没落进 baseline。** `billing-rules/baseline` 和 manifest 这次没有改。`docs/计费规则迁移与验收规范.md` 第 20 行、`scripts/fixed_price_excel_pairing_audit.py` 仍把 `docs/source/特殊收费(1).xlsx` 写成 G0 权威。云端若只读规范，会按旧表改规则。新规则应以 `docs/source/README.md` 和 `docs/特殊收费权威源更新说明-20261022.md` 为准，并先改规范里的那一句。哈尔滨工程大学不在新表里，确认前不要删现有规则。
2. **内勤 JSON 仍是旧生成物。** `clerk-rules/index.json` 的 `baseline_hash` 未变，`source` 仍写着 `铂康/内勤要求/医院内勤规则-20260924.xlsx`（文件在，但是旧路径）。`ZYY-D1` 已重新登记（43 家）。直接跑 v2 导入会盖掉 10 月手改。10.15 表只作对照。
3. **路径 A 缺原始账的医院没有补上。** 这次 diff 没有新的 `测试用例/**/原始表格`。远东、基准生物、奥兰、五常、森海、胸科、协大仍按上一份清单处理；作者在 `docs/source/README.md` 第 4 节写了本机没找到同账期原始账，协大完全没有。空库重建用的 `hospital_pricing_rule.sql` / `hospital_reconciliation_job.sql` 也故意没传，日常改规则不需要。

下面第 1–6 节是 2026-10-22 推送前的缺口，保留作对照。其中「`铂康/` 整目录忽略、git 历史为空」已被这次推送改掉。

内勤权威源、特殊计价权威源、10 月分析报告的结论沿用 `internal/rule-sources-and-oct-reports.md`，这里不重做。

`.gitignore` 第 10 行忽略整个 `铂康/`。后面的 `!铂康/特殊价格单/` 例外没有生效：`git check-ignore` 仍把 `铂康/特殊价格单/README.md` 判成被第 10 行忽略。`git ls-files 铂康` 与 `git rev-list --all -- 铂康` 都是空的。本检出没有 `铂康/` 目录。文件放进 `铂康/` 再提交，GitHub 上不会出现。要进仓库，放到 `docs/source/` 或 `测试用例/`，或先改忽略规则。

---

## 1. 已经在 GitHub、不必再传

这些是规则变更和两条测试路径会读的权威或运行副本，云端已经有。

- **特殊计价 G0 权威副本**：`docs/source/特殊收费(1).xlsx`（约 30KB）。规范要求它与用户指定的微信原件逐字节一致。历史归档 `(2)(3)(7)(11)(13)(17)` 也在同目录。`(4)` 不在 `docs/source/`，但 `测试用例/原始表格/特殊收费(4).xlsx` 已跟踪；规范规定不得把它当成比 `(1)` 更新的权威。
- **附一内勤和附一计价**：`docs/source/附一收费标准.xlsx`。`scripts/import_fuyi_standards.py` 只读这一份。`(2)` 是同内容副本。
- **可编辑落库源和生成物**：`backend/src/main/resources/billing-rules/baseline/`、`clerk-rules/baseline/`、两份 `billing-rules-manifest.json`、两边的 `index.json`。日常改规则按规范改 baseline，再跑 `refresh_baseline_index.py` 和 `baseline_to_manifest.py --write`，不读本机 Excel。
- **内勤附件的运行副本**：`clerk-rules/attachments/` 里映射表点名的 6 个价表/器械把数文件，以及 `settlement-layout/` 截图，都已跟踪。
- **旧内勤 Excel 副本**：`测试用例/医院内勤规则-20260918.xlsx`（约 422KB）。无日期的声明路径 `铂康/内勤要求/医院内勤规则.xlsx` 不在 git 里；若本机那份就是 09-18，不必再传。微信临时路径写在 `docs/内勤规则配置生效核查报告-20260923.md`，临时目录可能已清空。
- **路径 A 锁定材料**：`测试用例/特殊计价严格测试-材料锁定.json`（26 条）和基线报告 `测试用例/特殊收费v8严格Excel对账报告-20260827.json`（约 40KB，不大）。其中 22 条写了 raw/proc 文件名，这些文件都在对应医院的 `测试用例/<院名>/原始表格/` 与 `处理后表格/` 下。`测试用例/` 里已跟踪约 1627 个 xlsx（原始表格 231、处理后表格 939）。
- **闸门脚本和说明**：`scripts/keyword_gap_scan.py`、`scripts/tmp_keyword_gap_scan.py`（已被跟踪，忽略规则 `scripts/tmp_*.py` 只挡住未跟踪的新临时脚本）、`scripts/excel_manifest_parity_audit.py`、`scripts/special_v8_strict_excel_audit.py`、`scripts/verify-billing-seed.sh`、`docs/计费规则迁移与验收规范.md`、`测试用例/excel-manifest-parity-audit-20260902.md`、`.env.example`、`deploy/` 脚本和 `.github/workflows/`。
- **标准计价的仓库内说明**：`docs/通用计价规则手册.md`，不是客户那份《标准收费部分》原表。

下面这些账已经在别的目录里，不要按 `strict_hospital_codes.py` 的文件夹名再传一遍。G2（`keyword_gap_scan.py`）用的是代码里的短目录名，对不上时会直接跳过，这是代码问题，不是缺文件：

| 代码里的目录名 | 仓库里实际有账的目录 |
|----------------|----------------------|
| `新发红十字` | `测试用例/新发红十字医院/`（锁定的 5 月 raw+proc 在这里） |
| `哈尔滨红十字医院` | `测试用例/哈尔滨市红十字妇产医院/`，另有 `测试用例/呼兰区红十字医院/` |
| `电机厂医院` | `测试用例/国药总医院第二院区/`（`电机厂6月账单.xlsx`、`7月__电机厂原始.xlsx` 等） |
| `哈尔滨汽轮机医院` | `测试用例/国药总医院主院区/原始表格/汽轮机1–6月账单.xlsx` |
| `远东心脑血管` | `测试用例/黑龙江省远东心脑血管医院/`（只有处理后表，没有原始表，见第 2 节） |

---

## 2. 必须上传

只列仓库点了名、git 里没有、而且规则变更 / G1–G4 / 严格对账 / 内勤导入 / 部署会直接去读的材料。现有镜像部署和已经落库的 baseline **不读**这些文件，缺了不会挡住「把现有代码推上去」。会挡住的是「按生成器重导」或「把缺材料的院跑进严格对账」。

### 2.1 文件确认缺失

#### 医院内勤规则-20260924.xlsx

- **本机可能位置**：仓库写死的相对路径是 `铂康/内勤要求/医院内勤规则-20260924.xlsx`。没有写绝对路径。不要把它和 09-18 的微信临时路径当成同一个文件。
- **建议放到**：`docs/source/医院内勤规则-20260924.xlsx`。不要放进 `铂康/`，会被忽略，GitHub 收不到。放进去之后还要把 `scripts/clerk_rules_import_v2.py` 的 `XLSX` 和 `clerk-rules/index.json` 的 `source` 改到新路径，否则导入脚本仍去读被忽略的旧路径。改代码不在本次范围。
- **谁在读**：`scripts/clerk_rules_import_v2.py`；`clerk-rules/index.json` 的 `source`（`generated_at` `2026-10-21T01:58:41Z`，42 家）；`docs/内勤规则医院清单.md` 文首。
- **不传会卡**：重新从 Excel 生成内勤 baseline。G1–G4 和路径 A 不读这个文件。10 月已有直接改 baseline 的提交（南岗包名计价、悦美芳华等），所以这份 Excel 已经不是当前 JSON 的纯上游。上传是为了对照和以后重导；盲目重跑导入会盖掉这些手改。

### 2.2 整目录被忽略，需抽检后决定传哪些

整个 `铂康/` 都不在 git 里。本检出无法打开这些文件，不能确认本机是否还在。早期 `system_docs/bokang-legacy-analysis.md` 只列过 7 个文件（规则说明 + 建表语句转储），后来的脚本又点了内勤、账单批次、参考医院等子目录。下面只列**当前入口仍会打开**的路径。抽检时只挑缺的那几个文件，不要整目录提交。

#### 内勤导入会读的 Excel（与 2.1 是同一个文件）

`铂康/内勤要求/医院内勤规则-20260924.xlsx` 属于这一类：文件名已确认，所在目录被忽略。处理方式见 2.1。

同目录还有：

- `铂康/内勤要求/医院内勤规则.xlsx`：只被 `docs/内勤规则Excel映射表-20260918.md` 叫做权威来源。09-18 副本已在 `测试用例/`。待确认本机这份是不是比已跟踪的 09-18 更新；不是就不用传。
- `铂康/内勤要求/附件/`：旧脚本 `scripts/archive/clerk_rules_import_from_bokang_v1.py` 从这里复制附件。v2 导入脚本不再复制。映射里的 6 个文件已在 `clerk-rules/attachments/`。待抽检这个目录里有没有映射以外的新附件；没有就不用传。

#### 空库全量导入才会读的 SQL（部署流水线不读）

`docker-compose.yml` 把 `./铂康/建表语句` 挂到容器 `/app/bokang-data`。`BokangDataImportRunner` 在 `IMPORT_BOKANG_DATA=1` 时读取：

| 文件 | 文档里的规模 | 建议 |
|------|----------------|------|
| `hospital_pricing_rule.sql` | 8 条 INSERT | 只有要在空库重建旧系统计价方案时才传。建议路径 `docs/source/bokang-sql/`，不要放 `铂康/` |
| `hospital_reconciliation_job.sql` | 417 条作业 | 同上，用于补「种子 26 家以外的医院名」 |
| `hospital_reconciliation_row.sql` | 580,915 行明细 | **不要传**，见第 4 节 |
| `sys_user.sql` | 4 个旧账号 | **不要传** |
| `sys_menu.sql` | 6 条旧菜单 | 当前菜单不靠它，不必传 |

`scripts/verify-billing-seed.sh` 不打开这些 SQL。它只在库里没有 `bokang_data_import_v1` 标记时打印一句提示。`scripts/full-billing-seed-reset.sh --bokang` 才会去读目录。

文档和脚本里出现过的本机根目录（不保证文件还在）：`D:\Hui_Files\MyProjects\guangsha_technology\hospital-all-master\hospital-all-master\铂康\建表语句\`（`scripts/analyze_pack_name.py`、`deploy/MIGRATION.md`）。默认 `IMPORT_BOKANG_DATA=0`。云端要起 compose 时，可以本地建一个空的 `铂康/建表语句` 让挂载不报错，不必把转储推上 GitHub。

#### 严格对账还缺原始账的医院（处理后表多半已经在库里）

路径 A 锁定的 22 对文件都在。下面这些是锁定清单或 `strict_hospital_codes.py` 写明缺材料、而且 git 里没有成对 raw 的院。本机若在 `铂康/AI账单（原始未处理的）/`、`铂康/2026年账单(正确的)/`、`铂康/8.14新增/`、`铂康/缺失文件补充/` 等目录里还有原始账，抽出来放到右列。文件名以你本机实际文件为准，下表不编造原始账文件名。

| 医院 | git 里现在有什么 | 建议落到 |
|------|------------------|----------|
| 黑龙江省远东心脑血管医院 | 7 个处理后 xlsx，0 个原始表。锁定清单写「无 raw+proc 成对账单」 | `测试用例/黑龙江省远东心脑血管医院/原始表格/`，并配上同账期处理后表 |
| 基准生物 | 只有处理后表。代码原因是「无原始表格」 | `测试用例/基准生物/原始表格/` |
| 奥兰医院 | 只有处理后表（4–6 月）。内勤一口价在 `clerk-rules/baseline/AOLAN-YY.json` | 若要跑路径 A，补原始账到 `测试用例/奥兰医院/原始表格/` |
| 五常市人民医院 | 院目录里只有 `处理后表格/6月__五常6月账单.xlsx`；更多处理后表在 `测试用例/待匹配/处理后表格/` | 原始账到 `测试用例/五常市人民医院/原始表格/` |
| 森海医院 | 没有这个目录。处理后表在 `测试用例/待匹配/处理后表格/`（4 月、5 月、`6月__哈尔滨森海医院6月账单.xlsx`） | 原始账 + 把已有处理后表归到 `测试用例/森海医院/` |
| 胸科 | 没有 `哈尔滨胸科医院/`。处理后表在待匹配（`4月__胸科4月账单.xlsx` 等，文件名用「胸科」） | 待确认本机医院全名后，原始账和处理后表放进 `测试用例/<全名>/` |
| 协大医疗美容 | git 里没有任何文件名带「协大」。代码写「待补充测试材料」 | 待确认本机有没有这家的账；没有就保持 SKIP |

哈尔滨市第五医院、呼兰中医院在锁定清单里是 SKIP，原因是「ground truth 陈旧」，不是缺文件。它们的目录已在 `测试用例/`，不必为了严格对账再传一遍。

G1、当前自动化 G4（`excel_manifest_parity_audit.py`）不打开客户账单。G4 只查 manifest 是否盖住清单、FIXED_PRICE 是否 `skipPackaging+skipDiscount`，再调用 `keyword_gap_scan.py`。规范正文写的「逐条对 Excel」用的是已经在库里的 `docs/source/特殊收费(1).xlsx`。

---

## 3. 建议上传，可以后补

这些被文档或旧脚本点过名，当前 G1–G4、路径 A 锁定账期、部署流水线不会打开。后补即可。

- **`医院内勤规则10.15.xlsx` 和 `医院内勤规则10.15(1).xlsx`**：只出现在 `docs/内勤与特殊收费客户反馈问题分析报告-20261016.md`。没有任何脚本读取。报告说材料来自微信群「铂康销售内勤ai项目(11)」，没有绝对路径。待确认本机是否还留着、哪一份是 10 月客户给的最新内勤表。若它比 09-24 新，放到 `docs/source/`，并在以后把导入脚本从 09-24 改到这一份。在那之前，现有 baseline 手改就是运行依据。
- **`标准收费部分(1).xlsx`**：`scripts/customer_rules_compare.py` 写死微信临时路径 `.../xwechat_files/wxid_7qwn4vnuj7xo22_508c/temp/drag/标准收费部分(1).xlsx`。git 里没有这份文件。不跑这份对照脚本就不会卡住。临时目录很可能已经没了，待确认是否另存过。若要留，放到 `docs/source/标准收费部分(1).xlsx`。
- **`铂康/特殊收费(13).xlsx`**：同一脚本还读这个被忽略的路径。`docs/source/特殊收费(13).xlsx` 已经在库里。待确认两份是否同一内容；是就不必再传。
- **`铂康/特色账单规则.txt`**：`scripts/generate_hospital_requirement_docs.py` 用它再生 `docs/逐院需求登记表/`。登记表已经生成并在库里。只有要按原文重跑生成器时才需要。早期分析说它在 `铂康/` 根上，本检出无法核对是否还在。
- **`铂康/参考文件（按照医院）/<院名>/`**：同上，登记表的源材料。`verify-billing-seed.sh` 的「42 院」只查数据库里的客户编码，不读这个目录。
- **已入库的批次目录**：`铂康/AI账单（原始未处理的）/`、`铂康/2026年账单(正确的)/`、`铂康/已做完账单(1)/`、`铂康/账单原始数据-4(1)/`、`铂康/8.14新增/`、`铂康/8.19新增/第二批/`、`铂康/测试账单-6/`、`铂康/缺失文件补充/`、`铂康/特殊价格单/` 里的 PDF。对应脚本是 `ingest_bokang_*.py`、`reorganize_test_cases.py`。锁定账期已经在 `测试用例/`。待确认这些目录里有没有 2026-08-27 之后的新账期；有的话只补新的 raw+proc，不要整批再传。
- **一次性微信路径**（脚本写了、仓库权威副本已够用的）：`特殊收费(4).xlsx`、`特殊收费(8).xlsx`、`附一7月6-30号账单-睿思导出.xlsx`、`账单-附属第一医院-人工核对版(1).xlsx`。都在微信 `temp/drag/` 下。`(1)` 和附一价表已在 `docs/source/`。这些是旧核对脚本的输入，不是现行闸门。

---

## 4. 不要上传

| 类别 | 原因 |
|------|------|
| 私钥、`deploy/server.env`、项目根 `.env` | `.gitignore` 已排除。里面是 JWT、数据库口令、镜像地址。`deploy/PRODUCTION-RECOVERY.md` 写明服务器 `.env` 要 `chmod 600`，勿提交 Git。部署私钥放 GitHub Actions Secret `SSH_PRIVATE_KEY`，不要变成仓库文件。`.env.example` 已经在库里，云端可以按它生成本地口令，不必提交填好的 `.env`。 |
| `deploy/hospital-migration-*.sql`、`deploy/backups/`、服务器上的 `backups/hospital-backup-*.sql` | 忽略规则写明不要提交。这是整库转储，给服务器 `import-on-server.sh` 用，不是给 Git 用。 |
| `铂康/建表语句/hospital_reconciliation_row.sql` | 文档记为 580,915 行真实包名、材料和单价。`.gitignore` 注释写铂康转储含 400MB 以上文件，只给本地 Docker 挂载。生产迭代的规则以 baseline JSON 为准，不依赖这份明细。 |
| `铂康/建表语句/sys_user.sql` | 旧库账号（文档写有 admin、yang、admin001、qu001），不是规则源。 |
| 客户账单原件整包，若只存在于 `铂康/` 且 `测试用例/` 已有同账期副本 | 仓库已经选择把验收用的 raw/proc 放在 `测试用例/`。再把 `铂康/` 整树推上去会被忽略，也会把未脱敏原件和转储一起带上。只补第 2.2 节缺的那几家。 |
| 巨型生成报告 | 不要再提交新的 `测试用例/特殊计价严格对账报告-*.json`、`测试用例/.s8_exports/`、`测试用例/.clerk_exports/`、`测试用例/.tmp_*/`、`docs/*.zip`、`docs/.tmp_*/`。忽略规则已经挡住其中几类。`测试用例/.s8_exports_prod/` 虽已在历史里（约 202 个 xlsx），那是旧导出快照，不必再追加。G4 方法说明 `excel-manifest-parity-audit-20260902.md` 和 40KB 的 20260827 基线报告已经够用。 |

本次没有打开任何 `.env` 或密钥文件。GitHub Actions Secrets（`SSH_HOST`、`SSH_USER`、`SSH_PRIVATE_KEY`、`DEPLOY_PATH`、`GHCR_READ_TOKEN`、`SMOKE_USER`、`SMOKE_PASS`）是否已经配好，仓库里看不出来，待你在 GitHub 仓库设置里确认。不要把 Secret 的值贴进仓库或文档。

---

## 5. 上传之后仍然必须靠生产服务器才能做的事

依据 `deploy/README.md`、`deploy/MIGRATION.md`、`deploy/FULL-BILLING-SEED.md`、`deploy/PRODUCTION-RECOVERY.md` 和 `.github/workflows/deploy.yml`。没有连接 `39.102.213.51`。

服务器：`39.102.213.51`，目录 `/mnt/newdisk/app/Hospital`，前端 `8854`，API `8853`。`main` 推送后由 Actions 用 Secret 里的 SSH 私钥上去拉镜像、对版本、做 manifest reconcile。云端代理没有私钥文件也能走这条流水线，前提是 Secret 已配置。

上传齐仓库材料之后，下面这些仍只能在这台机器上做：

- **看生产现在跑的是哪一版、规则有没有对上。** `deploy/verify-billing-on-server.sh`、`verify-billing-api-on-server.sh` 查的是服务器本机 `127.0.0.1:8853` 和该容器里的 MySQL。`prod-drift-watchdog.yml` 也是 SSH 上去 curl `/api/v1/base/version`。
- **规范第 6 步的生产抽查。** 部署后在生产跑 rules compare，并对关键包名做 simulate。本地 G1（baseline 对 manifest）可以用仓库里的 JSON 完成；和生产库是否一致，必须打到这台服务器。
- **历史对账 Job 重算。** README 事故记录写明：引擎或规则修好并部署后，已经算过的 Job 仍是旧价。要在服务器上对受影响 Job 重算，再用新 Job 号交付。Job 和上传文件在 MySQL 卷、`backend-uploads`、`backend-storage` 里，不在 Git。
- **改服务器 `.env`。** 数据库口令、`IMAGE_BACKEND` / `IMAGE_FRONTEND`、历史上的 `MYSQL_HOST` 只在 `/mnt/newdisk/app/Hospital/.env`。文档要求勿提交。云端改仓库不会改到这份文件。
- **把一份库转储灌进生产（方案 A）。** `deploy/import-on-server.sh` 在服务器上执行。转储用 scp 到服务器，不要经过 GitHub。
- **浏览器验收真实客户数据。** `http://39.102.213.51:8854`。默认管理员文档写的是 `admin` / `admin123`，并以服务器 `.env` 为准。
- **`down -v` 一类清卷操作。** 文档写明会删掉 MySQL、上传和生成文件，只有服务器 `backups/` 里的 SQL 能恢复库。这不能在云端仓库里做。

本地或云端用仓库种子可以做的：改 baseline、跑 G1、跑自动化 G4、对已在 `测试用例/` 的 22 对账期跑路径 A（需要自己起 backend，脚本默认 `http://127.0.0.1:8000`）、跑 `verify-billing-seed.sh`（需要按 `.env.example` 起的本地 MySQL，不是生产库）。

---

## 6. 按优先级的上传清单

每项一句。路径都是建议落点；`铂康/` 不能作为 GitHub 落点。

### P0

1. 把 `医院内勤规则-20260924.xlsx` 放到 `docs/source/`，否则内勤 v2 导入脚本在云端找不到权威 Excel。

### P1

1. 待确认后上传 `医院内勤规则10.15(1).xlsx`（及同名的 `医院内勤规则10.15.xlsx`）到 `docs/source/`，它是 10 月客户反馈的对照表，但导入脚本还没改去读它。
2. 从本机 `铂康/` 抽检远东、基准生物、奥兰、五常、森海、胸科、协大的**原始账**，只把 git 里没有的 raw（和尚未入库的同账期处理后表）放进 `测试用例/<院名>/`。
3. 若还要在空库重建旧医院名，只传 `hospital_pricing_rule.sql` 和 `hospital_reconciliation_job.sql` 到 `docs/source/bokang-sql/` 一类未被忽略的目录。
4. 若还要跑 `customer_rules_compare.py`，把 `标准收费部分(1).xlsx` 放到 `docs/source/`；微信临时目录里可能已经没有，待确认。

### P2

1. `铂康/特色账单规则.txt` 和 `铂康/参考文件（按照医院）/` 只在要重跑登记表生成器时再传。
2. `铂康/` 下各账单批次目录只补 2026-08-27 基线之后、且 `测试用例/` 还没有的新账期。
3. `铂康/内勤要求/附件/` 只在发现 6 个已跟踪附件之外的新文件时再传。
4. `sys_menu.sql`、已在 `docs/source/` 的历史特殊收费表、微信 `temp/drag/` 里的 `(4)(8)` 和附一 7 月核对表，都不必再传。
