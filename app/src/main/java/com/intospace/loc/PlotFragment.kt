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

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.collection.ArrayMap
import androidx.viewpager2.widget.ViewPager2
import com.intospace.location.lbs.lonesmart.gnss.service.ITSPLocationProvider
import com.intospace.location.lbs.lonesmart.gnss.service.MeasurementListener
import org.achartengine.ChartFactory
import org.achartengine.GraphicalView
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer
import org.achartengine.util.MathHelper
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit


/** A plot fragment to show real-time Gnss analysis migrated from GnssAnalysis Tool.  */
open class PlotFragment : LonesmartFragment(), MeasurementListener {
    private var mChartView: GraphicalView? = null

    /** The average of the average of strongest satellite signal strength over history  */
    private var mAverageCn0 = 0.0

    /** Total number of [GnssMeasurementsEvent] has been received */
    private var mMeasurementCount = 0
    private var mInitialTimeSeconds = -1.0
    private lateinit var mAnalysisView: TextView
    private var mLastTimeReceivedSeconds = 0.0
    private val mColorMap = ColorMap()
    private var mDataSetManager: DataSetManager? = null
    private var mCurrentRenderer: XYMultipleSeriesRenderer? = null
    private lateinit var mLayout: LinearLayout
    private var mCurrentTab = 0

