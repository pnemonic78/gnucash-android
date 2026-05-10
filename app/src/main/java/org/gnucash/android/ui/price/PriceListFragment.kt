package org.gnucash.android.ui.price

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.MenuFragment
import org.gnucash.android.databinding.FragmentPricesListBinding

class PriceListFragment : MenuFragment() {

    private val viewModel by viewModels<PriceListViewModel>()
    private var binding: FragmentPricesListBinding? = null
    private val pricesAdapter = PriceCursorAdapter(onPriceClick = { viewModel.onPriceClick(it) })

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        //TODO inflater.inflate(R.menu.prices_actions, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                viewModel.onAddPriceClick()
                true
            }

            R.id.menu_delete -> {
                viewModel.onRemoveOldPricesClick()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentPricesListBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = this.binding!!

        binding.list.apply {
            setHasFixedSize(true)
            setLayoutManager(LinearLayoutManager(context))
            emptyView = binding.empty
            adapter = pricesAdapter
        }

        binding.fabAdd.setOnClickListener {
            viewModel.onAddPriceClick()
        }
        binding.fabAdd.isVisible = false//TODO

        lifecycleScope.launch {
            viewModel.prices.collect { cursor ->
                pricesAdapter.changeCursor(cursor)
            }
        }
    }

    override fun onDestroy() {
        pricesAdapter.changeCursor(null)
        super.onDestroy()
    }
}