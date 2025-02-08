package org.gnucash.android.ui.transaction

import androidx.lifecycle.LifecycleOwner
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

class ScheduledExportAdapter(owner: LifecycleOwner, refreshable: Refreshable) :
    ScheduledAdapter<ScheduledExportViewHolder>(owner, refreshable) {

    override fun createViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ) = ScheduledExportViewHolder(binding, refreshable)

    override suspend fun load() {
        val databaseAdapter = ScheduledActionDbAdapter.getInstance()
        val records = databaseAdapter.getRecords(ScheduledAction.ActionType.BACKUP)
        data.clear()
        data.addAll(records)
    }
}