package com.trustvault.android.di

import android.content.Context
import com.trustvault.android.compliance.*
import com.trustvault.android.data.importexport.CsvExporter
import com.trustvault.android.domain.repository.CredentialRepository
import com.trustvault.android.security.BiometricAuthManager
import com.trustvault.android.security.CryptoManager
import com.trustvault.android.security.DatabaseKeyManager
import com.trustvault.android.util.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ComplianceModule - Hilt Dependency Injection for Compliance Components
 *
 * Provides singleton instances of all Phase 3 GDPR/DPDP compliance components:
 * - Consent management
 * - Data retention policies
 * - Data erasure (right to be forgotten)
 * - Data portability
 * - Audit logging with tamper-proof hash chains
 * - Compliance report generation
 * - Security event monitoring
 * - Data breach management
 * - Forensic analysis
 *
 * **Compliance Standards:**
 * - GDPR (EU Regulation 2016/679)
 * - DPDP Act 2023 (India)
 * - ISO 27001:2022
 * - OWASP Mobile Top 10 2025
 */
@Module
@InstallIn(SingletonComponent::class)
object ComplianceModule {

    /**
     * Provides AuditLogger for tamper-proof security event logging.
     *
     * ISO 27001 A.12.4.1-4: Event logging and protection of log information
     */
    @Provides
    @Singleton
    fun provideAuditLogger(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): AuditLogger {
        return AuditLogger(context, preferencesManager)
    }

    /**
     * Provides ConsentManager for GDPR Article 7 consent tracking.
     *
     * GDPR Article 7: Conditions for consent
     * DPDP Act Section 6: Proof of consent
     */
    @Provides
    @Singleton
    fun provideConsentManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager,
        auditLogger: AuditLogger
    ): ConsentManager {
        return ConsentManager(context, preferencesManager, auditLogger)
    }

    /**
     * Provides DataRetentionManager for GDPR Article 5(1)(e) storage limitation.
     *
     * GDPR Article 5(1)(e): Storage limitation
     * GDPR Article 25: Data protection by design
     */
    @Provides
    @Singleton
    fun provideDataRetentionManager(
        @ApplicationContext context: Context,
        credentialRepository: CredentialRepository
    ): DataRetentionManager {
        return DataRetentionManager(context, credentialRepository)
    }

    /**
     * Provides DataErasure for GDPR Article 17 right to be forgotten.
     *
     * GDPR Article 17: Right to erasure
     * DPDP Act Section 9: Right to erasure
     */
    @Provides
    @Singleton
    fun provideDataErasure(
        @ApplicationContext context: Context,
        credentialRepository: CredentialRepository,
        databaseKeyManager: DatabaseKeyManager,
        consentManager: ConsentManager
    ): DataErasure {
        return DataErasure(
            context,
            credentialRepository,
            databaseKeyManager,
            consentManager
        )
    }

    /**
     * Provides DataPortability for GDPR Article 20 data portability.
     *
     * GDPR Article 20: Right to data portability
     */
    @Provides
    @Singleton
    fun provideDataPortability(
        @ApplicationContext context: Context,
        credentialRepository: CredentialRepository,
        csvExporter: CsvExporter,
        consentManager: ConsentManager,
        privacyManager: PrivacyManager,
        cryptoManager: CryptoManager
    ): DataPortability {
        return DataPortability(
            context,
            credentialRepository,
            csvExporter,
            consentManager,
            privacyManager,
            cryptoManager
        )
    }

    /**
     * Provides PrivacyManager for comprehensive privacy orchestration.
     *
     * GDPR Articles 5-9, 13-21: Data protection principles and data subject rights
     * DPDP Act Sections 4-11: Consent, correction, erasure, portability
     */
    @Provides
    @Singleton
    fun providePrivacyManager(
        @ApplicationContext context: Context,
        consentManager: ConsentManager,
        dataRetentionManager: DataRetentionManager
    ): PrivacyManager {
        return PrivacyManager(context, consentManager, dataRetentionManager)
    }

    /**
     * Provides ComplianceReportGenerator for regulatory reporting.
     *
     * GDPR Article 30: Record of processing activities
     * ISO 27001: Audit reports
     */
    @Provides
    @Singleton
    fun provideComplianceReportGenerator(
        @ApplicationContext context: Context,
        credentialRepository: CredentialRepository,
        consentManager: ConsentManager,
        auditLogger: AuditLogger,
        preferencesManager: PreferencesManager
    ): ComplianceReportGenerator {
        return ComplianceReportGenerator(
            context,
            credentialRepository,
            consentManager,
            auditLogger,
            preferencesManager
        )
    }

    /**
     * Provides SecurityEventMonitor for real-time threat detection.
     *
     * ISO 27001 A.16: Information security incident management
     * GDPR Article 33: Notification of personal data breach
     */
    @Provides
    @Singleton
    fun provideSecurityEventMonitor(
        @ApplicationContext context: Context,
        auditLogger: AuditLogger
    ): SecurityEventMonitor {
        return SecurityEventMonitor(context, auditLogger)
    }

    /**
     * Provides DataBreachManager for GDPR Article 33 breach notification.
     *
     * GDPR Article 33: 72-hour breach notification requirement
     * GDPR Article 34: Communication of breach to data subjects
     */
    @Provides
    @Singleton
    fun provideDataBreachManager(
        @ApplicationContext context: Context,
        auditLogger: AuditLogger,
        securityMonitor: SecurityEventMonitor
    ): DataBreachManager {
        return DataBreachManager(context, auditLogger, securityMonitor)
    }

    /**
     * Provides ForensicAnalyzer for security incident investigation.
     *
     * ISO 27001 A.16.1.5: Response to information security incidents
     * ISO 27001 A.16.1.7: Collection of evidence
     */
    @Provides
    @Singleton
    fun provideForensicAnalyzer(
        @ApplicationContext context: Context,
        auditLogger: AuditLogger
    ): ForensicAnalyzer {
        return ForensicAnalyzer(context, auditLogger)
    }
}
