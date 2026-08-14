from __future__ import annotations

import io
import math
import os
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional, Tuple, Union

from PIL import Image, ImageOps

try:
    from pillow_heif import register_heif_opener
    register_heif_opener(thumbnails=False)
    HEIF_AVAILABLE = True
except Exception:
    HEIF_AVAILABLE = False

HEIF_EXTENSIONS = {".heic", ".heif"}
JPEG_EXTENSIONS = {".jpg", ".jpeg"}
SUPPORTED_EXTENSIONS = HEIF_EXTENSIONS | JPEG_EXTENSIONS


@dataclass
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


@dataclass
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
    copied_original: bool = False


def human_size(size: int) -> str:
    if size < 1024:
        return "%d B" % size
    if size < 1024 * 1024:
        return "%.1f KB" % (size / 1024.0)
    return "%.2f MB" % (size / (1024.0 * 1024.0))


def unique_output_path(folder: Path, stem: str) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    candidate = folder / (stem + ".jpg")
    counter = 1
    while candidate.exists():
        candidate = folder / ("%s_%d.jpg" % (stem, counter))
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
    exif: Optional[bytes],
    icc: Optional[bytes],
    options: ConvertOptions,
) -> bytes:
    buf = io.BytesIO()
    kwargs = {
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
    exif: Optional[bytes],
    icc: Optional[bytes],
    options: ConvertOptions,
) -> Tuple[Optional[bytes], int, int]:
    low = max(1, int(options.min_quality))
    high = min(100, int(options.max_quality))
    best_data = None
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

    # JPEG size is normally monotonic with quality, but optimize/progressive can
    # introduce small irregularities. Probe a few higher qualities so we never
    # throw away quality unnecessarily.
    probe_start = min(100, best_quality + 1)
    probe_end = min(100, int(options.max_quality))
    for quality in range(probe_start, probe_end + 1):
        data = _jpeg_bytes(image, quality, exif, icc, options)
        if len(data) <= target_bytes:
            best_data = data
            best_quality = quality

    return best_data, best_quality, len(best_data)


def _compress_image_to_target(
    image: Image.Image,
    exif: Optional[bytes],
    icc: Optional[bytes],
    options: ConvertOptions,
    should_cancel: Optional[Callable[[], bool]] = None,
) -> Tuple[Optional[bytes], int, Image.Image, bool, str]:
    if should_cancel is None:
        should_cancel = lambda: False

    target_bytes = max(1, int(options.target_kb)) * 1024
    original_size = image.size
    current = image
    resized = False
    metadata_removed = False

    for _ in range(20):
        if should_cancel():
            return None, 0, current, resized, "لغو شد"

        data, quality, estimated_size = _best_quality_under_target(
            current, target_bytes, exif, icc, options
        )
        if data is not None:
            return data, quality, current, resized or current.size != original_size, "انجام شد"

        if not options.allow_resize:
            return None, 0, current, resized, (
                "حتی با کیفیت %d به %dKB نمی‌رسد" % (options.min_quality, options.target_kb)
            )

        w, h = current.size
        if min(w, h) <= int(options.min_dimension):
            # Large metadata can itself make a small target impossible. Before
            # giving up, remove metadata once and retry the pixels unchanged.
            if not metadata_removed and (exif or icc):
                exif = None
                icc = None
                metadata_removed = True
                continue
            return None, 0, current, resized, (
                "رسیدن به حجم هدف بدون کوچک‌کردن بیش از حد تصویر ممکن نیست"
            )

        ratio = target_bytes / float(max(1, estimated_size))
        scale = math.sqrt(max(0.15, min(0.96, ratio))) * 0.985
        scale = min(scale, 0.92)
        scale = max(scale, 0.72)
        new_w = max(int(options.min_dimension), int(w * scale))
        new_h = max(int(options.min_dimension), int(h * scale))
        if new_w == w and new_h == h:
            new_w = max(int(options.min_dimension), int(w * 0.9))
            new_h = max(int(options.min_dimension), int(h * 0.9))

        try:
            lanczos = Image.Resampling.LANCZOS
        except AttributeError:
            lanczos = Image.LANCZOS
        current = current.resize((new_w, new_h), lanczos)
        resized = True

    return None, 0, current, resized, "فشرده‌سازی به حجم هدف ناموفق بود"


