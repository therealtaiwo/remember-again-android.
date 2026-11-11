# Visual quality evaluation

## Scope and limitations

This is a qualitative review of the four upstream damaged photographs and the
RGB, replicated-grayscale, and BGR outputs in the generated contact sheets.
It is not a model-accuracy claim.

The official Microsoft BOPBTL repository was checked before expanding the
corpus. Its `test_images/old_w_scratch` directory contains exactly four public
files: `a.png`, `b.png`, `c.png`, and `d.png`. No additional official damaged
photographs were available locally or in that directory, so a ten-image
evaluation could not be completed without introducing an unrelated dataset.

The original BOPBTL checkpoint was not downloaded, so there is no independent
ground-truth mask or original-detector comparison. I also did not create
numerical human annotations: at this resolution, several faint marks and
photographic texture are ambiguous, and drawing masks after seeing the model
outputs would risk biasing the evaluation.

## Image-by-image observations

### a.png

The source contains visible damage along the right edge, bright curved marks
near the right side of the subject, and scratches near the borders.

- **RGB:** Mostly responds to a narrow vertical line and a few small points.
  It misses the obvious curved/right-side damage.
- **Replicated grayscale:** Highlights the right-side scratches and border
  damage much more clearly. It also produces many thin responses over the
  dark hair/background and around small bright details.
- **BGR:** Visually very close to replicated grayscale. It captures the same
  right-side and border structures, with slightly different fine responses.

The grayscale/BGR outputs are more useful for the visible scratches, but they
also create false positives in hair and textured background. The RGB output is
conservative but clearly under-detects this image.

**Misses:** curved/right-side damage and some border scratches in RGB.
**False positives:** thin responses in hair, dark background texture, and
small bright details in replicated grayscale and BGR.

### b.png

The source has several prominent cracks crossing the upper portion, left and
right borders, and lower edge. The face and clothing contain ordinary edges
that should not be treated as scratches.

- **RGB:** Produces mostly faint vertical responses and misses the major
  diagonal cracks.
- **Replicated grayscale:** Tracks the large diagonal and border cracks
  strongly. It also marks some small interior bright details and border
  structure.
- **BGR:** Nearly the same as replicated grayscale and also follows the major
  cracks. It has small differences around the interior details.

For this image, replicated grayscale and BGR are substantially better aligned
with visible damage than RGB. The strong response on the face is limited
compared with the response on the actual cracks; however, this is a visual
inspection, not a precision measurement.

**Misses:** the major diagonal cracks in RGB.
**False positives:** small interior bright details and some border structure in
replicated grayscale and BGR. The face is not dominated by detections, but
ordinary facial and clothing edges remain a concern.

### c.png

The source has a prominent diagonal crack across the upper-right area, border
damage, and a few faint vertical marks. The child’s face, hair, shirt, and
background contain many normal edges and textures.

- **RGB:** Detects several vertical structures and largely misses the
  diagonal crack.
- **Replicated grayscale:** Captures the diagonal crack and border damage,
  with limited response over the face and clothing.
- **BGR:** Closely follows replicated grayscale and captures the same main
  damage.

This is the clearest example where RGB/BGR channel handling changes whether a
visible crack is highlighted. The grayscale/BGR masks still include border
responses that may be valid damage or may be edge artifacts.

**Misses:** the prominent diagonal crack in RGB.
**False positives:** limited responses around facial, hair, shirt, and
background edges in replicated grayscale and BGR; these require annotations to
classify reliably.

### d.png

The source has extensive cracking across the upper and left portions, around
the couple, and through the background.

- **RGB:** Mostly detects faint vertical lines and a small bright horizontal
  feature. It misses most of the obvious crack network.
- **Replicated grayscale:** Highlights much of the visible crack network,
  including cracks near the subjects and background.
- **BGR:** Nearly matches replicated grayscale and highlights the same network.

The large number of detections is not automatically a false-positive failure:
this source genuinely contains extensive visible damage. There are, however,
some responses near clothing and background texture that require annotation or
reference masks to classify confidently.

**Misses:** most of the crack network in RGB.
**False positives:** some clothing and background texture responses in
replicated grayscale and BGR; detections near the subjects cannot be judged
pixel-accurately from the unannotated source.

