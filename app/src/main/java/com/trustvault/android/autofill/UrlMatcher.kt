package com.trustvault.android.autofill

/**
 * URL/domain matching utility for credential autofill.
 *
 * **Purpose**: Determine if a credential should be suggested for a given URL or package name
 *
 * **Matching Strategy**:
 * 1. Exact package name match (highest priority for apps)
 * 2. Domain matching (for web apps and browsers):
 *    - Exact domain match (example.com = example.com)
 *    - Subdomain match (login.example.com matches example.com via allowedDomains)
 *    - Wildcard match (*.example.com matches login.example.com)
 *    - Fuzzy match (example.com contains example)
 *
 * **Domain List Format**:
 * - Full URLs: https://example.com, https://login.example.com
 * - Bare domains: example.com, login.example.com
 * - Wildcards: *.example.com (matches any subdomain)
 * - No scheme handling: www.example.com is normalized to example.com
 *
 * **Security Notes**:
 * - No logging of URLs/credentials (only match results)
 * - Conservative matching (false negatives acceptable, false positives avoided)
 * - Subdomain matching requires explicit allowedDomains entry
 */
object UrlMatcher {

    /**
     * Determines if a credential matches a given app/web context.
     *
     * @param credentialPackageName Package name associated with credential (e.g., "com.google.android.gms")
     * @param credentialWebsite Primary website URL for credential (e.g., "https://accounts.google.com")
     * @param allowedDomains Additional domains/patterns to match against
     * @param requestPackageName App package name requesting autofill (e.g., "com.google.android.gms")
     * @param requestUrl URL of the page requesting autofill (from Credential Manager/Intent)
     * @return True if credential should be suggested for this context
     */
    fun isMatch(
        credentialPackageName: String,
        credentialWebsite: String,
        allowedDomains: List<String>,
        requestPackageName: String?,
        requestUrl: String?
    ): Boolean {
        // 1. Check exact package name match (highest priority for apps)
        if (credentialPackageName.isNotEmpty() && requestPackageName != null) {
            if (credentialPackageName == requestPackageName) {
                return true
            }
        }

        // 2. Check domain matching for web apps
        if (requestUrl != null) {
            // Check primary website
            if (credentialWebsite.isNotEmpty()) {
                if (domainsMatch(credentialWebsite, requestUrl)) {
                    return true
                }
            }

            // Check allowed domains
            for (domain in allowedDomains) {
                if (domainsMatch(domain, requestUrl)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Checks if two domains/URLs match.
     *
     * Supports:
     * - Exact match: "example.com" = "example.com"
     * - Subdomain match: "example.com" = "login.example.com"
     * - Wildcard match: "*.example.com" = "login.example.com"
     *
     * @param credentialDomain Domain/URL from credential (e.g., "example.com", "*.example.com")
     * @param requestUrl Request URL (e.g., "https://login.example.com/path")
     * @return True if domains match
     */
    fun domainsMatch(credentialDomain: String, requestUrl: String): Boolean {
        val credDomain = normalizeDomain(credentialDomain)
        val reqDomain = extractDomain(requestUrl)

        if (credDomain.isEmpty() || reqDomain.isEmpty()) {
            return false
        }

        // Exact match
        if (credDomain == reqDomain) {
            return true
        }

        // Wildcard match: *.example.com matches subdomain.example.com (single level only)
        if (credDomain.startsWith("*.")) {
            val suffix = credDomain.substring(2) // "example.com"
            if (suffix == reqDomain) {
                // Exact match to base domain (don't match *.example.com to example.com)
                return false
            }
            if (reqDomain.endsWith("." + suffix)) {
                // Make sure it's exactly one level of subdomain
                // Count dots: "subdomain.example.com" has 2 dots, suffix "example.com" has 1 dot
                val reqDots = reqDomain.count { it == '.' }
                val suffixDots = suffix.count { it == '.' }
                if (reqDots == suffixDots + 1) {
                    // Exactly one level deep: matches
                    return true
                }
            }
        }

        // Subdomain match (fuzzy): "example.com" matches "login.example.com"
        if (reqDomain.endsWith("." + credDomain)) {
            return true
        }

        return false
    }

    /**
     * Extracts domain from a URL or domain string.
     *
     * Examples:
     * - "https://example.com/path" -> "example.com"
     * - "https://login.example.com:8080/path" -> "login.example.com"
     * - "example.com" -> "example.com"
     * - "www.example.com" -> "example.com" (www removed)
     *
     * @param urlOrDomain URL or domain string
     * @return Normalized domain (lowercase, www removed, no scheme/port/path)
     */
    fun extractDomain(urlOrDomain: String): String {
        return normalizeDomain(urlOrDomain)
    }

    /**
     * Normalizes a domain string by removing scheme, port, path, and www prefix.
     *
     * @param domainOrUrl Domain or URL string
     * @return Normalized domain (e.g., "example.com")
     */
    fun normalizeDomain(domainOrUrl: String): String {
        return try {
            var result = domainOrUrl.trim().lowercase()

            // Remove scheme (http://, https://, etc.)
            if (result.contains("://")) {
                result = result.substringAfter("://")
            }

            // Remove port and path
            result = result.substringBefore("/")
            result = result.substringBefore(":")

            // Remove www. prefix (but preserve *.example.com)
            if (result.startsWith("www.")) {
                result = result.substring(4)
            }

            result
        } catch (e: Exception) {
            domainOrUrl.lowercase()
        }
    }

    /**
     * Generates default allowed domains from a primary website URL.
     *
     * **Logic**:
     * 1. Extract base domain (e.g., "accounts.google.com" -> "google.com")
     * 2. Create wildcard pattern (*.google.com)
     * 3. Include primary domain as-is
     *
     * This allows users to use the same credential for all subdomains.
     *
     * @param website Primary website URL
     * @return List of suggested allowed domains
     */
    fun generateDefaultAllowedDomains(website: String): List<String> {
        if (website.isEmpty()) return emptyList()

        val normalized = normalizeDomain(website)
        if (normalized.isEmpty()) return emptyList()

        val domains = mutableListOf(normalized)

        // Generate wildcard for subdomains
        // Split domain and reconstruct with wildcard
        // e.g., "accounts.google.com" -> ["accounts.google.com", "*.google.com"]
        val parts = normalized.split(".")
        if (parts.size > 2) {
            // Has subdomain, create wildcard for root domain
            val rootDomain = parts.drop(1).joinToString(".")
            domains.add("*.$rootDomain")
        }

        return domains
    }

    /**
     * Validates if a domain string is in valid format.
     *
     * Allows:
     * - Bare domains: "example.com"
     * - Full URLs: "https://example.com/path"
     * - Wildcards: "*.example.com"
     *
     * Rejects:
     * - Empty strings
     * - Invalid characters
     *
     * @param domain Domain string to validate
     * @return True if domain format is valid
     */
    fun isValidDomain(domain: String): Boolean {
        if (domain.isEmpty()) return false

        val normalized = normalizeDomain(domain)
        if (normalized.isEmpty()) return false

        // Check for invalid characters (basic validation)
        // Valid: alphanumeric, dots, hyphens, wildcards
        val validPattern = Regex("^[*a-z0-9.-]+$")
        return validPattern.matches(normalized)
    }
}
