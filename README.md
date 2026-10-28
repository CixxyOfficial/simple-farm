# Simple Farm 3D

Game farm 3D sederhana untuk Android, ditulis native (Kotlin + OpenGL ES 2.0, tanpa engine).
Sudut pandang orang pertama seperti Minecraft: hanya tangan yang terlihat.

## Kontrol
- Analog kiri bawah: berjalan
- Geser jari di layar: melihat sekeliling
- Ketuk layar: pakai item di tangan pada petak yang dibidik crosshair (+)
- Hotbar tengah bawah: slot 1 cangkul, slot 2 penyiram, slot 3 item dari tas (bibit / hasil panen)
- Tombol Tas (kanan atas): pilih item untuk dipasang di slot 3
- Tombol Toko muncul saat dekat toko (utara dari posisi awal)

## Cara main
1. Jalan ke toko, beli bibit (Gandum 5, Wortel 10, Labu 25 koin). Bibit otomatis masuk slot 3.
2. Cangkul tanah (slot 1), tanam bibit (slot 3), siram (slot 2).
3. Tanaman hanya tumbuh saat tanah basah (siraman bertahan 30 detik). Ulangi siram bila kering.
4. Ketuk tanaman matang untuk panen, lalu jual di toko.

## Build
APK dibuild otomatis oleh GitHub Actions setiap push ke `main`.
Ambil APK di tab **Releases** (file `app-debug.apk`) atau di **Actions > Artifacts**.

Build lokal (butuh Android SDK + Gradle 8.7 + JDK 17):

    gradle assembleDebug
