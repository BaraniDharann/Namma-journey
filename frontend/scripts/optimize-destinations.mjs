import sharp from 'sharp'
import fs from 'fs/promises'
import path from 'path'
// One-off asset pipeline: converts the unoptimised source photos into the .webp +
// .jpg pairs actually served from public/images/.
//
// SRC is frontend/images/, which is NOT in git — it is ~138 MB of originals kept on
// the author's machine only (see .gitignore). This script therefore does not run on a
// fresh clone, and does not need to: its output is committed. It is kept for the next
// time the destination set changes.

const SRC = path.resolve('images/travel places')
const DST = path.resolve('public/images/travel places')

// The source folder is gitignored, so it is absent on a fresh clone. Say so plainly rather
// than failing with an ENOENT stack trace — anyone running this is most likely following
// docs/IMAGE_RELICENSING.md and needs to know what to put where.
try {
  await fs.access(SRC)
} catch {
  console.error(`No source images found at:
  ${SRC}
`)
  console.error('That folder is gitignored, so it does not exist on a fresh clone. Put the')
  console.error('original photographs there, named as the left-hand column of the SLUGS table')
  console.error('below, then run this again. See docs/IMAGE_RELICENSING.md.')
  process.exit(1)
}

await fs.mkdir(DST, { recursive: true })

// Map original filenames -> safe ASCII slugs (no spaces, commas, parens — safe for URLs).
const SLUGS = {
  'Alappuzha keralam': 'alappuzha-kerala',
  'Brihadisvara Temple thanjavur': 'brihadisvara-temple-thanjavur',
  'Hawa Mahal jaipur': 'hawa-mahal-jaipur',
  'India Gate, an iconic war memorial located in New Delhi': 'india-gate-new-delhi',
  'Kashmir Valley': 'kashmir-valley',
  'Lodhi Gardens in New Delhi': 'lodhi-gardens-new-delhi',
  'Mahabalipuram': 'mahabalipuram',
  'Matrimandir, which is the spiritual heart of the experimental international township of Auroville, located in Tamil Nadu': 'matrimandir-auroville',
  'Mumbai': 'mumbai-skyline',
  'Murudeshwar Temple located in Karnataka': 'murudeshwar-temple-karnataka',
  'Mysore Palace': 'mysore-palace',
  'Naqqar Khana jaipur': 'naqqar-khana-jaipur',
  'Nohkalikai Falls': 'nohkalikai-falls',
  'Pattadakal Temples in karnataka': 'pattadakal-temples-karnataka',
  'Ripple Waterfalls': 'ripple-waterfalls',
  'Sree Padmanabhaswamy Temple in Thiruvananthapuram, Kerala,': 'sree-padmanabhaswamy-temple',
  'Sripuram Golden Temple (Sri Lakshmi Narayani Golden Temple) located in Vellore': 'sripuram-golden-temple-vellore',
  'Taj Mahal , New Delhi': 'taj-mahal-new-delhi',
  'Tamilnadu': 'tamil-nadu-heritage',
  'Thiruvalluvar Statue and the Vivekananda Rock Memorial in Kanyakumari': 'thiruvalluvar-statue-kanyakumari',
  'Varkala Beach in Kerala': 'varkala-beach-kerala',
  'Vijaya Vittala Temple complex in Hampi': 'vijaya-vittala-temple-hampi',
  'kasi': 'kashi-vishwanath',
  'mountianes': 'mountain-peaks-himalayas',
}

const entries = await fs.readdir(SRC)
const images = entries.filter(f => /\.(jpe?g|png|webp)$/i.test(f))

let totalIn = 0
let totalOut = 0

for (const file of images) {
  const baseName = file.replace(/\.[^.]+$/, '')
  const slug = SLUGS[baseName]
  if (!slug) {
    console.warn(`SKIP — no slug mapping for: ${baseName}`)
    continue
  }

  const inPath = path.join(SRC, file)
  const outJpg = path.join(DST, `${slug}.jpg`)
  const outWebp = path.join(DST, `${slug}.webp`)

  const inStat = await fs.stat(inPath)
  totalIn += inStat.size

  const pipeline = sharp(inPath, { failOn: 'none' })
    .rotate()
    .resize({ width: 720, height: 960, fit: 'cover', position: 'attention' })

  await pipeline.clone().jpeg({ quality: 72, progressive: true, mozjpeg: true }).toFile(outJpg)
  await pipeline.clone().webp({ quality: 70, effort: 5 }).toFile(outWebp)

  const jpgStat = await fs.stat(outJpg)
  const webpStat = await fs.stat(outWebp)
  totalOut += jpgStat.size + webpStat.size

  console.log(`${slug.padEnd(36)}  ${(inStat.size / 1024).toFixed(0).padStart(6)} KB  ->  ${(jpgStat.size / 1024).toFixed(0)} KB jpg + ${(webpStat.size / 1024).toFixed(0)} KB webp`)
}

console.log(`\nTotal: ${(totalIn / 1024 / 1024).toFixed(1)} MB  ->  ${(totalOut / 1024 / 1024).toFixed(1)} MB`)
