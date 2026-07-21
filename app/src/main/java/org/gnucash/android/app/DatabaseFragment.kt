package org.gnucash.android.app

import android.os.Bundle
import org.gnucash.android.db.DatabaseHelper

open class DatabaseFragment : MenuFragment() {
    protected lateinit var dbHelper: DatabaseHelper
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookUID = GnuCashApplication.activeBookUID
        dbHelper = DatabaseHelper(requireContext(), bookUID)
    }

    override fun onDestroy() {
        dbHelper.close()
        super.onDestroy()
    }
}