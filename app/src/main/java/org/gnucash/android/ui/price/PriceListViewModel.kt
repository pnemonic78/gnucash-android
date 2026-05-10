package org.gnucash.android.ui.price

import android.database.Cursor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gnucash.android.db.DatabaseSchema.PriceEntry
import org.gnucash.android.db.adapter.PricesDbAdapter
import org.gnucash.android.model.Price

typealias PriceCallback = (Price) -> Unit

class PriceListViewModel : ViewModel() {
    private val _prices = MutableStateFlow<Cursor?>(null)
    val prices: StateFlow<Cursor?> = _prices

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }

    private fun load() {
        val pricesDbAdapter = PricesDbAdapter.instance
        val cursor = pricesDbAdapter.fetchAllRecords(
            null,
            null,
            PriceEntry.COLUMN_DATE + " DESC"
        )
        _prices.update { cursor }
    }

    fun onAddPriceClick() {
        TODO("Not yet implemented")
    }

    fun onRemoveOldPricesClick() {
        TODO("Not yet implemented")
    }

    fun onPriceClick(price: Price) {
        //TODO("Not yet implemented")
    }
}