package com.intospace.loc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.IBinder

abstract class LonesmartService : Service(), OnSharedPreferenceChangeListener {

    protected lateinit var sharedPref : SharedPreferences

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    abstract override fun onBind(intent: Intent?): IBinder?

    abstract override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?)
}