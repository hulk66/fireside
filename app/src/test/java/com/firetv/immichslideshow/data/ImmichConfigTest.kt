package com.firetv.immichslideshow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmichConfigTest {

    @Test
    fun `normalizes a bare host to http with one trailing slash`() {
        assertEquals("http://192.168.1.20:2283/", ImmichConfig("192.168.1.20:2283", "k").normalizedBaseUrl())
    }

    @Test
    fun `keeps https and does not double the slash`() {
        assertEquals("https://photos.example.org/", ImmichConfig("https://photos.example.org/", "k").normalizedBaseUrl())
        assertEquals("https://photos.example.org/", ImmichConfig("  https://photos.example.org  ", "k").normalizedBaseUrl())
    }

    @Test
    fun `display host drops the scheme and trailing slash`() {
        assertEquals("photos.example.org", ImmichConfig("https://photos.example.org/", "k").displayHost())
        assertEquals("192.168.1.20:2283", ImmichConfig("http://192.168.1.20:2283", "k").displayHost())
    }

    @Test
    fun `sanitized trims the key and the trailing slash`() {
        val config = ImmichConfig(" https://photos.example.org/ ", "  abc  ").sanitized()
        assertEquals("https://photos.example.org", config.baseUrl)
        assertEquals("abc", config.apiKey)
    }

    @Test
    fun `a typed scheme is tried first, then the other one`() {
        assertEquals(
            listOf("https://photos.example.org", "http://photos.example.org"),
            ImmichConfig.addressCandidates("https://photos.example.org/"),
        )
        assertEquals(
            listOf("http://192.168.1.20:2283", "https://192.168.1.20:2283"),
            ImmichConfig.addressCandidates(" http://192.168.1.20:2283 "),
        )
    }

    @Test
    fun `a bare host is probed over https before http`() {
        assertEquals(
            listOf("https://photos.example.org", "http://photos.example.org"),
            ImmichConfig.addressCandidates("photos.example.org"),
        )
    }

    @Test
    fun `blank input has nothing to probe`() {
        assertTrue(ImmichConfig.addressCandidates("   ").isEmpty())
        assertTrue(ImmichConfig.addressCandidates("").isEmpty())
    }

    @Test
    fun `validity needs both a url and a key`() {
        assertTrue(ImmichConfig.isValid("https://photos.example.org", "abc"))
        assertFalse(ImmichConfig.isValid("", "abc"))
        assertFalse(ImmichConfig.isValid("https://photos.example.org", "  "))
    }

    @Test
    fun `the key hint shows only the tail`() {
        val config = ImmichConfig("https://photos.example.org", "EXAMPLE-TEST-KEY-not-a-real-credential-u5DQ")
        assertEquals("API key ••••u5DQ", config.keyHint())
        assertEquals("API key ••••", ImmichConfig("https://x.org", "abc").keyHint())
        assertEquals("no API key", ImmichConfig("https://x.org", "   ").keyHint())
    }

    @Test
    fun `the key hint never leaks a short key`() {
        val config = ImmichConfig("https://x.org", "abcd")
        assertFalse(config.keyHint().contains("abcd"))
    }

    @Test
    fun `demo config is marked as demo`() {
        assertTrue(ImmichConfig.Demo.demo)
    }
}
