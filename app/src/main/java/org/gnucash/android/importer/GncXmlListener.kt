package org.gnucash.android.importer

import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction

interface GncXmlListener {
    fun onAccountCount(count: Long)
    fun onAccount(account: Account)
    fun onBookCount(count: Long)
    fun onBook(book: Book)
    fun onBook(name: String)
    fun onBudgetCount(count: Long)
    fun onBudget(budget: Budget)
    fun onCommodityCount(count: Long)
    fun onCommodity(commodity: Commodity)
    fun onPriceCount(count: Long)
    fun onPrice(price: Price)
    fun onScheduleCount(count: Long)
    fun onSchedule(scheduledAction: ScheduledAction)
    fun onTransactionCount(count: Long)
    fun onTransaction(transaction: Transaction)
}