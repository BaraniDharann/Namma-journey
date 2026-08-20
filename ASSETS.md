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

The multi-word, hyphen-separated style of those names is characteristic of Freepik and
similar libraries. None has been confirmed.

### Destination photographs

The 22 destination photographs under `frontend/public/images/travel places/` (each shipped as
a `.webp` with a `.jpg` fallback) were renamed from descriptive originals.

**Their original filenames are not lost.** The full mapping is the `SLUGS` table in
[frontend/scripts/optimize-destinations.mjs](frontend/scripts/optimize-destinations.mjs) —
`taj-mahal-new-delhi` was `Taj Mahal , New Delhi.jpg`, `kashi-vishwanath` was `kasi.jpg`, and
so on for all 22. Those descriptive names are the starting point for a reverse-image or
filename search against the stock libraries.

## Unverified

> **There is a free way out of this.** See
> [docs/IMAGE_RELICENSING.md](docs/IMAGE_RELICENSING.md) for the per-file plan. Buying a stock
> subscription would *not* resolve it — those licences forbid redistributing the file itself,
> which is exactly what a public repository does. CC0 and public-domain replacements do resolve
> it, and cost nothing.

**Provenance for every photograph above still needs to be confirmed by the repository owner.**
Until each row is filled in, treat the image set as *not* redistributable under MIT.

For each file, one of the following resolves it:

1. **Record the source.** Add the provider, URL, licence and required credit line to the table
   above, and render the credit somewhere in the UI if the licence demands it.
2. **Replace it.** Substitute a CC0 / public-domain equivalent (Unsplash, Pexels, Wikimedia
   Commons public-domain) and note that here.
3. **Remove it.** If a photograph is decorative and unattributable, dropping it costs less than
   shipping it.

## A note on git history

Removing a file from the working tree does not remove it from the repository. The ~138 MB of
unoptimised originals under `frontend/images/` were untracked, but every one of them is still
reachable in the object database and is still downloaded by `git clone`. Among them:

- six files named `<photographer>-<id>-unsplash.jpg`, which identify both the source
  (Unsplash) and the individual photograph;
- `Mazda RX-7 by IvOfficial - SnIoWlh7S2.glb`, a Sketchfab model whose standard licence
  requires attribution, and which depicts a trademarked vehicle design;
- a set of map screenshots of unknown origin.

So the licensing exposure described above is not limited to what is currently served — it
covers everything the history still carries. Rewriting history to drop those blobs is what
actually resolves it. See [CONTRIBUTING.md](CONTRIBUTING.md) before attempting that, since it
invalidates every existing clone and fork.

Assets found to be unused were removed rather than researched — the 3D car model above and six
map screenshots, none of which were referenced by any code.
