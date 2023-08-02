package ru.bruimafia.pixabaylite.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import ru.bruimafia.pixabaylite.main.ImagesFragment
import ru.bruimafia.pixabaylite.main.VideosFragment

class TabAdapter(fa: FragmentActivity): FragmentStateAdapter(fa) {
    override fun getItemCount(): Int {
        return 2
    }
    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> ImagesFragment()
            1 -> VideosFragment()
            else -> ImagesFragment()
        }
    }

}