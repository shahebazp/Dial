package com.example

import com.example.data.model.ActiveCallSession
import com.example.data.model.CallDirection
import com.example.data.model.DialCallState
import com.example.telecom.CallStateRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @After
    fun tearDown() {
        CallStateRepository.setSessionForTesting(null)
    }

    @Test
    fun pocketModePowerButtonPress_rejectsIncomingCallWithoutAnswering() {
        val session = ActiveCallSession(
            callId = "call_pocket_1",
            contactName = "Ada Lovelace",
            phoneNumber = "+1 555 0100",
            direction = CallDirection.INCOMING,
            state = DialCallState.INCOMING,
            useSlideToAnswer = true,
            isPocketModeLatched = true
        )
        CallStateRepository.setSessionForTesting(session)

        val handled = CallStateRepository.onPowerLockButtonPressed()

        assertTrue("Power button press in Pocket Mode should reject the ringing call", handled)
        assertNull("Active call session should be cleared after rejection", CallStateRepository.activeCallSession.value)
    }

    @Test
    fun normalModePowerButtonPress_doesNotTriggerPocketRejection() {
        val session = ActiveCallSession(
            callId = "call_normal_1",
            contactName = "Grace Hopper",
            phoneNumber = "+1 555 0199",
            direction = CallDirection.INCOMING,
            state = DialCallState.INCOMING,
            useSlideToAnswer = false,
            isPocketModeLatched = false
        )
        CallStateRepository.setSessionForTesting(session)

        val handled = CallStateRepository.onPowerLockButtonPressed()

        assertFalse("Normal mode should not trigger Pocket Mode power rejection", handled)
        assertEquals(DialCallState.INCOMING, CallStateRepository.activeCallSession.value?.state)
    }
}
