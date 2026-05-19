package be.heyman.android.jemmapassdemo.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import be.heyman.android.jemmapassdemo.MainActivity
import be.heyman.android.jemmapassdemo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.File
import android.location.Location
import android.location.LocationManager

/**
 * Service de premier plan (Foreground) pour le Widget d'Urgence Jemma.
 * Déclenché par le widget (Double Tap).
 * 
 * Fonctionnalités:
 * - Text-To-Speech (toujours en anglais)
 * - Lampe torche en morse (SOS)
 * - Vibreur en morse (SOS)
 */
class JemmaWidgetEmergencyService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JEMMA-WIDGET-SVC"
        const val ACTION_START_EMERGENCY = "be.heyman.jemma.widget.START"
        const val ACTION_STOP_EMERGENCY = "be.heyman.jemma.widget.STOP"
        const val EXTRA_PATIENT_NAME = "patient_name"
        const val EXTRA_PATIENT_LANG = "patient_lang"

        private const val CHANNEL_ID = "jemma_emergency_widget_channel"
        private const val NOTIFICATION_ID = 112
        
        var isRunning = false
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsPhrase = ""
    private var patientName = "Unknown"
    private var deviceLangCode = "en"

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        
        createNotificationChannel()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JemmaPass::WidgetEmergencyWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
        
        tts = TextToSpeech(this, this)
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera ID for flashlight", e)
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP_EMERGENCY) {
            isRunning = false
            stopSelf()
            JemmaEmergencyWidget.updateAllWidgets(this)
            return START_NOT_STICKY
        }
        
        // Si le service tourne déjà et qu'on reçoit un START (ex: re-double-tap sur le widget), on l'arrête (Toggle)
        if (isRunning) {
            isRunning = false
            stopSelf()
            JemmaEmergencyWidget.updateAllWidgets(this)
            return START_NOT_STICKY
        }

        isRunning = true
        JemmaEmergencyWidget.updateAllWidgets(this)

        patientName = intent?.getStringExtra(EXTRA_PATIENT_NAME) ?: "Unknown"
        deviceLangCode = try {
            Locale.getDefault().language
        } catch (e: Exception) {
            "en"
        }
        
        // Phrase par défaut
        ttsPhrase = "Help me, my name is $patientName."

        startForeground(NOTIFICATION_ID, buildNotification())
        
        startSosLoops()
        startSosBroadcastHelper()
        
        if (ttsReady) {
            speak()
        }

        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed: $utteranceId")
                    if (utteranceId == "sos_device_utterance") {
                        // Phrase locale terminée -> bascule sur l'anglais !
                        serviceScope.launch {
                            delay(200)
                            speakEnglishPart()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error: $utteranceId")
                }
            })

            val deviceLocale = Locale(deviceLangCode)
            val result = tts?.setLanguage(deviceLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS: Device language $deviceLangCode is not supported, fallback to English")
                tts?.setLanguage(Locale.ENGLISH)
            }
            ttsReady = true
            Log.i(TAG, "TTS initialized with deviceLangCode=$deviceLangCode")
            speak()
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    private fun speak() {
        if (!ttsReady) return
        val currentLocale = Locale(deviceLangCode)
        tts?.setLanguage(currentLocale)
        val phrase = getSosPhrase(deviceLangCode, patientName)
        
        val params = android.os.Bundle()
        if (deviceLangCode.lowercase() != "en") {
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "sos_device_utterance")
        } else {
            tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "sos_english_utterance")
        }
    }

    private fun speakEnglishPart() {
        if (!ttsReady) return
        tts?.setLanguage(Locale.ENGLISH)
        val englishPhrase = getSosPhrase("en", patientName)
        val params = android.os.Bundle()
        tts?.speak(englishPhrase, TextToSpeech.QUEUE_ADD, params, "sos_english_utterance")
    }

    private fun getSosPhrase(langCode: String, name: String): String {
        return when (langCode.lowercase()) {
            "fr" -> "Aidez-moi, je m'appelle $name."
            "ja" -> "助けてください。私の名前は $name です。"
            "es" -> "Ayúdeme, mi nombre es $name."
            "de" -> "Helfen Sie mir, mein Name ist $name."
            "it" -> "Aiutatemi, mi chiamo $name."
            "pt" -> "Ajude-me, meu nome é $name."
            "nl" -> "Help mij, mijn naam is $name."
            "zh" -> "请帮帮我，我的名字是 $name。"
            "ko" -> "도와주세요, 제 이름은 $name 입니다."
            "ar" -> "ساعدوني، اسمي $name."
            "ru" -> "Помогите мне, меня зовут $name."
            "hi" -> "मेरी मदद करें, मेरा नाम $name है。"
            "bn" -> "আমাকে সাহায্য করুন, আমার নাম $name।"
            "tr" -> "Yardım edin, benim adım $name."
            "pl" -> "Pomóż mi, nazywam się $name."
            "uk" -> "Допоможіть мені, мене звати $name."
            "vi" -> "Xin giúp tôi, tôi tên là $name."
            "th" -> "ช่วยฉันด้วย ฉันชื่อ $name"
            "id" -> "Tolong saya, nama saya $name."
            "sv" -> "Hjälp mig, jag heter $name."
            "no" -> "Hjelp meg, mitt navn er $name."
            "da" -> "Hjælp mig, mit navn er $name."
            "fi" -> "Auttakaa minua, nimeni on $name."
            "ro" -> "Ajutați-mă, numele meu este $name."
            else -> "Help me, my name is $name."
        }
    }

    private fun startSosLoops() {
        // Boucle Flashlight + Vibreur
        serviceScope.launch {
            val dotMs = 200L
            val dashMs = 600L
            val gapMs = 200L
            val letterGapMs = 600L
            val wordGapMs = 1400L

            val pattern = listOf(
                dotMs, gapMs, dotMs, gapMs, dotMs, gapMs, letterGapMs, // S
                dashMs, gapMs, dashMs, gapMs, dashMs, gapMs, letterGapMs, // O
                dotMs, gapMs, dotMs, gapMs, dotMs, gapMs, wordGapMs // S
            )

            while (isActive) {
                // Relance TTS toutes les 15 secondes
                speak()
                
                for (i in pattern.indices) {
                    if (!isActive) break
                    val duration = pattern[i]
                    val isOn = (i % 2 == 0) // even index = signal, odd = gap
                    
                    if (isOn) {
                        setTorchModeSafe(true)
                        vibrateSafe(duration)
                    } else {
                        setTorchModeSafe(false)
                    }
                    delay(duration)
                }
            }
        }
    }

    private fun setTorchModeSafe(enabled: Boolean) {
        try {
            cameraId?.let { cameraManager?.setTorchMode(it, enabled) }
        } catch (e: Exception) {
            Log.e(TAG, "setTorchMode failed", e)
        }
    }

    private fun vibrateSafe(durationMs: Long) {
        try {
            val v = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                    v.vibrate(effect, attrs)
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "vibrate failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, JemmaWidgetEmergencyService::class.java).apply {
            action = ACTION_STOP_EMERGENCY
        }
        val pStopIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JEMMA Emergency")
            .setContentText("Emergency mode active (TTS, Flash, Vibrate)")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", pStopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jemma Emergency Widget",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        isRunning = false
        JemmaEmergencyWidget.updateAllWidgets(this)
        
        if (JemmaSosService.isRunning) {
            try {
                val stopSosIntent = Intent(this, JemmaSosService::class.java).apply {
                    action = JemmaSosService.ACTION_STOP
                }
                startService(stopSosIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop SOS service from widget emergency onDestroy", e)
            }
        }
        
        serviceJob.cancel()
        setTorchModeSafe(false)
        tts?.stop()
        tts?.shutdown()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    private fun startSosBroadcastHelper() {
        try {
            val prefs = getSharedPreferences("jemma_profiles_prefs", Context.MODE_PRIVATE)
            val currentId = prefs.getString("currentProfileId", null)
            if (currentId.isNullOrBlank()) {
                Log.w(TAG, "startSosBroadcastHelper: no current profile ID found")
                return
            }
            val profilesDir = File(getExternalFilesDir(null), "profiles")
            val file = File(profilesDir, "$currentId.json")
            if (!file.exists()) {
                Log.w(TAG, "startSosBroadcastHelper: profile file does not exist: ${file.absolutePath}")
                return
            }
            val rawJson = file.readText()
            val displayName = try {
                val o = org.json.JSONObject(rawJson)
                val p = o.optJSONObject("p")
                val gn = p?.optString("gn", "")?.trim().orEmpty()
                val fn = p?.optString("fn", "")?.trim().orEmpty()
                listOf(gn, fn).filter { it.isNotBlank() }
                    .joinToString(" ").take(16).ifBlank { "JEMMA" }
            } catch (e: Exception) {
                "JEMMA"
            }

            val langCode = try {
                Locale.getDefault().language.take(2)
            } catch (e: Exception) {
                "en"
            }

            var latE6 = 0
            var lonE6 = 0
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                var best: Location? = null
                for (p in providers) {
                    if (lm.isProviderEnabled(p)) {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && (best == null || loc.time > best.time)) {
                            best = loc
                        }
                    }
                }
                if (best != null) {
                    latE6 = (best.latitude * 1e6).toInt()
                    lonE6 = (best.longitude * 1e6).toInt()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get location for widget SOS activation", e)
            }

            JemmaSosService.configureBroadcast(
                profileJson = rawJson,
                latE6 = latE6,
                lonE6 = lonE6,
                name = displayName,
                criticality = 0,
                flags = 0,
                langCode = langCode
            )
            JemmaSosService.persistConfig(this)

            val startSosIntent = Intent(this, JemmaSosService::class.java).apply {
                action = JemmaSosService.ACTION_START_BROADCAST
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startSosIntent)
            } else {
                startService(startSosIntent)
            }
            Log.i(TAG, "✓ JemmaSosService started & broadcast configured from widget")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SOS broadcast from widget", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
