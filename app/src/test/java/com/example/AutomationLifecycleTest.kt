package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationLifecycleTest {

    @Test
    fun hardStopCannotBeClearedFromOverlayResume() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = DataStoreManager(context)

        settings.startAutomationFromApp()
        assertTrue(settings.automationEnabled.first())
        assertFalse(settings.emergencyStop.first())
        assertTrue(settings.overlayEnabled.first())

        settings.hardStop()
        assertFalse(settings.automationEnabled.first())
        assertTrue(settings.emergencyStop.first())
        assertFalse(settings.overlayEnabled.first())

        // The floating controller must never clear HARD STOP.
        settings.resumeAutomationFromOverlay()
        assertFalse(settings.automationEnabled.first())
        assertTrue(settings.emergencyStop.first())
        assertFalse(settings.overlayEnabled.first())

        // Only the app-level start transition is allowed to clear it.
        settings.startAutomationFromApp()
        assertTrue(settings.automationEnabled.first())
        assertFalse(settings.emergencyStop.first())
        assertTrue(settings.overlayEnabled.first())
    }

    @Test
    fun pauseCanResumeWithoutClearingAStop() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = DataStoreManager(context)

        settings.startAutomationFromApp()
        settings.pauseAutomation()

        assertFalse(settings.automationEnabled.first())
        assertFalse(settings.emergencyStop.first())
        assertTrue(settings.overlayEnabled.first())

        settings.resumeAutomationFromOverlay()

        assertTrue(settings.automationEnabled.first())
        assertFalse(settings.emergencyStop.first())
        assertTrue(settings.overlayEnabled.first())
    }
}
