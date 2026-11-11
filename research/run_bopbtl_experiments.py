#!/usr/bin/env python3
"""Controlled experiments for the pinned Keepsake BOPBTL PTE."""

import argparse
import csv
import time
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageDraw
from executorch.runtime import Runtime


THRESHOLDS = (0.1, 0.2, 0.3, 0.4)


def letterbox(image: Image.Image, size: int = 384) -> np.ndarray:
    scale = min(size / image.width, size / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    resized = image.resize((width, height), Image.Resampling.BICUBIC)
    canvas = Image.new("RGB", (size, size), (128, 128, 128))
    canvas.paste(resized, ((size - width) // 2, (size - height) // 2))
    return np.asarray(canvas, dtype=np.float32).transpose(2, 0, 1) / 255.0


def variant_input(rgb: np.ndarray, variant: str) -> np.ndarray:
    if variant == "rgb":
        return rgb
    if variant == "gray_replicated":
        gray = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]
        return np.repeat(gray[None, :, :], 3, axis=0)
    if variant == "bgr":
        return rgb[[2, 1, 0], :, :]
    raise ValueError(f"Unknown variant: {variant}")


def save_probability(probability: np.ndarray, path: Path) -> None:
    image = Image.fromarray(np.uint8(np.clip(probability, 0.0, 1.0) * 255.0), "L")
    image.save(path)


def save_mask(probability: np.ndarray, threshold: float, path: Path) -> None:
    Image.fromarray((probability >= threshold).astype(np.uint8) * 255, "L").save(path)


def contact_sheet(
    original: Image.Image,
    rendered: dict[str, tuple[Image.Image, Image.Image]],
    path: Path,
) -> None:
    labels = ["Original"]
    panels = [original.convert("RGB")]
    for variant, (probability, threshold) in rendered.items():
        labels.extend([f"{variant} probability", f"{variant} >= 0.4"])
        panels.extend([probability.convert("RGB"), threshold.convert("RGB")])

    thumb_width = 300
    thumb_height = 300
    label_height = 28
    sheet = Image.new(
        "RGB",
        (len(panels) * thumb_width, thumb_height + label_height),
        "white",
    )
    draw = ImageDraw.Draw(sheet)
    for index, (label, panel) in enumerate(zip(labels, panels)):
        panel.thumbnail((thumb_width, thumb_height))
        x = index * thumb_width + (thumb_width - panel.width) // 2
        y = label_height + (thumb_height - panel.height) // 2
        sheet.paste(panel, (x, y))
        draw.text((index * thumb_width + 6, 6), label, fill="black")
    sheet.save(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--images", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    method = Runtime.get().load_program(str(args.model)).load_method("forward")
    rows = []

    for source in sorted(args.images.glob("*.png")):
        original = Image.open(source).convert("RGB")
        rgb = letterbox(original)
        rendered = {}
        for variant in ("rgb", "gray_replicated", "bgr"):
            tensor = torch.from_numpy(variant_input(rgb, variant)[None, ...])
            started = time.perf_counter()
            output = method.execute([tensor])[0].detach().cpu().numpy()
            elapsed_ms = (time.perf_counter() - started) * 1000.0
            probability = output.reshape(384, 384)
            probability_image = Image.fromarray(
                np.uint8(np.clip(probability, 0.0, 1.0) * 255.0), "L"
            )
            threshold_image = Image.fromarray(
                (probability >= 0.4).astype(np.uint8) * 255, "L"
            )
            rendered[variant] = (probability_image, threshold_image)
            for threshold in THRESHOLDS:
                rows.append(
                    {
                        "image": source.name,
                        "variant": variant,
                        "input_layout": "aspect-preserving bicubic fit, 384x384, 128 gray pad",
                        "elapsed_ms": f"{elapsed_ms:.2f}",
                        "min": f"{probability.min():.9g}",
                        "max": f"{probability.max():.9g}",
                        "mean": f"{probability.mean():.9g}",
                        "stddev": f"{probability.std():.9g}",
                        "threshold": f"{threshold:.1f}",
                        "detected_percent": f"{(probability >= threshold).mean() * 100:.6f}",
                    }
                )
            variant_dir = args.output / source.stem / variant
            variant_dir.mkdir(parents=True, exist_ok=True)
            save_probability(probability, variant_dir / "probability.png")
            for threshold in THRESHOLDS:
                save_mask(
                    probability,
                    threshold,
                    variant_dir / f"mask_{threshold:.1f}.png",
                )

        contact_sheet(
            original,
            rendered,
            args.output / source.stem / "contact_sheet.png",
        )

    with (args.output / "results.csv").open("w", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
