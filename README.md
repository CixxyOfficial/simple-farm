# Simple Farm 3D

Game farm 3D sederhana untuk Android, ditulis native (Kotlin + OpenGL ES 2.0, tanpa engine).

## Cara main
- Ketuk lahan kosong: tanam bibit (5 koin)
- Tunggu ±10 detik sampai tanaman berwarna emas dan berdenyut
- Ketuk tanaman matang: panen (+12 koin)
- Geser satu jari: putar kamera, cubit dua jari: zoom

## Build
APK dibuild otomatis oleh GitHub Actions setiap push ke `main`.
Ambil APK di tab **Releases** (file `app-debug.apk`) atau di **Actions > Artifacts**.

Build lokal (butuh Android SDK + Gradle 8.7 + JDK 17):

    gradle assembleDebug
