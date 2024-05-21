/*
 * Copyright (c) 2019 Lumina Networks, Inc. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_COMMIT_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_DISCARD_CHANGES_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_CONFIG_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_GET_QNAME;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_UNLOCK_QNAME;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices.Rpcs;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.tx.WriteCandidateTx;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class KeepaliveSalFacadeResponseWaitingTest {

    private static final RemoteDeviceId REMOTE_DEVICE_ID =
            new RemoteDeviceId("test", new InetSocketAddress("localhost", 22));

    private KeepaliveSalFacade keepaliveSalFacade;
    private ScheduledExecutorServiceWrapper executorService;

    private LocalNetconfSalFacade underlyingSalFacade;

    @Mock
    private Rpcs.Normalized deviceRpc;

    @Mock
    private NetconfDeviceCommunicator listener;

    @Before
    public void setUp() throws Exception {
        executorService = new ScheduledExecutorServiceWrapper(2);

        underlyingSalFacade = new LocalNetconfSalFacade();
        keepaliveSalFacade = new KeepaliveSalFacade(REMOTE_DEVICE_ID, underlyingSalFacade, executorService, 2L, 10000L);
        keepaliveSalFacade.setListener(listener);
    }

    @After
    public void tearDown() {
        executorService.shutdown();
    }

    /**
     * Not sending keepalive rpc test while the response is processing.
     */
    @Test
    public void testKeepaliveSalResponseWaiting() {
        // This settable future object will be never set to any value. The test wants to simulate waiting for the result
        // of the future object.
        final SettableFuture<DOMRpcResult> settableFuture = SettableFuture.create();
        doReturn(settableFuture).when(deviceRpc).invokeRpc(null, null);

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke general RPC on simulated local facade with null args. Sending of general RPC suppresses sending
        // of keepalive in KeepaliveTask run.
        // Request timeout (10 sec) is scheduled for general RPC in keepalive sal facade. It should wait for any result
        // from the RPC, and then it disables suppression of sending keepalive RPC.
        // Variable "settableFuture" which is never completed (RPC result is never set) will be returned.
        underlyingSalFacade.invokeNullRpc();

        // Verify invocation of general RPC.
        verify(deviceRpc, after(500).times(1)).invokeRpc(null, null);

        // Verify the keepalive RPC invocation never happened because it was suppressed by sending of general RPC.
        verify(deviceRpc, after(2500).never()).invokeRpc(NETCONF_GET_CONFIG_QNAME,
            KeepaliveSalFacade.KeepaliveTask.KEEPALIVE_PAYLOAD);

        // Verify there was only one KeepaliveTask scheduled (next KeepaliveTask would be scheduled if general RPC
        // result was received).
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertTrue(executorService.keepaliveTasks.get(0).isDone());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * RPC unlock is sent in callback of RPC commit.
     */
    @Test
    public void testKeepaliveSalWithRpcCommitAndRpcUnlock() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> commitSettableFuture = SettableFuture.create();
        doReturn(commitSettableFuture).when(deviceRpc).invokeRpc(NETCONF_COMMIT_QNAME, null);
        final SettableFuture<DOMRpcResult> unlockSettableFuture = SettableFuture.create();
        doReturn(unlockSettableFuture).when(deviceRpc).invokeRpc(NETCONF_UNLOCK_QNAME, null);

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC commit on simulated local facade, and it adds callback which invokes RPC unlock on RPC commit
        // result.
        underlyingSalFacade.performCommit();

        // Wait 0.5sec and verify RPC commit is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_COMMIT_QNAME, null);
        // Set RPC commit result and it calls callback invoking RPC unlock
        commitSettableFuture.set(new DefaultDOMRpcResult());

        // Wait 0.5sec and verify RPC unlock is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_UNLOCK_QNAME, null);
        // Set RPC unlock result.
        unlockSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run so verify there was only one keepalive task scheduled.
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertFalse(executorService.keepaliveTasks.get(0).isDone());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * RPC discard-changes and RPC unlock are sent asynchronously in callback of RPC commit.
     */
    @Test
    public void testKeepaliveSalWithRpcCommitErrorRpcDiscardChangesRpcUnlock() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> commitSettableFuture = SettableFuture.create();
        doReturn(commitSettableFuture).when(deviceRpc).invokeRpc(NETCONF_COMMIT_QNAME, null);
        final SettableFuture<DOMRpcResult> discardChangesSettableFuture = SettableFuture.create();
        doReturn(discardChangesSettableFuture).when(deviceRpc).invokeRpc(NETCONF_DISCARD_CHANGES_QNAME, null);
        final SettableFuture<DOMRpcResult> unlockSettableFuture = SettableFuture.create();
        doReturn(unlockSettableFuture).when(deviceRpc).invokeRpc(NETCONF_UNLOCK_QNAME, null);

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC commit on simulated local facade, and it adds callback which invokes RPC discard-changes and RPC
        // unlock on RPC commit error result.
        underlyingSalFacade.performCommitWithError();

        // Wait 0.5sec and verify RPC commit is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_COMMIT_QNAME, null);
        // Set RPC commit result, and it calls callback invoking RPC discard-changes and RPC unlock
        commitSettableFuture.set(new DefaultDOMRpcResult());

        // Wait 0.5sec and verify RPC discard-changes is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_DISCARD_CHANGES_QNAME, null);
        // Wait 0.5sec and verify RPC unlock is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_UNLOCK_QNAME, null);
        // Set RPC discard-changes result.
        discardChangesSettableFuture.set(new DefaultDOMRpcResult());
        // Set RPC unlock result.
        unlockSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run so verify there was only one keepalive task scheduled.
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertFalse(executorService.keepaliveTasks.get(0).isDone());
    }

    /**
     * Not scheduling additional keepalive rpc test while the response is processing.
     * RPC get and RPC get-config are sent in parallel.
     */
    @Test
    public void testKeepaliveSalWithParallelRpcGetRpcGetConfig() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceRpc).invokeRpc(NETCONF_GET_QNAME, null);
        final SettableFuture<DOMRpcResult> getConfigSettableFuture = SettableFuture.create();
        doReturn(getConfigSettableFuture).when(deviceRpc).invokeRpc(NETCONF_GET_CONFIG_QNAME, null);

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC get and RPC get-config on simulated local facade.
        underlyingSalFacade.invokeGetRpc();
        underlyingSalFacade.invokeGetConfigRpc();

        // Wait 0.5sec and verify RPC get is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_GET_QNAME, null);
        // Wait 0.5sec and verify RPC get-config is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_GET_CONFIG_QNAME, null);

        // Set RPC get result.
        getSettableFuture.set(new DefaultDOMRpcResult());
        // Set RPC get-config result.
        getConfigSettableFuture.set(new DefaultDOMRpcResult());

        // RPC executions completed before KeepaliveTask run so verify there was only one keepalive task scheduled.
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertFalse(executorService.keepaliveTasks.get(0).isDone());
    }

    /**
     * Scheduling another keepalive rpc test after all responses are processed.
     * RPC get and RPC get-config are sent in parallel, and it takes a while to receive reply.
     */
    @Test
    public void testKeepaliveSalWithParallelRpcGetRpcGetConfigAndLongerWaitForReply() {
        // These settable future objects will be set manually to simulate RPC result.
        final SettableFuture<DOMRpcResult> getSettableFuture = SettableFuture.create();
        doReturn(getSettableFuture).when(deviceRpc).invokeRpc(NETCONF_GET_QNAME, null);
        final SettableFuture<DOMRpcResult> getConfigSettableFuture = SettableFuture.create();
        doReturn(getConfigSettableFuture).when(deviceRpc).invokeRpc(NETCONF_GET_CONFIG_QNAME, null);

        // Schedule KeepaliveTask to run in 2sec.
        keepaliveSalFacade.onDeviceConnected(null, null, new RemoteDeviceServices(deviceRpc, null));

        // Invoke RPC get and RPC get-config on simulated local facade.
        underlyingSalFacade.invokeGetRpc();
        underlyingSalFacade.invokeGetConfigRpc();

        // Wait 0.5sec and verify RPC get is invoked.
        verify(deviceRpc, after(500).times(1)).invokeRpc(NETCONF_GET_QNAME, null);
        // Wait 5sec and verify RPC get-config is invoked.
        verify(deviceRpc, after(5000).times(1)).invokeRpc(NETCONF_GET_CONFIG_QNAME, null);

        // After 5.5sec (keepalive is 2sec) we should see that the KeepaliveTask is done (no keepalive is sent)
        // and no other KeepaliveTask is scheduled because we are waiting for RPC get, get-config replies
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertTrue(executorService.keepaliveTasks.get(0).isDone());

        // Set RPC get result.
        getSettableFuture.set(new DefaultDOMRpcResult());
        // RPC reply for RPC get is received, but it should not schedule another KeepaliveTask because we are still
        // waiting for RPC get-config reply.
        Assert.assertEquals(1, executorService.keepaliveTasks.size());
        Assert.assertTrue(executorService.keepaliveTasks.get(0).isDone());

        // Set RPC get-config result.
        getConfigSettableFuture.set(new DefaultDOMRpcResult());
        // RPC reply for RPC get-config is received, and it should schedule another KeepaliveTask because no other
        // RPC replies are expected.
        Assert.assertEquals(2, executorService.keepaliveTasks.size());
        Assert.assertTrue(executorService.keepaliveTasks.get(0).isDone());
        Assert.assertFalse(executorService.keepaliveTasks.get(1).isDone());
    }

    private static final class LocalNetconfSalFacade implements RemoteDeviceHandler {
        private volatile Rpcs.Normalized rpcs;

        @Override
        public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
                                      final NetconfSessionPreferences sessionPreferences,
                                      final RemoteDeviceServices services) {
            final var newRpcs = services.rpcs();
            assertThat(newRpcs, instanceOf(Rpcs.Normalized.class));
            rpcs = (Rpcs.Normalized) newRpcs;
        }

        @Override
        public void onDeviceDisconnected() {
            rpcs = null;
        }

        @Override
        public void onDeviceFailed(final Throwable throwable) {
        }

        @Override
        public void onNotification(final DOMNotification domNotification) {
        }

        @Override
        public void close() {
        }

        public void invokeNullRpc() {
            final var local = rpcs;
            if (local != null) {
                local.invokeRpc(null, null);
            }
        }

        /**
         * This is simplified version of {@link WriteCandidateTx#performCommit()} but the key point
         * is that RPC unlock is invoked in callback of RPC commit.
         */
        public void performCommit() {
            final var local = rpcs;
            if (local != null) {
                final var commitResult = local.invokeRpc(NETCONF_COMMIT_QNAME, null);
                Futures.addCallback(commitResult, new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult domRpcResult) {
                        local.invokeRpc(NETCONF_UNLOCK_QNAME, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                }, MoreExecutors.directExecutor());
            }
        }

        /**
         * This is simplified version of {@link WriteCandidateTx#performCommit()} but the key point
         * is that RPC discard-changes and RPC unlock are invoked asynchronously in callback of RPC commit.
         */
        public void performCommitWithError() {
            final var local = rpcs;
            if (local != null) {
                final var commitResult = local.invokeRpc(NETCONF_COMMIT_QNAME, null);
                Futures.addCallback(commitResult, new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult domRpcResult) {
                        local.invokeRpc(NETCONF_DISCARD_CHANGES_QNAME, null);
                        local.invokeRpc(NETCONF_UNLOCK_QNAME, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                    }
                }, MoreExecutors.directExecutor());
            }
        }

        public void invokeGetRpc() {
            final var local = rpcs;
            if (local != null) {
                local.invokeRpc(NETCONF_GET_QNAME, null);
            }
        }

        public void invokeGetConfigRpc() {
            final var local = rpcs;
            if (local != null) {
                local.invokeRpc(NETCONF_GET_CONFIG_QNAME, null);
            }
        }
    }

    private static final class ScheduledExecutorServiceWrapper extends ScheduledThreadPoolExecutor {

        public List<ScheduledFuture<?>> keepaliveTasks = new CopyOnWriteArrayList<>();

        ScheduledExecutorServiceWrapper(int corePoolSize) {
            super(corePoolSize);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            var task = super.schedule(command, delay, unit);
            if (command instanceof KeepaliveSalFacade.KeepaliveTask) {
                this.keepaliveTasks.add(task);
            }
            return task;
        }
    }
}

