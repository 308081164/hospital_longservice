# -*- coding: utf-8 -*-
"""FIX-1/FIX-2 修复案例复测（修复前这两个案例本地算错）。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

from lib.api_client import ApiClient  # noqa: E402

client = ApiClient(api_base="http://127.0.0.1:8088", mode="direct", username="admin", password="admin123")
client.login()
RULE_ID = (client.get("/api/hospital-pricing-rules/active").get("data") or {}).get("id")

CASES = [
    ("FIX-2 气腹 针1(半角空格) 期望6件/110",
     "腹腔镜下胆囊切除（戳卡4转换器1气腹 针1）/Z1026", 6, "6 件", 110.0),
    # 全角空格形态：件数推断须为 6（FIX-2 生效）；随后命中医院特色折算规则
    # 「平房人民针盒针5合1含包材」（exact_token 词边界语义，07d692f0 基线保持不变），6→3 件/66
    ("FIX-2 气腹　针1(全角空格) 期望推断6件+特色折算3件/66",
     "腹腔镜下胆囊切除（戳卡4转换器1气腹　针1）/Z1026", 6, "原器械数 6 件", 66.0),
    ("FIX-1 剪刀2止血钳1探针1(拆分触发) 期望4件/88",
     "剪刀2止血钳1探针1", 4, "4 件", 88.0),
    ("回归 单段 转换器1探针1 期望2件/44",
     "转换器1探针1", 2, "2 件", 44.0),
]

cust = client.customer_by_code("PFQ-RM")
cid = int(cust.get("id"))

for name, pack_name, icount, expect_phrase, expect_total in CASES:
    sample = {
        "department": "手术室",
        "packName": pack_name,
        "type": "额外包(低温等离子)",
        "packageMaterial": "低温纸塑袋200*600",
        "instrumentCount": icount,
        "packCount": 1,
        "unitPrice": 22,
        "totalPrice": 22 * icount,
    }
    sim = client.simulate_billing(customer_id=cid, hospital_name="哈尔滨市平房区人民医院",
                                  sample_row=sample, rule_id=RULE_ID)
    notes = sim.get("notes") or []
    joined = " | ".join(notes)
    total = sim.get("corrected_total_price")
    ok = expect_phrase in joined and total is not None and abs(float(total) - expect_total) < 0.01
    print(f"[{'PASS' if ok else 'FAIL'}] {name}")
    print(f"    packName={pack_name} instrumentCount={icount}")
    print(f"    corrected_total={total} rule={sim.get('pricing_rule')!r} status={sim.get('status')}")
    for n in notes:
        print(f"    note: {n}")
    print()
