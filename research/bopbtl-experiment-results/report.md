# Pinned Keepsake BOPBTL experiment report

## Artifact

```text
bopbtl_scratch_t384_xnnpack_int8.pte
repository commit: ecdb76b5852ced5fc1d65a9ba5d916d407f6382b
sha256: 651299699f28ce2e7dedadcc4732137d7731d5cd3b21aeaa1395fbdfb8b232d5
```

The model was executed with ExecuTorch 1.4 and XNNPACK. Every experiment
used the static input shape `[1, 3, 384, 384]` and raw float32 channel values
in `[0, 1]`.

## Serialized graph findings

The PTE execution plan contains one `forward` method with one float32 input
and one float32 output. The XNNPACK delegate graph was decoded using the
ExecuTorch 1.4 XNNPACK schema.

- There is exactly one `XNNSigmoid`.
- It consumes tensor 341 and produces tensor 343.
- The final output transpose consumes 343 and writes the PTE output.
- Therefore, sigmoid is inside the serialized graph and the output is a
  probability-like map, not an unactivated logit.
- The graph begins with an input transpose and quantized XNNPACK conversion.
  No grayscale conversion or mean/std arithmetic occurs before the first
  convolution.
- The first convolution has three input channels and one group, so channels
  are jointly mixed by the network.
- The graph contains four internal resize-bilinear operators, but no
  application-level image tiler or overlap policy can be inferred from them.

The graph inspection cannot identify the intended host-side RGB ordering or
normalization constants. Those remain export-wrapper/reference-app questions.

## Experiment layout

The upstream damaged images were fit into a 384x384 canvas with bicubic
interpolation while preserving aspect ratio. Unused canvas pixels were filled
with neutral gray (`128/255`). This is a controlled static-shape experiment,
not evidence that Yesteryear uses letterboxing or that it uses overlapping
tiles.

Variants:

- `rgb`: source RGB channel order.
- `gray_replicated`: luminance grayscale replicated into all three channels.
- `bgr`: source channels reversed.

For each variant, probability maps and masks at 0.1, 0.2, 0.3, and 0.4 are
stored in each image directory. The CSV contains full ranges and distributions.

## Percentage of pixels detected

| Image | Variant | >=0.1 | >=0.2 | >=0.3 | >=0.4 |
|---|---|---:|---:|---:|---:|
| a.png | RGB | 1.131% | 0.836% | 0.637% | 0.458% |
| a.png | Gray replicated | 6.009% | 4.567% | 3.882% | 3.440% |
| a.png | BGR | 5.767% | 4.386% | 3.739% | 3.312% |
| b.png | RGB | 0.730% | 0.316% | 0.178% | 0.122% |
| b.png | Gray replicated | 10.147% | 9.157% | 8.551% | 8.119% |
| b.png | BGR | 10.134% | 9.111% | 8.514% | 8.039% |
| c.png | RGB | 3.111% | 2.357% | 2.011% | 1.725% |
| c.png | Gray replicated | 2.384% | 2.155% | 2.008% | 1.894% |
| c.png | BGR | 2.362% | 2.087% | 1.920% | 1.815% |
| d.png | RGB | 0.551% | 0.298% | 0.176% | 0.119% |
| d.png | Gray replicated | 10.838% | 8.860% | 7.811% | 7.027% |
| d.png | BGR | 10.927% | 8.881% | 7.789% | 7.003% |

The RGB, grayscale-replicated, and BGR results differ materially. This
confirms that choosing a channel convention is not a harmless implementation
detail. These experiments do not establish which convention matches the
private exporter.

## Visual outputs

Each image has a contact sheet containing the original, RGB probability and
threshold views, grayscale-replicated probability and threshold views, and BGR
probability and threshold views:

- [a contact sheet](./a/contact_sheet.png)
- [b contact sheet](./b/contact_sheet.png)
- [c contact sheet](./c/contact_sheet.png)
- [d contact sheet](./d/contact_sheet.png)

Raw per-variant outputs are under the corresponding image directory. The
machine-readable table is [results.csv](./results.csv).

## Original BOPBTL comparison

The published Microsoft implementation confirms grayscale input, `[-1, 1]`
normalization, short-side 256 scaling, multiples of 16, external sigmoid, and
threshold 0.4. Its original checkpoint was not downloaded, as requested, and
is not present in the workspace. Therefore, original-checkpoint masks and
pixel-level comparisons could not be produced.

No Android preprocessing was changed. The evidence is sufficient to say that
the serialized Keepsake graph includes sigmoid and jointly processes three
channels, but it is not sufficient to select RGB/BGR/grayscale-replicated
preprocessing, letterboxing versus tiling, or host normalization for
Remember.
