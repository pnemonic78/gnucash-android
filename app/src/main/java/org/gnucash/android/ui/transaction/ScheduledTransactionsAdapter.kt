package org.gnucash.android.ui.transaction

import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

/**
 * Extends a simple cursor adapter to bind transaction attributes to views
 */
internal class ScheduledTransactionsAdapter(
    scheduledActionDbAdapter: ScheduledActionDbAdapter,
    refreshable: Refreshable
) : ScheduledAdapter<ScheduledTransactionsViewHolder>(scheduledActionDbAdapter, refreshable) {

    override suspend fun loadData(): List<ScheduledAction> {
        return scheduledActionDbAdapter.getRecords(ScheduledAction.ActionType.TRANSACTION)
    }

    override fun createViewHolder(
        scheduledActionDbAdapter: ScheduledActionDbAdapter,
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) = ScheduledTransactionsViewHolder(scheduledActionDbAdapter, binding, refreshable)
}