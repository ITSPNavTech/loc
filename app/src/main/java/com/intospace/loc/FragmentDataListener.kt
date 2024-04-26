package com.intospace.loc

import android.location.Location


interface FragmentDataListener {
    fun onReceivedManualLocation(data: Location)
    fun onReceivedReferenceLocation(data: Location)
}