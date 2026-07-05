package com.david.waterreminder

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

abstract class BaseMenuFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.text_app_version).text =
            getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }
}
