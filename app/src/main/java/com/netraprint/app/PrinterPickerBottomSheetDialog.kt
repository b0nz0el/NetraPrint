package com.netraprint.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PrinterPickerBottomSheetDialog(
    private val context: Context,
    private val pairedDevices: List<BluetoothDevice>,
    private val currentAddress: String,
    private val includeAllPrintersOption: Boolean = true,
    private val onPrinterSelected: (name: String, address: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context, R.style.Theme_PrinterJTM_BottomSheetDialog)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_printer_picker_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val rvPrinters = view.findViewById<RecyclerView>(R.id.rvPrinters)
        rvPrinters.layoutManager = LinearLayoutManager(context)

        val items = mutableListOf<PrinterItem>()
        if (includeAllPrintersOption) {
            // Option to choose at print time / all printers
            items.add(
                PrinterItem(
                    name = context.getString(R.string.kt_all_printers_choose_when_printing),
                    address = "",
                    isDefaultOption = true
                )
            )
        }

        pairedDevices.forEach { device ->
            val name = try {
                device.name ?: "Thermal Printer"
            } catch (e: SecurityException) {
                "Thermal Printer"
            } catch (_: Exception) {
                "Thermal Printer"
            }
            items.add(PrinterItem(name = name, address = device.address, isDefaultOption = false))
        }

        val adapter = PrinterAdapter(items, currentAddress) { selected ->
            onPrinterSelected(if (selected.isDefaultOption) "" else selected.name, selected.address)
            dialog.dismiss()
        }
        rvPrinters.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnCancelPrinterPicker).setOnClickListener {
            dialog.dismiss()
        }
    }

    fun show() {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }
        try {
            dialog.show()
        } catch (_: Exception) {
            android.util.Log.e("PrinterPickerBottomSheetDialog", "Unable to display printer picker")
        }
    }

    data class PrinterItem(val name: String, val address: String, val isDefaultOption: Boolean)

    private class PrinterAdapter(
        private val list: List<PrinterItem>,
        private val selectedAddress: String,
        private val onItemClick: (PrinterItem) -> Unit
    ) : RecyclerView.Adapter<PrinterAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val tvName: TextView = view.findViewById(R.id.tvDeviceName)
            val tvAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
            val rbSelected: RadioButton = view.findViewById(R.id.rbSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_printer_bottom_sheet, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            holder.tvAddress.text = if (item.isDefaultOption) "Pilih perangkat saat proses cetak berlangsung" else item.address
            val isChecked = (item.address == selectedAddress) || (item.isDefaultOption && selectedAddress.isBlank())
            holder.rbSelected.isChecked = isChecked

            if (isChecked) {
                holder.card.strokeColor = android.graphics.Color.parseColor("#0F766E")
                holder.card.strokeWidth = 4
                holder.card.setCardBackgroundColor(android.graphics.Color.parseColor("#CCFBF1"))
            } else {
                holder.card.strokeColor = android.graphics.Color.parseColor("#CFD8DC")
                holder.card.strokeWidth = 2
                holder.card.setCardBackgroundColor(android.graphics.Color.parseColor("#F8FAFC"))
            }

            holder.card.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
