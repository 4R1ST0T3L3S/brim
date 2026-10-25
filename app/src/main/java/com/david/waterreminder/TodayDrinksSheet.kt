package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import com.david.waterreminder.databinding.ItemTodayDrinkBinding
import com.david.waterreminder.databinding.SheetTodayDrinksBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Date

/**
 * Today's drinks with their times, as a sheet over the home screen — opened from its "Last drink"
 * line. One row per drink, oldest first, with how many there were beside the heading.
 *
 * A plain dialog like the home screen's other popups, so the fragment owns it: [dismiss] it with
 * the view. [drinkLog] is [transientPrefs]; [unit] is read on every render, as the home screen does.
 */
class TodayDrinksSheet(
    private val context: Context,
    private val drinkLog: SharedPreferences,
    private val unit: () -> String,
) {
    private val binding = SheetTodayDrinksBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context).apply { setContentView(binding.root) }

    fun show() {
        render()
        dialog.show()
    }

    /** For a drink logged elsewhere — a notification button, the widget — while the sheet is up. */
    fun refreshIfShowing() {
        if (dialog.isShowing) render()
    }

    fun dismiss() = dialog.dismiss()

    private fun render() {
        drinkLog.clearStaleDrinkLog()
        val drinks = drinkLog.todaysDrinks()
        binding.containerToday.removeAllViews()
        binding.textTodayEmpty.visibility = if (drinks.isEmpty()) View.VISIBLE else View.GONE
        binding.textTodayCount.text =
            if (drinks.isEmpty()) ""
            else context.resources.getQuantityString(R.plurals.today_drinks_count, drinks.size, drinks.size)

        val timeFormat = DateFormat.getTimeFormat(context)
        val inflater = LayoutInflater.from(context)
        drinks.forEach { drink ->
            val row = ItemTodayDrinkBinding.inflate(inflater, binding.containerToday, true)
            val time = timeFormat.format(Date(drink.atMillis))
            val amount = drink.ml.fmt(unit())
            row.iconDrink.setImageResource(FirstFragment.AMOUNT_ICONS[drink.ml] ?: R.drawable.ic_water_drop)
            row.textDrinkTime.text = time
            row.textDrinkAmount.text = amount
            row.root.contentDescription = context.getString(R.string.today_drinks_row_description, time, amount)
        }
    }
}
