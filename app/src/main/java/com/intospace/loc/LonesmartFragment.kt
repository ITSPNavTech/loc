package com.intospace.loc

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.location.Location
import android.os.Bundle
import androidx.fragment.app.Fragment

abstract class LonesmartFragment : Fragment(), FragmentDataListener, OnSharedPreferenceChangeListener {

    var manualLocation : Location?=null
    var referenceLocation : Location?=null
    var networkLocation: Location?=null
    var gpsLocation : Location? = null
    var fusedLocation : Location? = null
    var lspiLocation : Location? = null

    val OPTION_PREFERENCE: String = "lonesmart_option"

    protected lateinit var sharedPref : SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPref = activity?.getSharedPreferences(OPTION_PREFERENCE, Context.MODE_PRIVATE)!!
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPref.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onReceivedManualLocation(data: Location) {
        this.manualLocation = data
    }

    override fun onReceivedReferenceLocation(data: Location) {
        this.referenceLocation = data
    }

    abstract override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?)
}