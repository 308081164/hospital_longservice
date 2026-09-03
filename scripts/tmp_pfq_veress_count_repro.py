# -*- coding: utf-8 -*-
"""临时复现：PFQ-RM 多数字包名（气腹针结尾）件数被算成 2 的问题。"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

from lib.api_client import ApiClient  # noqa: E402

client = ApiClient(api_base="http://127.0.0.1:8088", mode="direct", username="admin", password="admin123")
client.login()
RULE_ID = (client.get("/api/hospital-pricing-rules/active").get("data") or {}).get("id")

CASES = [
    ("7月错误案例-戳卡4转换器1气腹针1(期望6件/110)",
     "腹腔镜下胆囊切除（戳卡4转换器1气腹针1）/Z1026", 6),
    ("7月正确案例-戳卡5转换器1(期望6件/110)",
     "腹腔镜下胆囊切除（戳卡5转换器1）/Z1026", 6),
    ("4月错误案例-戳卡5转换器1气腹针1(期望7件/132)",
     "腹腔镜下胆囊切除（戳卡5转换器1气腹针1）/Z1026", 7),
    ("气腹针在中间-戳卡5气腹针1转换器1(期望7件/132)",
     "腹腔镜下胆囊切除（戳卡5气腹针1转换器1）/Z1026", 7),
]

for name, pack_name, icount in CASES:
    cust = client.customer_by_code("PFQ-RM")
    cid = int(cust.get("id"))
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
    print(f"=== {name}")
    print(f"    packName={pack_name} instrumentCount={icount}")
    print(f"    corrected_total={sim.get('corrected_total_price')} rule={sim.get('pricing_rule')!r} status={sim.get('status')}")
    for n in sim.get("notes") or []:
        print(f"    note: {n}")
    print()
