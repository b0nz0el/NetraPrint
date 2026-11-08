package com.netraprint.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class PrintOptionsBottomSheetDialog(
    private val context: Context,
    private val documentTitle: String,
    private val printerName: String,
    private val connectionType: String,
    private val initialPrintWidth: Int = 384,
    private val initialAutoCut: Boolean = true,
    private val onChangePrinterClicked: () -> Unit,
    private val onConfirmPrint: (copies: Int, printWidth: Int, autoCut: Boolean) -> Unit
) {
    private val dialog = BottomSheetDialog(context, R.style.Theme_NetraPrint_BottomSheetDialog)
    private var currentCopies = 1

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_print_options_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val tvDocType = view.findViewById<TextView>(R.id.tvPrintConfirmDocType)
        tvDocType.text = context.getString(R.string.print_dialog_doc_type_label, documentTitle)

        val tvPrinterName = view.findViewById<TextView>(R.id.tvTargetPrinterName)
        val tvPrinterStatus = view.findViewById<TextView>(R.id.tvTargetPrinterStatus)

        if (printerName.isNotBlank()) {
            tvPrinterName.text = "$printerName ($connectionType)"
            tvPrinterStatus.text = "● " + context.getString(R.string.print_dialog_ready_status)
            tvPrinterStatus.setTextColor(0xFF10B981.toInt())
        } else {
            tvPrinterName.text = context.getString(R.string.kt_all_printers_choose_when_printing)
            tvPrinterStatus.text = "● $connectionType"
            tvPrinterStatus.setTextColor(0xFF0F766E.toInt())
        }

        val btnChangePrinter = view.findViewById<MaterialButton>(R.id.btnChangeTargetPrinter)
        btnChangePrinter.setOnClickListener {
            dialog.dismiss()
            onChangePrinterClicked()
        }

        // Stepper Salinan / Copies
        val tvCopiesCount = view.findViewById<TextView>(R.id.tvCopiesCount)
        val btnCopiesMinus = view.findViewById<ImageButton>(R.id.btnCopiesMinus)
        val btnCopiesPlus = view.findViewById<ImageButton>(R.id.btnCopiesPlus)
        val btnPrintConfirm = view.findViewById<MaterialButton>(R.id.btnPrintConfirmAction)

        fun updateCopiesUi() {
            tvCopiesCount.text = currentCopies.toString()
            if (currentCopies > 1) {
                btnPrintConfirm.text = context.getString(R.string.print_dialog_btn_print_copies, currentCopies)
            } else {
                btnPrintConfirm.text = context.getString(R.string.print_dialog_btn_print_now)
            }
        }

        btnCopiesMinus.setOnClickListener {
            if (currentCopies > 1) {
                currentCopies--
                updateCopiesUi()
            }
        }

        btnCopiesPlus.setOnClickListener {
            if (currentCopies < 10) {
                currentCopies++
                updateCopiesUi()
            }
        }

        // Paper Width Radio
        val rb58 = view.findViewById<RadioButton>(R.id.rbPaper58mm)
        val rb80 = view.findViewById<RadioButton>(R.id.rbPaper80mm)
        if (initialPrintWidth >= 576) {
            rb80.isChecked = true
        } else {
            rb58.isChecked = true
        }

        // Auto Cut
        val switchAutoCut = view.findViewById<SwitchMaterial>(R.id.switchConfirmAutoCut)
        switchAutoCut.isChecked = initialAutoCut

        // Confirm Print
        btnPrintConfirm.setOnClickListener {
            val selectedWidth = if (rb80.isChecked) 576 else 384
            val autoCut = switchAutoCut.isChecked
            dialog.dismiss()
            onConfirmPrint(currentCopies, selectedWidth, autoCut)
        }

        // Cancel
        view.findViewById<MaterialButton>(R.id.btnPrintCancelAction).setOnClickListener {
            dialog.dismiss()
        }
    }

    fun show() {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }
        try {
            dialog.show()
        } catch (_: Exception) {
            android.util.Log.e("PrintOptionsBottomSheet", "Unable to show print options bottom sheet")
        }
    }
}
