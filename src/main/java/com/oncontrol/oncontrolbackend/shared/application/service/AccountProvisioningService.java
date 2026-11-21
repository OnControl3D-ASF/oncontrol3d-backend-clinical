package com.oncontrol.oncontrolbackend.shared.application.service;

import com.oncontrol.oncontrolbackend.shared.application.port.IdentityProvisioner;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Remote half of the distributed create protocol: call it AFTER the local business row has
 * been saved (and flushed) inside the current transaction. If the remote call fails the
 * exception rolls the local transaction back; if it succeeds but the local transaction later
 * rolls back, the auth account is removed again (compensation). A failed compensation is
 * logged at ERROR with the account id (orphan credential, no clinical data reachable).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountProvisioningService {

    private final IdentityProvisioner identityProvisioner;

    public long provision(NewAccount account) {
        long accountId = identityProvisioner.provision(account);
        if (accountId != IdentityProvisioner.NOT_PROVISIONED && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        deprovisionQuietly(accountId);
                    }
                }
            });
        }
        return accountId;
    }

    private void deprovisionQuietly(long accountId) {
        try {
            identityProvisioner.deprovision(accountId);
        } catch (RuntimeException e) {
            log.error("Failed to deprovision auth account {} after local rollback; orphan credential: {}",
                    accountId, e.getMessage());
        }
    }
}
