package com.github.danzuep.usfmbible.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DataPolicyTest {
    private fun allowed(policy: Int, unmetered: Boolean, roaming: Boolean) =
        Settings.DEFAULT.copy(dataPolicy = policy).allowsNetwork(unmetered, roaming)

    @Test
    fun defaultsToMobile() = assertEquals(Settings.DATA_POLICY_MOBILE, Settings.DEFAULT.dataPolicy)

    @Test
    fun wifiOnlyAllowsUnmetered() {
        assertEquals(true, allowed(Settings.DATA_POLICY_WIFI, unmetered = true, roaming = false))
        assertEquals(false, allowed(Settings.DATA_POLICY_WIFI, unmetered = false, roaming = false))
    }

    @Test
    fun mobileBlocksRoaming() {
        assertEquals(true, allowed(Settings.DATA_POLICY_MOBILE, unmetered = false, roaming = false))
        assertEquals(false, allowed(Settings.DATA_POLICY_MOBILE, unmetered = false, roaming = true))
    }

    @Test
    fun roamingAllowsEverything() = assertEquals(true, allowed(Settings.DATA_POLICY_ROAMING, unmetered = false, roaming = true))
}
