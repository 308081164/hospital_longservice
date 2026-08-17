# SC11 规则与账单实践对齐审计

| 属性 | 值 |
|------|-----|
| 生成日期 | 2026-08-14 |
| Fixture 总数 | 40 |
| Valid 可跑 | 9 |
| Confirmed 有账单 | 7 |
| Invalid 待补材料 | 31 |

## 16 类逐类结论

| 类型 | Registry 规则数 | CSV 账单行 | Confirmed Fixture | 判定 |
|------|----------------|-----------|-------------------|------|
| SC11-T01 | 3 | 40 | 1 (t01_bingcheng_huanzuan_5pc) | confirmed |
| SC11-T02 | 2 | 3 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T04 | 20 | 22 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T03b | 1 | 69 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T05 | 8 | 22 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T06 | 1 | 27 | 2 (t06_zuyan_pai_15, t06_zuyan_pai_25) | confirmed |
| SC11-T07 | 3 | 194 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T08 | 20 | 49 | 2 (t08_jiuzhou_fangpan, t08_shkf_billing_off) | confirmed |
| SC11-T09 | 2 | 1 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T10 | 1 | 26 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T11 | 3 | 0 | 0 (-) | invalid_pending_materials |
| SC11-T12 | 1 | 0 | 0 (-) | invalid_pending_materials |
| SC11-T13 | 5 | 13 | 0 (-) | csv_evidence_no_valid_fixture |
| SC11-T14 | 1 | 0 | 0 (-) | invalid_pending_materials |
| SC11-T15 | 2 | 0 | 0 (-) | invalid_pending_materials |
| SC11-T16 | 32 | 3 | 0 (-) | csv_evidence_no_valid_fixture |

## Confirmed Fixture 明细

- **t01_bingcheng_huanzuan_5pc** (SC11-T01) — 特殊收费(11).xlsx#冰城#环钻
- **t04b_neau_root_11** (SC11-T04b) — 特殊收费(11).xlsx#东北农大#根管锉5.6
- **t04b_neau_root_6** (SC11-T04b) — 特殊收费(11).xlsx#东北农大#根管锉5.6
- **t06_zuyan_pai_15** (SC11-T06) — 特殊收费(11).xlsx#祖研#排针
- **t06_zuyan_pai_25** (SC11-T06) — 特殊收费(11).xlsx#祖研#排针
- **t08_jiuzhou_fangpan** (SC11-T08) — 特殊收费(11).xlsx#九州#方盘
- **t08_shkf_billing_off** (SC11-T08) — 特殊收费(11).xlsx#社会康复#16.5

## Invalid / 待补材料

待补材料类型（13）：`SC11-T02`, `SC11-T04`, `SC11-T03b`, `SC11-T05`, `SC11-T07`, `SC11-T09`, `SC11-T10`, `SC11-T11`, `SC11-T12`, `SC11-T13`, `SC11-T14`, `SC11-T15`, `SC11-T16`

对齐 [`测试用例/814新增严格Excel对账报告-20260814.md`](测试用例/814新增严格Excel对账报告-20260814.md) §3 优先补录。

## Fixture 全表

