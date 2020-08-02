package com.music.player.fragments

import android.os.Bundle
import android.os.RecoverySystem
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.music.player.R
import com.music.player.activities.MainActivity
import com.music.player.adapters.TrackListAdapter


class TrackFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_track, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.trackRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val listViewAdapter = TrackListAdapter((activity as MainActivity).audioList, activity as MainActivity)
        recyclerView.adapter = listViewAdapter

        return view
    }
}