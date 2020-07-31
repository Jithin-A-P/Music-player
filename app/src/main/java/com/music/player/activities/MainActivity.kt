package com.music.player.activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.music.player.R
import com.music.player.adapters.ViewPagerAdapter
import com.music.player.services.MusicPlayerService
import com.music.player.utils.Audio
import com.music.player.utils.StorageUtil


class MainActivity : AppCompatActivity() {

    val ACTION_PLAY = "com.media.player.ACTION_PLAY"
    val ACTION_PLAY_NEW = "com.media.player.ACTION_PLAY_NEW"
    val ACTION_PAUSE = "com.media.player.ACTION_PAUSE"
    val ACTION_PREVIOUS = "com.media.player.ACTION_PREVIOUS"
    val ACTION_NEXT = "com.media.player.ACTION_NEXT"
    val ACTION_STOP = "com.media.player.ACTION_STOP"

    var serviceBound: Boolean = false
    var service: MusicPlayerService? = null


    var audioList = ArrayList<Audio>()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            loadAudio()
            Log.i("AudioLoaded", "Permission granted and audio loaded")
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_PHONE_STATE), 0)
        }

        val viewPager: ViewPager2 = findViewById(R.id.pager)
        viewPager.adapter = ViewPagerAdapter(this)
/*
        val btn = findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            playAudio(30)
            Log.i("AudioInfo", "Audio Played")
        }*/
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean("ServiceState", serviceBound)
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        serviceBound = savedInstanceState.getBoolean("ServiceState")
    }


    override fun onDestroy() {
        super.onDestroy()
        if(serviceBound) {
            unbindService(serviceConnection)
            service!!.stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    fun loadAudio() {
        var uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        var selection: String? = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        var sortOrder: String? = MediaStore.Audio.Media.TITLE + " ASC"
        var cursor: Cursor? = contentResolver.query(uri, null, selection, null, sortOrder)
        if(cursor != null && cursor.count > 0) {
            while(cursor.moveToNext()){
                var data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                var title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                var album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                var artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                var albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                audioList.add(Audio(data, title, album, artist, albumId))
            }
        }
        cursor!!.close()
    }

    fun playAudio(audioIndex: Int) {
        if(!serviceBound) {
            var storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioIndex)
            storage.storeAudio(audioList)

            Intent(this, MusicPlayerService::class.java).also { intent ->
                startService(intent)
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }

        } else {
            var storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioIndex)

            var broadcastIntent = Intent(ACTION_PLAY_NEW)
            sendBroadcast(broadcastIntent)
        }
    }

    private val serviceConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }

        override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
            var binder: MusicPlayerService.LocalBinder = iBinder as MusicPlayerService.LocalBinder
            service = binder.getService();
            serviceBound = true
            Toast.makeText(applicationContext, "Service bound", Toast.LENGTH_SHORT).show()
        }
    }

}