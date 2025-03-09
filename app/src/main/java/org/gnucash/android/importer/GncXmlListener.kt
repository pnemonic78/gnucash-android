package org.gnucash.android.importer

import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction

interface GncXmlListener {
    fun onImportAccountCount(count: Long)
    fun onImportAccount(account: Account)
    fun onImportBookCount(count: Long)
    fun onImportBook(book: Book)
    fun onImportBook(name: String)
    fun onImportBudgetCount(count: Long)
    fun onImportBudget(budget: Budget)
    fun onImportCommodityCount(count: Long)
    fun onImportCommodity(commodity: Commodity)
    fun onImportPriceCount(count: Long)
    fun onImportPrice(price: Price)
    fun onImportScheduleCount(count: Long)
    fun onImportSchedule(scheduledAction: ScheduledAction)
    fun onImportTransactionCount(count: Long)
    fun onImportTransaction(transaction: Transaction)
}