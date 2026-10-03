package be.heyman.android.jemmapassdemo.sos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import be.heyman.android.jemmapassdemo.R
import be.heyman.android.jemmapassdemo.kb.JemmaProfileHydrator
import be.heyman.android.jemmapassdemo.profiles.ProfilesRepository
import be.heyman.android.jemmapassdemo.qr.JemmaTextPayloadBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class JemmaEmergencyWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "JEMMA-EMERG-WIDGET"
        const val ACTION_SOS_TAP = "be.heyman.jemma.widget.SOS_TAP"
        
        // Timeout pour le double tap (ms)
        private const val DOUBLE_TAP_TIMEOUT = 2000L
        
        private var lastTapTime = 0L

        fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, JemmaEmergencyWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, JemmaEmergencyWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    @Inject lateinit var profilesRepo: ProfilesRepository
    @Inject lateinit var hydrator: JemmaProfileHydrator

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.jemma_emergency_widget)

        // Configuration de l'intent pour le tap
        val intent = Intent(context, JemmaEmergencyWidget::class.java).apply {
            action = ACTION_SOS_TAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_sos, pendingIntent)

        val isEmergencyActive = JemmaWidgetEmergencyService.isRunning
        if (isEmergencyActive) {
            views.setTextViewText(R.id.btn_sos_text, "STOP")
        } else {
            views.setTextViewText(R.id.btn_sos_text, "SOS")
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Chargement du profil actif en arrière-plan
        widgetScope.launch {
            try {
                val currentId = profilesRepo.currentProfileId
                if (currentId == null) {
                    Log.w(TAG, "No active profile found")
                    return@launch
                }

                val profile = profilesRepo.loadProfile(currentId) ?: return@launch
                
                val deviceLang = try {
                    val sysLang = java.util.Locale.getDefault().language.lowercase()
                    JemmaTextPayloadBuilder.Lang.values().firstOrNull { it.isoCode.lowercase() == sysLang } ?: JemmaTextPayloadBuilder.Lang.EN
                } catch (e: Exception) {
                    JemmaTextPayloadBuilder.Lang.EN
                }

                val chunks = mutableListOf<String>()

                if (isEmergencyActive) {
                    // 1. Device Language chunks
                    val hydratedDevice = hydrator.hydrate(profile, deviceLang.isoCode)
                    val payloadDevice = JemmaTextPayloadBuilder.build(hydratedDevice, deviceLang, labels = be.heyman.android.jemmapassdemo.qr.AndroidCodeLabels(context))
                    chunks.addAll(payloadDevice.split("\r\n\r\n").filter { it.isNotBlank() })

                    // 2. English chunks (fallback)
                    if (deviceLang != JemmaTextPayloadBuilder.Lang.EN) {
                        val hydratedEn = hydrator.hydrate(profile, JemmaTextPayloadBuilder.Lang.EN.isoCode)
                        val payloadEn = JemmaTextPayloadBuilder.build(hydratedEn, JemmaTextPayloadBuilder.Lang.EN, labels = be.heyman.android.jemmapassdemo.qr.AndroidCodeLabels(context))
                        chunks.addAll(payloadEn.split("\r\n\r\n").filter { it.isNotBlank() })
                    }
                } else {
                    val name = "${profile.p?.gn ?: ""} ${profile.p?.fn ?: ""}".trim()
                    chunks.add("🏥 JEMMA PASS\n\nPatient: $name\n\nTap SOS to unlock emergency profile.")
                }

                val updatedViews = RemoteViews(context.packageName, R.layout.jemma_emergency_widget)
                updatedViews.removeAllViews(R.id.view_flipper)

                if (chunks.isNotEmpty()) {
                    for (chunk in chunks) {
                        val chunkView = RemoteViews(context.packageName, R.layout.jemma_emergency_widget_chunk)
                        chunkView.setTextViewText(R.id.chunk_text, chunk)
                        updatedViews.addView(R.id.view_flipper, chunkView)
                    }
                } else {
                    val emptyView = RemoteViews(context.packageName, R.layout.jemma_emergency_widget_chunk)
                    emptyView.setTextViewText(R.id.chunk_text, "No data")
                    updatedViews.addView(R.id.view_flipper, emptyView)
                }
                
                // Remettre l'intent et le bon bouton
                updatedViews.setOnClickPendingIntent(R.id.btn_sos, pendingIntent)
                if (isEmergencyActive) {
                    updatedViews.setTextViewText(R.id.btn_sos_text, "STOP")
                } else {
                    updatedViews.setTextViewText(R.id.btn_sos_text, "SOS")
                }

                appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
                Log.i(TAG, "Widget updated with profile $currentId (${chunks.size} chunks)")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SOS_TAP) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val now = System.currentTimeMillis()
            
            if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                // Double tap confirmé !
                Log.i(TAG, "Double tap confirmed, starting emergency service")
                lastTapTime = 0 // Reset
                startEmergencyService(context, appWidgetId)
                
                // Remettre le bouton à "SOS"
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val mgr = AppWidgetManager.getInstance(context)
                    val views = RemoteViews(context.packageName, R.layout.jemma_emergency_widget)
                    views.setTextViewText(R.id.btn_sos_text, "SOS")
                    
                    val tapIntent = Intent(context, JemmaEmergencyWidget::class.java).apply {
                        action = ACTION_SOS_TAP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, appWidgetId, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_sos, pi)
                    mgr.updateAppWidget(appWidgetId, views)
                }
            } else {
                // Premier tap
                Log.i(TAG, "First tap, waiting for confirmation")
                lastTapTime = now
                
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val mgr = AppWidgetManager.getInstance(context)
                    val views = RemoteViews(context.packageName, R.layout.jemma_emergency_widget)
                    views.setTextViewText(R.id.btn_sos_text, "TAP AGAIN")
                    
                    val tapIntent = Intent(context, JemmaEmergencyWidget::class.java).apply {
                        action = ACTION_SOS_TAP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, appWidgetId, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_sos, pi)
                    mgr.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    private fun startEmergencyService(context: Context, widgetId: Int) {
        widgetScope.launch {
            try {
                val currentId = profilesRepo.currentProfileId ?: return@launch
                val profile = profilesRepo.loadProfile(currentId) ?: return@launch
                
                val name = listOfNotNull(profile.p?.gn, profile.p?.fn).joinToString(" ").ifBlank { "Unknown" }
                val lang = profile.p?.lang ?: "Unknown"

                val svcIntent = Intent(context, JemmaWidgetEmergencyService::class.java).apply {
                    action = JemmaWidgetEmergencyService.ACTION_START_EMERGENCY
                    putExtra(JemmaWidgetEmergencyService.EXTRA_PATIENT_NAME, name)
                    putExtra(JemmaWidgetEmergencyService.EXTRA_PATIENT_LANG, lang)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start emergency service", e)
            }
        }
    }
}
