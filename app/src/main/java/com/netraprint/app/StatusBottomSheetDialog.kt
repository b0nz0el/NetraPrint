package com.netraprint.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class StatusBottomSheetDialog(private val context: Context) {

    enum class Type {
        LOADING, SUCCESS, ERROR
    }

    private val dialog = BottomSheetDialog(context, R.style.Theme_NetraPrint_BottomSheetDialog)
    private val lottieView: LottieAnimationView
    private val tvTitle: TextView
    private val tvMessage: TextView
    private val progressIndicator: LinearProgressIndicator
    private val btnAction: MaterialButton
    private val btnSecondary: MaterialButton

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_status_bottom_sheet, null)
        dialog.setContentView(view)

        // Make bottom sheet container transparent so custom 28dp rounded corners show without artifacts
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        lottieView = view.findViewById(R.id.lottieAnimationView)
        tvTitle = view.findViewById(R.id.tvStatusTitle)
        tvMessage = view.findViewById(R.id.tvStatusMessage)
        progressIndicator = view.findViewById(R.id.progressIndicator)
        btnAction = view.findViewById(R.id.btnStatusAction)
        btnSecondary = view.findViewById(R.id.btnStatusSecondary)

        btnAction.setOnClickListener {
            dialog.dismiss()
        }
        btnSecondary.setOnClickListener {
            dialog.dismiss()
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun runOnMainThread(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun safeShowDialog() {
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }
        try {
            dialog.show()
        } catch (_: RuntimeException) {
            android.util.Log.e("StatusBottomSheetDialog", "Unable to display status dialog")
        }
    }

    fun showLoading(
        title: String? = null,
        message: String? = null,
        showProgress: Boolean = true
    ): StatusBottomSheetDialog {
        runOnMainThread {
            lottieView.setAnimation("lottie_loading.json")
            lottieView.repeatCount = LottieDrawable.INFINITE
            lottieView.playAnimation()

            val localized = AppLocaleContext.wrap(context)
            tvTitle.text = title ?: localized.getString(R.string.kt_sbs_default_loading_title)
            tvMessage.text = message ?: localized.getString(R.string.kt_sbs_default_loading_msg)
            progressIndicator.visibility = if (showProgress) View.VISIBLE else View.GONE
            btnAction.visibility = View.GONE
            btnSecondary.visibility = View.GONE
            dialog.setCancelable(false)
            safeShowDialog()
        }
        return this
    }

    fun updateProgress(message: String, progress: Int? = null) {
        runOnMainThread {
            tvMessage.text = message
            if (progress != null) {
                progressIndicator.isIndeterminate = false
                progressIndicator.setProgressCompat(progress, false)
            }
        }
    }

    fun showSuccess(
        title: String? = null,
        message: String? = null,
        actionText: String? = null,
        secondaryText: String? = null,
        onDismiss: (() -> Unit)? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        runOnMainThread {
            lottieView.setAnimation("lottie_success.json")
            lottieView.repeatCount = 0
            lottieView.playAnimation()

            val localized = AppLocaleContext.wrap(context)
            tvTitle.text = title ?: localized.getString(R.string.kt_sbs_default_success_title)
            tvMessage.text = message ?: localized.getString(R.string.kt_sbs_default_success_msg)
            progressIndicator.visibility = View.GONE
            btnAction.text = actionText ?: localized.getString(R.string.kt_btn_done)
            btnAction.visibility = View.VISIBLE
            btnAction.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }

            if (secondaryText != null) {
                btnSecondary.text = secondaryText
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.setOnClickListener {
                    onSecondary?.invoke()
                    dialog.dismiss()
                }
            } else {
                btnSecondary.visibility = View.GONE
            }

            dialog.setCancelable(true)
            if (!dialog.isShowing) {
                safeShowDialog()
            }
        }
    }

    fun showError(
        title: String? = null,
        message: String,
        actionText: String? = null,
        secondaryText: String? = null,
        onDismiss: (() -> Unit)? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        runOnMainThread {
            lottieView.setAnimation("lottie_error.json")
            lottieView.repeatCount = 0
            lottieView.playAnimation()

            val localized = AppLocaleContext.wrap(context)
            tvTitle.text = title ?: localized.getString(R.string.kt_sbs_default_error_title)
            tvMessage.text = message
            progressIndicator.visibility = View.GONE
            btnAction.text = actionText ?: localized.getString(R.string.kt_btn_understood)
            btnAction.visibility = View.VISIBLE
            btnAction.setOnClickListener {
                dialog.dismiss()
                onDismiss?.invoke()
            }

            if (secondaryText != null) {
                btnSecondary.text = secondaryText
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.setOnClickListener {
                    dialog.dismiss()
                    onSecondary?.invoke()
                }
            } else {
                btnSecondary.visibility = View.GONE
            }

            dialog.setCancelable(true)
            if (!dialog.isShowing) {
                safeShowDialog()
            }
        }
    }

    fun dismiss() {
        runOnMainThread {
            if (dialog.isShowing) {
                try {
                    dialog.dismiss()
                } catch (_: Exception) {}
            }
        }
    }

    val isShowing: Boolean
        get() = dialog.isShowing
}