def _copy_small_jpeg(
    source: Path,
    output_folder: Path,
    input_bytes: int,
) -> ConvertResult:
    try:
        with Image.open(str(source)) as opened:
            width, height = opened.size
            opened.verify()
    except Exception as exc:
        return ConvertResult(
            source, None, False, input_bytes,
            message="خطا در بازکردن JPG: %s" % exc,
        )

    try:
        output = unique_output_path(output_folder, source.stem)
        shutil.copy2(str(source), str(output))
        return ConvertResult(
            source=source,
            output=output,
            ok=True,
            input_bytes=input_bytes,
            output_bytes=input_bytes,
            quality=100,
            width=width,
            height=height,
            resized=False,
            message="JPG از قبل زیر حجم هدف بود؛ بدون افت کیفیت کپی شد",
            copied_original=True,
        )
    except Exception as exc:
        return ConvertResult(
            source, None, False, input_bytes,
            message="خطا در کپی JPG: %s" % exc,
        )


def convert_image_to_jpg(
    source: Union[str, Path],
    output_folder: Union[str, Path],
    options: Optional[ConvertOptions] = None,
    should_cancel: Optional[Callable[[], bool]] = None,
) -> ConvertResult:
    source = Path(source)
    output_folder = Path(output_folder)
    if options is None:
        options = ConvertOptions()
    if should_cancel is None:
        should_cancel = lambda: False

    try:
        input_bytes = source.stat().st_size
    except OSError:
        input_bytes = 0

    suffix = source.suffix.lower()
    if suffix not in SUPPORTED_EXTENSIONS:
        return ConvertResult(
            source, None, False, input_bytes,
            message="فرمت پشتیبانی نمی‌شود؛ HEIC، HEIF، JPG یا JPEG انتخاب کنید",
        )

    if suffix in HEIF_EXTENSIONS and not HEIF_AVAILABLE:
        return ConvertResult(
            source, None, False, input_bytes,
            message="موتور HEIC در این نسخه در دسترس نیست",
        )

    if should_cancel():
        return ConvertResult(source, None, False, input_bytes, message="لغو شد")

    target_bytes = max(1, int(options.target_kb)) * 1024

    # A JPG that is already small enough is never recompressed. This avoids a
    # needless generation loss and also makes JPG input a first-class workflow.
    if suffix in JPEG_EXTENSIONS and input_bytes <= target_bytes:
        return _copy_small_jpeg(source, output_folder, input_bytes)

    try:
        with Image.open(str(source)) as opened:
            exif = opened.info.get("exif")
            icc = opened.info.get("icc_profile")
            image = _normalize_to_rgb(opened.copy())
    except Exception as exc:
        return ConvertResult(
            source, None, False, input_bytes,
            message="خطا در بازکردن فایل: %s" % exc,
        )

    try:
        best_data, best_quality, current, resized, status = _compress_image_to_target(
            image, exif, icc, options, should_cancel
        )
        if best_data is None:
            return ConvertResult(source, None, False, input_bytes, message=status)

        output = unique_output_path(output_folder, source.stem)
        temp_output = output.with_suffix(".jpg.part")
        with open(str(temp_output), "wb") as handle:
            handle.write(best_data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(str(temp_output), str(output))

        try:
            stat = source.stat()
            os.utime(str(output), (stat.st_atime, stat.st_mtime))
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
            copied_original=False,
        )
    except Exception as exc:
        return ConvertResult(
            source, None, False, input_bytes,
            message="خطا در تبدیل: %s" % exc,
        )


# Backward-compatible name used by the first release.
def convert_heic_to_jpg(
    source: Union[str, Path],
    output_folder: Union[str, Path],
    options: Optional[ConvertOptions] = None,
    should_cancel: Optional[Callable[[], bool]] = None,
) -> ConvertResult:
    return convert_image_to_jpg(source, output_folder, options, should_cancel)
