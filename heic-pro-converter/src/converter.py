from __future__ import annotations

import io
import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from PIL import Image, ImageOps
try:
    from pillow_heif import register_heif_opener
    register_heif_opener(thumbnails=False)
    HEIF_AVAILABLE = True
except ImportError:
    HEIF_AVAILABLE = False

SUPPORTED_EXTENSIONS = {".heic", ".heif"}


@dataclass(slots=True)
class ConvertOptions:
    target_kb: int = 488
    min_quality: int = 30
    max_quality: int = 95
    preserve_exif: bool = True
    preserve_icc: bool = True
    progressive: bool = True
    optimize: bool = True
    allow_resize: bool = True
    min_dimension: int = 640


@dataclass(slots=True)
class ConvertResult:
    source: Path
    output: Optional[Path]
    ok: bool
    input_bytes: int
    output_bytes: int = 0
    quality: int = 0
    width: int = 0
    height: int = 0
    resized: bool = False
    message: str = ""


def human_size(size: int) -> str:
    if size < 1024:
        return f"{size} B"
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KB"
    return f"{size / (1024 * 1024):.2f} MB"


def unique_output_path(folder: Path, stem: str) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    candidate = folder / f"{stem}.jpg"
    counter = 1
    while candidate.exists():
        candidate = folder / f"{stem}_{counter}.jpg"
        counter += 1
    return candidate


def _normalize_to_rgb(image: Image.Image) -> Image.Image:
    image = ImageOps.exif_transpose(image)
    if image.mode == "RGB":
        return image
    if image.mode in ("RGBA", "LA"):
        bg = Image.new("RGB", image.size, "white")
        alpha = image.getchannel("A")
        bg.paste(image.convert("RGB"), mask=alpha)
        return bg
    return image.convert("RGB")


def _jpeg_bytes(
    image: Image.Image,
    quality: int,
    exif: bytes | None,
    icc: bytes | None,
    options: ConvertOptions,
) -> bytes:
    buf = io.BytesIO()
    kwargs: dict = {
        "format": "JPEG",
        "quality": int(quality),
        "optimize": bool(options.optimize),
        "progressive": bool(options.progressive),
        "subsampling": "4:2:0",
    }
    if options.preserve_exif and exif:
        kwargs["exif"] = exif
    if options.preserve_icc and icc:
        kwargs["icc_profile"] = icc
    image.save(buf, **kwargs)
    return buf.getvalue()


def _best_quality_under_target(
    image: Image.Image,
    target_bytes: int,
    exif: bytes | None,
    icc: bytes | None,
    options: ConvertOptions,
) -> tuple[bytes | None, int, int]:
    low = max(1, options.min_quality)
    high = min(100, options.max_quality)
    best_data: bytes | None = None
    best_quality = 0

    while low <= high:
        mid = (low + high) // 2
        data = _jpeg_bytes(image, mid, exif, icc, options)
        size = len(data)
        if size <= target_bytes:
            best_data = data
            best_quality = mid
            low = mid + 1
        else:
            high = mid - 1

    if best_data is None:
        data = _jpeg_bytes(image, options.min_quality, exif, icc, options)
        return None, 0, len(data)

    return best_data, best_quality, len(best_data)


def _compress_image_to_target(
    image: Image.Image,
    exif: bytes | None,
    icc: bytes | None,
    options: ConvertOptions,
    should_cancel: Callable[[], bool] | None = None,
) -> tuple[bytes | None, int, Image.Image, bool, str]:
    should_cancel = should_cancel or (lambda: False)
    target_bytes = max(1, options.target_kb) * 1024
    original_size = image.size
    current = image
    resized = False

    for _ in range(18):
        if should_cancel():
            return None, 0, current, resized, "لغو شد"

        data, quality, estimated_size = _best_quality_under_target(
            current, target_bytes, exif, icc, options
        )
        if data is not None:
            return data, quality, current, resized or current.size != original_size, "انجام شد"

        if not options.allow_resize:
            return None, 0, current, resized, (
                f"حتی با کیفیت {options.min_quality} به {options.target_kb}KB نمی‌رسد"
            )

        w, h = current.size
        if min(w, h) <= options.min_dimension:
            if exif or icc:
                exif, icc = None, None
                continue
            return None, 0, current, resized, (
                "رسیدن به حجم هدف بدون کوچک‌کردن بیش از حد تصویر ممکن نیست"
            )

        ratio = target_bytes / max(1, estimated_size)
        scale = math.sqrt(max(0.15, min(0.96, ratio))) * 0.985
        scale = min(scale, 0.92)
        scale = max(scale, 0.72)
        new_w = max(options.min_dimension, int(w * scale))
        new_h = max(options.min_dimension, int(h * scale))
        if new_w == w and new_h == h:
            new_w = max(options.min_dimension, int(w * 0.9))
            new_h = max(options.min_dimension, int(h * 0.9))
        current = current.resize((new_w, new_h), Image.Resampling.LANCZOS)
        resized = True

    return None, 0, current, resized, "فشرده‌سازی به حجم هدف ناموفق بود"


def convert_heic_to_jpg(
    source: str | Path,
    output_folder: str | Path,
    options: ConvertOptions | None = None,
    should_cancel: Callable[[], bool] | None = None,
) -> ConvertResult:
    source = Path(source)
    output_folder = Path(output_folder)
    options = options or ConvertOptions()
    should_cancel = should_cancel or (lambda: False)

    try:
        input_bytes = source.stat().st_size
    except OSError:
        input_bytes = 0

    if source.suffix.lower() not in SUPPORTED_EXTENSIONS:
        return ConvertResult(source, None, False, input_bytes, message="فرمت فایل HEIC/HEIF نیست")

    if not HEIF_AVAILABLE:
        return ConvertResult(source, None, False, input_bytes, message="کتابخانه pillow-heif نصب نیست")

    if should_cancel():
        return ConvertResult(source, None, False, input_bytes, message="لغو شد")

    try:
        with Image.open(source) as opened:
            exif = opened.info.get("exif")
            icc = opened.info.get("icc_profile")
            image = _normalize_to_rgb(opened.copy())
    except Exception as exc:
        return ConvertResult(source, None, False, input_bytes, message=f"خطا در بازکردن فایل: {exc}")

    try:
        best_data, best_quality, current, resized, status = _compress_image_to_target(
            image, exif, icc, options, should_cancel
        )
        if best_data is None:
            return ConvertResult(source, None, False, input_bytes, message=status)

        output = unique_output_path(output_folder, source.stem)
        temp_output = output.with_suffix(".jpg.part")
        with open(temp_output, "wb") as f:
            f.write(best_data)
            f.flush()
            os.fsync(f.fileno())
        temp_output.replace(output)

        try:
            stat = source.stat()
            os.utime(output, (stat.st_atime, stat.st_mtime))
        except OSError:
            pass

        return ConvertResult(
            source=source,
            output=output,
            ok=True,
            input_bytes=input_bytes,
            output_bytes=len(best_data),
            quality=best_quality,
            width=current.width,
            height=current.height,
            resized=resized,
            message="انجام شد",
        )
    except Exception as exc:
        return ConvertResult(source, None, False, input_bytes, message=f"خطا در تبدیل: {exc}")
