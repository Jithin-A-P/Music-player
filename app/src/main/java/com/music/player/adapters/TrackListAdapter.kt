package com.music.player.adapters

import android.content.ContentUris
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.music.player.R
import com.music.player.activities.MainActivity
import com.music.player.utils.Audio
import com.squareup.picasso.Picasso

class TrackListAdapter(var audioList: ArrayList<Audio>, var activity: MainActivity): RecyclerView.Adapter<TrackListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.track_list_item, null))
    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.track.text = audioList[position].title
        holder.album.text = audioList[position].album
        holder.artist.text = audioList[position].artist

        val albumArtUri: Uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), audioList[position].albumId)
        Picasso.get().load(albumArtUri).into(holder.alubmArt)

        holder.listItem.setOnClickListener {
            activity.playAudio(position)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listItem: LinearLayout = itemView.findViewById(R.id.listItemTL)
        val alubmArt: ImageView = itemView.findViewById(R.id.albumArtTL)
        val track: TextView = itemView.findViewById(R.id.trackNameTL)
        val album: TextView = itemView.findViewById(R.id.albumNameTL)
        val artist: TextView = itemView.findViewById(R.id.artistNameTL)
    }
}