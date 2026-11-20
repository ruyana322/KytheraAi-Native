# KYTHERA AI — MP4 Binary Structure & Metadata Toolkit

```
  ██╗  ██╗██╗   ██╗████████╗██╗  ██╗███████╗██████╗  █████╗
  ██║ ██╔╝╚██╗ ██╔╝╚══██╔══╝██║  ██║██╔════╝██╔══██╗██╔══██╗
  █████╔╝  ╚████╔╝    ██║   ███████║█████╗  ██████╔╝███████║
  ██╔═██╗   ╚██╔╝     ██║   ██╔══██║██╔══╝  ██╔══██╗██╔══██║
  ██║  ██╗   ██║      ██║   ██║  ██║███████╗██║  ██║██║  ██║
  ╚═╝  ╚═╝   ╚═╝      ╚═╝   ╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝
```

**Hex Manipulation · Repair · Faststart · Metadata Analysis**  
*Khusus untuk Termux / Android — Pure Python, zero dependency*

---

## Deskripsi

KYTHERA adalah toolkit berbasis Python untuk manipulasi binary MP4 langsung di level byte — tanpa FFmpeg, tanpa library eksternal. Cocok untuk:

- Menganalisis struktur box/atom MP4 secara mendalam
- Mendeteksi dan melaporkan korupsi file
- Memperbaiki posisi `moov` atom untuk streaming optimal (Faststart)
- Patch byte spesifik di `mvhd` header
- Export laporan scan dalam format JSON

---

## Struktur Repo

```
kythera/
├── core.py          # Python engine utama (KytheraCoreEngine)
├── kythera.sh       # Bash UI wrapper (menu interaktif)
├── README.md        # Dokumentasi ini
└── tests/
    └── test_core.py # Test suite — 43 unit & integration tests
```

---

## Cara Penggunaan

### Mode Interaktif (Bash UI)
```bash
bash kythera.sh
```

### Mode CLI Langsung
```bash
python3 core.py <menu_number> <filepath.mp4>
```

---

## Menu

| Menu | Fungsi | Deskripsi |
|------|--------|-----------|
| `[1]` | Scan MP4 Structure | Tampilkan semua top-level box dengan offset, size, dan info |
| `[2]` | Detect moov / mvhd | Deteksi posisi & info moov/mvhd, cek faststart readiness |
| `[3]` | Repair Missing moov | Diagnosis file tanpa moov, panduan recovery |
| `[4]` | **Patch mvhd Byte ★** | Patch 1 byte di offset ke-44 dari `mvhd` (dengan backup otomatis) |
| `[5]` | Analyze Metadata | Info lengkap: duration, timescale, brand, track handlers, iTunes tags |
| `[6]` | Faststart Optimizer | Reorder moov ke sebelum mdat untuk web streaming |
| `[7]` | Check Corruption | Validasi integritas file — 7 checks, laporan ✓/⚠/✗ |
| `[8]` | Export Scan Report | Export laporan JSON lengkap ke direktori file |
| `[0]` | Exit | Keluar |

---

## Keamanan & Stabilitas

- **Auto-backup** (`.bak`) dibuat sebelum setiap operasi modifikasi (menu 4 & 6)
- Menu 4 (patch) hanya menulis **1 byte** dengan konfirmasi eksplisit
- Menu 6 (faststart) menyimpan sebagai **file baru** (`*_faststart.mp4`) — file asli tidak diubah
- Semua fungsi handle edge case: file tidak ada, truncated, corrupt, timescale=0
- Parsing box mendukung **extended 64-bit size** dan **zero-size (extends to EOF)**

---

## Menjalankan Test Suite

```bash
# Dari direktori kythera/
python3 tests/test_core.py -v
```

**43 test** mencakup:
- Unit test: `MP4Box`, `_validate`, `_parse_boxes`, `_parse_mvhd`, `_backup`, `_patch_chunk_offsets`
- Integration test: Pipeline lengkap scan → check → export → faststart
- Edge case: file tidak ada, truncated, corrupt, timescale=0, moov kosong

---

## Arsitektur

```
kythera.sh (Bash UI)
     │
     └──► python3 core.py <menu> <file>
               │
               └──► KytheraCoreEngine
                         ├── _validate()
                         ├── _read_file()
                         ├── _backup()
                         ├── _parse_boxes()      ← recursive box parser
                         ├── _parse_mvhd()       ← version 0 & 1 support
                         ├── _patch_chunk_offsets() ← stco/co64 patcher
                         ├── scan_structure()    [1]
                         ├── detect_moov_mvhd()  [2]
                         ├── repair_missing_moov() [3]
                         ├── patch_mvhd_byte()   [4] ★
                         ├── analyze_metadata()  [5]
                         ├── faststart_optimizer() [6]
                         ├── check_corruption()  [7]
                         └── export_scan_report() [8]
```

---

## Fase Pengembangan

| Fase | Status | Deskripsi |
|------|--------|-----------|
| Phase 1 | ✅ Selesai | Core engine + 8 menu MP4 tools |
| Phase 2 | ✅ Selesai | Faststart optimizer, mvhd patcher, corruption checker, export report |
| Phase 2 Audit | ✅ Selesai | Test suite 43 tests, edge case handling, README |
| Phase 3 | 🔜 Planned | Batch processing, track-level analysis, co64 full support |

---

## Requirements

- Python 3.11+
- Termux / Linux / Android
- Tidak memerlukan library eksternal

---

## Contoh Output

```
  ╔────────────────────────────────────────────────────╗
  ║                 SCAN MP4 STRUCTURE                 ║
  ╚────────────────────────────────────────────────────╝

  [*] File : video.mp4
  [*] Size : 15,728,640 bytes  (15.00 MB)

      OFFSET          SIZE  TYPE    INFO
  ──────────  ────────────  ──────  ────────────────────────────
           0            32  ftyp    brand='mp42'
          32       1048576  moov    movie container ✓
          40           108    └─mvhd  movie header
        1048  14,680,032  mdat    media data payload

  Total: 3 top-level box(es) ditemukan
```

---

*by kythera*
