package com.intospace.loc

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.MenuItemCompat

class DebugSettingFragment : LonesmartFragment(), OnSharedPreferenceChangeListener {

    private var dataListener: FragmentDataListener? = null
    private val defaultPosition = arrayOf( 36.376830280, 127.354524670, 91.494 )

    companion object {
        val KEY_REF_LAT: String = "ref_lat"
        val KEY_REF_LON: String = "ref_lon"
        val KEY_REF_ALT: String = "ref_alt"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_debug_setting, container, false)
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentDataListener) {
            dataListener = context
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 기준 위치 설정
        val latStr = sharedPref.getString(KEY_REF_LAT, defaultPosition[0].toString())!!
        val lat_edit = view.findViewById<EditText>(R.id.latitude_number)
        lat_edit.setText(latStr)

        val lonStr = sharedPref.getString(KEY_REF_LON, defaultPosition[1].toString())!!
        val lon_edit = view.findViewById<EditText>(R.id.longitude_number)
        lon_edit.setText(lonStr)

        val altStr = sharedPref.getString(KEY_REF_ALT, defaultPosition[2].toString())!!
        val alt_edit = view.findViewById<EditText>(R.id.height_number)
        alt_edit.setText(altStr)

        updateLocation(latStr, lonStr, altStr)

        val ref_loc_button = view.findViewById<Button>(R.id.reference_location_button)
        ref_loc_button.setOnClickListener() { _ ->
            sharedPref.edit()?.putString(KEY_REF_LAT, lat_edit.text.toString())?.apply()
            sharedPref.edit()?.putString(KEY_REF_LON, lon_edit.text.toString())?.apply()
            sharedPref.edit()?.putString(KEY_REF_ALT, alt_edit.text.toString())?.apply()
            updateLocation(lat_edit.text.toString(), lon_edit.text.toString(), alt_edit.text.toString())
        }

        // 로그 저장 설정 초기화
        val logSwitchCompat: SwitchCompat = view.findViewById(R.id.logging_switch)
        logSwitchCompat.isChecked = (activity as LoneSmartMainActivity).mIsLogging
        logSwitchCompat.setOnCheckedChangeListener { _, isChecked ->
            val switchItem = (activity as LoneSmartMainActivity).menu?.findItem(R.id.menu_logging_item)
            val switchView = MenuItemCompat.getActionView(switchItem) as Switch
            if(switchView.isChecked != isChecked)
                switchView.isChecked = isChecked
            (activity as LoneSmartMainActivity).setLoging(isChecked)
        }
        // 위치 표시 설정 및 로깅 초기화
        val refPosSwitchCompat: SwitchCompat = view.findViewById(R.id.ref_marker_switch)
        refPosSwitchCompat.isChecked = false
        val gpsPosSwitchCompat: SwitchCompat = view.findViewById(R.id.gps_marker_switch)
        gpsPosSwitchCompat.isChecked = true
        val fusedPosSwitchCompat: SwitchCompat = view.findViewById(R.id.fused_marker_switch)
        fusedPosSwitchCompat.isChecked = false
        val apiPosSwitchCompat: SwitchCompat = view.findViewById(R.id.api_marker_switch)
        apiPosSwitchCompat.isChecked = true
    }

    fun updateLocation(latStr:String, lonStr:String, altStr:String) {
        val refLocation : Location = Location("REF").apply {
            latitude = latStr.toDouble() ; longitude =lonStr.toDouble(); altitude = altStr.toDouble() }
        dataListener?.onReceivedReferenceLocation(refLocation)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }
}
