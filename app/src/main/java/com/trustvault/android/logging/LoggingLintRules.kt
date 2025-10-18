package com.trustvault.android.logging

/**
 * Static logging lint rules (documentation and checklist).
 *
 * SECURITY CONTROL: Prevents accidental secret logging.
 * These rules should be enforced during code review:
 *
 * 1. ❌ FORBIDDEN PATTERNS:
 *    - Log.d(), Log.i(), Log.e() direct calls
 *    - println(), System.out.println()
 *    - print statements with variables
 *    - String interpolation with sensitive data
 *    - Any log statement containing "password", "secret", "key", "token"
 *
 * 2. ✅ REQUIRED PATTERNS:
 *    - Use SecureLogger.d/i/w/e() for all logging
 *    - Pass TAG constant (avoid logging class/object)
 *    - Never log variables containing: passwords, keys, tokens, PII
 *    - Use summary information only (e.g. "Login succeeded" not "Login user@ex.com")
 *
 * 3. 🔍 AUTOMATED CHECKS:
 *    Run these commands to detect violations:
 *    ```
 *    # Find direct Log calls
 *    grep -r "Log\\.d\\|Log\\.i\\|Log\\.e" app/src/main/java --include="*.kt"
 *
 *    # Find println calls
 *    grep -r "println\\|System\\.out\\.print" app/src/main/java --include="*.kt"
 *
 *    # Find suspicious logging patterns
 *    grep -r "Log\\..*password\\|Log\\..*secret\\|Log\\..*token" app/src/main/java --include="*.kt"
 *    ```
 *
 * 4. 📋 VALID LOGGING EXAMPLES:
 *    ```kotlin
 *    // ✅ GOOD: Use SecureLogger
 *    logDebug(TAG, "Credential saved successfully")
 *    logError(TAG, "Failed to load credentials", exception)
 *
 *    // ✅ GOOD: Generic logging with no sensitive data
 *    logInfo(TAG, "Login flow started")
 *    logWarn(TAG, "Database not yet initialized")
 *
 *    // ❌ BAD: Direct Log calls
 *    Log.d(TAG, "User logged in")
 *
 *    // ❌ BAD: Variables with sensitive data
 *    logDebug(TAG, "Password: $password")
 *    logDebug(TAG, "User: $username")
 *
 *    // ❌ BAD: println
 *    println("Debug: $credentialData")
 *    ```
 *
 * 5. 📌 ENCRYPTION/CRYPTO LOGGING GUIDELINES:
 *    - Log operation completion, not keys/credentials
 *    - Log error messages, not error details (keys might appear in trace)
 *    - Example:
 *      ✅ logDebug(TAG, "Database key derived successfully")
 *      ❌ logDebug(TAG, "Derived key: ${derivedKey.toHex()}")
 */
object LoggingLintRules {
    // This is documentation only. Actual linting should be done via:
    // 1. Code review checklists
    // 2. Static analysis tools (detekt, ktlint custom rules)
    // 3. Git pre-commit hooks
    // 4. CI/CD pipeline checks
}

/**
 * Pre-commit hook script to detect logging violations.
 * Save as `.git/hooks/pre-commit` and make executable.
 *
 * ```bash
 * #!/bin/bash
 * # Prevent logging secrets in git commits
 *
 * # Check for direct Log calls
 * if git diff --cached app/src/main/java --include="*.kt" | \
 *    grep -E '^\+.*Log\.(d|i|e|w)\(' ; then
 *     echo "❌ ERROR: Direct Log calls found. Use SecureLogger instead."
 *     exit 1
 * fi
 *
 * # Check for println
 * if git diff --cached app/src/main/java --include="*.kt" | \
 *    grep -E '^\+.*(println|System\.out\.)' ; then
 *     echo "❌ ERROR: println found. Use SecureLogger instead."
 *     exit 1
 * fi
 *
 * # Check for suspicious logging patterns
 * if git diff --cached app/src/main/java --include="*.kt" | \
 *    grep -iE '^\+.*Log\.(d|i).*\$\(password|secret|token|key)' ; then
 *     echo "❌ ERROR: Logging with sensitive variable detected."
 *     exit 1
 * fi
 *
 * exit 0
 * ```
 */
object PreCommitHookExample {
    // See docstring above for pre-commit hook implementation
}

/**
 * Static analysis rule for detekt.
 * Add to detekt-config.yml:
 *
 * ```yaml
 * NoDirectLogCalls:
 *   active: true
 *   pattern: 'Log\.(d|i|e|w)\('
 *   message: 'Use SecureLogger instead of direct Log calls'
 *
 * NoPrintln:
 *   active: true
 *   pattern: '(println|System\.out\.print)'
 *   message: 'Use SecureLogger instead of println'
 *
 * NoSensitiveLogging:
 *   active: true
 *   patterns:
 *     - 'Log\.(d|i).*\$.*password'
 *     - 'Log\.(d|i).*\$.*secret'
 *     - 'Log\.(d|i).*\$.*token'
 *     - 'println.*\$.*password'
 *   message: 'Sensitive data detected in logging statement'
 * ```
 */
object DetektRuleExample {
    // See docstring above for detekt configuration
}
