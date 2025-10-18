package com.trustvault.android.autofill

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for URL/domain matching in autofill.
 *
 * Tests cover:
 * - Exact package name matching
 * - Exact domain matching
 * - Subdomain matching
 * - Wildcard matching (*.example.com)
 * - Domain normalization
 * - URL parsing
 * - Default domain generation
 */
class UrlMatcherTest {

    @Test
    fun testExactPackageNameMatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "com.google.android.gms",
            credentialWebsite = "https://accounts.google.com",
            allowedDomains = emptyList(),
            requestPackageName = "com.google.android.gms",
            requestUrl = null
        )
        assertTrue("Should match exact package name", isMatch)
    }

    @Test
    fun testPackageNameMismatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "com.google.android.gms",
            credentialWebsite = "https://accounts.google.com",
            allowedDomains = emptyList(),
            requestPackageName = "com.example.app",
            requestUrl = null
        )
        assertFalse("Should not match different package name", isMatch)
    }

    @Test
    fun testExactDomainMatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://example.com/login"
        )
        assertTrue("Should match exact domain", isMatch)
    }

    @Test
    fun testExactDomainMatchBareUrl() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "example.com",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://example.com/login"
        )
        assertTrue("Should match exact domain (bare URL)", isMatch)
    }

    @Test
    fun testDomainMismatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://different.com/login"
        )
        assertFalse("Should not match different domain", isMatch)
    }

    @Test
    fun testSubdomainMatchWithAllowedDomains() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = listOf("*.example.com"),
            requestPackageName = null,
            requestUrl = "https://login.example.com"
        )
        assertTrue("Should match subdomain with wildcard in allowed domains", isMatch)
    }

    @Test
    fun testMultipleLevelSubdomainMatchWithWildcard() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = listOf("*.example.com"),
            requestPackageName = null,
            requestUrl = "https://api.login.example.com"
        )
        // Wildcards only match single level typically
        assertFalse("Should not match multi-level subdomain with single wildcard", isMatch)
    }

    @Test
    fun testWildcardDomainMatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = listOf("*.example.com"),
            requestPackageName = null,
            requestUrl = "https://api.example.com/endpoint"
        )
        assertTrue("Should match wildcard domain", isMatch)
    }

    @Test
    fun testSubdomainFuzzyMatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = listOf("login.example.com"),
            requestPackageName = null,
            requestUrl = "https://login.example.com"
        )
        assertTrue("Should match explicit subdomain in allowed domains", isMatch)
    }

    @Test
    fun testPrimaryWebsiteSubdomainMatch() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://accounts.google.com",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://accounts.google.com"
        )
        assertTrue("Should match primary website exactly", isMatch)
    }

    @Test
    fun testAllowedDomainsEntry() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = listOf("api.example.com", "cdn.example.com"),
            requestPackageName = null,
            requestUrl = "https://cdn.example.com/resource"
        )
        assertTrue("Should match entry in allowed domains list", isMatch)
    }

    @Test
    fun testNormalizeDomain() {
        assertEquals("example.com", UrlMatcher.normalizeDomain("https://example.com"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("https://example.com/path"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("https://example.com:8080"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("http://www.example.com"))
        assertEquals("login.example.com", UrlMatcher.normalizeDomain("https://login.example.com"))
    }

    @Test
    fun testNormalizeDomainCaseInsensitive() {
        assertEquals("example.com", UrlMatcher.normalizeDomain("HTTPS://EXAMPLE.COM"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("Example.Com"))
    }

    @Test
    fun testNormalizeDomainWithPort() {
        assertEquals("example.com", UrlMatcher.normalizeDomain("https://example.com:443/path"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("example.com:8080"))
    }

    @Test
    fun testNormalizeDomainWithWww() {
        assertEquals("example.com", UrlMatcher.normalizeDomain("www.example.com"))
        assertEquals("example.com", UrlMatcher.normalizeDomain("https://www.example.com"))
    }

    @Test
    fun testNormalizeDomainWithSubdomain() {
        assertEquals("login.example.com", UrlMatcher.normalizeDomain("https://login.example.com"))
        assertEquals("api.v2.example.com", UrlMatcher.normalizeDomain("https://api.v2.example.com/v1"))
    }

    @Test
    fun testExtractDomain() {
        assertEquals("example.com", UrlMatcher.extractDomain("https://example.com/path"))
        assertEquals("login.example.com", UrlMatcher.extractDomain("https://login.example.com"))
        assertEquals("example.com", UrlMatcher.extractDomain("example.com"))
    }

    @Test
    fun testDomainsMatchExact() {
        assertTrue(UrlMatcher.domainsMatch("example.com", "https://example.com"))
        assertTrue(UrlMatcher.domainsMatch("example.com", "example.com"))
    }

    @Test
    fun testDomainsMatchWildcard() {
        assertTrue(UrlMatcher.domainsMatch("*.example.com", "https://login.example.com"))
        assertTrue(UrlMatcher.domainsMatch("*.example.com", "api.example.com"))
        assertFalse(UrlMatcher.domainsMatch("*.example.com", "example.com"))
    }

    @Test
    fun testDomainsMatchSubdomain() {
        assertTrue(UrlMatcher.domainsMatch("example.com", "https://login.example.com"))
        assertTrue(UrlMatcher.domainsMatch("example.com", "api.example.com"))
    }

    @Test
    fun testDomainsMatchFailure() {
        assertFalse(UrlMatcher.domainsMatch("example.com", "https://different.com"))
        assertFalse(UrlMatcher.domainsMatch("example.com", "examplexcom"))
    }

    @Test
    fun testIsValidDomain() {
        assertTrue(UrlMatcher.isValidDomain("example.com"))
        assertTrue(UrlMatcher.isValidDomain("https://example.com"))
        assertTrue(UrlMatcher.isValidDomain("*.example.com"))
        assertTrue(UrlMatcher.isValidDomain("api.v2.example.com"))

        assertFalse(UrlMatcher.isValidDomain(""))
        assertFalse(UrlMatcher.isValidDomain("   "))
    }

    @Test
    fun testGenerateDefaultAllowedDomains() {
        val domains = UrlMatcher.generateDefaultAllowedDomains("https://accounts.google.com")
        assertTrue("Should include primary domain", domains.contains("accounts.google.com"))
        assertTrue("Should include wildcard", domains.contains("*.google.com"))
    }

    @Test
    fun testGenerateDefaultAllowedDomainsSimple() {
        val domains = UrlMatcher.generateDefaultAllowedDomains("https://example.com")
        assertEquals(1, domains.size)
        assertTrue("Should include example.com", domains.contains("example.com"))
    }

    @Test
    fun testGenerateDefaultAllowedDomainsEmpty() {
        val domains = UrlMatcher.generateDefaultAllowedDomains("")
        assertTrue("Should return empty list for empty input", domains.isEmpty())
    }

    @Test
    fun testComplexUrlMatching() {
        // Real-world scenario: Google account accessed from browser
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://accounts.google.com",
            allowedDomains = listOf("*.google.com"),
            requestPackageName = "com.android.chrome",
            requestUrl = "https://mail.google.com"
        )
        assertTrue("Should match Gmail subdomain", isMatch)
    }

    @Test
    fun testBankingScenario() {
        // Bank credential with both app and web access
        val appMatch = UrlMatcher.isMatch(
            credentialPackageName = "com.mybank.android",
            credentialWebsite = "https://banking.mybank.com",
            allowedDomains = listOf("*.mybank.com"),
            requestPackageName = "com.mybank.android",
            requestUrl = null
        )
        assertTrue("Should match app package", appMatch)

        val webMatch = UrlMatcher.isMatch(
            credentialPackageName = "com.mybank.android",
            credentialWebsite = "https://banking.mybank.com",
            allowedDomains = listOf("*.mybank.com"),
            requestPackageName = null,
            requestUrl = "https://secure.mybank.com/login"
        )
        assertTrue("Should match web domain via wildcard", webMatch)
    }

    @Test
    fun testWildcardDoesNotMatchBaseDomain() {
        val isMatch = UrlMatcher.domainsMatch("*.example.com", "example.com")
        assertFalse("Wildcard should not match bare domain", isMatch)
    }

    @Test
    fun testSubdomainRootMatch() {
        val isMatch = UrlMatcher.domainsMatch("example.com", "api.example.com")
        assertTrue("Root domain should match subdomain", isMatch)
    }

    @Test
    fun testPartialDomainNoMatch() {
        val isMatch = UrlMatcher.domainsMatch("example.com", "notexample.com")
        assertFalse("Partial domain name should not match", isMatch)
    }

    @Test
    fun testHttpsHttpParity() {
        val isMatch1 = UrlMatcher.domainsMatch("https://example.com", "http://example.com")
        val isMatch2 = UrlMatcher.domainsMatch("example.com", "https://example.com")

        assertTrue("https and http should match on domain", isMatch1)
        assertTrue("http and https should match on domain", isMatch2)
    }

    @Test
    fun testCaseSensitivityInDomainMatching() {
        val isMatch = UrlMatcher.domainsMatch("EXAMPLE.COM", "https://example.com/login")
        assertTrue("Domain matching should be case-insensitive", isMatch)
    }

    @Test
    fun testEmptyAllowedDomainsList() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "https://example.com",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://api.example.com"
        )
        assertTrue("Should fall back to primary website matching", isMatch)
    }

    @Test
    fun testNullPackageName() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "com.example.app",
            credentialWebsite = "",
            allowedDomains = emptyList(),
            requestPackageName = null,
            requestUrl = "https://example.com"
        )
        assertFalse("Should not match if request package is null", isMatch)
    }

    @Test
    fun testNoWebsiteNoPackage() {
        val isMatch = UrlMatcher.isMatch(
            credentialPackageName = "",
            credentialWebsite = "",
            allowedDomains = emptyList(),
            requestPackageName = "com.example.app",
            requestUrl = null
        )
        assertFalse("Should not match with no website or package", isMatch)
    }
}
