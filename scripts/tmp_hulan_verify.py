# -*- coding: utf-8 -*-
"""临时验证：胶帽/根管锉 contains 修复后复测。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))

from lib.api_client import ApiClient  # noqa: E402

client = ApiClient(api_base="http://127.0.0.1:8000", mode="docker", username="admin", password="admin123")
client.login()
RULE_ID = (client.get("/api/hospital-pricing-rules/active").get("data") or {}).get("id")

CASES = [
    # (customer_code, hospital_name, case_name, sample, expect_rule_substr, expect_total)
    ("HULAN-TCM", "呼兰中医院", "胶帽组件-25件 x25 低温 → >5分支 ceil(25/5)×22=110",
     {"department": "手术室", "packName": "胶帽组件-25件/Z1530", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋",
      "instrumentCount": 25, "packCount": 1, "unitPrice": 88, "totalPrice": 88}, "胶帽5合1免包材", 110.0),
    ("HULAN-TCM", "呼兰中医院", "胶帽组件-25件 x20 低温 → ceil(20/5)×22=88（对齐客户3月价）",
     {"department": "手术室", "packName": "胶帽组件-25件/Z1530", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋",
      "instrumentCount": 20, "packCount": 1, "unitPrice": 88, "totalPrice": 88}, "胶帽5合1免包材", 88.0),
    ("HULAN-TCM", "呼兰中医院", "胶帽(小) x1 低温纸塑袋20cm → ≤5分支 1件低温价22",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "低温纸塑袋20cm",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}, "胶帽5合1含包材", 22.0),
    ("HULAN-TCM", "呼兰中医院", "胶帽(小) x1 高温纸塑袋 → 温度门拦截，胶帽规则不应命中",
     {"department": "手术室", "packName": "胶帽(小)", "type": "辅料", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 8, "totalPrice": 8}, None, 8.0),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "加长根管锉-6 x6 → ceil(6/5)×5.5+2.5=13.5",
     {"department": "口腔科", "packName": "加长根管锉-6/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 6, "packCount": 1, "unitPrice": 33, "totalPrice": 33}, "根管锉5合1含包材", 13.5),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "根管锉-4 x4 → ceil(4/5)×5.5+2.5=8（原有命中不回归）",
     {"department": "口腔科", "packName": "根管锉-4/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 4, "packCount": 1, "unitPrice": 22, "totalPrice": 22}, "根管锉5合1含包材", 8.0),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "车针-1 x1 → 不受根管锉@contains影响（误伤检查）",
     {"department": "口腔科", "packName": "车针-1/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 1, "packCount": 1, "unitPrice": 5.5, "totalPrice": 5.5}, None, None),
    ("HAIYUAN-SB", "黑龙江省海员总医院（松北）", "胶帽-10 x10 低温 → >5分支 ceil(10/5)×22=44（行为不变）",
     {"department": "手术室", "packName": "胶帽-10/Z1526", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋",
      "instrumentCount": 10, "packCount": 1, "unitPrice": 44, "totalPrice": 44}, "胶帽", 44.0),
]

for code, hosp, name, sample, expect_rule, expect_total in CASES:
    cust = client.customer_by_code(code)
    cid = int(cust.get("id"))
    sim = client.simulate_billing(customer_id=cid, hospital_name=hosp, sample_row=sample, rule_id=RULE_ID)
    unit = sim.get("expected_unit_price")
    total = sim.get("corrected_total_price")
    rule = sim.get("pricing_rule") or ""
    notes = sim.get("notes") or []
    rule_ok = (expect_rule is None and "胶帽" not in rule and "根管锉" not in rule) or (expect_rule and expect_rule in rule)
    total_ok = expect_total is None or (total is not None and abs(float(total) - expect_total) < 0.01)
    mark = "PASS" if (rule_ok and total_ok) else "FAIL"
    print(f"[{mark}] {code} | {name}")
    print(f"       rule={rule} unit={unit} total={total} (expect rule~{expect_rule} total={expect_total})")
    if mark == "FAIL":
        for n in notes[:3]:
            print("       note:", str(n)[:120])
