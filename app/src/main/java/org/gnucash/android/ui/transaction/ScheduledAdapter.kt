package org.gnucash.android.ui.transaction

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.gnucash.android.databinding.ListItemScheduledTrxnBinding
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.Refreshable

/**
 * Extends a simple adapter to bind scheduled attributes to views
 */
abstract class ScheduledAdapter<VH : ScheduledViewHolder>(
    private val owner: LifecycleOwner,
    protected val refreshable: Refreshable
) : RecyclerView.Adapter<VH>() {

    protected val data: MutableList<ScheduledAction> = ArrayList()

    init {
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (position >= data.size) {
            return
        }
        val item = data[position]
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemScheduledTrxnBinding.inflate(inflater, parent, false)
        return createViewHolder(binding, refreshable)
    }

    protected abstract fun createViewHolder(
        binding: ListItemScheduledTrxnBinding,
        refreshable: Refreshable
    ): VH

    override fun getItemCount(): Int = data.size

    override fun getItemId(position: Int): Long {
        if (position >= data.size) {
            return 0
        }
        val item = data[position]
        return item.id
    }

    abstract suspend fun load()

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("NotifyDataSetChanged")
    fun loadAsync() {
        val scope = owner.lifecycleScope
        scope.launch(Dispatchers.IO) {
            load()
            scope.launch(Dispatchers.Main) {
                notifyDataSetChanged()
            }
        }
    }
}
