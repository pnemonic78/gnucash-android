package org.gnucash.android.ui.search

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentSearchFormBinding
import org.gnucash.android.ui.util.dialog.DatePickerDialogFragment
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

class SearchFormFragment : Fragment() {
    private val viewModel by viewModels<SearchFormViewModel>()
    private var binding: FragmentSearchFormBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.query.collect { form ->
                if (form != null) {
                    showResults(form)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSearchFormBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_search)

        val binding = binding!!

        val form = viewModel.form
        binding.description.setText(form.description)
        binding.notes.setText(form.notes)
        if (form.dateMin != null) {
            binding.dateStart.text = dateFormatter.print(form.dateMin!!)
        }
        if (form.dateMax != null) {
            binding.dateEnd.text = dateFormatter.print(form.dateMax!!)
        }

        binding.description.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                viewModel.setDescription(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        binding.notes.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                viewModel.setNotes(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        binding.dateStart.setOnClickListener {
            val listener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
                viewModel.setDateStart(year, month, dayOfMonth)
                binding.dateStart.text = dateFormatter.print(viewModel.form.dateMin!!)
            }
            val dateMillis = form.dateMin
                ?: (System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS)
            DatePickerDialogFragment.newInstance(listener, dateMillis)
                .show(parentFragmentManager, "date_start_fragment")
        }
        binding.dateEnd.setOnClickListener {
            val listener = DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
                viewModel.setDateEnd(year, month, dayOfMonth)
                binding.dateEnd.text = dateFormatter.print(viewModel.form.dateMax!!)
            }
            val dateMillis = form.dateMax ?: System.currentTimeMillis()
            DatePickerDialogFragment.newInstance(listener, dateMillis)
                .show(parentFragmentManager, "date_end_fragment")
        }
        binding.btnSearch.setOnClickListener { viewModel.onSearchClicked() }
    }

    private fun showResults(form: SearchForm) {
        val args = Bundle()
        args.putForm(form)

        val fragment = SearchResultsFragment()
        fragment.arguments = args

        val fm = parentFragmentManager
        fm.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("search")
            .commit()
    }

    companion object {
        private val dateFormatter: DateTimeFormatter = DateTimeFormat.fullDate()
    }
}