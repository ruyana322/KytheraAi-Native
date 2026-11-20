#!/usr/bin/env python3
# ╔══════════════════════════════════════════════════════════════╗
# ║   KYTHERA TEST SUITE — tests/test_core.py                   ║
# ║   Unit & Integration Tests untuk KytheraCoreEngine          ║
# ║   Phase 2 Final Audit                                       ║
# ╚══════════════════════════════════════════════════════════════╝

import sys
import os
import struct
import json
import tempfile
import shutil
import unittest

# ── Tambahkan parent dir ke path ──────────────────────────────
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import KytheraCoreEngine, MP4Box

# ══════════════════════════════════════════════════════════════
#  HELPERS — MP4 binary factory
# ══════════════════════════════════════════════════════════════

def _ftyp_box(brand=b'isom'):
    """Buat minimal valid ftyp box."""
    body = brand + b'\x00' * 4 + brand
    return struct.pack('>I', 8 + len(body)) + b'ftyp' + body

def _mvhd_box(timescale=1000, duration=5000, version=0):
    """Buat mvhd box version 0 dengan field minimal valid."""
    if version == 0:
        data = bytes([0]) + bytes(3)        # version + flags
        data += struct.pack('>I', 0)        # ctime
        data += struct.pack('>I', 0)        # mtime
        data += struct.pack('>I', timescale)
        data += struct.pack('>I', duration)
        data += struct.pack('>I', 0x00010000)  # rate 1.0
        data += struct.pack('>H', 0x0100)      # volume 1.0
        data += bytes(10)                      # reserved
        data += struct.pack('>9i',
            0x00010000, 0, 0,
            0, 0x00010000, 0,
            0, 0, 0x40000000)                  # unity matrix (36 bytes)
        data += bytes(24)                      # predefined
        data += struct.pack('>I', 2)           # next_track_id
    elif version == 1:
        data = bytes([1]) + bytes(3)
        data += struct.pack('>Q', 0)        # ctime 8B
        data += struct.pack('>Q', 0)        # mtime 8B
        data += struct.pack('>I', timescale)
        data += struct.pack('>Q', duration)
        data += struct.pack('>I', 0x00010000)
        data += struct.pack('>H', 0x0100)
        data += bytes(10)
        data += struct.pack('>9i',
            0x00010000, 0, 0,
            0, 0x00010000, 0,
            0, 0, 0x40000000)
        data += bytes(24)
        data += struct.pack('>I', 2)
    else:
        raise ValueError(f"version tidak dikenal: {version}")

    return struct.pack('>I', 8 + len(data)) + b'mvhd' + data

def _moov_box(*children):
    """Gabungkan children boxes menjadi satu moov box."""
    body = b''.join(children)
    return struct.pack('>I', 8 + len(body)) + b'moov' + body

def _mdat_box(payload=b'FAKEMEDIA' * 10):
    return struct.pack('>I', 8 + len(payload)) + b'mdat' + payload

def _build_faststart_mp4(timescale=1000, duration=5000):
    """ftyp + moov + mdat (faststart layout)."""
    return _ftyp_box() + _moov_box(_mvhd_box(timescale, duration)) + _mdat_box()

def _build_nonfaststart_mp4(timescale=1000, duration=5000):
    """ftyp + mdat + moov (non-faststart)."""
    return _ftyp_box() + _mdat_box() + _moov_box(_mvhd_box(timescale, duration))

def _write_tmp(data: bytes, suffix='.mp4') -> str:
    """Tulis data ke temp file, return path."""
    fd, path = tempfile.mkstemp(suffix=suffix)
    with os.fdopen(fd, 'wb') as f:
        f.write(data)
    return path


# ══════════════════════════════════════════════════════════════
#  TEST CASES
# ══════════════════════════════════════════════════════════════

class TestMP4Box(unittest.TestCase):
    """Unit test untuk class MP4Box."""

    def test_repr(self):
        b = MP4Box(100, 'moov', 0, b'\x00' * 100)
        r = repr(b)
        self.assertIn('moov', r)
        self.assertIn('100', r)

    def test_slots_no_extra_attr(self):
        b = MP4Box(8, 'free', 0, b'\x00' * 8)
        with self.assertRaises(AttributeError):
            b.nonexistent = 'x'


