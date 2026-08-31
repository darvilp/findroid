package dev.jdtech.jellyfin.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentSynchronizationEditPolicyTest {
    @Test
    fun cancelDoesNotProduceAValueToPersist() {
        val policy = PersistentSynchronizationEditPolicy(initialValueMs = 100)
        policy.edit(-250)

        assertNull(policy.cancel())
    }

    @Test
    fun saveProducesTheEditedValue() {
        val policy = PersistentSynchronizationEditPolicy(initialValueMs = 100)
        policy.edit(-250)

        assertEquals(-250L, policy.save())
    }
}
