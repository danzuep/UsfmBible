package com.github.danzuep.usfmbible.core.repository.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationUpdateTest {
    private val old = "Mon, 01 Jan 2024 00:00:00 GMT"
    private val new = "Tue, 02 Jan 2024 00:00:00 GMT"

    @Test
    fun sameVersionIsUpToDate() = assertEquals(UpdateStatus.UP_TO_DATE, updateStatus(old, " $old"))

    @Test
    fun differentVersionIsAnUpdate() = assertEquals(UpdateStatus.UPDATE_AVAILABLE, updateStatus(old, new))

    @Test
    fun missingVersionsAreUnknown() {
        assertEquals(UpdateStatus.UNKNOWN, updateStatus(null, new))
        assertEquals(UpdateStatus.UNKNOWN, updateStatus(old, null))
        assertEquals(UpdateStatus.UNKNOWN, updateStatus("", new))
    }
}
