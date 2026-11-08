# PrinterJTM Pro — Panduan untuk AI Agent

Panduan ini adalah konteks proyek yang persisten untuk AI agent di workspace ini. Baca panduan ini, lalu buka hanya file yang relevan dengan tugas. Jangan memindai seluruh repository atau mengulang pembacaan tanpa alasan.

## Identitas dan tujuan proyek

- **Nama:** PrinterJTM Pro / PrinterJTM.
- **Jenis:** aplikasi Android native untuk membuat dan mencetak struk serta label pengiriman menggunakan printer thermal ESC/POS.
- **Package / namespace:** `com.david.printerjtm`.
- **Fitur utama:** cetak via Bluetooth dan Wi-Fi, antrean offline dengan retry, teks/form/gambar/PDF, pratinjau thermal, dithering gambar, QR/barcode, pemindaian/OCR, Android System Print Service, berbagi file ke aplikasi, bahasa Indonesia dan Inggris, serta layout adaptif ponsel/tablet.
- **Bahasa komunikasi:** balas dalam bahasa yang dipakai pengguna; gunakan bahasa Indonesia bila tidak ada petunjuk lain. Pertahankan bahasa UI dan pola lokalisasi aplikasi.

## Teknologi dan batasan arsitektur

- Kotlin dan Android SDK, Gradle Kotlin DSL, Android Views/XML, Material Components, AndroidX, serta beberapa library ML Kit, CameraX, ZXing, WebKit, dan Lottie.
- Satu modul aplikasi Gradle: `:app`. Tidak menggunakan Jetpack Compose.
- Pola UI utama adalah Activity + `MainViewModel` + `PrintRepository`; dependency dibuat manual melalui `AppContainer`. Pola manual ini disengaja untuk menghindari code generation Hilt/KSP.
- `BluetoothService` menangani komunikasi printer Bluetooth/Wi-Fi dan pembentukan data ESC/POS. `PrintSpoolerManager` bersama `SpoolerFileStore` menangani antrean dan payload persisten sementara. `DitheringEngine` mengubah bitmap menjadi raster monokrom ESC/POS.
- Prefer perubahan kecil yang mengikuti desain dan pola yang sudah ada. Jangan mengganti arsitektur, menambah framework/dependency, atau memperkenalkan code generation kecuali diminta dan benar-benar diperlukan.
- Jaga dukungan Android API 26+, izin/runtime permission, lifecycle, thread/background I/O, cancellation coroutine, serta perilaku antar ukuran layar.
- Resource UI wajib menggunakan `@string` dan menyediakan pasangan bahasa yang konsisten di `app/src/main/res/values/strings.xml` dan `app/src/main/res/values-en/strings.xml`. Pertahankan key yang sama pada kedua locale dan hindari teks UI hardcoded di Kotlin/XML.
- Layout utama memiliki varian konfigurasi (mis. `layout`, `layout-land`, `layout-sw600dp`, `layout-sw600dp-land`). Jika mengubah UI, periksa varian relevan agar perilaku dan tampilan tetap konsisten.

## Peta file untuk navigasi cepat

Mulai dari file yang sesuai dengan lingkup tugas; daftar ini bukan instruksi untuk membaca semuanya.

