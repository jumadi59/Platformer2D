# Platformer2D — Meadow Run

Sample platformer untuk Jumbox Engine 0.7.16-SNAPSHOT. Buka folder proyek ini melalui **Project → Open Project**, lalu scene awal `game-shared/assets/scenes/Menu.scene`. Build scripts sebelum Play jika diminta editor.

## Cara bermain
- A/D atau panah kiri/kanan untuk berjalan; Space untuk lompat.
- Touch: KnobController kiri dan tombol JUMP kanan; mendukung multi-touch.
- Ambil 6 gem biru, lewati dua jurang, lalu sentuh bendera hijau.
- Bendera kuning menjadi checkpoint. Jatuh atau menyentuh spike mengembalikan pemain ke checkpoint; gem yang sudah diambil tetap tersimpan.
- Tombol Restart atau R mengulang level dan mengembalikan gem.
- Menu: klik Play/Settings atau Tab, Shift+Tab, Enter. Escape dari level membuka Settings; Escape/Back mengembalikan level tanpa menghapus progres.
- Settings: volume Master, Game sounds, Button sounds, mute, tes sound, dan reset. Perubahan tersimpan otomatis pada penyimpanan lokal pemain.

## Komponen yang dapat diedit di engine editor
Scene memakai Transform2D, EditorMetadata, Camera, Sprite, Parallax2D, Tilemap, AnimatedSprite2D, RigidBody, CollisionShape2D, Behavior, dan UiCanvas. Level disimpan dalam `.tilemap` dengan tileset image collection embedded; tidak memerlukan TSX. Animasi idle/walk/jump tersimpan dalam `.spriteframes`. HUD adalah `.jui` dengan Text, Button, dan KnobController.

`PlayerBehavior` memakai SchemaBehavior berbasis template PlatformerController2D editor (jump buffer/coyote time). Kecepatan, jump, spawn, fall limit, input action dan jumlah gem dapat diubah di Inspector. `CameraBehavior` berbasis template CameraFollow2D. Registrasi behavior native berada di `ProjectBehaviorInitializer`; catalog dan manifest script dibangkitkan oleh Gradle agar Editor Play membaca schema yang sama.

GravityScale pemain adalah 100 karena satuan level memakai pixel sementara gravitasi default engine -9.8. Tile 70px; kecepatan 280px/s dan jump 560px/s. Physics collider pemain fixed rotation dan CCD BULLET.

## Build / run
```sh
./gradlew :game-shared:classes :game-desktop:classes
./gradlew :game-desktop:run
./gradlew :game-android:assembleDebug
```
Engine snapshot dibaca melalui Maven Local (`USE_MAVEN_LOCAL=true`). Gunakan versi engine yang sama dengan editor. `local.properties` adalah konfigurasi SDK lokal dan dapat disesuaikan.

## Assets
PNG disalin dari proyek lokal `../MyGame/game-shared/assets` (astronaut, meadow, tiles, gems dan flags). Semua referensi asset dalam sample bersifat relatif dan proyek dapat dipindahkan. Metadata/cache asset editor tidak disalin.

## Status build
Sepuluh tes integrasi lulus; distribusi Desktop dan APK debug berhasil dibangun. Aplikasi macOS tersedia di `game-desktop/build/distributions/Platformer2D.app`, APK di `game-android/build/outputs/apk/debug/game-android-debug.apk`. Navigasi keyboard Menu/Settings/Play, pause/resume, perubahan volume dan mute diamati langsung pada aplikasi Desktop. Rincian hasil dan batas verifikasi: [laporan implementasi](docs/IMPLEMENTATION_AUDIO_MENU_SETTINGS_2026_10_10.md).

## Parallax background

Layer `meadow` memakai `motionScaleX = 0.2` untuk efek parallax horizontal dan repeat 1024 px. `motionScaleY = 1` membuat background mengikuti posisi Y kamera. `motionOffsetY` mengimbangi posisi Y entity background, sehingga pusat gambar berada pada pusat kamera, bukan di posisi dunia yang tetap. Ukuran gambar 540 px sesuai tinggi viewport 540 px pada zoom 1. Jika posisi Y background diubah, sesuaikan offset Y layer dengan nilai negatif posisi tersebut.

## Physics sleep dan kontrol pemain

Controller membangunkan RigidBody dengan `body.wake()` saat menerima gerak horizontal atau jump yang valid, sebelum menulis velocity. Body yang sedang tidur tidak diintegrasikan oleh physics; penulisan velocity sendiri tidak membangunkannya. Respawn juga membangunkan body supaya pemain jatuh kembali ke platform setelah teleport.

## Jump pada terrain Tilemap

Collider Tilemap harus membawa entity terrain sebagai `RigidBody.userData`, agar RuntimeSceneRunner meneruskan event kontak ke PlayerBehavior. Status grounded berasal dari normal kontak tersebut. Perbaikan ini berada di runtime engine (termasuk collider orthogonal dan isometric); gunakan editor hasil build terbaru dan engine snapshot Maven Local terbaru. Setelah mengganti editor, buka ulang proyek dan jalankan Play kembali.

## Keyboard Desktop

DesktopGameHost memasang binding KEY_* dari `inputActionMap` manifest ke kode GLFW sebelum game dimulai. Tombol gameplay yang terdaftar diproses sebelum navigasi HUD, sehingga Left/Right dan Space tetap menjalankan aksi pemain. Tombol tanpa binding tetap dapat menavigasi UI; text input dan scene-editor overlay tetap menangani keyboard saat aktif. Perubahan ActionInput native diteruskan ke Behavior.onInput sebelum fixed update untuk callback jump/restart.

## Sound dan scene menu

Asset `jump.wav`, `coin.wav`, `health.wav`, `respawn.wav`, `finish.wav` mengisi efek gameplay; `button.wav` mengisi klik UI. Referensi AUDIO pada PlayerBehavior dapat diubah di Inspector. Menu dan Settings memakai scene `.scene` serta layout `.jui`, dengan Scene Flow PUSH/POP untuk menjaga progres saat membuka Settings.

Entry point memasang dispatcher scene pada instance game secara eksplisit. `RuntimePointerAdapter` mengompensasi perbedaan asal koordinat TouchHandler/RuntimeScene pada snapshot 0.7.16 yang dipakai proyek ini; adapter dilepas sebelum perpindahan scene dan disposal. Evaluasi ulang adapter jika runtime engine diganti. Enter dibiarkan menjadi tombol aktivasi UI, bukan binding aksi gameplay.

## Main Scene redesign — 11 Oktober 2026

Main sekarang memakai HUD health/gem/enemy/timer dengan font Roboto Mono Bold dan ikon existing. Dua slime dan dua fly memiliki animasi serta patrol; jatuh dari atas mengalahkan musuh, side contact mengurangi health dengan grace period. Health habis mengembalikan player ke checkpoint; Retry mengembalikan enemy dan progres awal. Buka Main.scene dan Compile Scripts sebelum mengulang Editor Play.

Lihat [laporan redesign](docs/MAIN_SCENE_REDESIGN_2026_10_11.md) untuk screenshot runtime, hasil tes terkini, serta dua kegagalan pointer Menu yang sudah ada pada baseline. Bagian Status build di atas adalah hasil pekerjaan sebelumnya; laporan redesign memuat batas validasi terbaru.
