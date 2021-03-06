package com.iflytek.cyber.iot.show.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.iflytek.cyber.evs.sdk.EvsError
import com.iflytek.cyber.evs.sdk.EvsService
import com.iflytek.cyber.evs.sdk.RequestCallback
import com.iflytek.cyber.evs.sdk.agent.*
import com.iflytek.cyber.evs.sdk.auth.AuthDelegate
import com.iflytek.cyber.evs.sdk.focus.*
import com.iflytek.cyber.evs.sdk.socket.Result
import com.iflytek.cyber.iot.show.core.fragment.MainFragment2
import com.iflytek.cyber.iot.show.core.impl.alarm.EvsAlarm
import com.iflytek.cyber.iot.show.core.impl.appaction.EvsAppAction
import com.iflytek.cyber.iot.show.core.impl.audioplayer.EvsAudioPlayer
import com.iflytek.cyber.iot.show.core.impl.interceptor.EvsInterceptor
import com.iflytek.cyber.iot.show.core.impl.launcher.EvsLauncher
import com.iflytek.cyber.iot.show.core.impl.playback.EvsPlaybackController
import com.iflytek.cyber.iot.show.core.impl.prompt.PromptManager
import com.iflytek.cyber.iot.show.core.impl.recognizer.EvsRecognizer
import com.iflytek.cyber.iot.show.core.impl.screen.EvsScreen
import com.iflytek.cyber.iot.show.core.impl.speaker.EvsSpeaker
import com.iflytek.cyber.iot.show.core.impl.system.EvsSystem
import com.iflytek.cyber.iot.show.core.impl.template.EvsTemplate
import com.iflytek.cyber.iot.show.core.impl.videoplayer.EvsVideoPlayer
import com.iflytek.cyber.iot.show.core.model.ActionConstant
import com.iflytek.cyber.iot.show.core.model.ContentStorage
import com.iflytek.cyber.iot.show.core.model.PlayerInfoPayload
import com.iflytek.cyber.iot.show.core.record.EvsIvwHandler
import com.iflytek.cyber.iot.show.core.record.GlobalRecorder
import com.iflytek.cyber.iot.show.core.record.RecordVolumeUtils
import com.iflytek.cyber.iot.show.core.utils.ConfigUtils
import com.iflytek.cyber.iot.show.core.utils.ConnectivityUtils
import com.iflytek.cyber.iot.show.core.utils.RequestIdMap
import com.iflytek.cyber.iot.show.core.utils.*
import com.iflytek.cyber.iot.show.core.utils.ContextWrapper
import org.json.JSONObject
import java.lang.ref.SoftReference
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class EngineService : EvsService() {
    private val binder = EngineServiceBinder()

    companion object {
        private const val TAG = "EngineService"

        private const val ACTION_PREFIX = "com.iflytek.cyber.iot.show.core.action"
        private const val PERMISSION_PREFIX = "com.iflytek.cyber.iot.show.core.permission"

        const val ACTION_EVS_CONNECTED = "$ACTION_PREFIX.EVS_CONNECTED"
        const val ACTION_EVS_START_CONNECTING = "$ACTION_PREFIX.EVS_START_CONNECTING"
        const val ACTION_EVS_CONNECT_FAILED = "$ACTION_PREFIX.EVS_CONNECT_FAILED"
        const val ACTION_EVS_DISCONNECTED = "$ACTION_PREFIX.EVS_DISCONNECTED"
        const val ACTION_STOP_CAPTURE = "$ACTION_PREFIX.STOP_CAPTURE"
        const val ACTION_REQUEST_CANCEL = "$ACTION_PREFIX.REQUEST_CANCEL"
        const val ACTION_REQUEST_STOP_AUDIO_PLAYER = "$ACTION_PREFIX.REQUEST_STOP_AUDIO_PLAYER"
        const val ACTION_REQUEST_STOP_ALARM = "$ACTION_PREFIX.REQUEST_STOP_ALARM"
        const val ACTION_SET_WAKE_UP_ENABLED = "$ACTION_PREFIX.SET_WAKE_UP_ENABLED"
        const val ACTION_SEND_TEMPLATE_ELEMENT_SELECTED =
            "$ACTION_PREFIX.SEND_TEMPLATE_ELEMENT_SELECTED"
        const val ACTION_SEND_TEXT_IN = "$ACTION_PREFIX.SEND_TEXT_IN"
        const val ACTION_SEND_AUDIO_IN = "$ACTION_PREFIX.SEND_AUDIO_IN"
        const val ACTION_AUTH_REVOKED = "$ACTION_PREFIX.AUTH_REVOKED"
        const val ACTION_CLEAR_TEMPLATE_FOCUS = "$ACTION_PREFIX.CLEAR_TEMPLATE_FOCUS"
        const val ACTION_INIT_VOLUME = "$ACTION_PREFIX.INIT_VOLUME"
        const val ACTION_INTERCEPTOR_RESULT = "$ACTION_PREFIX.INTERCEPTOR_RESULT"
        const val ACTION_PLAY_TTS = "$ACTION_PREFIX.PLAY_TTS"
        const val ACTION_CANCEL_CURRENT_RESPONSE_EXECUTING = "$ACTION_PREFIX.END_REQUEST"

        const val PERMISSION_RECEIVE_BROADCAST = "$PERMISSION_PREFIX.RECEIVE_BROADCAST"

        const val ACTION_DISCONNECT_EVS = "$ACTION_PREFIX.DISCONNECT_EVS"

        const val ACTION_ALARM_STATE_CHANGED = "$ACTION_PREFIX.ALARM_STATE_CHANGED"
        const val ACTION_SEND_REQUEST_FAILED = "$ACTION_PREFIX.SEND_REQUEST_FAILED"
        const val ACTION_REQUEST_CLOSE_VIDEO = "$ACTION_PREFIX.REQUEST_CLOSE_VIDEO"

        const val EXTRA_CODE = "code"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_FROM_REMOTE = "from_remote"

        const val EXTRA_PLAYER_TYPE = "player_type"
        const val EXTRA_RESOURCE_ID = "resource_id"
        const val EXTRA_ERROR_CODE = "error_code"
        const val EXTRA_POSITION = "position"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_STATE = "alarm_state"
        const val EXTRA_TEMPLATE_ID = "template_id"
        const val EXTRA_ELEMENT_ID = "element_id"
        const val EXTRA_QUERY = "query"
        const val EXTRA_WAKE_UP_JSON = "wake_up_json"
        const val EXTRA_RESULT = "result"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_TYPE = "type"
        const val EXTRA_TTS_TEXT = "tts_text"
        const val EXTRA_TTS_PATH = "tts_path"
        const val EXTRA_WITH_TTS = "with_tts"
    }

    open inner class EngineServiceBinder : Binder() {
        fun getService(): EngineService {
            return this@EngineService
        }
    }

    private var transmissionListener: TransmissionListener? = null
    private var recognizerCallback: Recognizer.RecognizerCallback? = null
    private var templateRenderCallback: EvsTemplate.RenderCallback? = null
    private var preventWakeUp = false
    private lateinit var mIvwHandler: EvsIvwHandler
    private var wakeLock: PowerManager.WakeLock? = null
    private var ttsWakeLock: PowerManager.WakeLock? = null
    private var currentTtsResourceId: String? = null
    private var isShowingDaydream = false
    private var latestRecognizeTime = 0L
    private var reconnectHandler = ReconnectHandler(this)

    private var evsLauncherActivity: EvsLauncherActivity? = null

    private var hadInitVolume = false

    private var currentConnectingId: UUID? = null
    private var isConnecting = false

    private val handler = Handler()

    private val audioFocusListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                val stopPlayers = Intent(baseContext, EngineService::class.java)
                stopPlayers.action = ACTION_REQUEST_STOP_AUDIO_PLAYER
                stopPlayers.putExtra(EXTRA_PLAYER_TYPE, AudioPlayer.TYPE_PLAYBACK)
                startService(stopPlayers)
            }
        }
    private var audioFocusRequest: Any? = null

    private val evsSystem = EvsSystem.get()
    private val internalRecordCallback = object : Recognizer.RecognizerCallback {
        override fun onBackgroundRecognizeStateChanged(isBackgroundRecognize: Boolean) {
            try {
                recognizerCallback?.onBackgroundRecognizeStateChanged(isBackgroundRecognize)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_SET_BACKGROUND_RECOGNIZE
            intent.putExtra(FloatingService.EXTRA_ENABLED, isBackgroundRecognize)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
        }

        override fun onRecognizeStarted(isExpectReply: Boolean) {
            latestRecognizeTime = java.lang.System.currentTimeMillis()

            try {
                recognizerCallback?.onRecognizeStarted(isExpectReply)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!isExpectReply)
                overlayVisualFocusChannel.requestActive()

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_SHOW_RECOGNIZE
            intent.putExtra(FloatingService.EXTRA_EXPECT_REPLY, isExpectReply)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")

            acquireRecordWakeLock()
        }

        override fun onRecognizeStopped() {
            latestRecognizeTime = java.lang.System.currentTimeMillis()

            try {
                recognizerCallback?.onRecognizeStopped()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            overlayVisualFocusChannel.requestAbandon()

            handler.postDelayed({
                if (getRecognizer().isRecording())
                    return@postDelayed
                val intent = Intent(baseContext, FloatingService::class.java)
                intent.action = FloatingService.ACTION_DISMISS_RECOGNIZE
                ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
            }, 1000)
        }

        override fun onIntermediateText(text: String) {
            Log.d(TAG, "onIntermediateText($text)")
            try {
                recognizerCallback?.onIntermediateText(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_INTERMEDIATE_TEXT
            intent.putExtra(FloatingService.EXTRA_TEXT, text)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
        }
    }
    private val configChangedListener = object : ConfigUtils.OnConfigChangedListener {
        override fun onConfigChanged(key: String, value: Any?) {
            if (key == ConfigUtils.KEY_VOICE_WAKEUP_ENABLED) {
                if (value == true) {
                    getRecognizer().isPreventExpectReply = false
                } else {
                    getRecognizer().isPreventExpectReply = true
                    if (getRecognizer().isRecording()) {
                        getRecognizer().requestCancel()
                    }
                }
            }
        }
    }
    private val tokenChangedListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updateCookie()
        }
    private val innerTemplateCallback = object : EvsTemplate.RenderCallback {
        override fun renderCustomTemplate(
            type: String,
            templateId: String,
            showingDuration: String?,
            htmlSourceCode: String
        ) {
            EvsSpeaker.get(baseContext).isVisualFocusGain = true

            EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

            templateRenderCallback?.renderCustomTemplate(
                type,
                templateId,
                showingDuration,
                htmlSourceCode
            )
        }

        override fun renderTemplate(payload: String) {
            EvsSpeaker.get(baseContext).isVisualFocusGain = true

            EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_RENDER_TEMPLATE
            intent.putExtra(FloatingService.EXTRA_PAYLOAD, payload)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")

            templateRenderCallback?.renderTemplate(payload)
        }

        override fun notifyPlayerInfoUpdated(resourceId: String, payload: String) {
            templateRenderCallback?.notifyPlayerInfoUpdated(resourceId, payload)
        }

        override fun renderPlayerInfo(payload: String) {
            val playerInfo = Gson().fromJson(payload, PlayerInfoPayload::class.java)
            val intent = Intent(baseContext, FloatingService::class.java).apply {
                action = FloatingService.ACTION_UPDATE_MUSIC
            }
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
            ContentStorage.get().savePlayInfo(playerInfo)
            ContentStorage.get().isMusicPlaying = true

            if (playerInfo.shouldPopup) {
                val startPlayer = Intent(baseContext, EvsLauncherActivity::class.java)
                startPlayer.action = EvsLauncherActivity.ACTION_START_PLAYER
                startPlayer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startPlayer.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                applicationContext.startActivity(startPlayer)
            }

            templateRenderCallback?.renderPlayerInfo(payload)
        }

        override fun exitCustomTemplate() {
            EvsSpeaker.get(baseContext).isVisualFocusGain = true

            EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_CLEAR_TEMPLATE
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")

            templateRenderCallback?.exitCustomTemplate()
        }

        override fun exitPlayerInfo() {
            templateRenderCallback?.exitPlayerInfo()
        }

        override fun exitStaticTemplate(type: String?) {
            EvsSpeaker.get(baseContext).isVisualFocusGain = false

            EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_CLEAR_TEMPLATE
            intent.putExtra(FloatingService.EXTRA_TYPE, type)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")

            templateRenderCallback?.exitStaticTemplate(type)
        }

        override fun renderVideoPlayerInfo(payload: String) {
            templateRenderCallback?.renderVideoPlayerInfo(payload)
        }
    }
    private val audioPlayerListener = object : AudioPlayer.MediaStateChangedListener {
        override fun onStarted(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_TTS) {
                EvsSpeaker.get(baseContext).isAudioFocusGain = true

                EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

                acquireTtsWakeLock(resourceId)
            } else if (type == AudioPlayer.TYPE_PLAYBACK) {
                requestAudioFocus()
            }
        }

        override fun onResumed(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_TTS) {
                EvsSpeaker.get(baseContext).isAudioFocusGain = true

                EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)
            } else if (type == AudioPlayer.TYPE_PLAYBACK) {
                requestAudioFocus()
            }
        }

        override fun onPaused(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_TTS) {
                EvsSpeaker.get(baseContext).isAudioFocusGain = false

                EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)
            }
        }

        override fun onStopped(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_TTS) {
                EvsSpeaker.get(baseContext).isAudioFocusGain = false

                EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

                releaseTtsWakeLock(resourceId)

                val intent = Intent(baseContext, FloatingService::class.java)
                intent.action = FloatingService.ACTION_DISMISS_TTS_VIEW
                ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
            }
        }

        override fun onCompleted(player: AudioPlayer, type: String, resourceId: String) {
            if (type == AudioPlayer.TYPE_TTS) {
                EvsSpeaker.get(baseContext).isAudioFocusGain = false

                EvsSpeaker.get(baseContext).refreshNativeAudioFocus(baseContext)

                releaseTtsWakeLock(resourceId)

                val intent = Intent(baseContext, FloatingService::class.java)
                intent.action = FloatingService.ACTION_DISMISS_TTS_VIEW
                ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
            }
        }

        override fun onPositionUpdated(
            player: AudioPlayer,
            type: String,
            resourceId: String,
            position: Long
        ) {
        }

        override fun onError(
            player: AudioPlayer,
            type: String,
            resourceId: String,
            errorCode: String
        ) {
        }
    }
    private val alarmStateChangedListener = object : Alarm.AlarmStateChangedListener {
        @SuppressLint("InvalidWakeLockTag")
        override fun onAlarmStateChanged(alarmId: String, state: Alarm.AlarmState) {
            if (state == Alarm.AlarmState.Started) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val screenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    pm.isInteractive
                } else {
                    pm.isScreenOn
                }
                if (!screenOn) {
                    Thread {
                        try {
                            TerminalUtils.execute("input keyevent POWER")
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }.start()
                } else {
                    if (isShowingDaydream) {
                        val wl = pm.newWakeLock(
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                            "bright"
                        )
                        wl.acquire(10000)
                        wl.release()
                    }
                }
            }

            val intent = Intent(ACTION_ALARM_STATE_CHANGED)
            intent.putExtra(EXTRA_ALARM_ID, alarmId)
            intent.putExtra(EXTRA_ALARM_STATE, state.toString())
            sendBroadcast(intent)
        }
    }
    private val volumeChangedListener = object : EvsSpeaker.OnVolumeChangedListener {
        override fun onVolumeChanged(volume: Int, fromRemote: Boolean) {
            if (fromRemote)
                PromptManager.play(PromptManager.VOLUME)
        }
    }
    private val launcherVisualFocusChannel = object : VisualFocusChannel() {
        override fun onFocusChanged(focusStatus: FocusStatus) {
            // don't need do any thing
        }

        override fun getChannelName(): String {
            return VisualFocusManager.CHANNEL_APP
        }

        override fun getType(): String {
            return "Launcher"
        }
    }
    private val videoVisualFocusChannel = object : VisualFocusChannel() {
        override fun onFocusChanged(focusStatus: FocusStatus) {
            if (focusStatus == FocusStatus.Idle) {
                sendBroadcast(Intent(ACTION_REQUEST_CLOSE_VIDEO))

                getVideoPlayer()?.stop()
            }
        }

        override fun getChannelName(): String {
            return VisualFocusManager.CHANNEL_APP
        }

        override fun getType(): String {
            return "Video"
        }

    }
    private val overlayVisualFocusChannel = object : VisualFocusChannel() {
        override fun onFocusChanged(focusStatus: FocusStatus) {
        }

        override fun getChannelName(): String {
            return VisualFocusManager.CHANNEL_OVERLAY
        }

        override fun getType(): String {
            return "Recognize"
        }

    }
    private val daydreamReceiver = object : SelfBroadcastReceiver(
        Intent.ACTION_DREAMING_STARTED,
        Intent.ACTION_DREAMING_STOPPED
    ) {
        override fun onReceiveAction(action: String, intent: Intent) {
            when (action) {
                Intent.ACTION_DREAMING_STARTED -> {
                    isShowingDaydream = true
                }
                Intent.ACTION_DREAMING_STOPPED -> {
                    isShowingDaydream = false
                }
            }
        }
    }
    private val connectStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            when (intent.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    // api 21 以上应使用 networkCallback
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                    if (connectivityManager.activeNetworkInfo?.isConnected == true) {
                        connectEvs(DeviceUtils.getDeviceId(baseContext))

                        ContextWrapper.startServiceAsUser(
                            baseContext,
                            Intent(baseContext, TimeService::class.java),
                            "CURRENT"
                        )
                    }
                }
            }
        }
    }
    private var networkCallback: Any? = null

    private val authReceiver = object : SelfBroadcastReceiver(
        ActionConstant.ACTION_CLIENT_AUTH_REFRESHED
    ) {
        override fun onReceiveAction(action: String, intent: Intent) {
            when (action) {
                ActionConstant.ACTION_CLIENT_AUTH_REFRESHED -> {
                    connectEvs(DeviceUtils.getDeviceId(baseContext))
                }
            }
        }
    }
    private val recordObserver = object : GlobalRecorder.Observer {
        override fun onAudioData(array: ByteArray, offset: Int, length: Int) {
            val volume = RecordVolumeUtils.calculateVolume(array, length)

            val intent = Intent(baseContext, FloatingService::class.java)
            intent.action = FloatingService.ACTION_UPDATE_VOLUME
            intent.putExtra(FloatingService.EXTRA_VOLUME, volume)
            ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
        }

        override fun onWakeUp(angle: Int, beam: Int, params: String?) {
            this@EngineService.onWakeUp(angle, beam, params)
        }
    }
    private val onConfigChangedListener = object : ConfigUtils.OnConfigChangedListener {
        override fun onConfigChanged(key: String, value: Any?) {
            when (key) {
                ConfigUtils.KEY_RECOGNIZER_PROFILE -> {
                    if (value == Recognizer.Profile.CloseTalk.toString()) {
                        getRecognizer().profile = Recognizer.Profile.CloseTalk
                    } else if (value == Recognizer.Profile.FarField.toString()) {
                        getRecognizer().profile = Recognizer.Profile.FarField
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun setActivity(activity: EvsLauncherActivity) {
        this.evsLauncherActivity = activity
        EvsLauncher.get().setActivity(activity)
    }

    fun setTransmissionListener(listener: TransmissionListener?) {
        transmissionListener = listener
    }

    fun setRecognizerCallback(recognizerCallback: Recognizer.RecognizerCallback?) {
        this.recognizerCallback = recognizerCallback
    }

    fun setTemplateRenderCallback(templateRenderCallback: EvsTemplate.RenderCallback?) {
        this.templateRenderCallback = templateRenderCallback
    }

    override fun onCreate() {
        EvsSpeaker.get(this)

        super.onCreate()

//        mIvwHandler = EvsIvwHandler(this, mIvwListener)
//        GlobalRecorder.registerObserver(recordObserver)
        getRecognizer().setRecognizerCallback(internalRecordCallback)
        getAudioPlayer().addListener(audioPlayerListener)
        getAlarm()?.addListener(alarmStateChangedListener)

        PromptManager.init(this)
        PromptManager.setupAudioFocusManager(AudioFocusManager)

        launcherVisualFocusChannel.setupManager(VisualFocusManager)
        overlayVisualFocusChannel.setupManager(VisualFocusManager)
        videoVisualFocusChannel.setupManager(VisualFocusManager)

        EvsSpeaker.get(this).addOnVolumeChangedListener(volumeChangedListener)

        EvsLauncher.get().init(this)

        evsSystem.init(this)

        daydreamReceiver.register(this)

        ConfigUtils.registerOnConfigChangedListener(configChangedListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "evs_engine_main"
            val channel = NotificationChannel(
                channelId,
                "智能语音服务",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(0, notification)
        }

        handler.post(object : Runnable {
            override fun run() {
                sendKeepAlive()

                handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5))
            }
        })

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val flag = PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
        wakeLock = powerManager.newWakeLock(flag, "iflytek:evs_engine")

        acquireWakeLock()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            val connectStateFilter = IntentFilter()
            @Suppress("DEPRECATION")
            connectStateFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(connectStateReceiver, connectStateFilter)
        } else {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network?) {
                    super.onAvailable(network)

                    connectEvs(DeviceUtils.getDeviceId(baseContext))
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)

            this.networkCallback = networkCallback
        }

        authReceiver.register(this)

        GlobalRecorder.registerObserver(recordObserver)

        ConfigUtils.init(this)
        ConfigUtils.registerOnConfigChangedListener(onConfigChangedListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_CAPTURE -> {
                getRecognizer().stopCapture()
            }
            ACTION_REQUEST_CANCEL -> {
                getRecognizer().requestCancel()
            }
            ACTION_SET_WAKE_UP_ENABLED -> {
                val wakeUpEnabled = intent.getBooleanExtra(EXTRA_ENABLED, true)
                preventWakeUp = wakeUpEnabled == false
            }
            ACTION_REQUEST_STOP_AUDIO_PLAYER -> {
                val type = intent.getStringExtra(EXTRA_PLAYER_TYPE)
                if (type.isNullOrEmpty()) {
                    getAudioPlayer().stop(AudioPlayer.TYPE_TTS)
                    getAudioPlayer().stop(AudioPlayer.TYPE_RING)
                    getAudioPlayer().stop(AudioPlayer.TYPE_PLAYBACK)
                } else {
                    getAudioPlayer().stop(type)
                }
            }
            ACTION_REQUEST_STOP_ALARM -> {
                getAlarm()?.stop() ?: run {
                    getAudioPlayer().stop(AudioPlayer.TYPE_RING)
                }
            }
            ACTION_SEND_TEMPLATE_ELEMENT_SELECTED -> {
                getTemplate()?.let { templateAgent ->
                    val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
                    val elementId = intent.getStringExtra(EXTRA_ELEMENT_ID)
                    templateAgent.sendElementSelected(templateId, elementId)
                }
            }
            ACTION_SEND_TEXT_IN -> {
                sendTextIn(intent.getStringExtra(EXTRA_QUERY))
            }
            ACTION_SEND_AUDIO_IN -> {
                val json = intent.getStringExtra(EXTRA_WAKE_UP_JSON)
//                mIvwListener.onWakeUp(json ?: "")
                onWakeUp(-1, 0, "{}")
            }
            ACTION_AUTH_REVOKED -> {
                ConfigUtils.putBoolean(ConfigUtils.KEY_SETUP_COMPLETED, false)

                val openAuth = Intent(this, EvsLauncherActivity::class.java)
                openAuth.action = EvsLauncherActivity.ACTION_OPEN_AUTH
                openAuth.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(openAuth)

                getVideoPlayer()?.stop()
                getAudioPlayer().apply {
                    stop(AudioPlayer.TYPE_TTS)
                    stop(AudioPlayer.TYPE_PLAYBACK)
                    stop(AudioPlayer.TYPE_RING)
                }
                getAlarm()?.stop()
            }
            ACTION_CLEAR_TEMPLATE_FOCUS -> {
                clearCurrentTemplateFocus()
            }
            ACTION_DISCONNECT_EVS -> {
                disconnect()
            }
            ACTION_INIT_VOLUME -> {
                if (!hadInitVolume) {
                    hadInitVolume = true
                    initVolume()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()

        GlobalRecorder.stopRecording()
//        GlobalRecorder.unregisterObserver(recordObserver)
//        mIvwHandler.release()
        getRecognizer().removeRecognizerCallback()
        getAudioPlayer().removeListener(audioPlayerListener)
        getAlarm()?.removeListener(alarmStateChangedListener)

        PromptManager.destroy()

        evsSystem.destroy(this)

        daydreamReceiver.unregister(this)

        ConfigUtils.unregisterOnConfigChangedListener(configChangedListener)

        EvsSpeaker.get(this).removeOnVolumeChangedListener(volumeChangedListener)

        releaseWakeLock()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            unregisterReceiver(connectStateReceiver)
        } else {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.let { connectivityManager ->
                    val networkCallback =
                        (this.networkCallback as? ConnectivityManager.NetworkCallback)
                            ?: return
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
        }

        authReceiver.unregister(this)

        GlobalRecorder.unregisterObserver(recordObserver)

        ConfigUtils.unregisterOnConfigChangedListener(onConfigChangedListener)

        ConfigUtils.destroy()

        AuthDelegate.unregisterTokenChangedListener(baseContext, tokenChangedListener)
    }

    override fun onEvsConnected() {
        super.onEvsConnected()

        sendBroadcast(Intent(ACTION_EVS_CONNECTED))
    }

    override fun onEvsDisconnected(code: Int, message: String?, fromRemote: Boolean) {
        super.onEvsDisconnected(code, message, fromRemote)

        val intent = Intent(ACTION_EVS_DISCONNECTED)
        intent.putExtra(EXTRA_CODE, code)
        intent.putExtra(EXTRA_MESSAGE, message)
        intent.putExtra(EXTRA_FROM_REMOTE, fromRemote)
        sendBroadcast(intent)
    }

    override fun onConnectFailed(t: Throwable?) {
        Log.w(TAG, "onConnectFailed", t)
        val intent = Intent(ACTION_EVS_CONNECT_FAILED)
        sendBroadcast(intent)
        when (t) {
            is UnknownHostException -> {
                // 无网络
            }
            is EvsError.AuthorizationExpiredException -> {
                // Token 过期
                AuthDelegate.removeAuthResponseFromPref(this)

                val disconnectNotification =
                    Intent(baseContext, FloatingService::class.java)
                disconnectNotification.action = FloatingService.ACTION_SHOW_NOTIFICATION
                disconnectNotification.putExtra(
                    FloatingService.EXTRA_MESSAGE, getString(R.string.message_evs_auth_expired)
                )
                disconnectNotification.putExtra(FloatingService.EXTRA_TAG, "auth_error")
                disconnectNotification.putExtra(
                    FloatingService.EXTRA_ICON_RES, R.drawable.ic_default_error_white_40dp
                )
                disconnectNotification.putExtra(
                    FloatingService.EXTRA_POSITIVE_BUTTON_TEXT, getString(R.string.re_auth)
                )
                disconnectNotification.putExtra(
                    FloatingService.EXTRA_POSITIVE_BUTTON_ACTION, MainFragment2.ACTION_OPEN_AUTH
                )
                disconnectNotification.putExtra(
                    FloatingService.EXTRA_KEEPING, true
                )
                startService(disconnectNotification)
            }
        }
    }

    override fun onSendFailed(code: Int, reason: String?) {
        super.onSendFailed(code, reason)
        if (code == EvsError.Code.ERROR_SOCKET_TIMEOUT) {
            val timeoutNotification =
                Intent(baseContext, FloatingService::class.java)
            timeoutNotification.action = FloatingService.ACTION_SHOW_NOTIFICATION
            timeoutNotification.putExtra(
                FloatingService.EXTRA_MESSAGE, getString(R.string.message_evs_timeout)
            )
            timeoutNotification.putExtra(
                FloatingService.EXTRA_ICON_RES, R.drawable.ic_default_error_white_40dp
            )
            timeoutNotification.putExtra(
                FloatingService.EXTRA_POSITIVE_BUTTON_TEXT, getString(R.string.i_got_it)
            )
            startService(timeoutNotification)
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioManager.requestAudioFocus(request)

            audioFocusRequest = request
        } else {
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun initVolume() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, (alarmMax * .8).roundToInt(), 0)

            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL)
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL

            val notificationMax = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            am.setStreamVolume(
                AudioManager.STREAM_NOTIFICATION,
                (notificationMax * .6).roundToInt(),
                0
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (mediaVol == 0 || am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    am.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * .3).toInt(),
                        0
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest as? AudioFocusRequest ?: return
            audioManager.abandonAudioFocusRequest(request)
        } else {
            audioManager.abandonAudioFocus(
                audioFocusListener
            )
        }
    }

    fun connectEvs(deviceId: String) {
        if (!isEvsConnected) {
            isConnecting = true

            connect("wss://ivs.iflyos.cn/embedded/v1", deviceId)

            sendBroadcast(Intent(ACTION_EVS_START_CONNECTING))

            reconnectHandler.postReconnectEvs() //比如路由器断电了，那么等路由器恢复后需要多次重连 EVS 服务才能连的上，只连一次是不行的
        }
    }

    fun requestLauncherVisualFocus() {
        launcherVisualFocusChannel.requestActive()
    }

    fun requestVideoVisualFocus() {
        videoVisualFocusChannel.requestActive()
    }

    fun sendAudioIn(wakeUpJson: String? = null, replyKey: String? = null) {
        getRecognizer().sendAudioIn(replyKey, wakeUpJson, object : RequestCallback {
            override fun onResult(result: Result) {
                if (!result.isSuccessful) {
                    val intent = Intent(ACTION_SEND_REQUEST_FAILED)
                    intent.putExtra(EXTRA_RESULT, result)
                    sendBroadcast(intent)
                }
            }
        })
    }

    fun sendTextIn(query: String, withTts: Boolean = true, replyKey: String? = null) {
        Log.d(TAG, "sendTextIn")
        getRecognizer().sendTextIn(query, withTts, replyKey, object : RequestCallback {
            override fun onResult(result: Result) {
                Log.d(TAG, "sendTextIn result: $result")
                if (!result.isSuccessful) {
                    val intent = Intent(ACTION_SEND_REQUEST_FAILED)
                    intent.putExtra(EXTRA_RESULT, result)
                    sendBroadcast(intent)
                }
            }
        })
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireTtsWakeLock(resourceId: String) {
        if (currentTtsResourceId == resourceId)
            return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val screenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            pm.isScreenOn
        }
        if (isShowingDaydream || !screenOn) {
            return
        }
        currentTtsResourceId = resourceId
        ttsWakeLock?.acquire() ?: run {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val flag = PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK
            val wakeLock = powerManager.newWakeLock(flag, "iflytek:evs_tts")

            wakeLock.acquire()
            ttsWakeLock = wakeLock
        }
    }

    private fun releaseTtsWakeLock(resourceId: String) {
        if (currentTtsResourceId == resourceId) {
            ttsWakeLock?.release()
            ttsWakeLock = null
        }
    }

    private fun acquireRecordWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val flag = PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK
        val wakeLock = powerManager.newWakeLock(flag, "iflytek:record")

        wakeLock.acquire(1 * 60 * 1000L /*1 minutes*/)
    }

    private fun acquireWakeLock() {
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
    }

    private fun sendKeepAlive() {
        try {
            val intent = Intent()
            intent.action = "com.iflytek.cyber.iot.keepalive.action.KEEP_ALIVE"
            intent.setClassName(
                "com.iflytek.cyber.iot.keepalive",
                "com.iflytek.cyber.iot.keepalive.KeepAliveService"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ContextWrapper.startForegroundServiceAsUser(baseContext, intent, "CURRENT")
            else
                ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun overrideAlarm(): Alarm? {
        return EvsAlarm.get(this)
    }

    override fun overrideSpeaker(): Speaker {
        return EvsSpeaker.get(this)
    }

    override fun overrideRecognizer(): Recognizer {
        return EvsRecognizer(this)
    }

    override fun overrideTemplate(): Template? {
        val template = EvsTemplate.get()
        template.renderCallback = innerTemplateCallback
        return template
    }

    override fun overrideScreen(): Screen? {
        return EvsScreen.get(this)
    }

    override fun overrideInterceptor(): Interceptor? {
        return EvsInterceptor.get().transferSemantic { payload ->
            val intent = Intent(ACTION_INTERCEPTOR_RESULT)
            intent.putExtra(EXTRA_PAYLOAD, payload.toString())
            intent.putExtra(EXTRA_TYPE, "TransferSemantic")
            sendBroadcast(intent)
        }.custom { payload ->
            val intent = Intent(ACTION_INTERCEPTOR_RESULT)
            intent.putExtra(EXTRA_PAYLOAD, payload.toString())
            intent.putExtra(EXTRA_TYPE, "Custom")
            sendBroadcast(intent)
        }
    }

    override fun onResponsesRaw(json: String) {
        super.onResponsesRaw(json)

        Thread {
            var ttsText = ""
            var hasTemplate = false
            var hasTts = false

            val jsonObject = JsonParser().parse(json).asJsonObject
            val meta = jsonObject.getAsJsonObject("iflyos_meta")
            val requestId = meta.get("request_id")?.asString ?: return@Thread
            val responses = jsonObject.getAsJsonArray("iflyos_responses")
            for (i in 0 until responses.size()) {
                val item = responses[i].asJsonObject
                val header = item.getAsJsonObject("header")
                val headerName = header.get("name")?.asString
                val payload = item.getAsJsonObject("payload")
                if (TextUtils.equals("template.static_template", headerName)) {
                    hasTemplate = true
                } else if ("template.custom_template" == headerName) {
                    hasTemplate = true
                }
                if (headerName?.startsWith("template.") == true) {
                    // 如果是 playing_template，不存在 template_id 则直接忽略

                    val templateId = payload.get("template_id")?.asString
                    if (!templateId.isNullOrEmpty()) {
                        RequestIdMap.putRequestTemplate(requestId, templateId)
                    }
                } else if (headerName?.startsWith("audio_player") == true) {
                    val type = payload.get("type")?.asString
                    if (type == AudioPlayer.TYPE_TTS) {
                        hasTts = true

                        val resourceId = payload.get("resource_id")?.asString
                        if (!resourceId.isNullOrEmpty()) {
                            RequestIdMap.putRequestTts(requestId, resourceId)
                        }

                        ttsText = payload.getAsJsonObject("metadata").get("text").asString
                    }
                }
            }

            val currentAppType = EvsLauncher.get().getCurrentAppType()
            if (!hasTemplate && hasTts && currentAppType != Launcher.TYPE_SKILL) { //有 template 不显示 tts，没有则显示 tts
                handler.post {
                    val intent = Intent(baseContext, FloatingService::class.java)
                    intent.action = FloatingService.ACTION_SHOW_TTS
                    intent.putExtra(FloatingService.EXTRA_TEXT, ttsText)
                    ContextWrapper.startServiceAsUser(baseContext, intent, "CURRENT")
                }
            }
        }.start()

        transmissionListener?.onResponsesRaw(json)
    }

    override fun onRequestRaw(obj: Any) {
        super.onRequestRaw(obj)

        transmissionListener?.onRequestRaw(obj)
    }

    private fun updateCookie() {
        AuthDelegate.getAuthResponseFromPref(baseContext)?.let { authResponse ->
            val accessToken = authResponse.accessToken

            val cookieManger = CookieManager.getInstance()
            cookieManger.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManger.removeAllCookies {}
            } else {
                cookieManger.removeAllCookie()
            }
            cookieManger.setCookie(
                "https://homev2.iflyos.cn",
                "token=$accessToken"
            )
        }
    }

    fun onWakeUp(angle: Int, beam: Int, params: String?) {
        if (preventWakeUp && angle != -1)
            return
        if (!ConfigUtils.getBoolean(ConfigUtils.KEY_VOICE_WAKEUP_ENABLED, true))
            return
        val shouldIgnore =
            java.lang.System.currentTimeMillis() - latestRecognizeTime > TimeUnit.MINUTES.toMillis(
                30
            ) && getAudioPlayer().playbackState != AudioPlayer.PLAYBACK_STATE_PLAYING
                && getVideoPlayer()?.state != VideoPlayer.STATE_PLAYING
        val pm = applicationContext
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        val screenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            pm.isScreenOn
        }
        try {
            Log.d(TAG, "wake up $params")
            val json = JSONObject(params)
            var resolved = false
            val isRecognizing = getRecognizer().isRecording()
            when (json.getString("keyword")) {
                "zan4 ting2" -> {
                    // 暂停
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        when {
                            getAudioPlayer().playbackState == AudioPlayer.PLAYBACK_STATE_PLAYING -> {
                                getAudioPlayer().pause(AudioPlayer.TYPE_PLAYBACK)
                            }
                            getVideoPlayer()?.state == VideoPlayer.STATE_PLAYING -> {
                                getVideoPlayer()?.pause()
                            }
                            else -> {
                                sendTextIn("暂停")
                            }
                        }
                    resolved = true
                }
                "ji4 xu4 bo1 fang4" -> {
                    // 继续播放
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        sendTextIn("继续播放")
                    resolved = true
                }
                "xia4 yi1 ge4" -> {
                    // 下一个
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        sendTextIn("下一个")
                    resolved = true
                }
                "shang4 yi1 ge4" -> {
                    // 上一个
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        sendTextIn("上一个")
                    resolved = true
                }
                "sheng1 yin1 da4 yi1 dian3" -> {
                    // 声音大一点
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        handler.post {
                            EvsSpeaker.get(this).raiseVolumeLocally(true)
                        }
                    resolved = true
                }
                "sheng1 yin1 xiao3 yi1 dian3" -> {
                    // 声音小一点
                    if (!isRecognizing && !shouldIgnore && screenOn)
                        handler.post {
                            EvsSpeaker.get(this).lowerVolumeLocally(true)
                        }
                    resolved = true
                }
            }
            if (resolved) {
                if (!shouldIgnore)
                    latestRecognizeTime = java.lang.System.currentTimeMillis()
                return
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        if (!screenOn || isShowingDaydream) {
            // 点亮屏幕
            @SuppressLint("InvalidWakeLockTag")
            val wl = pm.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "bright"
            )
            wl.acquire(10000)
            wl.release()
        }
        if (isEvsConnected) {

//            SleepWorker.get(baseContext).hideSleepView(baseContext)

            if (ConfigUtils.getBoolean(ConfigUtils.KEY_WAKE_UP_SOUND, true))
                PromptManager.playWakeSound()

            sendAudioIn()
        } else if (!isEvsConnected) {
            ConnectivityUtils.checkNetworkAvailable({
                PromptManager.play(PromptManager.CONNECTING_PLEASE_WAIT)

                // 网络可用但 EVS 断连
                if (!isConnecting) {
                    reconnectHandler.postReconnectEvs()
                }
            }, { _, _ ->
                // 网络不可用
                PromptManager.play(PromptManager.NETWORK_LOST)

                val networkErrorNotification =
                    Intent(baseContext, FloatingService::class.java)
                networkErrorNotification.action =
                    FloatingService.ACTION_SHOW_NOTIFICATION
                networkErrorNotification.putExtra(
                    FloatingService.EXTRA_MESSAGE, "网络连接异常，请重新设置"
                )
                networkErrorNotification.putExtra(
                    FloatingService.EXTRA_TAG, "network_error"
                )
                networkErrorNotification.putExtra(
                    FloatingService.EXTRA_POSITIVE_BUTTON_TEXT, "设置网络"
                )
                networkErrorNotification.putExtra(
                    FloatingService.EXTRA_POSITIVE_BUTTON_ACTION,
                    MainFragment2.ACTION_OPEN_WIFI
                )
                networkErrorNotification.putExtra(
                    FloatingService.EXTRA_ICON_RES,
                    R.drawable.ic_wifi_error_white_40dp
                )
                startService(networkErrorNotification)
            })
        }
    }

    override fun overrideAppAction(): AppAction? {
        return EvsAppAction.get(baseContext)
    }

    override fun overrideAudioPlayer(): AudioPlayer {
        return EvsAudioPlayer.get(this)
    }

    override fun overrideSystem(): System {
        return evsSystem
    }

    override fun overrideLauncher(): Launcher? {
        return EvsLauncher.get()
    }

    override fun overridePlaybackController(): PlaybackController? {
        return EvsPlaybackController.get()
    }

    override fun overrideVideoPlayer(): VideoPlayer? {
        return EvsVideoPlayer.get(this)
    }

    override fun getExternalAudioFocusChannels(): List<AudioFocusChannel> {
        return listOf(PromptManager.promptAudioChannel)
    }

    override fun getExternalVisualFocusChannels(): List<VisualFocusChannel> {
        return listOf(
            launcherVisualFocusChannel,
            videoVisualFocusChannel,
            overlayVisualFocusChannel
        )
    }

    override fun isResponseSoundEnabled(): Boolean {
        return ConfigUtils.getBoolean(ConfigUtils.KEY_RESPONSE_SOUND, true)
    }

    override fun getResponseSoundVolume(): Float {
        return EvsSpeaker.get(baseContext).getCurrentVolume() / 100f
    }

    override fun getResponseSoundPool(): SoundPool? {
        val maxStream = 3
        val streamType = AudioManager.STREAM_NOTIFICATION
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SoundPool.Builder()
                .setMaxStreams(maxStream)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setLegacyStreamType(streamType)
                        .build()
                )
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(maxStream, streamType, 0)
        }
    }

    interface TransmissionListener {
        fun onResponsesRaw(json: String)
        fun onRequestRaw(obj: Any)
    }

    private class ReconnectHandler(engineService: EngineService) : Handler() {
        var retryCount = -1
            private set
        private val serviceRef = SoftReference(engineService)

        private val shortDelay = 3L * 1000
        private val middleDelay = 6L * 1000
        private val longDelay = 10L * 1000

        fun isCounting() = retryCount >= 0

        fun postReconnectEvs() {
            removeCallbacksAndMessages(null)
            retryCount = 0
            sendEmptyMessageDelayed(0, shortDelay)
        }

        fun clearRetryCount() {
            retryCount = -1
        }

        override fun handleMessage(msg: Message?) {
            if (msg?.what == 0) {
                serviceRef.get()?.let { service ->
                    if (!service.isEvsConnected &&
                        AuthDelegate.getAuthResponseFromPref(service) != null
                    ) {
                        service.connectEvs(DeviceUtils.getDeviceId(service))

                        retryCount++
                        val delay = when (retryCount) {
                            in 0..20 -> {
                                shortDelay
                            }
                            in 21..30 -> {
                                middleDelay
                            }
                            else -> {
                                longDelay
                            }
                        }
                        sendEmptyMessageDelayed(0, delay)
                    } else {
                        clearRetryCount()
                    }
                }
            }
        }
    }
}