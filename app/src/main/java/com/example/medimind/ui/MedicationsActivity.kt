package com.example.medimind.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medimind.R
import com.example.medimind.databinding.ActivityMedicationsBinding
import com.example.medimind.db.DatabaseHelper
import com.example.medimind.model.MedFilter
import com.example.medimind.model.Medication
import com.example.medimind.util.Extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MedicationsActivity : AppCompatActivity() {
    private lateinit var b: ActivityMedicationsBinding
    private val db by lazy { DatabaseHelper.getInstance(this) }
    private lateinit var adapter: MedicationAdapter
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMedicationsBinding.inflate(layoutInflater)
        setContentView(b.root)
        val left = b.root.paddingLeft
        val top = b.root.paddingTop
        val right = b.root.paddingRight
        val bottom = b.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        adapter = MedicationAdapter(::openMedication)
        b.medications.layoutManager = LinearLayoutManager(this)
        b.medications.adapter = adapter
        b.add.setOnClickListener { startActivity(Intent(this, MedicationFormActivity::class.java)) }
        b.search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = loadMedications()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        b.filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All", "Active", "Finished"))
        b.filter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = loadMedications()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    override fun onResume() {
        super.onResume()
        loadMedications()
    }

    private fun loadMedications() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            b.loading.isVisible = true
            try {
                val items = withContext(Dispatchers.IO) { db.getAllMedications(b.search.text.toString(), selectedFilter()) }
                adapter.submitList(items)
                b.empty.text = when {
                    items.isNotEmpty() -> ""
                    b.search.text.toString().trim().isNotEmpty() -> getString(R.string.empty_search, b.search.text.toString().trim())
                    b.filter.selectedItemPosition != 0 -> getString(R.string.empty_filter)
                    else -> getString(R.string.empty_medications)
                }
                b.empty.isVisible = items.isEmpty()
            } finally {
                b.loading.isVisible = false
            }
        }
    }

    private fun selectedFilter() = when (b.filter.selectedItemPosition) {
        1 -> MedFilter.ACTIVE
        2 -> MedFilter.DONE
        else -> MedFilter.ALL
    }

    private fun openMedication(m: Medication) = startActivity(Intent(this, MedicationDetailActivity::class.java).putExtra(Extras.MEDICATION_ID, m.id))
}
