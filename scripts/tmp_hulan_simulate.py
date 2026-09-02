# -*- coding: utf-8 -*-
"""临时验证：呼兰中医院 胶帽 规则命中情况。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

from lib.api_client import ApiClient  # noqa: E402

client = ApiClient(api_base="http://127.0.0.1:8000", mode="docker", username="admin", password="admin123")
client.login()
RULE_ID = (client.get("/api/hospital-pricing-rules/active").get("data") or {}).get("id")
cust = client.customer_by_code("HULAN-TCM")
cid = int(cust.get("id"))
print("customer:", cust.get("name"), "id:", cid, "rule_id:", RULE_ID)

CASES = [
    ("9月账单案例：胶帽(小)x1 辅料+低温等离子",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "额外包(低温等离子)",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}),
    ("胶帽(小)x1 辅料+低温灭菌袋",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "低温灭菌袋20cm",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}),
    ("胶帽(小)x1 辅料+无包材",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}),
    ("胶帽(小)x1 辅料+高温纸塑袋",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}),
    ("历史包名：胶帽组件-25件/Z1530 x25 低温等离子",
     {"department": "手术室", "packName": "胶帽组件-25件/Z1530", "type": "额外包(低温等离子)", "packageMaterial": "低温等离子",
      "instrumentCount": 25, "packCount": 1, "unitPrice": 88, "totalPrice": 88}),
]

for name, sample in CASES:
    sim = client.simulate_billing(customer_id=cid, hospital_name="呼兰中医院", sample_row=sample, rule_id=RULE_ID)
    unit = sim.get("expected_unit_price")
    total = sim.get("corrected_total_price")
    rule = sim.get("pricing_rule")
    status = sim.get("status")
    notes = sim.get("pricing_notes") or sim.get("pricingNotes") or []
    print(f"\n== {name}")
    print(f"   unit={unit} total={total} rule={rule} status={status}")
    for n in (notes or [])[:4]:
        print("   ", str(n)[:130])
