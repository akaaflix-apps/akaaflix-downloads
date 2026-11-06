package com.akaaflix.prime.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    private fun base(input: String) = ServerUrl.normalize(input)?.baseUrl

    @Test fun acceptsCommonFormats() {
        assertEquals("http://example.com:8080/", base("example.com:8080"))
        assertEquals("http://example.com:8080/", base("http://example.com:8080"))
        assertEquals("http://example.com:8080/", base("  http://example.com:8080/  "))
        assertEquals("https://example.com/", base("https://example.com"))
        assertEquals("http://example.com:8080/", base("http://example.com:8080/player_api.php?username=a&password=b"))
        assertEquals("http://1.2.3.4:25461/", base("http://1.2.3.4:25461/get.php?username=a&password=b&type=m3u_plus"))
        assertEquals("http://example.com/panel/", base("http://example.com/panel/"))
        assertEquals("http://example.com/panel/", base("http://example.com/panel/player_api.php"))
        assertEquals("http://[::1]:8000/", base("[::1]:8000"))
        assertEquals("http://example.com/", base("http://example.com:80"))
        assertEquals("http://localhost:8000/", base("localhost:8000"))
    }

    @Test fun schemeGuessFlag() {
        assertTrue(ServerUrl.normalize("example.com")!!.schemeWasGuessed)
        assertFalse(ServerUrl.normalize("http://example.com")!!.schemeWasGuessed)
        assertEquals("https://example.com:8080/", ServerUrl.withScheme("http://example.com:8080/", "https"))
    }

    @Test fun rejectsInvalid() {
        assertNull(base(""))
        assertNull(base("not a url"))
        assertNull(base("ftp://example.com"))
        assertNull(base("http://"))
        assertNull(base("http://999.1.1.1"))
        assertNull(base("justtext"))
    }

    @Test fun displayHostHasNoCredentialsOrPath() {
        assertEquals("http://example.com:8080", ServerUrl.displayHost("http://example.com:8080/panel/"))
        assertEquals("https://example.com", ServerUrl.displayHost("https://example.com/"))
    }
}
