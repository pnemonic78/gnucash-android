package org.gnucash.android.ui.settings

import android.content.Context
import android.os.Bundle
import org.gnucash.android.app.GnuCashApplication.Companion.activeBookUID
import org.gnucash.android.db.DatabaseHelper

abstract class BookPreferencesFragment : GnuPreferenceFragment() {
    private var dbHelper: DatabaseHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = requireContext()
        val bookUID = activeBookUID
        val dbHelper = DatabaseHelper(context, bookUID)
        this.dbHelper = dbHelper
        initDatabase(dbHelper)
    }

    abstract fun initDatabase(dbHelper: DatabaseHelper)

    override fun onDestroy() {
        dbHelper?.close()
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.setSharedPreferencesName(activeBookUID)
    }
}