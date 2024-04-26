/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intospace.loc

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.intospace.location.lbs.lonesmart.gnss.service.ITSPLocationProvider
import com.intospace.location.lbs.lonesmart.gnss.service.MeasurementListener
import java.text.SimpleDateFormat

/**
 * A map fragment to show the computed least square position and the device computed position on
 * Google map.
 */
class MapFragment : LonesmartFragment(), OnMapReadyCallback, MeasurementListener {

    private var isReady: Boolean = false
    private var isMoveCamera: Boolean = true

    companion object {
        private const val ZOOM_LEVEL = 19f
        private const val TAG = "MapFragment"
        @SuppressLint("SimpleDateFormat")
        private val DATE_SDF = SimpleDateFormat("HH:mm:ss")

        private const val REAL = 0
        private const val GPS = 2
        private const val FUSED = 1
        private const val LSPI = 3
    }

    // UI members
    private lateinit var mMap: GoogleMap
    private var mMarker: Marker? = null
    private lateinit var mMapView: MapView
    private var mLastLocationMarkerApi: Marker? = null
    private var mLastLocationMarkerFused: Marker? = null
    private var mLastLocationMarkerGps: Marker? = null
    private var mLastLocationMarkerReal: Marker? = null
    private var mFocusedMarker = 2
    private var viewPager2: ViewPager2? = null
    private var dataListener: FragmentDataListener? = null

