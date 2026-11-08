package com.netraprint.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Animated Fluid Curved Bottom Navigation Bar
 * Terinspirasi dari standar desain modern Pinterest & Material Design 3.
 *
 * Menampilkan bilah navigasi dengan kurva Bézier dinamis yang mengalir (fluid wave)
 * dan ikon aktif yang terangkat (elevated circular badge) dengan animasi sentuhan mulus.
 */
class CurvedBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_outline)
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.0f)
    }

    private val bgPath = Path()
    private val strokePath = Path()

    // Ukuran kurva gelombang dinamis
    private var barTopY = dpToPx(18f)
    private var humpHeight = dpToPx(16f)
    private var humpHalfWidth = dpToPx(42f)

    // Posisi X gelombang aktif saat ini
    private var currentCradleX = 0f
    private var targetCradleX = 0f
    private var activeTabIndex = 0
    private var isFirstLayout = true

    // Floating elevated circle badge
    private val floatingBadgeSize = dpToPx(44f).toInt()
    private lateinit var floatingBadge: FrameLayout
    private lateinit var ivFloatingIcon: AppCompatImageView

    private var cradleAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setupFloatingBadge()
    }

    private fun setupFloatingBadge() {
        floatingBadge = FrameLayout(context).apply {
            layoutParams = LayoutParams(floatingBadgeSize, floatingBadgeSize).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dpToPx(0f).toInt()
            }
            background = ContextCompat.getDrawable(context, R.drawable.bg_nav_floating_badge)
            elevation = dpToPx(6f)
            translationZ = dpToPx(6f)
            clipChildren = false
            clipToPadding = false
        }

        ivFloatingIcon = AppCompatImageView(context).apply {
            val iconSize = dpToPx(22f).toInt()
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setColorFilter(ContextCompat.getColor(context, R.color.nav_active))
        }

        floatingBadge.addView(ivFloatingIcon)
        addView(floatingBadge)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // Pastikan floatingBadge selalu di atas layout anak
        bringChildToFront(floatingBadge)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bringChildToFront(floatingBadge)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCradlePosition(activeTabIndex, animate = false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isFirstLayout || changed) {
            post {
                updateCradlePosition(activeTabIndex, animate = !isFirstLayout)
                isFirstLayout = false
            }
        }
    }

    /**
     * Memilih tab dan menganimasikan kurva gelombang serta elevated badge.
     */
    fun selectTab(index: Int, animate: Boolean = true) {
        if (index == activeTabIndex && !isFirstLayout) return
        activeTabIndex = index
        updateCradlePosition(index, animate)
    }

    fun getActiveTabIndex(): Int = activeTabIndex

    private fun updateCradlePosition(index: Int, animate: Boolean) {
        val tabView = findTabView(index) ?: return
        if (tabView.width == 0) {
            tabView.post { updateCradlePosition(index, animate) }
            return
        }

        val targetX = tabView.x + (tabView.width / 2f)
        targetCradleX = targetX

        // Perbarui icon pada floating badge
        val iconDrawable = getTabIconDrawable(index)
        ivFloatingIcon.setImageDrawable(iconDrawable)

        // Sembunyikan ikon statis tab aktif agar tidak dobel dengan floating badge
        updateTabIconsVisibility(index)

        if (!animate || currentCradleX == 0f) {
            currentCradleX = targetX
            floatingBadge.translationX = targetX - (floatingBadgeSize / 2f)
            buildPaths()
            invalidate()
        } else {
            cradleAnimator?.cancel()
            val startX = currentCradleX
            cradleAnimator = ValueAnimator.ofFloat(startX, targetX).apply {
                duration = 260L
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { va ->
                    val value = va.animatedValue as Float
                    currentCradleX = value
                    floatingBadge.translationX = value - (floatingBadgeSize / 2f)
                    buildPaths()
                    invalidate()
                }
                start()
            }

            // Animasi membal (pop bounce) pada floating badge
            floatingBadge.scaleX = 0.85f
            floatingBadge.scaleY = 0.85f
            floatingBadge.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(260L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun updateTabIconsVisibility(activeIndex: Int) {
        val iconIds = listOf(R.id.ivTabLabel, R.id.ivTabText, -1, R.id.ivTabImage, R.id.ivTabPdf)

        for (i in iconIds.indices) {
            val iconId = iconIds[i]
            if (iconId != -1) {
                val iconView = findViewById<ImageView>(iconId)
                if (i == activeIndex) {
                    iconView?.animate()?.alpha(0f)?.setDuration(120)?.start()
                } else {
                    iconView?.animate()?.alpha(1f)?.setDuration(120)?.start()
                }
            }
        }
    }

    private fun findTabView(index: Int): View? {
        val tabIds = listOf(R.id.tabLabel, R.id.tabText, R.id.tabCenterCetak, R.id.tabImage, R.id.tabPdf)
        if (index !in tabIds.indices) return null
        val id = tabIds[index]
        return findViewById(id)
    }

    private fun getTabIconDrawable(index: Int): Drawable? {
        val drawableRes = when (index) {
            0 -> R.drawable.ic_tab_label
            1 -> R.drawable.ic_tab_text
            2 -> R.drawable.ic_print
            3 -> R.drawable.ic_tab_image
            4 -> R.drawable.ic_tab_pdf
            else -> R.drawable.ic_tab_label
        }
        return ContextCompat.getDrawable(context, drawableRes)
    }

    private fun buildPaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        bgPath.reset()
        strokePath.reset()

        val cradleX = if (currentCradleX > 0f) currentCradleX else (w / 2f)

        val leftCurveStart = (cradleX - humpHalfWidth).coerceAtLeast(0f)
        val rightCurveEnd = (cradleX + humpHalfWidth).coerceAtMost(w)
        val peakY = barTopY - humpHeight

        // Mulai dari kiri atas bilah datar
        bgPath.moveTo(0f, barTopY)
        strokePath.moveTo(0f, barTopY)

        // Garis datar sampai awal gelombang
        bgPath.lineTo(leftCurveStart, barTopY)
        strokePath.lineTo(leftCurveStart, barTopY)

        // Kurva Bézier naik menuju puncak gelombang
        val controlPoint1X = leftCurveStart + (humpHalfWidth * 0.45f)
        val controlPoint1Y = barTopY
        val controlPoint2X = cradleX - (humpHalfWidth * 0.45f)
        val controlPoint2Y = peakY
        bgPath.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, cradleX, peakY)
        strokePath.cubicTo(controlPoint1X, controlPoint1Y, controlPoint2X, controlPoint2Y, cradleX, peakY)

        // Kurva Bézier turun kembali ke bilah datar
        val controlPoint3X = cradleX + (humpHalfWidth * 0.45f)
        val controlPoint3Y = peakY
        val controlPoint4X = rightCurveEnd - (humpHalfWidth * 0.45f)
        val controlPoint4Y = barTopY
        bgPath.cubicTo(controlPoint3X, controlPoint3Y, controlPoint4X, controlPoint4Y, rightCurveEnd, barTopY)
        strokePath.cubicTo(controlPoint3X, controlPoint3Y, controlPoint4X, controlPoint4Y, rightCurveEnd, barTopY)

        // Garis datar ke kanan
        bgPath.lineTo(w, barTopY)
        strokePath.lineTo(w, barTopY)

        // Tutup bentuk ke bawah
        bgPath.lineTo(w, h)
        bgPath.lineTo(0f, h)
        bgPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        if (bgPath.isEmpty) {
            buildPaths()
        }
        // Gambar latar belakang putih lengkung
        canvas.drawPath(bgPath, bgPaint)
        // Gambar garis batas bayangan lembut di permukaan atas
        canvas.drawPath(strokePath, strokePaint)
        super.onDraw(canvas)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
