package org.himstedt.fireside

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `adb shell am start -e demo true` sends the flag as a String extra, while
 * `--ez demo true` sends it as a Boolean. Reading only the boolean form is what
 * made the documented invocation silently fall through to the setup screen.
 */
class IntentConfigTest {

    @Test
    fun `a typed boolean flag means demo`() {
        assertTrue(isDemoFlag(true))
    }

    @Test
    fun `a string flag means demo`() {
        assertTrue(isDemoFlag("true"))
        assertTrue(isDemoFlag("TRUE"))
        assertTrue(isDemoFlag(" true "))
        assertTrue(isDemoFlag("1"))
    }

    @Test
    fun `anything else is not demo`() {
        assertFalse(isDemoFlag(false))
        assertFalse(isDemoFlag("false"))
        assertFalse(isDemoFlag("0"))
        assertFalse(isDemoFlag("yes please"))
        assertFalse(isDemoFlag(null))
        assertFalse(isDemoFlag(42))
    }
}
