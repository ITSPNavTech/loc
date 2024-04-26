package com.intospace.loc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PorterDuff
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.intospace.location.lbs.lonesmart.gnss.service.MeasurementListener


class LoneSmartMainActivity : AppCompatActivity(), FragmentDataListener, MeasurementListener, LonesmartLocationService.ServiceCallback {

    private val permission = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.INTERNET,
        Manifest.permission.POST_NOTIFICATIONS
        )
    private val PERM_FLAG = 99

    val NUMBER_OF_FRAGMENTS = 3

    companion object {
        const val update_url: String = "https://lonesmart.com:2107"
        const val FRAGMENT_INDEX_MAP = 0
        const val FRAGMENT_INDEX_PLOT = 1
        const val FRAGMENT_INDEX_DEBUG_SETTING = 2
    }

    // for debug
    var menu: Menu? = null
    lateinit var viewPager: ViewPager2
    private lateinit var progressDialog: ProgressDialog

    private lateinit var mListenerList : MutableList<MeasurementListener>
    private lateinit var handlerThread : HandlerThread
    private lateinit var handler : Handler

    var locationService : LonesmartLocationService?= null
    private var isBound = false
    var mIsLogging : Boolean = false

    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LonesmartLocationService.LocalBinder
            locationService = binder.getService()
            locationService?.callback = this@LoneSmartMainActivity
            isBound = true
            setLoging(mIsLogging)
            locationService!!.start()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            locationService?.callback = null
            locationService = null
            isBound = false
            stopLogging()
            locationService?.stop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 위치, 네트워크, 파일 저장을 위한 권한 확인 및 요청
        requestPermissionAndSetupFragments(this)
    }

    override fun onStart() {
        super.onStart()
        locationService?.callback = this@LoneSmartMainActivity
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        locationService?.callback = null
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(locationServiceConnection)
        Intent(this, LonesmartLocationService::class.java).also { intent ->
            stopService(intent)
        }
        handlerThread.quit()
    }

    private fun setupFragment() {
        progressDialog = ProgressDialog(this).apply {
            setMessage("새 버전을 확인하는 중...")
            setCancelable(false)
        }

        mListenerList = mutableListOf()

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        handlerThread = HandlerThread("LocationWaitThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        viewPager = findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = NUMBER_OF_FRAGMENTS
        viewPager.adapter = ViewPagerAdapter(this)

        val tablayout = findViewById<TabLayout>(R.id.menu_tabLayout)
        setTabLayoutConnectToViewpager(tablayout, viewPager)

        Intent(this, LonesmartLocationService::class.java).also { intent ->
            startService(intent)
            bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
        }

        //setLogging(mIsLogging)
        //val logSwitchCompat: SwitchCompat = findViewById(R.id.logging_switch)
        //logSwitchCompat.isChecked = true;
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.debug_menu, menu)
        this.menu = menu
        val switchItem = menu?.findItem(R.id.menu_logging_item)
        val switchView = MenuItemCompat.getActionView(switchItem) as Switch
        switchView.isChecked = mIsLogging
        switchView.setOnCheckedChangeListener { _, isChecked ->
            val switch = findViewById<SwitchCompat>(R.id.logging_switch)
            switch.isChecked = isChecked
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        enableSwipe(true)
        return when (item.itemId) {
            R.id.menu_debug_map -> {
                viewPager.setCurrentItem(FRAGMENT_INDEX_MAP, true)
                true
            }
            R.id.menu_debug_plot -> {
                viewPager.setCurrentItem(FRAGMENT_INDEX_PLOT, true)
                true
            }
            R.id.menu_debug_setting -> {
                viewPager.setCurrentItem(FRAGMENT_INDEX_DEBUG_SETTING, true)
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            PERM_FLAG -> {
                var check = true
                for(grant in grantResults) {
                    if(grant != PermissionChecker.PERMISSION_GRANTED){
                        check = false
                        break
                    }
                }
                if(!check) {
                    Toast.makeText(this, "권한을 승인해야 앱을 사용할 수 있습니다.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    setupFragment()
                }
            }
        }
    }

    fun startLogging(directory: String) {
        locationService?.startLogging(directory)
    }

    fun stopLogging() {
        locationService?.stopLogging()
    }

    fun setLoging(logging:Boolean) {
        if (logging) {
            startLogging(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).toString())
            Toast.makeText(this, "로그 저장을 시작합니다.", Toast.LENGTH_SHORT).show()
        } else {
            stopLogging()
            Toast.makeText(this, "로그 저장을 종료합니다.", Toast.LENGTH_SHORT).show()
        }
        mIsLogging = logging;
    }

    fun registerLocationListener(listener : MeasurementListener) {
        if(!mListenerList.contains(listener))
            mListenerList.add(listener)
    }

    fun unRegisterLocationListener(listener: MeasurementListener) {
        mListenerList.remove(listener)
    }

    private fun enableSwipe(enable: Boolean) {
        viewPager.isUserInputEnabled = enable
    }

    private fun isPermitted(activity: Activity) : Boolean {
        for(perm in permission) {
            if (ContextCompat.checkSelfPermission(activity, perm) != PermissionChecker.PERMISSION_GRANTED) {
                return false
            }
        }
        Log.d("DEBUG", "권한 확인됨")
        return true
    }

    private fun requestPermissionAndSetupFragments(activity: Activity) {
        if (isPermitted(activity)) {
            setupFragment()
        } else {
            ActivityCompat.requestPermissions(this, permission, PERM_FLAG)
        }
    }

    override fun onReceivedManualLocation(data: Location) {
        handler.post() {
            locationService?.onReceivedManualLocation(data)
            val fragments = (viewPager.adapter as ViewPagerAdapter).getFragments()
            for(fragment:Fragment in fragments) {
                if(fragment is LonesmartFragment) {
                    fragment.onReceivedManualLocation(data)
                }
            }
        }
    }

    override fun onReceivedReferenceLocation(data: Location) {
        handler.post() {
            val fragments = (viewPager.adapter as ViewPagerAdapter).getFragments()
            for(fragment:Fragment in fragments) {
                if(fragment is LonesmartFragment) {
                    fragment.onReceivedReferenceLocation(data)
                }
            }
        }
    }

    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
        handler.post {
            for (listener in mListenerList) {
                listener.onGnssMeasurementsReceived(event)
            }
        }
    }

    override fun onGnssLocationReceived(location: Location?) {
        handler.post {
            for (listener in mListenerList) {
                listener.onLocationChanged(location)
            }
        }
    }
    fun setTabLayoutConnectToViewpager(tabLayout: TabLayout, viewPager: ViewPager2) {
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->

            // 여기에서 각 탭의 이름을 설정할 수 있습니다.
            tab.icon = when (position) {
                //0 -> ContextCompat.getDrawable(this, R.drawable.ic_baseline_emergency_24)
                FRAGMENT_INDEX_MAP -> ContextCompat.getDrawable(this, R.drawable.ic_baseline_map_24)
                FRAGMENT_INDEX_PLOT -> ContextCompat.getDrawable(this, R.drawable.baseline_show_chart_24)
                FRAGMENT_INDEX_DEBUG_SETTING -> ContextCompat.getDrawable(this, R.drawable.ic_baseline_settings_24)
                //2 -> ContextCompat.getDrawable(this, R.drawable.ic_baseline_info_24)
                //3 -> ContextCompat.getDrawable(this, R.drawable.ic_baseline_settings_24)
                else -> null
            }
            val color = ContextCompat.getColor(this, R.color.purple_500)
            tab.icon?.setTint(color)
            tab.icon?.setColorFilter(color, PorterDuff.Mode.SRC_IN);

        }.attach()
    }

    class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

        private val fragments = arrayListOf<Fragment>()

        override fun getItemId(position: Int): Long {
            return if (position in 0 until fragments.size) {
                        position.toLong()
                    } else {
                        -1L
                    }
        }

        init {
            fragments.add(MapFragment())
            fragments.add(PlotFragment())
            fragments.add(DebugSettingFragment())
        }

        override fun getItemCount(): Int {
            return fragments.size
        }

        override fun createFragment(position: Int): Fragment {
            return fragments[position]
        }

        fun getFragments() : ArrayList<Fragment> {
            return fragments
        }
    }
}

