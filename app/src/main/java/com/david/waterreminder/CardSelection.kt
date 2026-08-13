package com.david.waterreminder

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * Styles one card of a two-card "pick one" pair: brand-blue stroke + checkmark when selected,
 * theme-default outline otherwise. Shared by ProfileSetupFragment and ReminderSetupFragment.
 */
fun Fragment.applyCardSelectionStyle(card: MaterialCardView, checkIcon: View, selected: Boolean) {
    card.isChecked = selected
    checkIcon.visibility = if (selected) View.VISIBLE else View.GONE
    card.strokeColor = if (selected) {
        ContextCompat.getColor(requireContext(), R.color.brand_blue)
    } else {
        MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
    }
    card.strokeWidth = ((if (selected) 2 else 1) * resources.displayMetrics.density).toInt()
}
