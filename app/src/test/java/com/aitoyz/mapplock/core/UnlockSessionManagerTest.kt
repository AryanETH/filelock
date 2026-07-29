package com.aitoyz.mapplock.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UnlockSessionManagerTest {

    private var currentTime: Long = 0L

    @Before
    fun setup() {
        UnlockSessionManager.clearAll()
        currentTime = 1000L
        UnlockSessionManager.clock = { currentTime }
        UnlockSessionManager.gracePeriodMillis = 15000L
    }

    @Test
    fun `isAuthenticationRequired should return false after marking authenticated`() {
        val pkg = "com.example.app"
        UnlockSessionManager.onForeground(pkg)
        UnlockSessionManager.markAuthenticated(pkg)
        
        assertFalse(UnlockSessionManager.isAuthenticationRequired(pkg))
    }

    @Test
    fun `isUnlocked should return true within grace period after backgrounding`() {
        val pkg = "com.example.app"
        UnlockSessionManager.onForeground(pkg)
        UnlockSessionManager.markAuthenticated(pkg)
        
        UnlockSessionManager.onBackground(pkg)
        
        // Advance time by 5 seconds
        currentTime += 5000L
        
        assertTrue(UnlockSessionManager.isUnlocked(pkg))
    }

    @Test
    fun `isAuthenticationRequired should return true after grace period expires`() {
        val pkg = "com.example.app"
        UnlockSessionManager.onForeground(pkg)
        UnlockSessionManager.markAuthenticated(pkg)
        
        UnlockSessionManager.onBackground(pkg)
        
        // Advance time by 20 seconds (greater than 15s grace period)
        currentTime += 20000L
        
        assertTrue(UnlockSessionManager.isAuthenticationRequired(pkg))
    }

    @Test
    fun `onForeground should keep authenticated status if within app usage`() {
        val pkg = "com.example.app"
        UnlockSessionManager.onForeground(pkg)
        UnlockSessionManager.markAuthenticated(pkg)
        
        UnlockSessionManager.onBackground(pkg)
        currentTime += 10000L // 10s passed
        
        UnlockSessionManager.onForeground(pkg)
        currentTime += 10000L // Another 10s passed
        
        // App is back in foreground within reasonable time (no relock needed if still foreground)
        assertFalse(UnlockSessionManager.isAuthenticationRequired(pkg))
    }

    @Test
    fun `clearAll should require authentication for all apps`() {
        val pkg1 = "app1"
        val pkg2 = "app2"
        UnlockSessionManager.markAuthenticated(pkg1)
        UnlockSessionManager.markAuthenticated(pkg2)
        
        UnlockSessionManager.clearAll()
        
        assertTrue(UnlockSessionManager.isAuthenticationRequired(pkg1))
        assertTrue(UnlockSessionManager.isAuthenticationRequired(pkg2))
    }
}
