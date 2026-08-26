package com.hospital.backend.config;

import com.hospital.backend.entity.Customer;
import com.hospital.backend.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 幂等停用非铂康参考 42 院的内置额外客户（MasterData + HardcodedRules 共 19 个 code）。
 */
@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class ExtraCustomerDeactivationRunner implements CommandLineRunner {

    static final List<String> INACTIVE_EXTRA_CODES = List.of(
            // MasterDataInitializer (11)
            "HRB-XK", "HRB-AM", "HRB-ASM", "HRB-BY", "HRB-CY",
            "HRB-BNXS", "HRB-CJ", "WCSRMYY", "YMYXZX", "HY-HYY", "ZYY-DSFY",
            // HardcodedRulesMigrationRunner (4)
            "HLFB-SF", "HRB-DLFB", "HRB-MHM", "ZXYSJT"
    );

    private final CustomerMapper customerMapper;

    @Override
    public void run(String... args) {
        int updated = 0;
        for (String code : INACTIVE_EXTRA_CODES) {
            Customer customer = customerMapper.selectByCode(code);
            if (customer == null) {
                continue;
            }
            if ("inactive".equals(customer.getStatus())) {
                continue;
            }
            customer.setStatus("inactive");
            customerMapper.updateById(customer);
            updated++;
        }
        if (updated > 0) {
            log.info("Deactivated {} non-reference extra customers", updated);
        }
    }
}
