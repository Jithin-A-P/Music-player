package com.music.player.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.music.player.fragments.AlbumFragment
import com.music.player.fragments.ArtistFragment
import com.music.player.fragments.TrackFragment

class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment){
    override fun getItemCount(): Int {
        return 3
    }

    override fun createFragment(position: Int): Fragment {
        when(position) {
            0 -> return AlbumFragment()
            1 -> return TrackFragment()
            2 -> return ArtistFragment()
        }
        return AlbumFragment()
    }

}