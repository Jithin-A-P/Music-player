package com.music.player.activities

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import com.music.player.R

class PlayerActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        setContentView(R.layout.activity_player)
        val toolbar: Toolbar? = findViewById(R.id.playerToolbar)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
    }
}