## Cross-image comparison

RGB is consistently the least useful visual variant on these four images: it
misses prominent cracks in all four, although it produces fewer ordinary-detail
responses.

Replicated grayscale and BGR are consistently more responsive to the visible
scratch/crack structures. They are visually very similar, with small
differences around facial features, hair, clothing edges, and textured
backgrounds. Neither can be declared superior from these four examples:

- replicated grayscale is marginally stronger on some large crack regions;
- BGR is close enough that the difference may be channel-order sensitivity
  rather than a meaningful quality advantage;
- both can mark borders and ordinary high-contrast detail.

The detected-pixel percentages alone would not establish this conclusion; the
conclusion comes from comparing spatial alignment with visible damage in the
originals.

### Threshold comparison

The following are mean percentages of output pixels at or above each threshold
across the four images. These are response-density measurements, not
precision, recall, or accuracy:

| Variant | 0.1 | 0.2 | 0.3 | 0.4 |
|---|---:|---:|---:|---:|
| RGB | 1.381% | 0.952% | 0.750% | 0.606% |
| Replicated grayscale | 7.345% | 6.185% | 5.563% | 5.120% |
| BGR | 7.298% | 6.116% | 5.491% | 5.043% |

Across all four thresholds, replicated grayscale and BGR produce nearly the
same response density and are much more responsive than RGB. This does not
show that their additional detections are correct; the image review above
shows both genuine crack responses and ordinary-detail responses.

### Diversity and facial-region assessment

The available set covers prominent scratches and cracks, border damage,
extensive crack networks, and photographs containing faces, hair, clothing,
and textured backgrounds. It does not provide a balanced ten-or-more-image
sample, nor does it provide enough independent examples to establish behavior
on stains or faded photographs as separate damage classes.

On the available faces, replicated grayscale and BGR generally preserve
stronger responses on visible cracks than on the main facial regions, but they
still produce localized responses around facial highlights, hair, clothing
edges, and background texture. RGB produces fewer such responses largely
because it misses more visible damage. No variant consistently avoids
face-adjacent false positives while retaining the observed crack responses.

## Human annotation status

Approximate numerical precision, recall, and IoU were **not** reported. A
defensible annotation would need a separate mask for every visibly damaged
pixel or line, including decisions about:

- border wear versus frame edges;
- faint scratches versus film grain;
- cracks crossing faces or clothing;
- texture that is damaged but not line-shaped.

The current four low-resolution images are sufficient for qualitative review,
but not for reliable pixel-level annotation without introducing substantial
subjectivity. Reporting metrics from ad-hoc lines would create false
precision.

## Next validation step

Do not select RGB, BGR, or replicated grayscale for Android integration solely
from this visual sample. The most defensible next step is to obtain the
Keepsake/Yesteryear host preprocessing or export script, specifically:

1. channel order and whether grayscale replication is intentional;
2. host value range and normalization;
3. resize versus crop versus letterbox behavior;
4. full-image tiling size and overlap;
5. threshold and any morphology/post-processing.

If that source remains unavailable, create a pre-registered annotation set
from additional damaged and clean photos **before** choosing a variant, then
evaluate all variants against the same masks. The current evidence supports
the hypothesis that replicated grayscale or BGR is more visually plausible
than RGB, but it does not establish the model's intended input contract or
accuracy.

## Recommendation

BOPBTL remains suitable for continued **research integration**, not
production integration. The four official examples show a repeatable signal:
RGB misses prominent cracks, while replicated grayscale and BGR recover more
visible damage. However, the unresolved channel contract, the lack of a
ten-image public corpus, and the absence of independent ground-truth masks
make it premature to choose preprocessing or claim detector accuracy.

Do not switch to another detector solely because this evaluation is small.
First obtain the Keepsake/Yesteryear preprocessing contract or run a
pre-registered, independently annotated corpus of at least ten diverse
images. Investigate an alternative scratch detector in parallel only if that
evidence shows unacceptable face/detail false positives or the intended
Keepsake preprocessing cannot be established.

No Android source was modified for this evaluation.
