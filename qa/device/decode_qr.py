#!/usr/bin/env python3
"""
decode_qr.py — decode the QR of a screenshot into a text file, no trial and error.

  python3 qa/device/decode_qr.py <screenshot.png> <out.txt>

Tries, in order: full image, the QR view bounds, padded / scaled crops, with both OpenCV
detectors. Writes the decoded text verbatim (rule 8) and prints "<bytes> <out.txt>".
Exit code 1 when nothing decodes (re-take the screenshot).
"""
import sys

import cv2


def candidates(img):
    h, w = img.shape[:2]
    yield img
    x1, y1, x2, y2 = 81, 1228, 927, 2074          # bounds of the qr_image view (Pixel 9 Pro XL)
    for pad in (0, 10, 20, 30, 40, -10):
        yield img[max(0, y1 - pad):min(h, y2 + pad), max(0, x1 - pad):min(w, x2 + pad)]
    base = img[max(0, y1 - 20):min(h, y2 + 20), max(0, x1 - 20):min(w, x2 + 20)]
    for scale in (0.75, 1.5, 2.0):
        yield cv2.resize(base, (0, 0), fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    yield cv2.copyMakeBorder(base, 60, 60, 60, 60, cv2.BORDER_CONSTANT, value=[255, 255, 255])


def decode(img):
    detectors = [cv2.QRCodeDetector()]
    if hasattr(cv2, "QRCodeDetectorAruco"):
        detectors.insert(0, cv2.QRCodeDetectorAruco())
    for crop in candidates(img):
        for det in detectors:
            try:
                val, _, _ = det.detectAndDecode(crop)
            except cv2.error:
                continue
            if val:
                return val
    return ""


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    img = cv2.imread(sys.argv[1])
    if img is None:
        sys.exit(f"cannot read {sys.argv[1]}")
    text = decode(img)
    if not text:
        sys.exit(f"no QR decoded in {sys.argv[1]}")
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        f.write(text)
    print(len(text.encode("utf-8")), sys.argv[2])


if __name__ == "__main__":
    main()
