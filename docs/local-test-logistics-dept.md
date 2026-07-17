# Local verification — logistics policy & dept/physician

## Prerequisites

- Docker stack running (MySQL + backend + frontend)
- Admin login (default billing config role: `R_ADMIN` or `billing_configurator`)

## Schema

On backend startup, `SchemaMigrationRunner` creates `department_entry` and `physician_entry` (schema_008).

## Verify logistics tab (Customer edit)

1. **主数据 → 客户管理** → edit a customer with billing enabled
2. Open **计费策略 → 物流** tab
3. Configure:
   - **基础物流**: fee per trip, trip source
   - **物流分摊**: click **配置分摊** → choose mode (none / dept ratio / equal / proportional / single owner / cross-hospital merge), select group hospitals, optional ratios, sync toggle
   - **物流抵扣 / 物流卡**: enable deduction, mode `自动扣减`, optional monthly cap (separate subsection)
4. Save allocation dialog → confirm `PUT /api/v1/customer-groups/{id}/allocation-config` when cross-hospital mode + sync enabled
5. Save customer → confirm `PUT /api/v1/customers/{id}/billing-policies/{policyId}` includes `allocationMode`, `logisticsMergeGroupId`, `singleOwnerCustomerId`

## Verify dept/physician CRUD

1. **账单配置 → 科室与医生**
2. Select customer → add department and physician rows
3. APIs:
   - `GET/POST /api/v1/customers/{id}/departments`
   - `GET/POST /api/v1/customers/{id}/physicians`
4. Customer list shows **科室/医生** column; edit drawer shows counts + link

## Verify logistics card link

1. **账单配置 → 物流卡** → create card for same customer
2. Return to customer logistics tab → balance hint appears

## Backend tests

```bash
cd backend && mvn test -Dtest=LogisticsFeeCalculatorPhase5Test,LogisticsMergeServiceTest,CustomerGroupAllocationSyncServiceTest,DepartmentEntryServiceImplTest
```
