package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity

class CallNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.telecom.ACTION_DECLINE_CALL"
        const val ACTION_END_CALL = "com.example.telecom.ACTION_END_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.telecom.ACTION_TOGGLE_MUTE"
        const val ACTION_CYCLE_AUDIO_ROUTE = "com.example.telecom.ACTION_CYCLE_AUDIO_ROUTE"
        const val EXTRA_RETURN_TO_CALL = "extra_return_to_call"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d("CallNotifReceiver", "onReceive action=$action")
        when (action) {
            ACTION_ANSWER_CALL -> {
                CallStateRepository.answerIncomingCall()
                CallStateRepository.setUiMinimized(false)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_RETURN_TO_CALL, true)
                }
                runCatching { context.startActivity(openIntent) }
            }
            ACTION_DECLINE_CALL, ACTION_END_CALL -> {
                CallStateRepository.rejectOrEndCall()
            }
            ACTION_TOGGLE_MUTE -> {
                CallStateRepository.toggleMuteFromNotification()
            }
            ACTION_CYCLE_AUDIO_ROUTE -> {
                CallStateRepository.cycleAudioRouteFromNotification()
            }
        }
    }
}