| id | 类型 | valid | evidence | customer | mode | enabled |
|----|------|-------|----------|----------|------|---------|
| t01_bingcheng_huanzuan_5pc | SC11-T01 | True | confirmed | BINGCHENG-YM | special_only | True |
| t01_bingcheng_huanzuan_1pc | SC11-T01 | False | none | BINGCHENG-YM | special_only | True |
| t02_guoyao2_suture_8 | SC11-T02 | False | none | GUOYAO-2 | hybrid | None |
| t02_guoyao2_suture_generic_packaging | SC11-T02 | False | none | GUOYAO-2 | hybrid | None |
| t03_guoyao2_double_lt3 | SC11-T03 | False | none | GUOYAO-2 | hybrid | None |
| t03_guoyao2_double_ge3 | SC11-T03 | False | none | GUOYAO-2 | hybrid | None |
| t03b_generic_double_lt35 | SC11-T03b | False | none | ZYY-D1 | hybrid | None |
| t03b_generic_double_lt35_alt | SC11-T03b | False | none | ZYY-D1 | hybrid | None |
| t04_guoyao2_pointer_12 | SC11-T04 | False | none | GUOYAO-2 | hybrid | None |
| t04_fnn_pdrill_8 | SC11-T04 | False | none | FNN-YY | standard | None |
| t04b_neau_root_11 | SC11-T04b | True | confirmed | NEAU-YY | standard | True |
| t04b_neau_root_6 | SC11-T04b | True | confirmed | NEAU-YY | standard | True |
| t05_guoyao2_pointer_20 | SC11-T05 | False | none | GUOYAO-2 | hybrid | None |
| t05_fnn_jikuozhen_15 | SC11-T05 | False | none | FNN-YY | standard | None |
| t06_zuyan_pai_15 | SC11-T06 | True | confirmed | ZUYAN-NG | hybrid | True |
| t06_zuyan_pai_25 | SC11-T06 | True | confirmed | ZUYAN-NG | hybrid | True |
| t07_guoyao2_cotton_w90 | SC11-T07 | False | none | GUOYAO-2 | hybrid | None |
| t07_guoyao2_cotton_w60 | SC11-T07 | False | none | GUOYAO-2 | hybrid | None |
| t08_jiuzhou_fangpan | SC11-T08 | True | confirmed | JIUZHOU-FK | standard | False |
| t08_jzsw_bio_150 | SC11-T08 | False | none | JZSW-BIO | standard | None |
| t08_shkf_billing_off | SC11-T08 | True | confirmed | SHKF-YY | special_only | False |
| t08_shkf_44 | SC11-T08 | False | none | SHKF-YY | special_only | False |
| t09_jiayi_kongjin | SC11-T09 | False | none | JIAYI-YL | standard | None |
| t09_jiayi_kongjin_alt | SC11-T09 | False | none | JIAYI-YL | standard | None |
| t10_hrb2nd_dressing_lt20 | SC11-T10 | False | none | HRB-2ND | special_only | None |
| t10_hrb2nd_dressing_lt20_alt | SC11-T10 | False | none | HRB-2ND | special_only | None |
| t11_haiyuan_cap_3 | SC11-T11 | False | none | HAIYUAN-SB | hybrid | None |
| t11_prison_seal_4 | SC11-T11 | False | none | HLJ-JYGLJ-YY | standard | None |
| t12_chunyu_plastic_tube | SC11-T12 | False | none | CHUNYU-YL | hybrid | None |
| t12_chunyu_tube_alt | SC11-T12 | False | none | CHUNYU-YL | hybrid | None |
| t13_hlzgh_lens_plus8 | SC11-T13 | False | none | HL-ZGH | hybrid | None |
| t13_hlzgh_lens_empty_rule | SC11-T13 | False | none | HL-ZGH | hybrid | None |
| t14_suofei_face_3 | SC11-T14 | False | none | SUOFEI-YL | standard | None |
| t14_suofei_face_4 | SC11-T14 | False | none | SUOFEI-YL | standard | None |
| t15_soft_mirror_eto | SC11-T15 | False | none | HRB-2ND | special_only | None |
| t15_soft_mirror_lt | SC11-T15 | False | none | HRB-2ND | special_only | None |
| t16_lt_standard_1pc | SC11-T16 | False | none | HRB-2ND | special_only | None |
| t16_lt_standard_5pc | SC11-T16 | False | none | HRB-2ND | special_only | None |
| billing_hybrid_unchanged | SC11-DB-HYBRID | True | none | GUOYAO-2 | hybrid | None |
| billing_disabled_jiuzhou | SC11-DB-BILLING_OFF | True | none | JIUZHOU-FK | standard | False |
