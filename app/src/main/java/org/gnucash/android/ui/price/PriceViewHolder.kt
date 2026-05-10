package org.gnucash.android.ui.price

import android.content.Context
import android.database.Cursor
import android.text.format.DateUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.gnucash.android.databinding.CardviewPriceBinding
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.export.xml.GncXmlHelper.formatFormula
import org.gnucash.android.export.xml.GncXmlHelper.formatNumeric
import org.gnucash.android.util.formatMediumDate

class PriceViewHolder(
    binding: CardviewPriceBinding,
    private val useAbsoluteDate: Boolean,
    private val onPriceClick: PriceCallback
) : RecyclerView.ViewHolder(binding.root) {
    private val primaryText: TextView = binding.listItem2Lines.primaryText
    private val secondaryText: TextView = binding.listItem2Lines.secondaryText
    private val dateText: TextView = binding.date
    private val amountText: TextView = binding.amount
    private val optionsMenu: ImageView = binding.optionsMenu

    private val pricesDbAdapter = PricesDbAdapter.instance

    fun bind(cursor: Cursor) {
        val context = itemView.context
        val price = pricesDbAdapter.buildModelInstance(cursor)

        itemView.setOnClickListener {
            onPriceClick(price)
        }

        primaryText.text = price.commodity.formatListItem()
        secondaryText.text = price.currency.formatListItem()
        dateText.text = if (useAbsoluteDate) {
            formatMediumDate(price.date)
        } else {
            formatPrettyDate(context, price.date)
        }
        amountText.text = formatFormula(price.toBigDecimal())
        optionsMenu.isVisible = false//TODO
    }

    private fun formatPrettyDate(context: Context, time: Long): String {
        return DateUtils.getRelativeDateTimeString(
            context,
            time,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            0
        ).toString()
    }
}