from pathlib import Path

import pytest

from lantransfer.security import is_private_client, sanitize_filename, unique_path


def test_private_networks():
    assert is_private_client("192.168.1.10")
    assert is_private_client("10.0.0.2")
    assert is_private_client("127.0.0.1")
    assert not is_private_client("8.8.8.8")


def test_filename_rejects_traversal():
    for name in ["../x.txt", "..\\x.txt", "a/b.txt", "a\\b.txt", "..", "a:b.txt", "CON.txt"]:
        with pytest.raises(ValueError):
            sanitize_filename(name)


def test_unique_path_stays_inside(tmp_path: Path):
    p = unique_path(tmp_path, "ok.txt")
    assert p.parent == tmp_path.resolve()
