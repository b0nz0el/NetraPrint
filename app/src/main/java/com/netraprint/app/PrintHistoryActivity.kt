package com.netraprint.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintHistoryActivity : AppCompatActivity() {

    private lateinit var printHistoryManager: PrintHistoryManager
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var adapter: PrintHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_print_history)

        val appContainer = (application as PrinterApplication).appContainer
        printHistoryManager = appContainer.printHistoryManager

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarHistory)
        toolbar.setNavigationOnClickListener { finish() }

        // Tambahkan tombol Hapus Semua di menu toolbar
        toolbar.menu.add(0, 1, 0, getString(R.string.riwayat_cetak_hapus_semua)).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                showClearHistoryConfirmDialog()
                true
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        rvHistory = findViewById(R.id.rvPrintHistory)
        layoutEmpty = findViewById(R.id.layoutEmptyHistory)

        rvHistory.layoutManager = LinearLayoutManager(this)
        adapter = PrintHistoryAdapter(
            items = printHistoryManager.getAll().toMutableList(),
            onDeleteItem = { item ->
                printHistoryManager.deleteItem(item.id)
                refreshList()
            }
        )
        rvHistory.adapter = adapter

        updateEmptyState()
    }

    private fun showClearHistoryConfirmDialog() {
        if (printHistoryManager.getCount() == 0) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.riwayat_cetak_dialog_hapus_title)
            .setMessage(R.string.riwayat_cetak_dialog_hapus_msg)
            .setPositiveButton(R.string.riwayat_cetak_hapus_semua) { _, _ ->
                printHistoryManager.clearAll()
                refreshList()
            }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    private fun refreshList() {
        val updated = printHistoryManager.getAll()
        adapter.updateData(updated)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = printHistoryManager.getCount() == 0
        layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private class PrintHistoryAdapter(
        private var items: MutableList<PrintHistoryItem>,
        private val onDeleteItem: (PrintHistoryItem) -> Unit
    ) : RecyclerView.Adapter<PrintHistoryAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun updateData(newItems: List<PrintHistoryItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_print_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context

            holder.tvTitle.text = item.title
            holder.tvTime.text = dateFormat.format(Date(item.timestamp))

            val targetDetails = buildString {
                append(item.targetName)
                if (!item.targetInfo.isNullOrBlank()) {
                    append(" (").append(item.targetInfo).append(")")
                }
                append(" • ")
                append(context.getString(R.string.riwayat_cetak_salinan, item.copies))
                append(" • ")
                append(item.printType)
            }
            holder.tvDetails.text = targetDetails

            when (item.status) {
                PrintHistoryItem.Status.SUCCESS -> {
                    holder.tvStatus.text = context.getString(R.string.riwayat_cetak_status_berhasil)
                    holder.tvStatus.setTextColor(Color.parseColor("#166534"))
                    holder.tvStatus.background.setTint(Color.parseColor("#DCFCE7"))
                }
                PrintHistoryItem.Status.QUEUED_OFFLINE -> {
                    holder.tvStatus.text = context.getString(R.string.riwayat_cetak_status_antrean)
                    holder.tvStatus.setTextColor(Color.parseColor("#92400E"))
                    holder.tvStatus.background.setTint(Color.parseColor("#FEF3C7"))
                }
                PrintHistoryItem.Status.FAILED -> {
                    holder.tvStatus.text = context.getString(R.string.riwayat_cetak_status_gagal)
                    holder.tvStatus.setTextColor(Color.parseColor("#991B1B"))
                    holder.tvStatus.background.setTint(Color.parseColor("#FEE2E2"))
                }
            }

            holder.btnDelete.setOnClickListener {
                onDeleteItem(item)
            }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
            val tvStatus: TextView = view.findViewById(R.id.tvHistoryStatus)
            val tvDetails: TextView = view.findViewById(R.id.tvHistoryDetails)
            val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteHistory)
        }
    }
}
