from pathlib import Path

from lantransfer.transfer_store import OffsetError, TransferStore


def test_chunked_upload_and_resume_offset(tmp_path: Path):
    store = TransferStore(tmp_path / "state.json", tmp_path / "recv")
    rec = store.init_upload("dev", "a.bin", 6)
    store.write_upload_chunk(rec.id, 0, b"abc", 6)
    try:
        store.write_upload_chunk(rec.id, 0, b"bad", 6)
        assert False, "OffsetError expected"
    except OffsetError as exc:
        assert exc.expected_offset == 3
    done = store.write_upload_chunk(rec.id, 3, b"def", 6)
    assert done.status == "done"
    assert Path(done.target_path).read_bytes() == b"abcdef"
