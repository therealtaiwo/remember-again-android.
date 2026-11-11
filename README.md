# Remember

Offline Android photo restoration, starting with BOPBTL scratch detection.

## Current milestone

The native Kotlin app imports a compiled ExecuTorch `.pte` model and selects a
photo. Runtime integration is compiled, but the current preprocessing still
targets the earlier detector contract and is not yet compatible with the
pinned model. The original bitmap is never overwritten.

The app uses `org.pytorch:executorch-android:1.4.0`. The Android AAR includes
the XNNPACK CPU backend; this project does not use a network service or upload
image data.

## Model status

The public
[`mlx-community/BOPBTL-scratch-detection-fp32-mlx`](https://huggingface.co/mlx-community/BOPBTL-scratch-detection-fp32-mlx)
asset is a 150.6 MB MLX safetensors checkpoint, not an ExecuTorch module. It
cannot be loaded by this app directly. A compatible `.pte` must be generated
from the original BOPBTL network and weights, then imported with **Import
BOPBTL .pte model**. No model is bundled or downloaded by the app.

The pinned BOPBTL int8 model has been inspected with ExecuTorch 1.4 and
exposes one `forward` method with static float32 input `[1, 3, 384, 384]` and
float32 output `[1, 1, 384, 384]`. The current UI preprocessing is not yet
wired to this contract: it still produces grayscale `[1, 1, H, W]` input.
The model card does not document the RGB value range/normalization or whether
the output is already a probability, so those must be confirmed by comparing
against the publisher's reference app before changing preprocessing.

The exact pinned model was downloaded for local verification only and is not
committed or bundled in the APK. Its SHA-256 is
`651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5`.

## Build

The environment must provide Android SDK platform 35 and build-tools. From
the repository root:

```bash
gradle :app:assembleDebug
```

The current Codespaces environment has Java and Gradle but did not expose an
Android SDK or `adb`, so an APK/device inference run has not yet been claimed.
The target device should be tested with the arm64-v8a APK and the resulting
inference time and memory measured separately from build success.