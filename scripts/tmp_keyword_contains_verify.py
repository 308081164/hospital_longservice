# -*- coding: utf-8 -*-
"""临时验证：2026-09-02 关键词包含语义全量对齐 + 水管膜片补词复测。"""
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
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "水管膜片-4 x4 低温 → 密封件≤5按1件（补词生效）",
     {"department": "口腔科", "packName": "水管膜片-4/Z7520", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋20cm",
      "instrumentCount": 4, "packCount": 1, "unitPrice": 22, "totalPrice": 22}, "密封件≤5按1件", 28.0),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "水管膜片-8 x8 低温 → 密封件>5折算22 ceil(8/5)×22=44",
     {"department": "口腔科", "packName": "水管膜片-8/Z7520", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋20cm",
      "instrumentCount": 8, "packCount": 1, "unitPrice": 44, "totalPrice": 44}, "密封件>5折算22", 44.0),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "水管膜片-4 x4 高温 → 密封件规则LT域拦截，不应命中",
     {"department": "口腔科", "packName": "水管膜片-4/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 4, "packCount": 1, "unitPrice": 8, "totalPrice": 8}, "!密封件", None),
    ("FNN-YY", "方南南医院", "加长根管锉-6 x6 → 方南南小件5合1含包材 ceil(6/5)×5.5+2.5=13.5（CJK前缀变体）",
     {"department": "口腔科", "packName": "加长根管锉-6/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 6, "packCount": 1, "unitPrice": 33, "totalPrice": 33}, "方南南小件5合1含包材", 13.5),
    ("CHUNYU-YL", "春语医疗美容医院", "塑料管子-3 x3 低温 → 春语塑料管≤10按1件（管子@contains）",
     {"department": "手术室", "packName": "塑料管子-3/Z1530", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋20cm",
      "instrumentCount": 3, "packCount": 1, "unitPrice": 22, "totalPrice": 22}, "塑料管≤10按1件", 25.0),
    ("ZUYAN-NG", "祖研-黑龙江省中医医院（南岗院区）", "美容科排针-15 x15 → 排针10合1含包材 ceil(15/10)×5.5+2.5=13.5",
     {"department": "美容科", "packName": "美容科排针-15/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 15, "packCount": 1, "unitPrice": 33, "totalPrice": 33}, "排针10合1含包材", 13.5),
    ("GUOYAO-2", "电机厂医院", "指针-6 x6 → 电机厂指针5合1含包材 13.5",
     {"department": "口腔科", "packName": "指针-6/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 6, "packCount": 1, "unitPrice": 33, "totalPrice": 33}, "指针5合1含包材", 13.5),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "垫片-4件 x4 高温 → 垫片5合1含包材 8.0（当日早些时候修复不回归）",
     {"department": "口腔科", "packName": "垫片-4件/Z7520", "type": "额外包(纸塑袋)", "packageMaterial": "高温纸塑袋75*200",
      "instrumentCount": 4, "packCount": 1, "unitPrice": 20, "totalPrice": 20}, "垫片5合1含包材", 8.0),
    ("HLJ-FY-RK", "黑龙江省妇幼保健院（人口）", "密封件-3 x3 低温 → 密封件≤5按1件（@contains 后原命中不回归）",
     {"department": "口腔科", "packName": "密封件-3/Z7520", "type": "额外包(低温等离子)", "packageMaterial": "低温纸塑袋20cm",
      "instrumentCount": 3, "packCount": 1, "unitPrice": 22, "totalPrice": 22}, "密封件≤5按1件", 28.0),
]

passed = failed = 0
for code, hosp, name, sample, expect_rule, expect_total in CASES:
    cust = client.customer_by_code(code)
    cid = int(cust.get("id"))
    sim = client.simulate_billing(customer_id=cid, hospital_name=hosp, sample_row=sample, rule_id=RULE_ID)
    total = sim.get("corrected_total_price")
    rule = sim.get("pricing_rule") or ""
    rule_ok = (expect_rule is None and not rule) or (expect_rule and expect_rule.startswith("!") and expect_rule[1:] not in rule) or (expect_rule and not expect_rule.startswith("!") and expect_rule in rule)
    total_ok = expect_total is None or (total is not None and abs(float(total) - expect_total) < 0.001)
    status = "PASS" if (rule_ok and total_ok) else "FAIL"
    if status == "PASS":
        passed += 1
    else:
        failed += 1
    print(f"[{status}] {name}\n      rule={rule!r} total={total}")
print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
