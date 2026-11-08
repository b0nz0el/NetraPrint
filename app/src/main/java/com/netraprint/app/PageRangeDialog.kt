package com.netraprint.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PageRangeDialog(
    context: Context,
    private val totalPages: Int,
    private val onPagesSelected: (List<Int>) -> Unit
) {
    private val dialog = BottomSheetDialog(context)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_page_range, null)
        dialog.setContentView(view)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvTotalPagesSubtitle)
        tvSubtitle.text = context.getString(R.string.kt_page_range_total, totalPages)

        val rg = view.findViewById<RadioGroup>(R.id.rgPageSelection)
        val rbAll = view.findViewById<RadioButton>(R.id.rbAllPages)
        val rbCustom = view.findViewById<RadioButton>(R.id.rbCustomRange)

        val layoutCustom = view.findViewById<TextInputLayout>(R.id.layoutCustomRange)
        val etCustom = view.findViewById<TextInputEditText>(R.id.etCustomRange)
        val tvError = view.findViewById<TextView>(R.id.tvRangeError)

        rbAll.text = context.getString(R.string.kt_page_range_all, totalPages)

        rg.setOnCheckedChangeListener { _, checkedId ->
            tvError.visibility = View.GONE
            if (checkedId == R.id.rbCustomRange) {
                layoutCustom.visibility = View.VISIBLE
                etCustom.requestFocus()
            } else {
                layoutCustom.visibility = View.GONE
            }
        }

        view.findViewById<MaterialButton>(R.id.btnCancelPageRange).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnPrintSelectedPages).setOnClickListener {
            tvError.visibility = View.GONE
            val selectedPages = when (rg.checkedRadioButtonId) {
                R.id.rbAllPages -> (0 until totalPages).toList()
                R.id.rbOddPages -> (0 until totalPages).filter { it % 2 == 0 } // index 0 is page 1, 2 is page 3
                R.id.rbEvenPages -> (0 until totalPages).filter { it % 2 != 0 } // index 1 is page 2, 3 is page 4
                R.id.rbCustomRange -> {
                    val input = etCustom.text.toString().trim()
                    try {
                        PageRangeHelper.parsePageRange(input, totalPages)
                    } catch (_: Exception) {
                        tvError.text = context.getString(R.string.kt_page_range_invalid)
                        tvError.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                }
                else -> (0 until totalPages).toList()
            }

            if (selectedPages.isEmpty()) {
                tvError.setText(R.string.kt_page_range_empty)
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            dialog.dismiss()
            onPagesSelected(selectedPages)
        }
    }

    fun show() {
        dialog.show()
    }
}
