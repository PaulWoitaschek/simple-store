package com.ubercab.simplestorage.impl;

import android.content.Context;

import com.ubercab.simplestorage.ScopeConfig;
import com.ubercab.simplestorage.SimpleStore;
import com.ubercab.simplestorage.SimpleStoreConfig;
import com.ubercab.simplestorage.StoreClosedException;
import com.ubercab.simplestorage.utils.BlockingResult;
import com.ubercab.simplestorage.utils.ManualExecutor;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.ubercab.simplestorage.executors.StorageExecutors.directExecutor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public final class SimpleStoreImplTest {

    private static final String TEST_KEY = "test";
    private Context context = RuntimeEnvironment.systemContext;

    @After
    public void reset() {
        SimpleStoreConfig.setIOExecutor(null);
        SimpleStoreImplFactory.crashIfAnyOpen();
    }

    @Test
    public void nullWhenMissing() {
        try(SimpleStore store = SimpleStoreImplFactory.get(context)) {
            BlockingResult<byte[]> missing = new BlockingResult<>();
            store.get(TEST_KEY, missing, directExecutor());
            assertTrue(missing.isSuccess());
            Assert.assertNull(missing.getSuccessful());
        }
    }

    @Test
    public void puttingNullDeletesKey() {
        try(SimpleStore store = SimpleStoreImplFactory.get(context)) {
            BlockingResult<byte[]> saved = new BlockingResult<>();
            store.put(TEST_KEY, new byte[1], saved, directExecutor());
            saved.getSuccessful();
            BlockingResult<byte[]> done = new BlockingResult<>();
            store.put(TEST_KEY, null, done, directExecutor());
            Assert.assertNull(done.getSuccessful());
        }
    }

    @Test
    public void deleteAll() {
        try(SimpleStore store = SimpleStoreImplFactory.get(context)) {
            BlockingResult<byte[]> saved = new BlockingResult<>();
            store.put(TEST_KEY, new byte[1], saved, directExecutor());
            saved.getSuccessful();
            BlockingResult<Void> deleted = new BlockingResult<>();
            store.deleteAll(deleted, directExecutor());
            deleted.getSuccessful();
            BlockingResult<byte[]> emptyResult = new BlockingResult<>();
            store.get(TEST_KEY, emptyResult, directExecutor());
            assertNull(emptyResult.getSuccessful());
        }
    }

    @Test
    public void failsAllOnClose() {
        ManualExecutor manualExecutor = new ManualExecutor();
        SimpleStoreConfig.setIOExecutor(manualExecutor);
        BlockingResult<byte[]> savedSuccess = new BlockingResult<>();
        BlockingResult<byte[]> write = new BlockingResult<>();
        BlockingResult<byte[]> read = new BlockingResult<>();
        try(SimpleStore store = SimpleStoreImplFactory.get(context)) {
            store.put(TEST_KEY, new byte[1], savedSuccess, directExecutor());
            manualExecutor.flush();
            store.put(TEST_KEY, new byte[1], write, directExecutor());
            store.get(TEST_KEY, read, directExecutor());
        }
        manualExecutor.flush();
        assertTrue(savedSuccess.isSuccess());
        assertEquals(StoreClosedException.class, read.getFailure().getClass());
        assertEquals(StoreClosedException.class, write.getFailure().getClass());
    }

    @Test
    public void closes() {
        SimpleStore store = SimpleStoreImplFactory.get(context);
        store.close();
    }

    @Test
    public void scopes() {
        String someScope = "bar";
        String value = "some value";
        try(SimpleStore scopedBase = SimpleStoreImplFactory.get(context, someScope, ScopeConfig.DEFAULT)) {
            BlockingResult<String> savedSuccess = new BlockingResult<>();
            scopedBase.putString("foo", value, savedSuccess, directExecutor());
            savedSuccess.getSuccessful();
        }

        try(SimpleStore scopedFactory = SimpleStoreImplFactory.get(context, someScope, ScopeConfig.DEFAULT)) {
            BlockingResult<String> read = new BlockingResult<>();
            scopedFactory.getString("foo", read, directExecutor());
            assertEquals(value, read.getSuccessful());
        }
    }

    @Test
    public void oneInstancePerScope() {
        String someScope = "foo";
        try(SimpleStore ignoredOuter = SimpleStoreImplFactory.get(context)) {
            try(SimpleStore ignored = SimpleStoreImplFactory.get(context, someScope, ScopeConfig.DEFAULT)) {
                try {
                    SimpleStoreImplFactory.get(context, someScope, ScopeConfig.DEFAULT);
                    fail();
                } catch (IllegalStateException e) {
                    // expected.
                }
            }
        }
    }
}