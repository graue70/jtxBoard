/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.*
import at.techbee.jtx.database.ICalDatabase
import at.techbee.jtx.database.Module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

private const val TAG = "JournalsWidgetRec"


class JournalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JournalsWidget()

    private val coroutineScope = MainScope()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        observeData(context)
        setWork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        //if(intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            observeData(context)
            setWork(context)
        //}
    }

    private fun observeData(context: Context) {
        coroutineScope.launch(Dispatchers.IO) {

            Log.v(TAG, "Loading journals...")
            val entries = ICalDatabase.getInstance(context)
                .iCalDatabaseDao
                .getIcal4ListByModuleSync(Module.JOURNAL)

            GlanceAppWidgetManager(context).getGlanceIds(JournalsWidget::class.java).forEach { glanceId ->

                glanceId.let {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, it) { pref ->
                        pref.toMutablePreferences().apply {
                            this[journalsList] = entries.map { entry -> Json.encodeToString(entry) }.toSet()
                        }
                    }
                    glanceAppWidget.update(context, it)
                    Log.d(TAG, "Widget updated")
                }
            }
        }
    }

    private fun setWork(context: Context) {

        val work: PeriodicWorkRequest = PeriodicWorkRequestBuilder<JournalsWidgetUpdateWorker>(5, TimeUnit.MINUTES).build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork("journalWidgetWorker", ExistingPeriodicWorkPolicy.KEEP, work)
        Log.d(TAG, "Work enqueued")
    }

    companion object {
        val journalsList = stringSetPreferencesKey("journalsList")

        fun updateJournalsWidgets(context: Context) {
            val widgetProvider = JournalsWidgetReceiver::class.java
            val comp = ComponentName(context, widgetProvider)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(comp)
            val intent = Intent(context, widgetProvider).apply {
                this.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                this.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}