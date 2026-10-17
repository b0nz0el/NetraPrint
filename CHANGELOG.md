# Changelog — PrinterJTM (Native Android ESC/POS Hub)

## [2.0.4] - 2026-10-18
### International Readiness Follow-up

- Aligned the Android release metadata with the current changelog sequence (`versionName` 2.0.4, `versionCode` 10).
- Added English translations for common runtime UI in Word-document guidance, page-range selection, QR generation, printer selection, and thermal-preview status.
- Enabled AppCompat locale persistence on Android versions below API 33 and removed silent suppression when applying a selected language.
- Added Java keystore files to `.gitignore`; the existing local signing keystore is intentionally retained and must not be committed.

## [2.0.3] - 2026-10-17
### Full UI Internationalization (i18n): Resource Externalization + English Locale (`com.david.printerjtm`)

- **Hardcoded String Extraction (434 okuran → `@string`):** Mengekstrak seluruh teks UI hardcoded dari 18 file layout + `menu/drawer_menu.xml` ke `values/strings.xml` (**214 resource**) mencakup `android:text`, `android:hint`, `android:contentDescription`, `app:title`, dan `android:title`. Hanya 19 literal numerik/simbol dibiarkan disengaja (locale-invariant: `100%`, `9100`, `1.0.0`, glyph `›`, placeholder MAC).
- **English Locale Lengkap (`values-en/strings.xml`, 214 string):** Terjemahan penuh dengan key identik ID↔EN (divalidasi otomatis: 0 mismatch, 0 undefined ref). Terbukti berjalan di emulator API 37: tab `Label/Text/Image/PDF`, hint `Recipient (Name, Address, Phone No.)`, `Store Logo / Receipt`, `WYSIWYG Roll Preview`.
- **Konsolidasi Bahasa (EN→ID pada default locale):** 20 label campuran diseragamkan — menu drawer `Address/Free Text/Web Page/PDF File/Image File/Word File/QR Code/Setting/Subscription/Rating & Review/Contact Us/Exit` → `Alamat/Teks Bebas/Halaman Web/Berkas PDF/Berkas Gambar/Berkas Word/Kode QR/Pengaturan/Langganan/Penilaian & Ulasan/Hubungi Kami/Keluar`; tab bar `Text/Image/Pdf` → `Teks/Gambar/PDF`; `WYSIWYG Roll Preview` → `Pratinjau Roll WYSIWYG`; `Printer Name` → `Nama Printer`; `Backup Data` → `Cadangkan Data`; header `CLOUD SYNC & BACKUP` → `SINKRONISASI CLOUD & CADANGAN`.
- **AAPT Name-Safety Pass:** Auto-rename key non-valid: slug diawali digit → prefiks `txt_` (`txt_58_mm`, `txt_80_mm`, `txt_100_mm`, `txt_58mm_32_cpl`, `txt_384_dots_standar_58mm`) dan reserved keyword Java `default` → `str_default`.
- **Verifikasi:** Validator berbasis parser XML atas 18 layout + 2 file strings: **0 referensi `@string` tak-terdefinisi, 0 mismatch key ID/EN, 0 error well-formedness, 0 `android:text` hardcoded tersisa** (selain 19 literal numerik), `:app:assembleDebug` **BUILD SUCCESSFUL**. Screenshot verifikasi di `docs/verification/` (`01_id_main.png`, `02_id_drawer.png`, `03_en_main.png`) diambil via ADB dari emulator API 37.
- **Ruang lingkup diketahui (Phase B):** 51 string runtime Kotlin (title toolbar `Alamat Pengiriman`, pill `Pilih Printer`, komposisi konten pratinjau resi, 21 Toast + 30 `view.text`) masih hardcoded di kode — tetap Indonesia pada mode EN hingga ekstraksi Kotlin dieksekusi.

## [2.0.2] - 2026-10-17
### International-Grade Typography System & Accessibility Hardening (`com.david.printerjtm`)