    @SuppressLint("ClickableViewAccessibility")
    val touchListener = View.OnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 뷰에 터치되었을 때의 동작을 처리합니다.
                // 터치 시 이미지를 약간 줄입니다.
                v.scaleX = 0.95f;
                v.scaleY = 0.95f;
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 뷰에서 손가락을 뗄 때 또는 터치 동작이 취소되었을 때의 동작을 처리합니다.
                v.scaleX = 1.0f;
                v.scaleY = 1.0f;
            }
        }
        false
    }

    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val rootView: View = inflater.inflate(R.layout.fragment_mapview, container, false)
        isReady = false

        mMapView = rootView.findViewById<MapView>(R.id.map)
        mMapView.onCreate(savedInstanceState)
        mMapView.getMapAsync(this)
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST) {
        }

        viewPager2 = activity?.findViewById(R.id.viewPager)

        return rootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentDataListener) {
            dataListener = context
        }
    }

    override fun onResume() {
        super.onResume()
        if(::mMapView.isInitialized) mMapView.onResume()
    }

    override fun onPause() {
        mMapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView.onDestroy()
        (activity as LoneSmartMainActivity).unRegisterLocationListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val lastKnownLocation = mMap.cameraPosition.target
        outState.putParcelable("location", lastKnownLocation)
        outState.putFloat("zoom", mMap.cameraPosition.zoom)
    }

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMapLoadedCallback {
            mMap.snapshot {
                // it is not called
            }
        }
        //val lastKnownLocation = mMapView.savedInstanceState.getParcelable<LatLng>("location")
        //val zoomLevel = mMapView.savedInstanceState.getFloat("zoom")
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mMap., zoomLevel))
        mMap.isMyLocationEnabled = false
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = false
        mMap.setOnMarkerClickListener { marker ->
            if (marker == mLastLocationMarkerReal) {
                mFocusedMarker = REAL
                mLastLocationMarkerReal!!.showInfoWindow()
            } else if (marker == mLastLocationMarkerGps) {
                mFocusedMarker = GPS
                mLastLocationMarkerGps!!.showInfoWindow()
            } else if (marker == mLastLocationMarkerApi) {
                mFocusedMarker = LSPI
                mLastLocationMarkerApi!!.showInfoWindow()
            } else if (marker == mLastLocationMarkerFused) {
                mLastLocationMarkerFused!!.showInfoWindow()
                mFocusedMarker = FUSED
            } else {
                //mLastLocationMarkerRaw!!.showInfoWindow()
                mFocusedMarker = 4
            }
            isMoveCamera = mFocusedMarker < 4;

            val handler = Handler(Looper.getMainLooper())
            val runnable = Runnable {
                when (mFocusedMarker) {
                    REAL -> mLastLocationMarkerReal!!.hideInfoWindow()
                    FUSED -> mLastLocationMarkerFused!!.hideInfoWindow()
                    GPS -> mLastLocationMarkerGps!!.hideInfoWindow()
                    else -> mLastLocationMarkerApi!!.hideInfoWindow()
                }
            }
            handler.postDelayed(runnable, 2000)
            false
        }

        // ViewPager2의 스와이프 제어
        mMap.setOnMapClickListener {
            viewPager2?.isUserInputEnabled = false
        }
        val imageCallLayout: LinearLayout = requireActivity().findViewById(R.id.image_call_layout)
        imageCallLayout.setOnTouchListener { _, _ ->
            if(viewPager2?.isUserInputEnabled == true) {
                false
            } else {
                viewPager2?.isUserInputEnabled = true
                false
            }
        }

        // 지도를 길게 클릭했을 때의 리스너를 설정합니다.
        mMap.setOnMapLongClickListener { latLng ->
            if (mMarker == null) {
                // 마커가 없는 경우 새로운 마커를 추가합니다.
                mMarker = mMap.addMarker(MarkerOptions().position(latLng).title("전송할 좌표"))
            } else {
                // 이미 마커가 있는 경우 해당 마커를 클릭한 위치로 옮깁니다.
                mMarker?.position = latLng
            }
            // 클릭한 위치의 위도와 경도 정보를 가져옵니다.
            val lat = latLng.latitude
            val lon = latLng.longitude

            val location : Location = Location("USER").apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
            }
            // 위치 정보를 출력합니다.
            dataListener?.onReceivedManualLocation(location)
            isMoveCamera = false
            // Toast.makeText(requireActivity(), "Lat: $lat, Lon: $lon", Toast.LENGTH_SHORT).show()
        }

        (activity as LoneSmartMainActivity).registerLocationListener(this)

        isReady = true
    }

    private fun updateMapViewWithPositions(
        lspi : Location?,
        fused: Location?,
        gps : Location?,
        real : Location?
    ) {
        val refSwitch = activity?.findViewById<SwitchCompat>(R.id.ref_marker_switch)
        val gpsSwitch = activity?.findViewById<SwitchCompat>(R.id.gps_marker_switch)
        val fusedSwitch = activity?.findViewById<SwitchCompat>(R.id.fused_marker_switch)
        val apiSwitch = activity?.findViewById<SwitchCompat>(R.id.api_marker_switch)

        val real_lat  = if(refSwitch?.isChecked == true) real?.latitude else null
        val real_lon  = if(refSwitch?.isChecked == true) real?.longitude else null
        val gps_lat   = if(gpsSwitch?.isChecked == true) gps?.latitude else null
        val gps_lon   = if(gpsSwitch?.isChecked == true) gps?.longitude else null
        val fused_lat = if(fusedSwitch?.isChecked == true) fused?.latitude else null
        val fused_lon = if(fusedSwitch?.isChecked == true) fused?.longitude else null
        val lspi_lat  = if(apiSwitch?.isChecked == true) lspi?.latitude else null
        val lspi_lon  = if(apiSwitch?.isChecked == true) lspi?.longitude else null

        if(fused != null) {
            updateMapViewWithPositions(
                lspi_lat,
                lspi_lon,
                fused_lat,
                fused_lon,
                gps_lat,
                gps_lon,
                real_lat,
                real_lon
            )
        }
    }

    private fun updateMapViewWithPositions(
        latDegApi: Number?,
        lngDegApi: Number?,
        latDegFused: Number?,
        lngDegFused: Number?,
        latDegGps: Number?,
        lngDegGps: Number?,
        latDegReal: Number?,
        lngDegReal: Number?
    ) {
        val activity = activity ?: return

        if((activity as LoneSmartMainActivity).viewPager.currentItem != LoneSmartMainActivity.FRAGMENT_INDEX_MAP) {
            return
        }
        activity.runOnUiThread { // Log.i(TAG, "onLocationChanged");
            val latLngApi = latDegApi?.let { lngDegApi?.let { it1 -> LatLng(it.toDouble(), it1.toDouble()) } }
            val latLngFused = latDegFused?.toDouble()?.let { lngDegFused?.toDouble()?.let { it1 -> LatLng(it, it1) } }
            val latLngGps = latDegGps?.toDouble()?.let { lngDegGps?.toDouble()?.let { it1 -> LatLng(it, it1) } }
            val latLngReal = latDegReal?.let { lngDegReal?.toDouble()?.let { it1 -> LatLng(it.toDouble(), it1) } }
            if (isReady) {

                if (mLastLocationMarkerFused == null && latLngFused != null) {
                    mLastLocationMarkerFused = mMap.addMarker(
                        MarkerOptions()
                            .position(latLngFused)
                            .title("Fused")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_blue))
                    )
                    mLastLocationMarkerFused!!.showInfoWindow()
                }
                if (mLastLocationMarkerGps == null && latLngGps != null) {
                    mLastLocationMarkerGps = mMap.addMarker(
                        MarkerOptions()
                            .position(latLngGps)
                            .title("GPS")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_yellow))
                    )
                    mLastLocationMarkerGps!!.showInfoWindow()
                }
                if (mLastLocationMarkerReal == null && latLngReal != null) {
                    mLastLocationMarkerReal = latLngReal.let {
                        MarkerOptions()
                            .position(it)
                            .title("Real")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red))
                    }.let {
                        mMap.addMarker(
                            it
                        )
                    }
                    mLastLocationMarkerReal!!.showInfoWindow()
                }
                if (mLastLocationMarkerApi == null && latLngApi != null) {
                    mLastLocationMarkerApi = latLngApi.let {
                        MarkerOptions()
                            .position(it)
                            .title("API")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_green))
                    }.let {
                        mMap.addMarker(
                            it
                        )
                    }
                    latLngApi.let { CameraUpdateFactory.newLatLngZoom(it, ZOOM_LEVEL) }
                        .let {
                            mMap.moveCamera(it)
                        }
                }
                if (mLastLocationMarkerFused != null && latLngFused != null) {
                    mLastLocationMarkerFused!!.position = latLngFused
                }
                if (mLastLocationMarkerGps != null && latLngGps != null) {
                    mLastLocationMarkerGps!!.position = latLngGps
                }
                if (mLastLocationMarkerReal != null && latLngReal != null) {
                    mLastLocationMarkerReal!!.position = latLngReal
                }
                if (mLastLocationMarkerApi != null && latLngApi != null) {
                    mLastLocationMarkerApi!!.position = latLngApi
                }
                if(isMoveCamera) {
                    when (mFocusedMarker) {
                        REAL -> latLngReal?.let { CameraUpdateFactory.newLatLng(it) }
                            ?.let { mMap.moveCamera(it) }
                        FUSED -> latLngFused?.let { CameraUpdateFactory.newLatLng(it) }
                            ?.let { mMap.moveCamera(it) }
                        GPS -> latLngGps?.let { CameraUpdateFactory.newLatLng(it) }
                            ?.let { mMap.moveCamera(it) }
                        else -> latLngApi?.let { CameraUpdateFactory.newLatLng(it) }
                            ?.let { mMap.moveCamera(it) }
                    }
                }
            }
        }
    }

    private fun updateLocationInfo(location: Location) {
        val bundle: Bundle? = location.extras
        val displayString = StringBuilder()
        displayString.append(String.format("Latitude: %.8f\n", location.latitude));
        displayString.append(String.format("Longitude: %.8f\n", location.longitude));
        displayString.append(String.format("Altitude: %.3f\n", location.altitude));

        val compareSwitch = (activity?.findViewById<SwitchCompat>(R.id.compare_switch))
        if (compareSwitch != null) {
            if(compareSwitch.isChecked) {
                try {
                    val latStr = sharedPref.getString(DebugSettingFragment.KEY_REF_LAT, "36.0")!!
                    val ref_lat = latStr.toDouble()
                    val lonStr = sharedPref.getString(DebugSettingFragment.KEY_REF_LON, "127.0")!!
                    val ref_lon = lonStr.toDouble()
                    val altStr = sharedPref.getString(DebugSettingFragment.KEY_REF_ALT, "100.0")!!
                    val ref_alt = altStr.toDouble()
                    val distance = Distance.getDistanceMeters(
                        ref_lat,
                        ref_lon,
                        ref_alt,
                        location.latitude,
                        location.longitude,
                        location.altitude
                    )
                    val hError = String.format("Horizontal Error: %.3f" + System.lineSeparator(), distance.dh)
                    val vError = String.format("Vertical Error: %.3f" + System.lineSeparator(), distance.dalt)
                    displayString.append(hError)
                    displayString.append(vError)
                    Log.d(TAG, hError + vError)
                } catch (e : Exception) {}
            }
        }
        val keys: MutableSet<String>? = bundle?.keySet()
        if (keys != null) {
            for (key in keys) {
                val value: Any? = bundle.get(key)
                displayString.append(key).append(": ").append(value.toString()).append("\n")
            }
        }

        val textView: TextView = (activity as LoneSmartMainActivity).findViewById(R.id.map_textview)
        textView.text = displayString.toString()
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            if (location.provider.equals(LocationManager.GPS_PROVIDER)) {
                gpsLocation = location
            } else if (location.provider.equals(LocationManager.FUSED_PROVIDER)) {
                fusedLocation = location
            } else if (location.provider.equals(ITSPLocationProvider.LSPI_PROVIDER)) {
                lspiLocation = location
                activity?.runOnUiThread {
                    updateLocationInfo(location)
                }
            }
            if (fusedLocation != null)
                updateMapViewWithPositions(lspiLocation, fusedLocation, gpsLocation, referenceLocation)
        }
    }
}