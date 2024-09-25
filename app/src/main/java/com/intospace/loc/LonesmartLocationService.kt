package com.intospace.loc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.intospace.location.lbs.lonesmart.gnss.service.ITSPLocationProvider
import com.intospace.location.lbs.lonesmart.gnss.service.MeasurementListener
import java.lang.IllegalArgumentException

class LonesmartLocationService : LonesmartService(), MeasurementListener, FragmentDataListener {

    companion object {
        const val NOTIFICATION_CHANNEL_NAME = "ITSPlocationServiceChannel"
    }

    private val binder = LocalBinder()
    var callback: ServiceCallback? = null

    private lateinit var locationProvider: ITSPLocationProvider
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var lspiLocation: Location

    inner class LocalBinder : Binder() {
        fun getService(): LonesmartLocationService = this@LonesmartLocationService
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()

        handlerThread = HandlerThread("LocationWaitThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        try {
            /**
             * INFO: api key를 meta-data로부터 가져오는 부분입니다.
             *       api key의 저장 방식은 앱에 따라 다를 수 있으므로 참고만 하시기 바랍니다.
             */
            val applicationInfo = this.packageName?.let {
                this.packageManager?.getApplicationInfo(it, PackageManager.GET_META_DATA)
            }
            val bundle = applicationInfo?.metaData
            val apiKey = bundle?.getString("com.intospace.loc.ITSP_API_KEY")
                ?: throw IllegalArgumentException("API 키가 없습니다.")
            /**
             * INFO: ITSPLocationProvider 초기화할 뿐, 자동으로 시작되지 않으며 start()를 호출해야 시작됨
             */
            locationProvider = ITSPLocationProvider(this, apiKey)
            /**
             * INFO: ITSPLocationProvider 에서 발생하는 측정치 또는 위치 이벤트를 처리하는 Listener 등록
             */
            locationProvider.registerMeasurementListener(this)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("WrongConstant")
    fun createNotificationChannel() {
        val name = "보정 정보 측위 서비스"
        val descriptionText = "위성 항법 기반 위치를 측정합니다."
        val importance = NotificationManager.IMPORTANCE_MAX
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_NAME, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_NAME)
            .setContentTitle("Location Service Running")
            .setContentText("Fetching location data...")
            .setSmallIcon(R.drawable.ic_baseline_location_on_24)
            .build()

        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(applicationContext, "권한 문제로 서비스를 수행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return START_STICKY
        }

        /**
         * INFO: ITSPLocationProvider를 시작하는 부분입니다.
         * 이 예제의 경우에는 MainActivity의 lonesmartLocationService 서비스의 시작 이벤트 처리 부분에 구현하였습니다.
         * if (!locationProvider.isStarted)
         *    locationProvider.start()
        */

        startForeground(1, notification)

        return START_STICKY
    }

    /**
     * INFO: ITSPLocationProvider 를 시작
     */
    @SuppressLint("MissingPermission")
    fun start() {
        locationProvider.start()
    }

    /**
     * INFO: ITSPLocationProvider 정지
     */
    fun stop() {
        locationProvider.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        /**
         * INFO: LocationProvider 정지
         */
        locationProvider.stop()
        /**
         * INFO: ITSPLocationProvider 에 등록된 Listener 해제
         */
        locationProvider.unregisterMeasurementListener(this)

        handlerThread.quit()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    }

    /**
     * INFO: Fused, GPS, Network, ITSP(Intospace API) 등에서 위치 변화나 계산이 완료되면 발생하는 이벤트를 처리하는 Listener
     *      Location 은 https://developer.android.com/reference/android/location/Location 를 참고
     */
    override fun onLocationChanged(location: Location?) {
        handler.post {
            if (location != null) {
                if (location.provider.equals(LocationManager.NETWORK_PROVIDER)) {
                } else if (location.provider.equals(LocationManager.GPS_PROVIDER)) {
                } else if (location.provider.equals(LocationManager.FUSED_PROVIDER)) {
                } else if (location.provider.equals(ITSPLocationProvider.LSPI_PROVIDER)) {
                    /**
                     * INFO: 위치 계산 이벤트가 ITSP Location API에서 발생할 경우, Location.proivder 는 "LSPI"로 전달됨
                     * Location 의 Extra 값은 다음과 같다.
                     *      SSRSat : Satellites using Correction. 보정 정보가 적용된 위성 개수
                     *      CMode  : Correction Mode [SBAS:보정 정보를 사용하는 위성이 하나 이상일 때, STANDALONE: 보정 정보를 적용한 위성이 없을 때)
                     *      PMode  : Positioning Mode. 정지 또는 이동 상태를 나타냄, STATIC: 정지, MOVING: 이동
                     *      SVNum  : Number of Satellite. 사용 위성 개수
                     *      PDop   : Position DOP(Dilusion of Precision). 기하학적인 위성 배치에 기반한 계산 결과의 모호성
                     */
                    lspiLocation = location;
                }
            }
            callback?.onGnssLocationReceived(location)
        }
    }

    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
        super.onGnssMeasurementsReceived(event)
        handler.post {
            callback?.onGnssMeasurementsReceived(event)
        }
    }

    override fun onReceivedManualLocation(data: Location) {
    }

    override fun onReceivedReferenceLocation(data: Location) {

    }

    fun startLogging(directory: String) : Boolean {
        return locationProvider.startLogging(directory)
    }

    fun stopLogging() {
        locationProvider.stopLogging()
    }

    fun getLastLocation() : Location? {
        return locationProvider.getLastLocation()
    }

    interface ServiceCallback {
        fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?)
        fun onGnssLocationReceived(location: Location?)
    }
}