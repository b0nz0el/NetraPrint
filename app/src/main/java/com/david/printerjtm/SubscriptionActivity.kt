package com.david.printerjtm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class SubscriptionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayoutSub)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        findViewById<MaterialToolbar>(R.id.toolbarSub).setNavigationOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnUpgradePro).setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.kt_sub_license_title))
                .setMessage(getString(R.string.kt_sub_license_msg))
                .setPositiveButton(getString(R.string.tutup), null)
                .show()
        }
    }
}
