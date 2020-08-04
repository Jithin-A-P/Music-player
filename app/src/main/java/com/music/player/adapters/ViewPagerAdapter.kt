package com.music.player.adapters

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.music.player.fragments.AlbumFragment
import com.music.player.fragments.ArtistFragment
import com.music.player.fragments.TrackFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity){
    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> AlbumFragment()
            1 -> TrackFragment()
            else -> ArtistFragment()
        }
    }
}