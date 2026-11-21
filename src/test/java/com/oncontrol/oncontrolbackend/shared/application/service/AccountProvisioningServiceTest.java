package com.oncontrol.oncontrolbackend.shared.application.service;

import com.oncontrol.oncontrolbackend.shared.application.port.IdentityConflictException;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityProvisioner;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** Distributed create protocol: provision remotely, compensate if the local transaction rolls back. */
@ExtendWith(MockitoExtension.class)
class AccountProvisioningServiceTest {

    private static final NewAccount ACCOUNT = new NewAccount("doc@h.com", "Secret123!", "DOCTOR", 1L, 5L, 7L, null);

    @Mock
    private IdentityProvisioner provisioner;

    @InjectMocks
    private AccountProvisioningService service;

    @BeforeEach
    void startTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(status));
    }

    @Test
    void provision_returnsAccountId() {
        when(provisioner.provision(ACCOUNT)).thenReturn(42L);

        assertThat(service.provision(ACCOUNT)).isEqualTo(42L);
    }

    @Test
    void localRollbackAfterRemoteSuccess_deprovisionsTheAccount() {
        when(provisioner.provision(ACCOUNT)).thenReturn(42L);
        service.provision(ACCOUNT);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(provisioner).deprovision(42L);
    }

    @Test
    void localCommit_doesNotDeprovision() {
        when(provisioner.provision(ACCOUNT)).thenReturn(42L);
        service.provision(ACCOUNT);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(provisioner, never()).deprovision(42L);
    }

    @Test
    void failedDeprovision_isSwallowedSoRollbackCompletes() {
        when(provisioner.provision(ACCOUNT)).thenReturn(42L);
        doThrow(new IdentityUnavailableException("auth down", null)).when(provisioner).deprovision(42L);
        service.provision(ACCOUNT);

        assertThatCode(() -> completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK))
                .doesNotThrowAnyException();
        verify(provisioner).deprovision(42L);
    }

    @Test
    void disabledProvisioner_registersNoCompensation() {
        when(provisioner.provision(ACCOUNT)).thenReturn(IdentityProvisioner.NOT_PROVISIONED);

        assertThat(service.provision(ACCOUNT)).isEqualTo(IdentityProvisioner.NOT_PROVISIONED);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(provisioner, never()).deprovision(IdentityProvisioner.NOT_PROVISIONED);
    }

    @Test
    void remoteFailure_propagatesAndRegistersNothing() {
        when(provisioner.provision(ACCOUNT)).thenThrow(new IdentityConflictException("exists"));

        assertThatThrownBy(() -> service.provision(ACCOUNT)).isInstanceOf(IdentityConflictException.class);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(provisioner, never()).deprovision(org.mockito.ArgumentMatchers.anyLong());
    }
}
