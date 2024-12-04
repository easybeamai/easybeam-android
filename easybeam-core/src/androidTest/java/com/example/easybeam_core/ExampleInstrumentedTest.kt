package com.example.easybeam_core

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val config = EasyBeamConfig("123")
        val easybeam = Easybeam(config)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.easybeam_core", appContext.packageName)
    }
}