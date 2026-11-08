# NetraPrint 🖨️📦

[![Latest Release](https://img.shields.io/github/v/release/b0nz0el/NetraPrint?color=0F766E&label=Latest%20Version)](https://github.com/b0nz0el/NetraPrint/releases/latest)
[![Android Min SDK](https://img.shields.io/badge/Android-8.0%2B%20%28API%2026%2B%29-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![ESC/POS](https://img.shields.io/badge/Protocol-ESC%2FPOS%20Thermal-00463B)](https://en.wikipedia.org/wiki/ESC/P)
[![License](https://img.shields.io/badge/License-Proprietary-blue.svg)](#lisensi)

Aplikasi Android native berkinerja tinggi untuk format dan cetak otomatis label pengiriman ekspedisi, resi marketplace, struk kasir POS, berkas PDF multi-halaman, serta konversi halaman web menggunakan printer thermal nirkabel (**Bluetooth** & **Wi-Fi**).

---

## 🚀 Fitur Unggulan

### 1. 📦 Label Pengiriman Otomatis (23 Kurir Logistik)
- Parsing otomatis dan cetak resi siap tempel untuk 23 ekspedisi populer (J&T Express, SiCepat, JNE, Shopee Xpress, Ninja Van, Anteraja, Lion Parcel, ID Express, POS Indonesia, GoSend, GrabExpress, dll).
- Otomatis menyesuaikan ukuran kertas printer thermal **58mm** dan **80mm**.

### 2. 📶 Konektivitas Nirkabel Ganda (Bluetooth & Wi-Fi)
- **Bluetooth:** Mendukung pairing cepat dan direct print ke printer thermal mobile/POS standar (RPP02N, GOOJPRT, Panda, Zjiang, dll).
- **Wi-Fi LAN / Network:** Cetak via jaringan lokal dengan scanner IP otomatis pada port ESC/POS (default: 9100).

### 3. 📄 Dokumen PDF Multi-Halaman & Filter Rentang
- Cetak berkas PDF banyak halaman sekaligus tanpa batas.
- Dilengkapi **Page Range Selector** pintar (contoh: cetak hanya halaman `1-3, 5, 8`).

### 4. 📷 Pemindai Barcode Cepat & Kamera OCR (CameraX + ML Kit)
- Deteksi instan resi dan nomor pelacakan menggunakan kamera resolusi tinggi 1080p.
- Ekstraksi teks otomatis (OCR) langsung dari struk fisik atau layar smartphone.

### 5. 🎨 Mesin Dithering Monokrom Presisi Tinggi
- Mengubah gambar, logo, dan foto menjadi biner monokrom tajam tanpa gradien buram:
  - **Floyd-Steinberg:** Gradien halus seimbang untuk foto & dokumen.
  - **Atkinson:** Kontras tinggi tajam khas cetakan struk thermal modern.
  - **Sharp Black & White:** Thresholding langsung untuk teks tebal & barcode presisi.

### 6. ⚡ Android System Print Service
- Terintegrasi langsung dengan menu cetak sistem bawaan Android.
- Mendukung direct background printing tanpa perlu membuka aplikasi perantara.

### 7. 🔒 Privasi & Keamanan Data Lokal 100%
- Seluruh pemrosesan formulir, render dokumen, dan parsing gambar berjalan secara lokal di perangkat Anda (*on-device*).
- Tidak ada data pelanggan atau transaksi yang dikirim ke server luar pihak ketiga.

---

## 📥 Unduh & Instalasi

Anda dapat mengunduh file APK rilis resmi terbaru langsung ke perangkat Android Anda:

| Sumber Unduhan | Tautan | Keterangan |
| :--- | :--- | :--- |
| **Direct Download (CDN)** | [Unduh NetraPrint.apk](https://netraprint-updater.yt-hackrandom.workers.dev/download) | Unduhan berkecepatan tinggi via Cloudflare Proxy |
| **GitHub Releases** | [Halaman Rilis GitHub](https://github.com/b0nz0el/NetraPrint/releases/latest) | Riwayat versi lengkap & changelog |

---

## 🛠️ Persyaratan Sistem

- **Sistem Operasi:** Android 8.0 (Oreo / API Level 26) ke atas.
- **Rekomendasi:** Android 10, 11, 12, 13, 14, 15, atau 16.
- **Printer yang Didukung:** Semua printer thermal ESC/POS 58mm / 80mm dengan koneksi Bluetooth (SPP) atau Wi-Fi/Ethernet (TCP/IP).

---

## 🔄 Pembaruan Otomatis (In-App Updates)

NetraPrint dilengkapi fitur pembaruan mandiri di dalam aplikasi:
1. Buka menu **Pengaturan** di dalam aplikasi NetraPrint.
2. Klik menu **Cek Pembaruan**.
3. Jika versi baru tersedia, klik **Unduh Sekarang** untuk memperbarui secara otomatis tanpa kehilangan data setting Anda.

---

## 📋 Catatan Rilis Terbaru

Detail lengkap mengenai perubahan di setiap versi dapat dilihat pada [CHANGELOG.md](CHANGELOG.md).

---

## 📄 Lisensi

Hak Cipta © 2026 NetraPrint. Seluruh hak cipta dilindungi undang-undang.
