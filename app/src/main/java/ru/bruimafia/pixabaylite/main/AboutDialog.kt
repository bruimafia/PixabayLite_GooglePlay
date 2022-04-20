package ru.bruimafia.pixabaylite.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import androidx.fragment.app.DialogFragment
import ru.bruimafia.pixabaylite.App
import ru.bruimafia.pixabaylite.BuildConfig
import ru.bruimafia.pixabaylite.R
import ru.bruimafia.pixabaylite.databinding.DialogAboutBinding
import ru.bruimafia.pixabaylite.util.SharedPreferencesManager


class AboutDialog : DialogFragment() {

    val VK_ID = "31223368"

    lateinit var bind: DialogAboutBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bind = DataBindingUtil.inflate(inflater, R.layout.dialog_about, container, false)
        bind.view = this

        val version = if (SharedPreferencesManager.isFullVersion) "pro" else ""
        bind.tvAppVersion.text =
            String.format(getString(R.string.app_version), BuildConfig.VERSION_NAME, version, BuildConfig.VERSION_CODE)

        return bind.root
    }

    fun onVkLink() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(String.format("linkedin://profile/%s", VK_ID))))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.com/id$VK_ID")))
        }
    }

    fun onGoogleplayLink() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)
                )
            )
        }
    }

    fun onPrivacyPolicyLink() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_link))))
    }

}