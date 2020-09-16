package com.music.player.activities

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import com.music.player.R
import com.music.player.utils.Audio
import com.music.player.utils.StorageUtil
import com.squareup.picasso.Picasso

class PlayerActivity: AppCompatActivity() {

    val storageUtil = StorageUtil(applicationContext)
    var activeAudioIndex = -1
    lateinit var audioList: List<Audio>
    lateinit var activeAudio: Audio

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.activity_player)
        val toolbar: Toolbar? = findViewById(R.id.playerToolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val albumArt = findViewById<ImageView>(R.id.playerAlbumArt)
        audioList = storageUtil.loadAudio()
        activeAudioIndex = storageUtil.loadAudioIndex()
        activeAudio = audioList[activeAudioIndex]

        val albumArtUri: Uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), activeAudio.albumId)
        Picasso.get().load(albumArtUri).into(albumArt)
    }
}