- **Canonical Material 3 Type Scale (`values/text_appearances.xml`):** Menstandarkan **152 elemen `TextView` di 16 file layout** ke 5 role M3 — `LabelSmall` 11sp · `BodySmall` 12sp · `BodyMedium` 14sp · `TitleMedium` 16sp · `TitleLarge` 22sp. Seluruh ukuran fraksional dihapus (10.5 → 11, 11.5 → 12, 13.5 → 14) dan ukuran off-scale di-absorbsi ke langkah terdekat (13 → 12, 15 → 16, 18/20/21/24 → 22). Setiap style sengaja hanya memuat `android:textSize` sehingga `textColor`/`fontFamily`/`textStyle` per-view serta text appearance milik widget Material (MaterialButton, Chip, TextInputEditText, CheckBox, RadioButton, Switch) tidak pernah tertimpa — zero-regresi visual.
- **TalkBack / Accessibility Compliance:** Menambahkan `android:contentDescription` pada **15 kontrol & konten interaktif** yang sebelumnya bisu (tombol Kembali/Senter di barcode scanner, Kembali/Maju/Muat ulang di web viewer, placeholder Logo/PDF/Gambar, pratinjau thermal). **25 ikon dekoratif** (tab icon, receipt tear edge, ikon header banner, ikon drawer) ditandai `android:importantForAccessibility="no"` agar screen reader tidak membaca label ganda. Label FAB diseragamkan `Print` → `Cetak` di 4 varian layout.
- **Defensive Migration Validation:** Validasi berbasis parser XML (bukan regex) atas 18 file layout: **0 well-formedness error, 0 `android:textSize` mentah tersisa pada `TextView`** (hanya referensi `@dimen/nav_text_size` yang memang adaptif per layar), dan `:app:assembleDebug` **BUILD SUCCESSFUL** (37 tasks).

## [2.0.1] - 2026-10-17
### UI/UX Stability Hotfix & Project Synchronization (`com.david.printerjtm`)

- **Fixed Cold-Start `BottomAppBar` Inflate Crash (Material 1.12.0):** Menghapus atribut `android:elevation`, `app:elevation`, dan `android:translationZ` dari `<com.google.android.material.bottomappbar.BottomAppBar>` di seluruh varian layout untuk mencegah `NullPointerException` pada `materialShapeDrawable.setElevation()` saat inisialisasi constructor.
- **Fixed Scroll Overflow & Header Bleeding:** Mengunci `AppBarLayout` (`#004D40`, `fitsSystemWindows="true"`) dan mengisolasi batas atas kontainer scroll (`app:layout_constraintTop_toBottomOf="@id/appBarLayout"`) sehingga kartu pratinjau dan form terpotong rapi di bawah header tanpa menabrak jam status bar sistem.
- **Rebalanced Tablet Portrait (`layout-sw600dp`):** Meringkas pratinjau struk menjadi ~170dp, merampingkan banner upload logo menjadi 76dp, dan menyeimbangkan Bento Grid 2-kolom (Kiri: Penerima, Pengirim, Catatan Tambahan; Kanan: Jasa Kurir, Kode Booking, Isi Paket, Pengaturan Font).
- **Refined Tablet Landscape (`layout-sw600dp-land` & `layout-land`):** Memisahkan panel secara permanen menjadi 58% Form Scrollable di sisi kiri dan 42% Sticky WYSIWYG Thermal Paper Roll Preview di sisi kanan.
- **MD3 Iconography & Spring Micro-Animations:** Memperbarui 14 ikon vektor dengan garis bulat 1.75dp, chip aksi lingkaran 36dp (`#F1F5F3`), aksen merah `#D32F2F` untuk tombol hapus, serta animasi pegas 60fps (`scale_bounce.xml`).

---

## [2.0.0] - 2026-10-17
### Major Release: Multi-Device Adaptive UI, WYSIWYG Thermal Preview, Offline Spooler & 3-Mode Dithering

- **Real-Time WYSIWYG Thermal Paper Roll Preview:** Komponen `view_thermal_preview.xml` dengan simulasi kertas struk bergerigi (`bg_receipt_paper.xml`), batas karakter presisi 58mm (32 CPL) & 80mm (48 CPL), serta pratinjau dithering live.
- **Mesin Dithering Monokrom 3-Mode (`DitheringEngine.kt`):** Konversi raster ESC/POS (`GS v 0`) dengan pilihan algoritma *Floyd-Steinberg*, *Atkinson*, dan *Sharp Threshold (140)*.
- **Resilient Offline Print Spooler Queue (`PrintSpoolerManager.kt`):** Antrean cetak persisten (`print_spooler_queue.json`) saat printer Bluetooth/Wi-Fi di luar jangkauan dengan fitur *1-Tap Retry* dan *sequential flushing*.
- **Adaptive Multi-Device & Hardware Keyboard Shortcuts:** Dukungan shortcut `Ctrl+P`, `Ctrl+B`, `Ctrl+1..4`, dan `Ctrl+S` untuk penggunaan tablet/desktop/Samsung DeX.
- **Native Android Share Intent Receiver (`ACTION_SEND`):** Menerima kiriman `text/plain`, `image/*`, dan `application/pdf` langsung dari WhatsApp, Browser, atau aplikasi POS eksternal.
