package com.trustvault.android.accessibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.trustvault.android.accessibility.AllowlistManager.InstalledAppInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AllowlistManager.
 *
 * Tests:
 * - Adding/removing packages from allowlist
 * - Checking if package is allowed
 * - Getting all allowed packages
 * - App info retrieval
 * - Clearing allowlist
 */
class AllowlistManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var manager: AllowlistManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        manager = AllowlistManager(context, packageManager)
    }

    @Test
    fun `initially allowlist is empty`() = runBlocking {
        val allowed = manager.getAllowedPackages().first()
        assertTrue(allowed.isEmpty())
    }

    @Test
    fun `package not in allowlist is rejected`() = runBlocking {
        val isAllowed = manager.isPackageAllowed("com.example.app")
        assertFalse(isAllowed)
    }

    @Test
    fun `adding package to allowlist succeeds`() = runBlocking {
        manager.addPackageToAllowlist("com.example.app")
        val isAllowed = manager.isPackageAllowed("com.example.app")
        assertTrue(isAllowed)
    }

    @Test
    fun `multiple packages can be added`() = runBlocking {
        manager.addPackageToAllowlist("com.example.app1")
        manager.addPackageToAllowlist("com.example.app2")
        manager.addPackageToAllowlist("com.example.app3")

        val allowed = manager.getAllowedPackages().first()
        assertEquals(3, allowed.size)
        assertTrue(allowed.contains("com.example.app1"))
        assertTrue(allowed.contains("com.example.app2"))
        assertTrue(allowed.contains("com.example.app3"))
    }

    @Test
    fun `removing package from allowlist succeeds`() = runBlocking {
        manager.addPackageToAllowlist("com.example.app")
        assertTrue(manager.isPackageAllowed("com.example.app"))

        manager.removePackageFromAllowlist("com.example.app")
        assertFalse(manager.isPackageAllowed("com.example.app"))
    }

    @Test
    fun `clearing allowlist removes all packages`() = runBlocking {
        manager.addPackageToAllowlist("com.example.app1")
        manager.addPackageToAllowlist("com.example.app2")

        manager.clearAllowlist()
        val allowed = manager.getAllowedPackages().first()
        assertTrue(allowed.isEmpty())
    }

    @Test
    fun `getting app label returns package name if app not found`() {
        every {
            packageManager.getApplicationInfo(any(), any() as Int)
        } throws PackageManager.NameNotFoundException()

        val label = manager.getAppLabel("com.example.notfound")
        assertEquals("com.example.notfound", label)
    }

    @Test
    fun `getting installed apps filters system apps`() {
        val userApp = ApplicationInfo().apply {
            packageName = "com.example.user"
            flags = 0 // No FLAG_SYSTEM
        }

        val systemApp = ApplicationInfo().apply {
            packageName = "android.system"
            flags = ApplicationInfo.FLAG_SYSTEM
        }

        every {
            packageManager.getInstalledApplications(any() as Int)
        } returns listOf(userApp, systemApp)

        every {
            packageManager.getApplicationLabel(any())
        } answers { arg ->
            val appInfo = arg.invocation.args[0] as ApplicationInfo
            appInfo.packageName
        }

        val apps = manager.getInstalledApps()

        assertEquals(1, apps.size)
        assertEquals("com.example.user", apps[0].packageName)
    }

    @Test
    fun `installed app info contains correct data`() {
        val app = InstalledAppInfo(
            packageName = "com.example.app",
            displayName = "Example App"
        )

        assertEquals("com.example.app", app.packageName)
        assertEquals("Example App", app.displayName)
    }
}
