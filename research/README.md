# BOPBTL contract research

This folder records experiments against the pinned model:

```text
bopbtl_scratch_t384_xnnpack_int8.pte
commit: ecdb76b5852ced5fc1d65a9ba5d916d407f6382b
sha256: 651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5
```

## Reproduction

The experiment runner requires Python, Pillow, PyTorch, and ExecuTorch 1.4:

```bash
python research/run_bopbtl_experiments.py \
  --model /path/to/bopbtl_scratch_t384_xnnpack_int8.pte \
  --images /path/to/old_w_scratch \
  --output research/bopbtl-experiment-results
```

Each image is fit into a 384x384 canvas with bicubic interpolation, preserving
aspect ratio. Remaining pixels are neutral gray (`128/255`). This is a
controlled experiment layout, not a claim about the private Yesteryear tiler.

## Findings

The PTE execution plan has one `forward` method with one float32 input
`[1, 3, 384, 384]` and one float32 output `[1, 1, 384, 384]`. Its XNNPACK
delegate graph contains:

- an input NHWC/NCHW transpose;
- an input conversion to the quantized XNNPACK representation;
- a first 1x1 convolution with three input channels and one group;
- one explicit `XNNSigmoid` immediately before the final output transpose.

This verifies that sigmoid is inside the serialized delegate graph. It also
verifies that there is no input-side grayscale conversion or normalization
arithmetic before the first convolution. The first convolution jointly mixes
the three channels; it is not three independent channel paths.

The graph has no host-side resize or tiling operation. It contains internal
upsampling operations belonging to the neural network, but that does not
establish an application tiler or overlapping-tile policy.

The published Microsoft detector uses grayscale, `[-1, 1]` normalization,
short-side 256 scaling, multiples of 16, external sigmoid, and threshold 0.4.
That is the original model's reference script, not a verified contract for
this RGB 384 export.

The original BOPBTL checkpoint was not downloaded, per the experiment
constraint. Consequently, original-checkpoint mask comparisons are not
available. The downloaded damaged-photo inputs are the upstream public
`test_images/old_w_scratch/{a,b,c,d}.png` files.

## Results

See `bopbtl-experiment-results/results.csv` for ranges, distribution
statistics, and detection percentages at thresholds 0.1, 0.2, 0.3, and 0.4
for RGB, replicated grayscale, and BGR input.

Each image directory contains probability maps, all threshold masks, and a
contact sheet. Contact sheets show the original input and the three Keepsake
variants. No original BOPBTL mask is shown because the original checkpoint was
not available locally.
