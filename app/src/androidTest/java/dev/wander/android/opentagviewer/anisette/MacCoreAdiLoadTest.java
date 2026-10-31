package dev.wander.android.opentagviewer.anisette;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** Loads Apple's hash-checked library but never provisions a device or signs into an account. */
@RunWith(AndroidJUnit4.class)
public class MacCoreAdiLoadTest {
    @Test
    public void nativeMacLibraryLoadsAndReportsUnprovisioned() throws Exception {
        File cache = new File(getInstrumentation().getTargetContext().getCacheDir(), "macadi-test-library");
        byte[] image = MacCoreAdiFetcher.ensure(cache);
        assertTrue(image.length > 1000000);
        try (MacCoreAdi adi = new MacCoreAdi(image)) {
            adi.setHostUuid(new byte[16]);
            try {
                adi.headers(MacCoreAdi.ANONYMOUS_DS_ID);
                fail("A clean emulator must not have an Apple machine identity");
            } catch (MacCoreAdi.NotProvisionedException expected) {
                // Native load and dispatch reached CoreADI's actual provisioning check.
            }
        }
    }
}