| Area | File/lokasi utama |
|---|---|
| Konfigurasi modul, SDK, signing, dependencies | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` |
| Manifest, permission, komponen Android | `app/src/main/AndroidManifest.xml` |
| Aktivitas utama dan alur layar | `app/src/main/java/com/david/printerjtm/MainActivity.kt` |
| State UI dan aksi layar utama | `app/src/main/java/com/david/printerjtm/viewmodel/MainViewModel.kt` |
| Komposisi dependency | `app/src/main/java/com/david/printerjtm/AppContainer.kt`, `PrinterApplication.kt` |
| Pencetakan dan antrean | `repository/PrintRepository.kt`, `BluetoothService.kt`, `PrintSpoolerManager.kt`, `SpoolerFileStore.kt`, `PrinterJtmPrintService.kt` |
| ESC/POS, gambar, dan input | `DitheringEngine.kt`, `EscPosSanitizer.kt`, `PageRangeHelper.kt`, `ApiEndpointValidator.kt` |
| Preferensi dan pemulihan data aman | `SecurePreferencesHelper.kt`, `EncryptedStorageRecovery.kt` |
| Layar lain | Activity/dialog terkait di `app/src/main/java/com/david/printerjtm/` |
| String, tema, dimensi, layout | `app/src/main/res/` |
| Unit test | `app/src/test/java/com/david/printerjtm/` |
| Instrumentation test | `app/src/androidTest/java/com/david/printerjtm/` |
| Catatan rilis | `CHANGELOG.md` |
| QA printer fisik | `docs/verification/physical-device-printer-qa.md` |

## Cara bekerja

1. Pastikan permintaan pengguna dan lingkup perubahan; periksa `git status --short` sebelum mengedit.
2. Worktree mungkin memiliki perubahan pengguna yang belum di-commit. Jangan menghapus, mereset, menimpa, memformat massal, atau memasukkan perubahan tersebut ke scope tugas. Jika perubahan yang harus disentuh bertabrakan atau kepemilikannya tidak jelas, berhenti dan minta arahan.
3. Telusuri simbol/file yang berkaitan langsung dengan tugas. Baca pemanggil, implementasi, dan test terkait seperlunya; jangan membuka seluruh source, output build, dependency cache, atau riwayat kecuali memang dibutuhkan.
4. Implementasikan solusi paling kecil yang lengkap. Pertahankan API, UX, kompatibilitas, lokalisasi, error handling, dan pola proyek yang tidak diminta untuk diubah.
5. Untuk perubahan perilaku, tambahkan atau perbarui unit test bila layak. Jalankan test/build paling terarah yang memverifikasi perubahan; laporkan bila pemeriksaan perangkat fisik atau environment eksternal tidak tersedia.
6. Akhiri dengan ringkasan singkat perubahan, file yang disentuh, pemeriksaan yang dijalankan beserta hasilnya, dan batasan yang belum dapat diverifikasi. Jangan mengklaim test/build berhasil jika tidak dijalankan.

## Build dan verifikasi

Jalankan dari root repository menggunakan Gradle Wrapper:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- Jalankan hanya task yang relevan; jangan otomatis menjalankan seluruh build/test untuk perubahan kecil.
- Unit test berada di `app/src/test/`; instrumentation test memerlukan perangkat/emulator dan dapat dijalankan dengan `./gradlew :app:connectedDebugAndroidTest` bila tersedia.
- Perubahan Bluetooth, Wi-Fi, kualitas cetak, ukuran kertas, pemulihan antrean, kamera, atau System Print Service mungkin memerlukan verifikasi pada perangkat/printer fisik. Gunakan checklist di `docs/verification/physical-device-printer-qa.md`; jangan mengarang hasil uji perangkat.
- Build release membutuhkan konfigurasi signing lokal. Jangan menjalankannya kecuali pengguna meminta atau verifikasi release memang diperlukan dan konfigurasi tersedia.

## Keamanan dan data sensitif

- Jangan membaca, menyalin, mencetak, mengirim, atau meng-commit nilai rahasia dari `local.properties`, keystore (`*.jks`, `*.keystore`), konfigurasi lokal, token, kredensial, atau signing key. Jangan pernah meminta pengguna menempelkan rahasia ke chat.
- `app/build.gradle.kts` mengambil `RELEASE_STORE_PASSWORD` dan `RELEASE_KEY_PASSWORD` dari `local.properties`; nama key bukan nilai rahasia. File keystore dan properti lokal harus tetap lokal/di-ignore.
- Jangan menaruh isi dokumen, label, bitmap, kredensial, alamat printer pribadi, atau payload cetak dalam log, exception, notifikasi, maupun test fixture yang tidak perlu. Gunakan pesan error yang aman dan berguna.
- Perlakukan payload spooler dan file sementara sebagai data pengguna: ikuti cleanup/TTL dan izin penyimpanan yang sudah ada; jangan menambah logging isi payload.
- Jangan memperlebar izin Android, ekspor komponen, cleartext/network access, atau permukaan WebView tanpa kebutuhan yang jelas dan pemeriksaan dampak keamanan.

## Efisiensi konteks dan token

- Gunakan panduan ini sebagai konteks awal; jangan meminta pengguna mengulang informasi yang sudah tersedia di repository.
- Cari berdasarkan nama simbol, error, atau file spesifik. Batasi hasil pencarian dan baca hanya rentang yang diperlukan; jangan mengeluarkan dump repository, `app/build/`, `.gradle/`, APK, file biner, atau file generated.
- Jangan membuka ulang file yang sudah dibaca kecuali perlu mengecek perubahan atau konteks baru; jangan mengulang build/test yang hasilnya masih berlaku.
- Jangan membuat rencana panjang atau daftar todo untuk perubahan sederhana. Jangan mendelegasikan pekerjaan kecuali tugas benar-benar memerlukan konteks terpisah.
- Tanyakan klarifikasi hanya jika pilihan yang belum diketahui dapat mengubah perilaku, data, keamanan, atau lingkup secara material. Untuk hal kecil, ikuti pola yang sudah ada.
- Jawaban akhir harus ringkas dan fokus pada hasil; sertakan hanya detail teknis yang membantu pengguna mengambil tindakan.

## Dokumentasi

Perbarui dokumentasi yang terdampak langsung oleh perubahan, terutama `CHANGELOG.md` bila sesuai dengan praktik rilis dan checklist QA bila prosedur fisik berubah. Jangan menyalin seluruh dokumentasi ke file panduan ini; tambahkan tautan/petunjuk lokasi seperlunya.
