package com.hospital.backend.service;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.entity.CustomerProductRule;
import com.hospital.backend.entity.CustomerProductRuleTombstone;

import java.util.List;
import java.util.Map;

public interface RuleQuarantineService {

    /**
     * 将规则停用并写入隔离存档（替代硬删）。
     *
     * @return true 若本次执行了隔离
     */
    boolean quarantineRule(
            Customer customer,
            CustomerProductRule rule,
            String manifestHash,
            String reason,
            String operatorName);

    List<CustomerProductRuleTombstone> listActiveQuarantines(Long customerId);

    List<CustomerProductRuleTombstone> listAllActiveQuarantines(int limit);

    boolean restoreQuarantine(Long tombstoneId, String operatorName);

    long countActiveQuarantines();

    /**
     * 生成种子补丁 JSON 草稿（供 CLI 引导录入 manifest）。
     */
    Map<String, Object> buildSeedPatchDraft(CustomerProductRuleTombstone tombstone);

    CustomerProductRuleTombstone getTombstone(Long tombstoneId);
}