    //private lateinit var handlerThread: HandlerThread
    //private lateinit var plotHandler: Handler
    //private lateinit var mainContext : Context

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (position==LoneSmartMainActivity.FRAGMENT_INDEX_PLOT) {
                // Set up the Graph View
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val plotView: View =
            inflater.inflate(R.layout.fragment_plot, container, false /* attachToRoot */)
        mDataSetManager = DataSetManager(NUMBER_OF_TABS, NUMBER_OF_CONSTELLATIONS, context, mColorMap)

        //handlerThread = HandlerThread("PlotThread")
        //handlerThread.start()
        //plotHandler = Handler(handlerThread.looper)

        // Set UI elements handlers
        val spinner: Spinner = plotView.findViewById(R.id.constellation_spinner)
        val tabSpinner: Spinner = plotView.findViewById(R.id.tab_spinner)
        val spinnerOnSelectedListener: AdapterView.OnItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        mCurrentTab = tabSpinner.selectedItemPosition
                        val renderer: XYMultipleSeriesRenderer = mDataSetManager!!.getRenderer(
                            mCurrentTab, spinner.selectedItemPosition
                        )
                        val dataSet: XYMultipleSeriesDataset =
                            mDataSetManager!!.getDataSet(mCurrentTab, spinner.getSelectedItemPosition())
                        if (mLastTimeReceivedSeconds > TIME_INTERVAL_SECONDS) {
                            renderer.setXAxisMax(mLastTimeReceivedSeconds)
                            renderer.setXAxisMin(mLastTimeReceivedSeconds - TIME_INTERVAL_SECONDS)
                        }
                        mCurrentRenderer = renderer
                        mLayout.removeAllViews()
                        mChartView = ChartFactory.getLineChartView(context, dataSet, renderer)
                        mLayout.addView(mChartView)
                    } catch (ex : Exception) {
                        ex.printStackTrace()
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        spinner.setOnItemSelectedListener(spinnerOnSelectedListener)
        tabSpinner.setOnItemSelectedListener(spinnerOnSelectedListener)

        // Set up the Graph View
        mCurrentRenderer = mDataSetManager!!.getRenderer(mCurrentTab, DATA_SET_INDEX_ALL)
        mCurrentRenderer!!.xLabelsPadding = 10.0F
        mCurrentRenderer!!.yLabelsPadding = 10.0F
        val currentDataSet: XYMultipleSeriesDataset =
            mDataSetManager!!.getDataSet(mCurrentTab, DATA_SET_INDEX_ALL)
        mChartView = ChartFactory.getLineChartView(context, currentDataSet, mCurrentRenderer)
        mAnalysisView = plotView.findViewById(R.id.analysis_textview)
        mAnalysisView.setTextColor(Color.BLACK)
        mLayout = plotView.findViewById(R.id.plot)
        mLayout.addView(mChartView)

        (activity as LoneSmartMainActivity).registerLocationListener(this)
        (activity as LoneSmartMainActivity).viewPager.registerOnPageChangeCallback(pageChangeCallback)
        return plotView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        //if (context is LoneSmartMainActivity) {
        //    mainContext = context
        //}
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as LoneSmartMainActivity).unRegisterLocationListener(this)
        (activity as LoneSmartMainActivity).viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        //handlerThread.quit()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        
    }

    private fun updateLocationInfo(location: Location) {
        val bundle: Bundle? = location.extras
        val displayString = StringBuilder()
        val keys: MutableSet<String>? = bundle?.keySet()
        if (keys != null) {
            for (key in keys) {
                val value: Any? = bundle.get(key)
                displayString.append(key).append(": ").append(value.toString()).append("\n")
            }
        }
        // TextView에 키-값 쌍을 표시합니다.
        val textView: TextView = (activity as LoneSmartMainActivity).findViewById(R.id.location_textview)
        textView.text = displayString.toString()
    }

    /**
     * Updates the CN0 versus Time plot data from a [GnssMeasurement]
     */
    private fun updateCnoTab(event: GnssMeasurementsEvent) {

        if((activity as LoneSmartMainActivity).viewPager.currentItem != LoneSmartMainActivity.FRAGMENT_INDEX_PLOT) {
            return
        }

        val timeInSeconds = TimeUnit.NANOSECONDS.toSeconds(event.getClock().getTimeNanos())
        if (mInitialTimeSeconds < 0) {
            mInitialTimeSeconds = timeInSeconds.toDouble()
        }

        // Building the texts message in analysis text view
        val measurements: List<GnssMeasurement> =
            sortByCarrierToNoiseRatio(ArrayList(event.getMeasurements()))
        val builder = SpannableStringBuilder()
        var currentAverage = 0.0
        if (measurements.size >= NUMBER_OF_STRONGEST_SATELLITES) {
            mAverageCn0 = ((mAverageCn0 * mMeasurementCount
                    + (measurements[0].cn0DbHz
                    + measurements[1].cn0DbHz
                    + measurements[2].cn0DbHz
                    + measurements[3].cn0DbHz)
                    / NUMBER_OF_STRONGEST_SATELLITES)
                    / ++mMeasurementCount)
            currentAverage = ((measurements[0].cn0DbHz
                    + measurements[1].cn0DbHz
                    + measurements[2].cn0DbHz
                    + measurements[3].cn0DbHz)
                    / NUMBER_OF_STRONGEST_SATELLITES)
        }
        builder.append(
            getString(
                R.string.history_average_hint,
                """
                ${sDataFormat.format(mAverageCn0)}
                
                """.trimIndent()
            )
        )
        builder.append(
            getString(
                R.string.current_average_hint,
                """
                ${sDataFormat.format(currentAverage)}
                
                """.trimIndent()
            )
        )
        var i = 0
        while (i < NUMBER_OF_STRONGEST_SATELLITES && i < measurements.size) {
            val start: Int = builder.length
            builder.append(
                """
                    ${mDataSetManager!!.getConstellationPrefix(measurements[i].getConstellationType())}${measurements[i].getSvid()}: ${
                    sDataFormat.format(
                        measurements[i].getCn0DbHz()
                    )
                }
                    
                    """.trimIndent()
            )
            val end: Int = builder.length
            builder.setSpan(
                ForegroundColorSpan(
                    mColorMap.getColor(
                        measurements[i].getSvid(), measurements[i].getConstellationType()
                    )
                ),
                start,
                end,
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            )
            i++
        }
        builder.append(getString(R.string.satellite_number_sum_hint, measurements.size))
        mAnalysisView.setText(builder)

        // Adding incoming data into Dataset
        mLastTimeReceivedSeconds = timeInSeconds - mInitialTimeSeconds
        for (measurement in measurements) {
            val constellationType: Int = measurement.getConstellationType()
            val svID: Int = measurement.getSvid()
            try {
                if (constellationType != GnssStatus.CONSTELLATION_UNKNOWN) {
                   //if (ITSPLocationProvider.isSupportCodeType(measurement)) {
                       mDataSetManager!!.addValue(
                           CN0_TAB,
                           constellationType,
                           svID,
                           mLastTimeReceivedSeconds,
                           measurement.getCn0DbHz()
                       )
                   //}
                }
            } catch (_: Exception) {
            }
        }
        mDataSetManager!!.fillInDiscontinuity(CN0_TAB, mLastTimeReceivedSeconds)
        if((activity as LoneSmartMainActivity).viewPager.currentItem == LoneSmartMainActivity.FRAGMENT_INDEX_PLOT) {
            // Checks if the plot has reached the end of frame and resize
            if (mLastTimeReceivedSeconds > mCurrentRenderer?.getXAxisMax()!!) {
                mCurrentRenderer?.setXAxisMax(mLastTimeReceivedSeconds)
                mCurrentRenderer?.setXAxisMin(mLastTimeReceivedSeconds - TIME_INTERVAL_SECONDS)
            }
            mChartView?.invalidate()
        }
    }

    ///**
    // * Updates the pseudorange residual plot from residual results calculated by
    // * [ITSPLocationProvider]
    // *
    // * @param residuals An array of MAXSAT elements where indexes of satellites was
    // * not seen are fixed with `Double.NaN` and indexes of satellites what were seen
    // * are filled with pseudorange residual in meters
    // * @param timeInSeconds the time at which measurements are received
    // */
    //protected fun updatePseudorangeResidualTab(residuals: DoubleArray, timeInSeconds: Double) {
    //    val timeSinceLastMeasurement = timeInSeconds - mInitialTimeSeconds
    //    for (i in 1..SatUtil.MAXSAT) {
    //        if (!java.lang.Double.isNaN(residuals[i - 1])) {
    //            mDataSetManager!!.addValue(
    //                PR_RESIDUAL_TAB,
    //                GnssStatus.CONSTELLATION_GPS,
    //                i,
    //                timeSinceLastMeasurement,
    //                residuals[i - 1]
    //            )
    //        }
    //    }
    //    mDataSetManager!!.fillInDiscontinuity(PR_RESIDUAL_TAB, timeSinceLastMeasurement)
    //}

    private fun sortByCarrierToNoiseRatio(measurements: MutableList<GnssMeasurement>): MutableList<GnssMeasurement> {
        measurements.sortWith { o1, o2 ->
            o2.cn0DbHz.compareTo(o1.cn0DbHz)
        }
        return measurements
    }

    /**
     * An utility class provides and keeps record of all color assignments to the satellite in the
     * plots. Each satellite will receive a unique color assignment through out every graph.
     */
    private class ColorMap {
        private val mColorMap = ArrayMap<Int, Int>()
        private var mColorsAssigned = 0
        private val mRandom = Random()
        fun getColor(svId: Int, constellationType: Int): Int {
            // Assign the color from Kelly's 21 contrasting colors to satellites first, if all color
            // has been assigned, use a random color and record in {@link mColorMap}.
            if (mColorMap.containsKey(constellationType * 1000 + svId)) {
                return mColorMap[getUniqueSatelliteIdentifier(constellationType, svId)]!!
            }
            if (mColorsAssigned < CONTRASTING_COLORS.size) {
                val color = Color.parseColor(CONTRASTING_COLORS[mColorsAssigned++])
                mColorMap[getUniqueSatelliteIdentifier(constellationType, svId)] = color
                return color
            }
            val color =
                Color.argb(255, mRandom.nextInt(256), mRandom.nextInt(256), mRandom.nextInt(256))
            mColorMap[getUniqueSatelliteIdentifier(constellationType, svId)] = color
            return color
        }

        companion object {
            /**
             * Source of Kelly's contrasting colors:
             * https://medium.com/@rjurney/kellys-22-colours-of-maximum-contrast-58edb70c90d1
             */
            private val CONTRASTING_COLORS = arrayOf(
                "#222222", "#F3C300", "#875692", "#F38400", "#A1CAF1", "#BE0032","#C2B280",
                "#848482", "#008856", "#E68FAC", "#0067A5", "#F99379", "#604E97","#F6A600",
                "#B3446C", "#DCD300", "#882D17", "#8DB600", "#654522", "#E25822","#2B3D26"
            )
        }
    }

    /**
     * An utility class stores and maintains all the data sets and corresponding renders.
     * We use 0 as the `dataSetIndex` of all constellations and 1 - 6 as the
     * `dataSetIndex` of each satellite constellations
     */
    private class DataSetManager(
        private val numberOfTabs: Int, private val numberOfConstellations: Int,
        private val mContext: Context?, private val mColorMap: ColorMap
    ) {
        val mSatelliteIndex: Array<MutableList<ArrayMap<Int, Int>>> = arrayOfNulls<ArrayList<*>>(numberOfTabs) as Array<MutableList<ArrayMap<Int, Int>>>
        val mSatelliteConstellationIndex: Array<MutableList<ArrayMap<Int, Int>>> = arrayOfNulls<ArrayList<*>>(numberOfTabs) as Array<MutableList<ArrayMap<Int, Int>>>
        val mDataSetList: Array<MutableList<XYMultipleSeriesDataset>> = arrayOfNulls<ArrayList<*>>(numberOfTabs) as Array<MutableList<XYMultipleSeriesDataset>>
        val mRendererList: Array<MutableList<XYMultipleSeriesRenderer>> = arrayOfNulls<ArrayList<*>>(numberOfTabs) as Array<MutableList<XYMultipleSeriesRenderer>>

        init {

            // Preparing data sets and renderer for all six constellations
            for (i in 0 until numberOfTabs) {
                mDataSetList[i] = arrayListOf()
                mRendererList[i] = arrayListOf()
                mSatelliteIndex[i] = arrayListOf()
                mSatelliteConstellationIndex[i] = arrayListOf()
                for (k in 0..numberOfConstellations) {
                    mSatelliteIndex[i].add(ArrayMap())
                    mSatelliteConstellationIndex[i].add(ArrayMap())
                    val tempRenderer = XYMultipleSeriesRenderer()
                    setUpRenderer(tempRenderer, i)
                    mRendererList[i].add(tempRenderer)
                    val tempDataSet = XYMultipleSeriesDataset()
                    mDataSetList[i].add(tempDataSet)
                }
            }
        }

        // The constellation type should range from 1 to 6
        fun getConstellationPrefix(constellationType: Int): String {
            return if (constellationType <= GnssStatus.CONSTELLATION_UNKNOWN
                || constellationType > NUMBER_OF_CONSTELLATIONS
            ) {
                ""
            } else CONSTELLATION_PREFIX[constellationType - 1]
        }

        /** Returns the multiple series data set at specific tab and index  */
        fun getDataSet(tab: Int, dataSetIndex: Int): XYMultipleSeriesDataset {
            return mDataSetList[tab][dataSetIndex]
        }

        /** Returns the multiple series renderer set at specific tab and index  */
        fun getRenderer(tab: Int, dataSetIndex: Int): XYMultipleSeriesRenderer {
            return mRendererList[tab][dataSetIndex]
        }

        /**
         * Adds a value into the both the data set containing all constellations and individual data set
         * of the constellation of the satellite
         */
        fun addValue(
            tab: Int, constellationType: Int, svID: Int,
            timeInSeconds: Double, inputValue: Double
        ) {
            val dataSetAll: XYMultipleSeriesDataset = getDataSet(tab, DATA_SET_INDEX_ALL)
            val rendererAll: XYMultipleSeriesRenderer = getRenderer(tab, DATA_SET_INDEX_ALL)
            val value = sDataFormat.format(inputValue).toDouble()
            if (hasSeen(constellationType, svID, tab)) {
                // If the satellite has been seen before, we retrieve the dataseries it is add and add new
                // data
                mSatelliteIndex[tab][constellationType][svID]?.let {
                    dataSetAll
                        .getSeriesAt(it)
                        .add(timeInSeconds, value)
                }
                mSatelliteConstellationIndex[tab][constellationType][svID]?.let {
                    mDataSetList[tab][constellationType]
                        .getSeriesAt(it)
                        .add(timeInSeconds, value)
                }
            } else {
                // If the satellite has not been seen before, we create new dataset and renderer before
                // adding data
                mSatelliteIndex[tab][constellationType][svID] = dataSetAll.getSeriesCount()
                mSatelliteConstellationIndex[tab][constellationType][svID] =
                    mDataSetList[tab][constellationType].getSeriesCount()
                val tempSeries = XYSeries(CONSTELLATION_PREFIX[constellationType - 1] + svID)
                tempSeries.add(timeInSeconds, value)
                dataSetAll.addSeries(tempSeries)
                mDataSetList[tab][constellationType].addSeries(tempSeries)
                val tempRenderer = XYSeriesRenderer()
                tempRenderer.setLineWidth(5F)
                tempRenderer.setColor(mColorMap.getColor(svID, constellationType))
                rendererAll.addSeriesRenderer(tempRenderer)
                mRendererList[tab][constellationType].addSeriesRenderer(tempRenderer)
            }
        }

        /**
         * Creates a discontinuity of the satellites that has been seen but not reported in this batch
         * of measurements
         */
        fun fillInDiscontinuity(tab: Int, referenceTimeSeconds: Double) {
            for (dataSet in mDataSetList[tab]) {
                for (i in 0 until dataSet.seriesCount) {
                    if (dataSet.getSeriesAt(i).maxX < referenceTimeSeconds) {
                        dataSet.getSeriesAt(i).add(referenceTimeSeconds, MathHelper.NULL_VALUE)
                    }
                }
            }
        }

        /**
         * Returns a boolean indicating whether the input satellite has been seen.
         */
        private fun hasSeen(constellationType: Int, svID: Int, tab: Int): Boolean {
            return mSatelliteIndex[tab][constellationType].containsKey(svID)
        }

        /**
         * Set up a [XYMultipleSeriesRenderer] with the specs customized per plot tab.
         */
        private fun setUpRenderer(renderer: XYMultipleSeriesRenderer, tabNumber: Int) {
            renderer.xAxisMin = 0.0
            renderer.xAxisMax = 60.0
            renderer.yAxisMin = RENDER_HEIGHTS[tabNumber][0].toDouble()
            renderer.yAxisMax = RENDER_HEIGHTS[tabNumber][1].toDouble()
            renderer.setYAxisAlign(Paint.Align.RIGHT, 0)
            renderer.legendTextSize = 30F
            renderer.labelsTextSize = 30F
            renderer.setYLabelsColor(0, Color.BLACK)
            renderer.xLabelsColor = Color.BLACK
            renderer.isFitLegend = true
            renderer.isShowGridX = true
            //renderer.margins = intArrayOf(10, 10, 30, 10)
            renderer.margins = intArrayOf(20, 20, 30, 40)
            // setting the plot untouchable
            renderer.setZoomEnabled(false, false)
            renderer.setPanEnabled(false, true)
            renderer.isClickEnabled = false
            renderer.marginsColor = Color.WHITE
            renderer.chartTitle = mContext!!.resources
                .getStringArray(R.array.plot_titles)[tabNumber]
            renderer.chartTitleTextSize = 50F
        }

        fun clear() {

        }

        companion object {
            /** The Y min and max of each plot  */
            private val RENDER_HEIGHTS = arrayOf(intArrayOf(5, 55), intArrayOf(-60, 60))

            /**
             *
             *  * A list of constellation prefix
             *  * G : GPS, US Constellation
             *  * S : Satellite-based Augmentation System
             *  * R : GLONASS, Russia Constellation
             *  * J : QZSS, Japan Constellation
             *  * C : BEIDOU China Constellation
             *  * E : GALILEO EU Constellation
             *
             */
            private val CONSTELLATION_PREFIX = arrayOf("G", "S", "R", "J", "C", "E")
        }
    }

    companion object {
        /** Total number of kinds of plot tabs  */
        private const val NUMBER_OF_TABS = 1

        /** The position of the CN0 over time plot tab  */
        private const val CN0_TAB = 0

        /** The position of the prearrange residual plot tab */
        //private const val PR_RESIDUAL_TAB = 1

        /** The number of Gnss constellations  */
        private const val NUMBER_OF_CONSTELLATIONS = 6

        /** The X range of the plot, we are keeping the latest one minute visible  */
        private const val TIME_INTERVAL_SECONDS = 60.0

        /** The index in data set we reserved for the plot containing all constellations  */
        private const val DATA_SET_INDEX_ALL = 0

        /** The number of satellites we pick for the strongest satellite signal strength calculation  */
        private const val NUMBER_OF_STRONGEST_SATELLITES = 4

        /** Data format used to format the data in the text view  */
        private val sDataFormat = DecimalFormat("##.#", DecimalFormatSymbols(Locale.US))
        private fun getUniqueSatelliteIdentifier(constellationType: Int, svID: Int): Int {
            return constellationType * 1000 + svID
        }
    }

    override fun onProviderEnabled(p0: String?) {}
    override fun onProviderDisabled(p0: String?) {}
    override fun onLocationChanged(location: Location?) {
        if((activity as LoneSmartMainActivity).viewPager.currentItem != LoneSmartMainActivity.FRAGMENT_INDEX_MAP) {
            if (location?.provider == ITSPLocationProvider.LSPI_PROVIDER) {
                activity?.runOnUiThread {
                    updateLocationInfo(location)
                }
            }
        }
    }
    override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
        if((activity as LoneSmartMainActivity).viewPager.currentItem != LoneSmartMainActivity.FRAGMENT_INDEX_MAP) {
            if (event != null) {
                (activity as LoneSmartMainActivity).runOnUiThread {
                    updateCnoTab(event)
                }
            }
        }
    }

    override fun onGnssNavigationMessageReceived(p0: GnssNavigationMessage?) {}
    override fun onGnssStatusChanged(p0: GnssStatus?) {}
    override fun onListenerRegistration(p0: String?, p1: Boolean) {}
}