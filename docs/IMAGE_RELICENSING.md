# Relicensing the bundled images — free, no purchase

[ASSETS.md](../ASSETS.md) records that the photographs shipped under `frontend/public/images/`
have unconfirmed provenance, and that until that is resolved the image set is **not**
redistributable under this repository's MIT licence. This file is the plan for resolving it
without spending anything.

## Why buying a stock licence would not fix it

Freepik, Shutterstock, Adobe Stock and Envato licence an image **for use in an end product**.
They almost universally forbid redistributing the image file *as a file*. A public git repository
does exactly that — every `git clone` hands someone the raw `.jpg`.

So a paid subscription would licence the deployed app and still leave the repository
non-redistributable. The licence this project actually needs is one that permits redistribution:
**CC0, public domain, Unsplash, or Pexels**. All are free. Free is not the budget option here, it
is the correct one.

## Sources that are safe for an open-source repository

| Source | Cost | Attribution | Redistribution |
|---|---|---|---|
| [Unsplash](https://unsplash.com) | Free | Not required | Permitted |
| [Pexels](https://pexels.com) | Free | Not required | Permitted |
| [Wikimedia Commons](https://commons.wikimedia.org) — filter to **public domain** or **CC0** | Free | Varies by file | Permitted |
| [Storyset](https://storyset.com) | Free | **Required** | Permitted |

Wikimedia is the best fit for the destination photographs: every landmark below is a famous
Indian monument with public-domain photography available.

When downloading, **record the URL and licence immediately** — that is the row you will add to
ASSETS.md, and it is impossible to reconstruct later. That is the mistake this file exists to
undo.

---

## Task 1 — delete what is not used (already free)

- [x] `india-3d-map.jfif` — referenced by no source file. Deleted rather than researched.

## Task 2 — the illustration needs a credit, not a replacement

- [ ] `GPS navigator-cuate.png` — this is **Storyset** (the `-cuate` suffix is their own naming).
      Storyset is free and redistributable, but requires attribution. Keep the file and add a
      visible credit — "Illustration by [Storyset](https://storyset.com)" — in the footer or an
      About section. Used by `pages/driver/DriverDashboard.jsx` and `pages/user/UserDashboard.jsx`.

Doing this costs nothing and resolves one of the 35 images outright.

## Task 3 — replace the 9 remaining root images

These have hyphenated descriptive filenames characteristic of Freepik. Replace each with an
Unsplash or Pexels equivalent, keeping the **same filename** so no code changes are needed — or
rename and update the references listed beside each.

| File | Used by | Search for a free replacement |
|---|---|---|
| `backpacker-standing-sunrise-viewpoint-...-thailand.jpg` | LandingPage, OwnerDashboard, SignupPage, UserDashboard | "backpacker sunrise viewpoint mountain" |
| `beautiful-shot-lodhi-garden-delhi-india-cloudy-sky.jpg` | OwnerDashboard, UserDashboard | "Lodhi Garden Delhi" |
| `car image.jpeg` | DriverDashboard | "sedan car side view" |
| `city-view-from-mountain-hill (1).jpg` | DriverDashboard, UserDashboard | "city view from hill" |
| `indian-city-buildings-scene.jpg` | DriverDashboard, OwnerDashboard | "Indian city street buildings" |
| `indian-hindu-temple-singapore.jpg` | LoginPage, OwnerDashboard, UserDashboard | "Hindu temple gopuram" |
| `palace-king-mahal-kingdom-shiva.jpg` | DriverDashboard, OwnerDashboard, UserDashboard | "Indian palace architecture" |
| `prasart-phimai-ancient-stone-thailand.jpg` | DriverDashboard, OwnerDashboard | "ancient stone temple ruins" |
| `tourist-presenting-something.jpg` | OwnerDashboard | "travel agent presenting" |

`car image.jpeg` was renamed from its original and carries no clue to its source, so replace it
rather than trying to identify it.

## Task 4 — replace the 24 destination photographs

Their original descriptive filenames survive in the `SLUGS` table in
[`frontend/scripts/optimize-destinations.mjs`](../frontend/scripts/optimize-destinations.mjs), so
each one is identifiable. Search Wikimedia Commons for the landmark, filter the licence to public
domain or CC0, and download.

| Served slug | Original filename — what to search for |
|---|---|
| `alappuzha-kerala` | Alappuzha, Kerala (backwaters) |
| `brihadisvara-temple-thanjavur` | Brihadisvara Temple, Thanjavur |
| `hawa-mahal-jaipur` | Hawa Mahal, Jaipur |
| `india-gate-new-delhi` | India Gate, New Delhi |
| `kashi-vishwanath` | Kashi Vishwanath / Varanasi |
| `kashmir-valley` | Kashmir Valley |
| `lodhi-gardens-new-delhi` | Lodhi Gardens, New Delhi |
| `mahabalipuram` | Mahabalipuram |
| `matrimandir-auroville` | Matrimandir, Auroville, Tamil Nadu |
| `mountain-peaks-himalayas` | Himalayan peaks |
| `mumbai-skyline` | Mumbai skyline |
| `murudeshwar-temple-karnataka` | Murudeshwar Temple, Karnataka |
| `mysore-palace` | Mysore Palace |
| `naqqar-khana-jaipur` | Naqqar Khana, Jaipur |
| `nohkalikai-falls` | Nohkalikai Falls |
| `pattadakal-temples-karnataka` | Pattadakal Temples, Karnataka |
| `ripple-waterfalls` | Ripple Waterfalls |
| `sree-padmanabhaswamy-temple` | Sree Padmanabhaswamy Temple, Thiruvananthapuram |
| `sripuram-golden-temple-vellore` | Sripuram Golden Temple, Vellore |
| `taj-mahal-new-delhi` | Taj Mahal, Agra (the original filename says Delhi; it is in Agra) |
| `tamil-nadu-heritage` | Tamil Nadu heritage architecture |
| `thiruvalluvar-statue-kanyakumari` | Thiruvalluvar Statue, Kanyakumari |
| `varkala-beach-kerala` | Varkala Beach, Kerala |
| `vijaya-vittala-temple-hampi` | Vijaya Vittala Temple, Hampi |

### Regenerating them

Put the downloaded originals in `frontend/images/travel places/` named exactly as the left-hand
column of the `SLUGS` table (the original descriptive names), then:

```bash
cd frontend
npm run optimize:destinations     # or: node scripts/optimize-destinations.mjs
```

That regenerates the `.webp` + `.jpg` pair for each at the size actually served. `frontend/images/`
is gitignored, so the originals stay on your machine and only the optimised output is committed.

## Task 5 — record what you did

For every file replaced, add a row to the tables in [ASSETS.md](../ASSETS.md): file, source URL,
licence, and whether attribution is required. Then delete the **Unverified** section, because it
will no longer be true.

Once that section is gone, the MIT licence on this repository is accurate for the whole tree, and
the repo is fully redistributable.

---

## A note on git history

Replacing the served files does not remove the originals from the repository's history — the
unoptimised source photographs are still reachable in earlier commits, along with a Sketchfab
model of a trademarked car design. See the "A note on git history" section of
[ASSETS.md](../ASSETS.md). Rewriting history is what removes those, and it is worth doing in the
same pass as this work.
