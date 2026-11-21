package com.oncontrol.oncontrolbackend.shared.infrastructure.seeder;

import com.oncontrol.oncontrolbackend.shared.application.port.IdentityProvisioner;
import com.oncontrol.oncontrolbackend.shared.application.port.IdentityUnavailableException;
import com.oncontrol.oncontrolbackend.shared.application.port.NewAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Test double for the auth service: records calls and can fail from the Nth provision on. */
class RecordingIdentityProvisioner implements IdentityProvisioner {

    private final List<NewAccount> provisioned = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> deprovisioned = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong ids = new AtomicLong(100);
    private final int failFromCall;

    RecordingIdentityProvisioner(int failFromCall) {
        this.failFromCall = failFromCall;
    }

    @Override
    public long provision(NewAccount account) {
        if (failFromCall > 0 && provisioned.size() + 1 >= failFromCall) {
            throw new IdentityUnavailableException("auth service down (test)", null);
        }
        provisioned.add(account);
        return ids.incrementAndGet();
    }

    @Override
    public void deprovision(long accountId) {
        deprovisioned.add(accountId);
    }

    List<NewAccount> provisioned() {
        return provisioned;
    }

    List<Long> deprovisioned() {
        return deprovisioned;
    }
}
