package org.gnucash.android.ui.search

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.gnucash.android.R
import org.gnucash.android.app.actionBar
import org.gnucash.android.databinding.FragmentSearchFormBinding
import org.gnucash.android.ui.adapter.SpinnerArrayAdapter
import org.gnucash.android.ui.adapter.SpinnerItem
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
        val context: Context = view.context

        val actionBar: ActionBar? = this.actionBar
        actionBar?.setTitle(R.string.title_search_form)

        val binding = binding!!
        val form = viewModel.form

        val comparisons = listOf(
            SpinnerItem(ComparisonType.All, context.getString(R.string.search_criteria_all)),
            SpinnerItem(ComparisonType.Any, context.getString(R.string.search_criteria_any))
        )
        binding.groupingCombo.adapter = SpinnerArrayAdapter(context, comparisons)
        binding.groupingCombo.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.setComparison(comparisons[position].value)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        binding.description.setText(form.description)
        binding.description.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                viewModel.setDescription(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })
        binding.descriptionClear.setOnClickListener { binding.description.text = null }
        binding.notes.setText(form.notes)
        binding.notes.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                viewModel.setNotes(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
        })
        binding.notesClear.setOnClickListener { binding.notes.text = null }
        if (form.dateMin != null) {
            binding.dateStart.text = dateFormatter.print(form.dateMin!!)
        }
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
        binding.dateStartClear.setOnClickListener {
            viewModel.setDateStart(null)
            binding.dateStart.text = null
        }
        if (form.dateMax != null) {
            binding.dateEnd.text = dateFormatter.print(form.dateMax!!)
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
        binding.dateEndClear.setOnClickListener {
            viewModel.setDateEnd(null)
            binding.dateEnd.text = null
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