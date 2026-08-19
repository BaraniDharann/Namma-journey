# Third-party assets

The MIT licence in [LICENSE](LICENSE) covers the **source code** of this repository. It does
not cover the photographs and illustrations under `frontend/public/images/`, which are
third-party works redistributed here under their own terms.

This file records what those terms are. **It is not complete** — see
[Unverified](#unverified) below.

## Why this matters

Most free stock providers (Freepik, Storyset, Unsplash, Pexels, Wikimedia) grant broad reuse
but attach conditions: attribution, a ban on redistributing the asset as a standalone file, or
a ban on commercial use. Shipping such a file inside an MIT-licensed repository asserts that
downstream users may do anything they like with it, which is a claim the repository is not in a
position to make. Recording the real provenance here is what keeps the two consistent.

## Illustrations

| File | Source | Licence | Attribution required |
|---|---|---|---|
| `GPS navigator-cuate.png` | Storyset (the `-cuate` suffix is Storyset's own naming) | Storyset Free Licence | **Yes** — "Illustration by Storyset" with a link back |

## Photographs

The files below are served by the application. Their filenames are unmodified provider slugs,
which points to a stock library rather than original photography:

- `backpacker-standing-sunrise-viewpoint-ja-bo-village-mae-hong-son-province-thailand.jpg`
- `beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg`
- `city-view-from-mountain-hill (1).jpg`
- `indian-city-buildings-scene.jpg`
- `indian-hindu-temple-singapore.jpg`
- `palace-king-mahal-kingdom-shiva.jpg`
- `prasart-phimai-ancient-stone-thailand.jpg`
- `tourist-presenting-something.jpg`
- `car image.jpeg`
- `india-3d-map.jfif`

The 22 destination photographs under `frontend/public/images/travel places/` (each shipped as a
`.webp` with a `.jpg` fallback) were renamed from descriptive originals by
`frontend/scripts/optimize-destinations.mjs`, so their filenames no longer carry any clue to
their source.

## Unverified

**Provenance for every photograph above still needs to be confirmed by the repository owner.**
Nobody but the person who downloaded them can say where they came from. Until each row is
filled in, treat the image set as *not* redistributable under MIT.

For each file, one of the following resolves it:

1. **Record the source.** Add the provider, URL, licence and required credit line to the table
   above, and render the credit somewhere in the UI if the licence demands it.
2. **Replace it.** Substitute a CC0 / public-domain equivalent (Unsplash, Pexels, Wikimedia
   Commons public-domain) and note that here.
3. **Remove it.** If a photograph is decorative and unattributable, dropping it costs less than
   shipping it.

Assets found to be unused have already been removed rather than researched — a 3D car model
(`car.glb`, from Sketchfab, where the standard licence requires attribution) and six map
screenshots, none of which were referenced by any code.