class TestValidate(unittest.TestCase):
    """Test _validate() method."""

    def test_nonexistent_file(self):
        engine = KytheraCoreEngine('/nonexistent/path/file.mp4')
        self.assertFalse(engine._validate())

    def test_too_small_file(self):
        path = _write_tmp(b'\x00\x01\x02')
        try:
            engine = KytheraCoreEngine(path)
            self.assertFalse(engine._validate())
        finally:
            os.unlink(path)

    def test_valid_file(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            self.assertTrue(engine._validate())
        finally:
            os.unlink(path)

    def test_non_mp4_extension_still_valid(self):
        path = _write_tmp(_build_faststart_mp4(), suffix='.mov')
        try:
            engine = KytheraCoreEngine(path)
            # _validate returns True (hanya warn untuk non-.mp4)
            self.assertTrue(engine._validate())
        finally:
            os.unlink(path)


class TestParseBoxes(unittest.TestCase):
    """Test _parse_boxes()."""

    def test_parse_faststart(self):
        data = _build_faststart_mp4()
        path = _write_tmp(data)
        try:
            engine = KytheraCoreEngine(path)
            boxes = engine._parse_boxes(data)
            types = [b.type for b in boxes]
            self.assertIn('ftyp', types)
            self.assertIn('moov', types)
            self.assertIn('mdat', types)
        finally:
            os.unlink(path)

    def test_box_offsets_correct(self):
        data = _build_faststart_mp4()
        path = _write_tmp(data)
        try:
            engine = KytheraCoreEngine(path)
            boxes = engine._parse_boxes(data)
            # ftyp harus di offset 0
            ftyp = next(b for b in boxes if b.type == 'ftyp')
            self.assertEqual(ftyp.offset, 0)
            # moov setelah ftyp
            moov = next(b for b in boxes if b.type == 'moov')
            self.assertEqual(moov.offset, ftyp.size)
        finally:
            os.unlink(path)

    def test_parse_empty_data(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            boxes = engine._parse_boxes(b'')
            self.assertEqual(boxes, [])
        finally:
            os.unlink(path)

    def test_parse_truncated_data(self):
        """Data truncated di tengah box — harus tidak crash."""
        data = _build_faststart_mp4()
        path = _write_tmp(data)
        try:
            engine = KytheraCoreEngine(path)
            # potong di tengah
            boxes = engine._parse_boxes(data[:20])
            self.assertIsInstance(boxes, list)
        finally:
            os.unlink(path)

    def test_parse_with_base_offset(self):
        data = _build_faststart_mp4()
        path = _write_tmp(data)
        try:
            engine = KytheraCoreEngine(path)
            boxes = engine._parse_boxes(data, base_offset=1000)
            ftyp = next(b for b in boxes if b.type == 'ftyp')
            self.assertEqual(ftyp.offset, 1000)
        finally:
            os.unlink(path)


class TestParseMvhd(unittest.TestCase):
    """Test _parse_mvhd() parsing version 0 dan 1."""

    def _make_engine(self):
        path = _write_tmp(_build_faststart_mp4())
        engine = KytheraCoreEngine(path)
        # simpan path untuk cleanup
        engine._test_path = path
        return engine

    def test_version0_basic(self):
        engine = self._make_engine()
        try:
            mvhd_data = _mvhd_box(timescale=1000, duration=5000)[8:]  # skip box header
            info = engine._parse_mvhd(mvhd_data)
            self.assertEqual(info['version'], 0)
            self.assertEqual(info['timescale'], 1000)
            self.assertEqual(info['duration'], 5000)
            self.assertAlmostEqual(info['duration_sec'], 5.0, places=3)
            self.assertAlmostEqual(info['rate'], 1.0, places=4)
            self.assertAlmostEqual(info['volume'], 1.0, places=2)
        finally:
            os.unlink(engine._test_path)

    def test_version1_basic(self):
        engine = self._make_engine()
        try:
            mvhd_data = _mvhd_box(timescale=90000, duration=450000, version=1)[8:]
            info = engine._parse_mvhd(mvhd_data)
            self.assertEqual(info['version'], 1)
            self.assertEqual(info['timescale'], 90000)
            self.assertAlmostEqual(info['duration_sec'], 5.0, places=3)
        finally:
            os.unlink(engine._test_path)

    def test_empty_payload(self):
        engine = self._make_engine()
        try:
            info = engine._parse_mvhd(b'')
            self.assertEqual(info, {})
        finally:
            os.unlink(engine._test_path)

    def test_zero_timescale_no_divzero(self):
        """Timescale=0 tidak boleh raise ZeroDivisionError."""
        engine = self._make_engine()
        try:
            # Buat mvhd version 0 dengan timescale=0
            data = bytes([0]) + bytes(3)
            data += struct.pack('>I', 0) * 2  # ctime + mtime
            data += struct.pack('>I', 0)       # timescale = 0
            data += struct.pack('>I', 1000)    # duration
            data += struct.pack('>I', 0x00010000)
            data += struct.pack('>H', 0x0100)
            data += bytes(10)
            data += struct.pack('>9i', 0x00010000,0,0, 0,0x00010000,0, 0,0,0x40000000)
            data += bytes(24)
            data += struct.pack('>I', 2)
            info = engine._parse_mvhd(data)
            # duration_sec harus 0 (bukan crash)
            self.assertEqual(info.get('duration_sec', 0), 0)
        finally:
            os.unlink(engine._test_path)


class TestBackup(unittest.TestCase):
    """Test _backup() method."""

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.filepath = os.path.join(self.tmpdir, 'test.mp4')
        with open(self.filepath, 'wb') as f:
            f.write(_build_faststart_mp4())
        self.engine = KytheraCoreEngine(self.filepath)

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_backup_created(self):
        result = self.engine._backup()
        self.assertTrue(result)
        self.assertTrue(os.path.exists(self.filepath + '.bak'))

    def test_backup_idempotent(self):
        """Backup kedua tidak boleh fail (sudah ada)."""
        self.engine._backup()
        result = self.engine._backup()
        self.assertTrue(result)

    def test_backup_content_matches(self):
        self.engine._backup()
        with open(self.filepath, 'rb') as f:
            original = f.read()
        with open(self.filepath + '.bak', 'rb') as f:
            backup = f.read()
        self.assertEqual(original, backup)


class TestScanStructure(unittest.TestCase):
    """Test scan_structure() tidak crash pada berbagai input."""

    def test_valid_faststart(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            # Tidak boleh raise exception
            engine.scan_structure()
        finally:
            os.unlink(path)

    def test_valid_nonfaststart(self):
        path = _write_tmp(_build_nonfaststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            engine.scan_structure()
        finally:
            os.unlink(path)

    def test_nonexistent_file(self):
        engine = KytheraCoreEngine('/nonexistent.mp4')
        engine.scan_structure()  # harus handle gracefully


class TestDetectMoovMvhd(unittest.TestCase):

    def test_faststart_detected(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            KytheraCoreEngine(path).detect_moov_mvhd()
        finally:
            os.unlink(path)

    def test_nonfaststart_detected(self):
        path = _write_tmp(_build_nonfaststart_mp4())
        try:
            KytheraCoreEngine(path).detect_moov_mvhd()
        finally:
            os.unlink(path)

    def test_no_moov(self):
        """File tanpa moov — harus handle gracefully."""
        data = _ftyp_box() + _mdat_box()
        path = _write_tmp(data)
        try:
            KytheraCoreEngine(path).detect_moov_mvhd()
        finally:
            os.unlink(path)


class TestRepairMissingMoov(unittest.TestCase):

    def test_moov_exists_no_repair_needed(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            KytheraCoreEngine(path).repair_missing_moov()
        finally:
            os.unlink(path)

    def test_no_moov_no_moof_with_mdat(self):
        """mdat ada tapi moov tidak — harus kasih petunjuk recovery."""
        data = _ftyp_box() + _mdat_box()
        path = _write_tmp(data)
        try:
            KytheraCoreEngine(path).repair_missing_moov()
        finally:
            os.unlink(path)

    def test_no_moov_no_mdat(self):
        """File corrupt parah — harus tetap graceful."""
        data = _ftyp_box() + b'\x00' * 20
        path = _write_tmp(data)
        try:
            KytheraCoreEngine(path).repair_missing_moov()
        finally:
            os.unlink(path)


class TestAnalyzeMetadata(unittest.TestCase):

    def test_full_metadata(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            KytheraCoreEngine(path).analyze_metadata()
        finally:
            os.unlink(path)

    def test_no_mvhd(self):
        # File dengan moov kosong (tanpa mvhd)
        moov = struct.pack('>I', 8) + b'moov'
        data = _ftyp_box() + moov + _mdat_box()
        path = _write_tmp(data)
        try:
            KytheraCoreEngine(path).analyze_metadata()
        finally:
            os.unlink(path)


class TestFaststartOptimizer(unittest.TestCase):

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_already_faststart_no_output(self):
        """File sudah faststart — tidak perlu output baru."""
        filepath = os.path.join(self.tmpdir, 'already.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_faststart_mp4())
        engine = KytheraCoreEngine(filepath)
        engine.faststart_optimizer()
        # Tidak ada file _faststart.mp4 yang dibuat
        out = filepath.replace('.mp4', '_faststart.mp4')
        self.assertFalse(os.path.exists(out))

    def test_nonfaststart_produces_output(self):
        """File non-faststart — harus menghasilkan file baru."""
        filepath = os.path.join(self.tmpdir, 'slow.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_nonfaststart_mp4())
        engine = KytheraCoreEngine(filepath)
        engine.faststart_optimizer()
        out = filepath.replace('.mp4', '_faststart.mp4')
        self.assertTrue(os.path.exists(out))

    def test_faststart_output_valid_structure(self):
        """Output file harus moov sebelum mdat."""
        filepath = os.path.join(self.tmpdir, 'slow2.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_nonfaststart_mp4())
        engine = KytheraCoreEngine(filepath)
        engine.faststart_optimizer()

        out = filepath.replace('.mp4', '_faststart.mp4')
        self.assertTrue(os.path.exists(out))

        with open(out, 'rb') as f:
            data = f.read()

        moov_pos = data.find(b'moov')
        mdat_pos = data.find(b'mdat')
        self.assertNotEqual(moov_pos, -1)
        self.assertNotEqual(mdat_pos, -1)
        # moov harus SEBELUM mdat
        self.assertLess(moov_pos, mdat_pos, "moov harus sebelum mdat di output faststart")

    def test_no_moov_aborts_gracefully(self):
        """Tanpa moov — harus abort tanpa crash."""
        filepath = os.path.join(self.tmpdir, 'nomoov.mp4')
        with open(filepath, 'wb') as f:
            f.write(_ftyp_box() + _mdat_box())
        engine = KytheraCoreEngine(filepath)
        engine.faststart_optimizer()  # tidak boleh raise


class TestCheckCorruption(unittest.TestCase):

    def test_healthy_file(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            KytheraCoreEngine(path).check_corruption()
        finally:
            os.unlink(path)

    def test_no_moov_reports_issue(self):
        path = _write_tmp(_ftyp_box() + _mdat_box())
        try:
            KytheraCoreEngine(path).check_corruption()
        finally:
            os.unlink(path)

    def test_no_ftyp_reports_warning(self):
        data = _moov_box(_mvhd_box()) + _mdat_box()
        path = _write_tmp(data)
        try:
            KytheraCoreEngine(path).check_corruption()
        finally:
            os.unlink(path)


class TestExportScanReport(unittest.TestCase):

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_report_created(self):
        filepath = os.path.join(self.tmpdir, 'sample.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_faststart_mp4())
        KytheraCoreEngine(filepath).export_scan_report()

        report_path = os.path.join(self.tmpdir, 'sample_kythera.json')
        self.assertTrue(os.path.exists(report_path))

    def test_report_valid_json(self):
        filepath = os.path.join(self.tmpdir, 'sample2.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_faststart_mp4())
        KytheraCoreEngine(filepath).export_scan_report()

        report_path = os.path.join(self.tmpdir, 'sample2_kythera.json')
        with open(report_path, 'r') as f:
            data = json.load(f)

        self.assertIn('tool', data)
        self.assertIn('file', data)
        self.assertIn('boxes', data)
        self.assertIn('mvhd', data)
        self.assertIn('status', data)

    def test_report_status_flags_correct(self):
        filepath = os.path.join(self.tmpdir, 'sample3.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_faststart_mp4())
        KytheraCoreEngine(filepath).export_scan_report()

        report_path = os.path.join(self.tmpdir, 'sample3_kythera.json')
        with open(report_path, 'r') as f:
            data = json.load(f)

        status = data['status']
        self.assertTrue(status['has_ftyp'])
        self.assertTrue(status['has_moov'])
        self.assertTrue(status['has_mvhd'])
        self.assertTrue(status['has_mdat'])
        self.assertTrue(status['faststart'])

    def test_report_mvhd_fields(self):
        filepath = os.path.join(self.tmpdir, 'sample4.mp4')
        with open(filepath, 'wb') as f:
            f.write(_build_faststart_mp4(timescale=90000, duration=450000))
        KytheraCoreEngine(filepath).export_scan_report()

        report_path = os.path.join(self.tmpdir, 'sample4_kythera.json')
        with open(report_path, 'r') as f:
            data = json.load(f)

        mvhd = data['mvhd']
        self.assertEqual(mvhd['timescale'], 90000)
        self.assertAlmostEqual(mvhd['duration_seconds'], 5.0, places=2)


class TestPatchChunkOffsets(unittest.TestCase):
    """Test _patch_chunk_offsets() untuk stco patching."""

    def _make_moov_with_stco(self, offsets):
        """Buat moov data yang berisi stco dengan offset entries."""
        entry_count = len(offsets)
        stco_body = bytes(4)  # version + flags
        stco_body += struct.pack('>I', entry_count)
        for o in offsets:
            stco_body += struct.pack('>I', o)
        stco_box = struct.pack('>I', 8 + len(stco_body)) + b'stco' + stco_body
        return stco_box

    def test_stco_shift_applied(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            original_offsets = [1000, 2000, 3000]
            moov_data = self._make_moov_with_stco(original_offsets)
            shift = 500
            patched = engine._patch_chunk_offsets(moov_data, shift)

            # Baca kembali stco dari patched data
            stco_pos = patched.find(b'stco')
            self.assertNotEqual(stco_pos, -1)
            count = struct.unpack('>I', patched[stco_pos + 8:stco_pos + 12])[0]
            self.assertEqual(count, len(original_offsets))
            for i, expected in enumerate(o + shift for o in original_offsets):
                val = struct.unpack('>I', patched[stco_pos + 12 + i * 4:stco_pos + 16 + i * 4])[0]
                self.assertEqual(val, expected)
        finally:
            os.unlink(path)

    def test_no_stco_returns_unchanged(self):
        path = _write_tmp(_build_faststart_mp4())
        try:
            engine = KytheraCoreEngine(path)
            data = b'\x00\x00\x00\x08free\x00\x00\x00\x08skip'
            result = engine._patch_chunk_offsets(data, 100)
            self.assertEqual(data, result)
        finally:
            os.unlink(path)


# ══════════════════════════════════════════════════════════════
#  INTEGRATION TEST — full pipeline
# ══════════════════════════════════════════════════════════════

class TestIntegrationFullPipeline(unittest.TestCase):
    """Test alur kerja lengkap: scan → check → export → faststart."""

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.filepath = os.path.join(self.tmpdir, 'pipeline.mp4')
        with open(self.filepath, 'wb') as f:
            f.write(_build_nonfaststart_mp4(timescale=44100, duration=220500))

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def test_full_pipeline(self):
        engine = KytheraCoreEngine(self.filepath)

        # 1. Scan
        engine.scan_structure()

        # 2. Detect
        engine.detect_moov_mvhd()

        # 3. Check corruption
        engine.check_corruption()

        # 4. Analyze metadata
        engine.analyze_metadata()

        # 5. Export report
        engine.export_scan_report()
        report_path = self.filepath.replace('.mp4', '_kythera.json')
        self.assertTrue(os.path.exists(report_path))

        # 6. Faststart
        engine.faststart_optimizer()
        out = self.filepath.replace('.mp4', '_faststart.mp4')
        self.assertTrue(os.path.exists(out))

        # 7. Verify output faststart
        with open(out, 'rb') as f:
            data = f.read()
        self.assertLess(data.find(b'moov'), data.find(b'mdat'))

        # 8. Verify backup exists
        self.assertTrue(os.path.exists(self.filepath + '.bak'))


# ══════════════════════════════════════════════════════════════
#  ENTRYPOINT
# ══════════════════════════════════════════════════════════════

if __name__ == '__main__':
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(unittest.TestLoader().loadTestsFromModule(
        sys.modules[__name__]
    ))
    sys.exit(0 if result.wasSuccessful() else 1)
