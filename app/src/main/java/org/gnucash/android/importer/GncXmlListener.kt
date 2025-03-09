package org.gnucash.android.importer

import org.gnucash.android.model.Account
import org.gnucash.android.model.Book
import org.gnucash.android.model.Budget
import org.gnucash.android.model.Commodity
import org.gnucash.android.model.Price
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.model.Transaction

interface GncXmlListener {
    fun onImportAccount(account: Account)
    fun onImportBook(book: Book)
    fun onImportBook(name: String)
    fun onImportBudget(budget: Budget)
    fun onImportCommodity(commodity: Commodity)
    fun onImportPrice(price: Price)
    fun onImportSchedule(scheduledAction: ScheduledAction)
    fun onImportTransaction(transaction: Transaction)
}