package com.music.player.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.music.player.R
import com.music.player.utils.Audio
import com.music.player.utils.PlaybackStatus
import com.music.player.utils.StorageUtil
import java.io.FileNotFoundException
import java.io.IOException

class MusicPlayerService : Service(), MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
    MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
    AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var activeAudio: Audio
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var transportControls: MediaControllerCompat.TransportControls
    private lateinit var audioList: ArrayList<Audio>
    private var resumePosition = 0
    private var activeAudioIndex = 0
    private var ongoingCall: Boolean = false
    private val iBinder: IBinder = LocalBinder()
    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener

    private val NOTIFICATION_ID = 101
    private val NOTIFICATION_CHANNEL_ID = "com.media.player.NOTIFICATION"
    private val ACTION_PLAY = "com.media.player.ACTION_PLAY"
    private val ACTION_PLAY_NEW = "com.media.player.ACTION_PLAY_NEW"
    private val ACTION_PAUSE = "com.media.player.ACTION_PAUSE"
    private val ACTION_PREVIOUS = "com.media.player.ACTION_PREVIOUS"
    private val ACTION_NEXT = "com.media.player.ACTION_NEXT"
    private val ACTION_STOP = "com.media.player.ACTION_STOP"

    override fun onCreate() {
        super.onCreate()
        callStatelistener()
        regBroadcastReceiver()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            activeAudioIndex = storage.loadAudioIndex()
            if(activeAudioIndex != -1 && activeAudioIndex < audioList.size) {
                activeAudio = audioList[activeAudioIndex]
            } else {
                stopSelf()
            }
        } catch(e: NullPointerException) {
            stopSelf()
        }

        if(!gainAudioFocus()) {
            stopSelf()
        }

        if(mediaSessionManager == null) {
            try {
                initMediaSession()
                initMediaPlayer()
            } catch(e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(PlaybackStatus.PLAYING)
        }
        handleIncomingActions(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return iBinder
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPrepared(p0: MediaPlayer?) {
        playMedia()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCompletion(p0: MediaPlayer?) {
        skipToNext()
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        TODO("Not yet implemented")
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onSeekComplete(p0: MediaPlayer?) {
        resumeMedia()
    }

    override fun onInfo(mediaPlayer: MediaPlayer?, p1: Int, p2: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(p0: MediaPlayer?, p1: Int) {
        TODO("Not yet implemented")
    }

    override fun onAudioFocusChange(focusState: Int) {
        when(focusState) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if(mediaPlayer == null)
                    initMediaPlayer()
                else if(!mediaPlayer!!.isPlaying)
                    mediaPlayer!!.start()
                mediaPlayer!!.setVolume(1.0f, 1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if(mediaPlayer!!.isPlaying)
                    mediaPlayer!!.stop()
                mediaPlayer!!.release()
                mediaPlayer = null
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if(mediaPlayer!!.isPlaying)
                    mediaPlayer!!.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if(mediaPlayer!!.isPlaying)
                    mediaPlayer!!.setVolume(0.2f, 0.2f)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun initMediaPlayer() {
        mediaPlayer = MediaPlayer()

        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)

        mediaPlayer!!.reset()

        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        try {
            mediaPlayer!!.setDataSource(activeAudio.data)
        } catch(e: IOException) {
            e.printStackTrace()
            stopSelf()
        }
        mediaPlayer!!.prepareAsync()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(mediaPlayer != null) {
            stopMedia()
            mediaPlayer!!.release()
        }
        removeAudioFocus()
        if(phoneStateListener != null)
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)

        removeNotification()
        unregisterReceiver(broadcastReceiver)
        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun initMediaSession() {
        if(mediaSessionManager != null)
            return
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        transportControls = mediaSession!!.controller.transportControls
        mediaSession!!.isActive = true
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        updateMetaData()

        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                skipToPrev()
            }

            override fun onStop() {
                super.onStop()
                removeNotification()
            }
        })
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateMetaData() {

        val albumArtUri: Uri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            activeAudio.albumId
        )
        var albumArt: Bitmap? = null

        try {
            albumArt =
                MediaStore.Images.Media.getBitmap(applicationContext.contentResolver, albumArtUri)
        } catch(e: FileNotFoundException) {
            e.printStackTrace()
        } catch(e: IOException) {
            e.printStackTrace()
        }
        mediaSession!!.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio.album)
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio.title)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun playMedia() {
        if(!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    fun stopMedia() {
        if(mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun pauseMedia() {
        if(mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer!!.currentPosition
            buildNotification(PlaybackStatus.PAUSED)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun resumeMedia() {
        if(!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
            buildNotification(PlaybackStatus.PLAYING)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun skipToNext() {
        if(activeAudioIndex == audioList.size - 1) {
            activeAudioIndex = 0
            activeAudio = audioList[activeAudioIndex]
        } else {
            activeAudio = audioList[++activeAudioIndex]
        }
        StorageUtil(applicationContext).storeAudioIndex(activeAudioIndex)
        stopMedia()
        mediaPlayer!!.reset()
        buildNotification(PlaybackStatus.PLAYING)
        initMediaPlayer()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun skipToPrev() {
        if(activeAudioIndex == 0) {
            activeAudioIndex = audioList.size - 1
            activeAudio = audioList[activeAudioIndex]
        } else {
            activeAudio = audioList[--activeAudioIndex]
        }
        StorageUtil(applicationContext).storeAudioIndex(activeAudioIndex)
        stopMedia()
        mediaPlayer!!.reset()
        buildNotification(PlaybackStatus.PLAYING)
        initMediaPlayer()
    }

    @Suppress("DEPRECATION")
    fun gainAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        if(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    fun removeAudioFocus(): Boolean {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onReceive(context: Context?, intent: Intent?) {
            if(intent != null && intent.action != null) {
                handleActions(intent)
            }
        }
    }

    fun regBroadcastReceiver() {
        val intent: IntentFilter = IntentFilter()
        intent.addAction(ACTION_PLAY_NEW)
        intent.addAction(ACTION_PLAY)
        intent.addAction(ACTION_PAUSE)
        intent.addAction(ACTION_NEXT)
        intent.addAction(ACTION_PREVIOUS)
        intent.addAction(ACTION_STOP)
        intent.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intent)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun handleActions(intent: Intent) {
        var action: String? = intent.action
        when(action) {
            ACTION_PLAY_NEW -> {
                activeAudioIndex = StorageUtil(applicationContext).loadAudioIndex()
                if(activeAudioIndex != -1 && activeAudioIndex < audioList.size) {
                    activeAudio = audioList[activeAudioIndex]
                    Log.i("AudioInfo", "Audio played inside service")
                } else {
                    stopSelf()
                }
                stopMedia()
                mediaPlayer!!.reset()
                initMediaPlayer()
                updateMetaData()
                buildNotification(PlaybackStatus.PLAYING)
            }
            ACTION_PLAY -> {
                transportControls.play()
            }
            ACTION_PAUSE -> {
                transportControls.pause()
            }
            ACTION_NEXT -> {
                transportControls.skipToNext()
            }
            ACTION_PREVIOUS -> {
                transportControls.skipToPrevious()
            }
            ACTION_STOP -> {
                transportControls.stop()
            }
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                pauseMedia()
                buildNotification(PlaybackStatus.PAUSED)
            }
        }
    }

    fun callStatelistener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {

                when(state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if(mediaPlayer != null) {
                            pauseMedia()
                            ongoingCall = true
                        }
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        if(mediaPlayer != null) {
                            if(ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {

                    }
                }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Suppress("DEPRECATION")
    fun buildNotification(playbackStatus: PlaybackStatus) {
        var notificationAction: Int = R.drawable.ic_media_pause_notification
        var playPauseAction: PendingIntent? = null
        when(playbackStatus) {
            PlaybackStatus.PLAYING -> {
                notificationAction = R.drawable.ic_media_pause_notification
                playPauseAction = playbackAction(1)
            }
            PlaybackStatus.PAUSED -> {
                notificationAction = R.drawable.ic_media_play_notification
                playPauseAction = playbackAction(0)
            }
        }

        var largeIcon: Bitmap? = null
        var albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            activeAudio.albumId
        )
        try {
            largeIcon =
                MediaStore.Images.Media.getBitmap(applicationContext.contentResolver, albumArtUri)
        } catch(e: FileNotFoundException) {
            e.printStackTrace()
            largeIcon = BitmapFactory.decodeResource(
                applicationContext.resources,
                android.R.drawable.ic_media_play
            )
        } catch(e: IOException) {
            e.printStackTrace()
        }

        var notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(applicationContext)
                .setShowWhen(false)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(activeAudio.title)
                .setContentText(activeAudio.album)
                .setContentInfo(activeAudio.artist)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setOngoing(playbackStatus == PlaybackStatus.PLAYING)
                .addAction(R.drawable.ic_media_previous_notification, "previous", playbackAction(3))
                .addAction(notificationAction, "playPause", playPauseAction)
                .addAction(R.drawable.ic_media_next_notification, "next", playbackAction(2))

        var notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = "Music Playback Notification"
            val descriptionText = "This provides controls over media playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(mChannel)
        }

        with(NotificationManagerCompat.from(this)) {
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    fun removeNotification() {
        var notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
        }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MusicPlayerService::class.java)
        when(actionNumber) {
            0 -> {
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                playbackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                playbackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
        }
        return null
    }

    private fun handleIncomingActions(playbackAction: Intent?) {
        if(playbackAction == null || playbackAction.action == null) return
        val actionString = playbackAction.action
        when {
            actionString.equals(ACTION_PLAY, ignoreCase = true) -> {
                transportControls.play()
            }
            actionString.equals(ACTION_PAUSE, ignoreCase = true) -> {
                transportControls.pause()
            }
            actionString.equals(ACTION_NEXT, ignoreCase = true) -> {
                transportControls.skipToNext()
            }
            actionString.equals(ACTION_PREVIOUS, ignoreCase = true) -> {
                transportControls.skipToPrevious()
            }
            actionString.equals(ACTION_STOP, ignoreCase = true) -> {
                transportControls.stop()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlayerService {
            return this@MusicPlayerService
        }
    }
}


