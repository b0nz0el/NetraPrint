package com.david.printerjtm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val cbAgree = findViewById<CheckBox>(R.id.cbAgreePolicy)
        val tvLegal = findViewById<TextView>(R.id.tvLegalLinks)
        val btnGetStarted = findViewById<MaterialButton>(R.id.btnGetStarted)

        // Make getString(R.string.kt_onboarding_privacy_link) and getString(R.string.kt_onboarding_tos_link) clickable
        val fullText = getString(R.string.kt_onboarding_full_text)
        val spannable = SpannableString(fullText)

        val privacyStart = fullText.indexOf(getString(R.string.kt_onboarding_privacy_link))
        val privacyEnd = privacyStart + getString(R.string.kt_onboarding_privacy_link).length
        if (privacyStart != -1) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(this@OnboardingActivity, LegalActivity::class.java).apply {
                        putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_PRIVACY_POLICY)
                    }
                    startActivity(intent)
                }
            }, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(android.graphics.Color.parseColor("#004D40")),
                privacyStart,
                privacyEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val tosStart = fullText.indexOf(getString(R.string.kt_onboarding_tos_link))
        val tosEnd = tosStart + getString(R.string.kt_onboarding_tos_link).length
        if (tosStart != -1) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(this@OnboardingActivity, LegalActivity::class.java).apply {
                        putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_TERMS_OF_SERVICE)
                    }
                    startActivity(intent)
                }
            }, tosStart, tosEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                ForegroundColorSpan(android.graphics.Color.parseColor("#004D40")),
                tosStart,
                tosEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvLegal.text = spannable
        tvLegal.movementMethod = LinkMovementMethod.getInstance()

        cbAgree.setOnCheckedChangeListener { _, isChecked ->
            btnGetStarted.isEnabled = isChecked
        }

        btnGetStarted.setOnClickListener {
            val prefs = SecurePreferencesHelper.getPreferences(this)
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
