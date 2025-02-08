package org.gnucash.android.ui.transaction

import androidx.lifecycle.LifecycleOwner
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

class ScheduledTransactionsAdapter(owner: LifecycleOwner, refreshable: Refreshable) :
    ScheduledAdapter<ScheduledTransactionsViewHolder>(owner, refreshable) {

    override fun createViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) = ScheduledTransactionsViewHolder(binding, refreshable)

    override suspend fun load() {
        val databaseAdapter = ScheduledActionDbAdapter.getInstance()
        val records = databaseAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
        data.clear()
        data.addAll(records)
    }
}