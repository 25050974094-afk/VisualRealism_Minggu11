# Visual Realism - Grafika Komputer Minggu 11

Adegan: **bola bertekstur di atas lantai** (Java OOP, LWJGL 3.3.4, JOML, OpenGL 3.3, GLSL 330 core).
Bola dibuat dengan 16 slice x 12 stack (352 segitiga) supaya beda flat/smooth mudah terlihat.

## Cara membuka dan menjalankan (Windows 11, NetBeans 21, JDK 21)

1. Ekstrak ZIP, lalu buka NetBeans -> **File -> Open Project** dan pilih folder `VisualRealism_Minggu11_25050974094` (ikon proyek Maven, berisi `pom.xml` dan `src`).
2. Pastikan komputer **terhubung internet** pada build pertama: Maven mengunduh LWJGL, JOML, dan library native Windows otomatis.
3. Klik **Run Project (F6)**. Kelas utama: `com.grafkom.viewing3d.Main`.
4. Jika ada tanda error dependensi: klik kanan proyek -> **Reload POM** (atau Clean and Build) lalu F6 lagi.

Tekstur (`bola.png`, `lantai.png`) dan shader ada di `src/main/resources` dan dimuat lewat classpath, jadi proyek bisa diekstrak ke folder mana pun.

## Kontrol

| Tombol | Fungsi |
|---|---|
| **B** | Phong <-> Blinn-Phong |
| **F** | Flat shading <-> Smooth shading |
| **Z** | Depth test (Z-Buffer) ON/OFF |
| **C** | Back-face culling ON/OFF |
| **T** | Filter tekstur Nearest <-> Bilinear + Mipmap |
| **Panah kiri/kanan** | Geser lampu sumbu X |
| **Panah atas/bawah** | Geser lampu sumbu Z |
| **PageUp / PageDown** | Naik / turunkan lampu |
| **N / M** | Kurangi / tambah shadow bias (0.0000 - 0.0200), Shift = langkah 5x |
| **H** | Bayangan ON/OFF (pembanding) |
| **V** | VSync ON/OFF (agar FPS tidak terkunci 60 saat membandingkan kinerja) |
| **W A S D / Q E** | Gerak kamera (Shift = cepat) |
| **Drag mouse kiri / scroll** | Putar kamera / maju-mundur |
| **1 / 2 / 3 / 4** | Sudut pandang siap pakai (lihat di bawah) |
| **R** | Reset kamera, lampu, dan bias |
| **Esc** | Keluar |

Setiap tombol mode hanya bereaksi **satu kali per penekanan** (tombol yang ditahan tidak mengulang).
Judul jendela menampilkan: mode shading, flat/smooth, status Z-Buffer dan Culling, filter tekstur, status bayangan, **shadow bias**, VSync, dan **FPS**.

Keadaan awal: Blinn-Phong, Smooth, Z ON, Culling ON, Bilinear+Mipmap, Bayangan ON, bias 0.0015.

## Sudut pandang siap pakai

- **1** - tampilan utama: bola dan lantai dari atas depan.
- **2** - kamera **di bawah lantai** menghadap ke atas (untuk back-face culling).
- **3** - kamera rendah dan jauh (untuk mipmap pada lantai dan bayangan panjang).
- **4** - dekat bola (untuk facet flat shading, piksel tekstur, dan kilauan).

## Urutan screenshot untuk lima indikator

Tekan **R** dulu sebelum tiap indikator agar keadaan awal sama.

1. **Phong vs Blinn-Phong** - tekan **1** (atau **4**). Ambil screenshot saat judul bertuliskan `Phong`, tekan **B**, ambil lagi saat `Blinn-Phong`. Kilauan Blinn-Phong tampak lebih lebar. Geser lampu dengan panah untuk menambah bukti.
2. **Flat vs Smooth** - tekan **4**. Screenshot `Smooth`, tekan **F**, screenshot `Flat` (permukaan bola tampak berfaset dan kilauan patah-patah).
3. **Z-Buffer dan Culling**
   - Tekan **1**: screenshot Z ON + Culling ON (normal).
   - Tekan **Z**: screenshot Z OFF (lantai menimpa bagian bawah bola). Nyalakan Z lagi.
   - Tekan **Z** dan **C** (keduanya OFF): bagian atas bola tampak kacau karena sisi belakang menimpa sisi depan. Nyalakan keduanya lagi.
   - Tekan **2** (kamera di bawah lantai): screenshot Culling ON (lantai hilang, bola terlihat dari bawah), tekan **C**, screenshot Culling OFF (lantai tampak dari sisi belakangnya dan menutupi bola).
4. **Filter tekstur** - tekan **4**, screenshot `Bilinear+Mipmap`, tekan **T**, screenshot `Nearest` (kotak tekstur tampak bergerigi/blocky). Lalu tekan **3** dan ulangi **T**: lantai di kejauhan tampak bergerigi/bermoire pada Nearest dan halus pada Mipmap.
5. **Bayangan real-time dan bias**
   - Tekan **1**: screenshot bayangan bola di lantai (bias 0.0015). Tekan **H** sekali untuk screenshot pembanding tanpa bayangan, tekan **H** lagi.
   - Geser lampu dengan panah / PageUp / PageDown: screenshot setelah bayangan berpindah arah.
   - Tekan **N** sampai bias `0.0000`: muncul garis-garis **shadow acne** pada lantai dan bola. Screenshot.
   - Tekan **M** (atau Shift+M) sampai bias sekitar `0.0200`: bayangan terlepas dari bola (**peter panning**). Screenshot.
   - Catat FPS di judul untuk analisis kinerja (tekan **V** untuk mematikan VSync).

## Struktur proyek

```
pom.xml, nbactions.xml
src/main/java/com/grafkom/viewing3d/
  Main, VisualRealismApp, Camera, ShaderProgram, Mesh, MeshData, MeshBuilder,
  HSRManager, Texture2D, ShadowMapFBO
src/main/resources/shaders/  phong.vert, phong.frag, shadow.vert, shadow.frag
src/main/resources/textures/ bola.png, lantai.png
```

Catatan teknis: normal flat dihitung per segitiga (vertex tidak dibagi); normal smooth dihitung per titik (arah dari pusat bola) lalu diinterpolasi di fragment shader. Bayangan memakai shadow map 2048x2048 (depth FBO), proyeksi ortografik, bias dinamis `max(10*b*(1-N.L), b)`, dan PCF 3x3.
