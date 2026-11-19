package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheck"
private const val DEBUG_TOKEN_EXTRA = "app_check_debug_token"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store"
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

fun configureAppCheck(context: Context, intent: Intent?) {
    val tokenFromIntent = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.takeIf { it.isNotBlank() }
    if (tokenFromIntent != null) {
        context.getSharedPreferences(DEBUG_STORE_PREFIX, Context.MODE_PRIVATE)
            .edit()
            .putString(DEBUG_SECRET_KEY, tokenFromIntent)
            .apply()
        Log.i(TAG, "Debug App Check token received via intent and persisted.")
    }

    try {
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to install AppCheckProviderFactory", e)
    }
}
