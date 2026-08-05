// ─────────────────────────────────────────────────────────────
//  Namma Journey — the world tour data
//  Each "stop" is one chapter of the drive. The car pulls in,
//  the place reveals its photographs, then the car drives on.
// ─────────────────────────────────────────────────────────────

const place = (slug, title) => ({
  title,
  webp: `/images/travel%20places/${slug}.webp`,
  jpg: `/images/travel%20places/${slug}.jpg`,
})

/**
 * The road trip: Kashmir → Kanyakumari, ten chapters.
 * `km` is the odometer reading when the car rolls into the stop.
 */
export const journeyStops = [
  {
    id: 'kashmir',
    chapter: 'Chapter One',
    name: 'Kashmir Valley',
    region: 'Jammu & Kashmir',
    country: 'India',
    tagline: 'Where the road begins, in snow',
    blurb:
      'The engine warms at 1,600 m. Chinar leaves, frozen lakes and the first pass of the Himalaya — the northernmost kilometre of the journey.',
    accent: '#38bdf8',
    glow: 'rgba(56,189,248,0.45)',
    emoji: '🏔️',
    km: 0,
    coord: [34.08, 74.79],
    facts: ['Alt. 1,600 m', 'Best: Apr–Oct', '3 day drive'],
    images: [place('kashmir-valley', 'Kashmir Valley'), place('mountain-peaks-himalayas', 'Himalayan Peaks')],
  },
  {
    id: 'delhi',
    chapter: 'Chapter Two',
    name: 'New Delhi',
    region: 'Delhi & Agra',
    country: 'India',
    tagline: 'Empires, gardens and one marble promise',
    blurb:
      'Down the plains into the capital — the war memorial at dusk, Lodhi tombs in the fog, and a short run east to the Taj at sunrise.',
    accent: '#f59e0b',
    glow: 'rgba(245,158,11,0.45)',
    emoji: '🏛️',
    km: 880,
    coord: [28.61, 77.21],
    facts: ['3 UNESCO sites', 'Best: Oct–Mar', '2 day halt'],
    images: [
      place('india-gate-new-delhi', 'India Gate'),
      place('taj-mahal-new-delhi', 'Taj Mahal'),
      place('lodhi-gardens-new-delhi', 'Lodhi Gardens'),
    ],
  },
  {
    id: 'jaipur',
    chapter: 'Chapter Three',
    name: 'Jaipur',
    region: 'Rajasthan',
    country: 'India',
    tagline: 'The pink city, seen through 953 windows',
    blurb:
      'West into the desert state. Sandstone turns rose at four in the evening and the whole city looks like it was cut from one block.',
    accent: '#fb7185',
    glow: 'rgba(251,113,133,0.45)',
    emoji: '🕌',
    km: 1150,
    coord: [26.91, 75.78],
    facts: ['953 windows', 'Best: Nov–Feb', 'Desert route'],
    images: [place('hawa-mahal-jaipur', 'Hawa Mahal'), place('naqqar-khana-jaipur', 'Naqqar Khana')],
  },
  {
    id: 'varanasi',
    chapter: 'Chapter Four',
    name: 'Kashi Vishwanath',
    region: 'Varanasi, Uttar Pradesh',
    country: 'India',
    tagline: 'The oldest living city on earth',
    blurb:
      'East along the Ganga. Lamps go into the river at dusk and the ghats never quite go quiet — the spiritual midpoint of the drive.',
    accent: '#f97316',
    glow: 'rgba(249,115,22,0.5)',
    emoji: '🛕',
    km: 1790,
    coord: [25.31, 83.01],
    facts: ['3,000 yrs old', '88 ghats', 'Dawn aarti'],
    images: [place('kashi-vishwanath', 'Kashi Vishwanath Ghats')],
  },
  {
    id: 'meghalaya',
    chapter: 'Chapter Five',
    name: 'Nohkalikai Falls',
    region: 'Meghalaya',
    country: 'India',
    tagline: 'The wettest turn on the map',
    blurb:
      'A detour to the north-east, where cloud sits below the road and India\'s tallest plunge waterfall drops 340 m into a green bowl.',
    accent: '#22c55e',
    glow: 'rgba(34,197,94,0.45)',
    emoji: '💧',
    km: 3260,
    coord: [25.28, 91.68],
    facts: ['340 m drop', 'Monsoon route', 'Living bridges'],
    images: [place('nohkalikai-falls', 'Nohkalikai Falls'), place('ripple-waterfalls', 'Ripple Waterfalls')],
  },
  {
    id: 'mumbai',
    chapter: 'Chapter Six',
    name: 'Mumbai',
    region: 'Maharashtra',
    country: 'India',
    tagline: 'Sea link, skyline, second wind',
    blurb:
      'Back across the country to the west coast. The car joins eight million others and the Arabian Sea shows up on the right for the first time.',
    accent: '#818cf8',
    glow: 'rgba(129,140,248,0.45)',
    emoji: '🌆',
    km: 5140,
    coord: [19.08, 72.88],
    facts: ['Coastal drive', 'Night city', '24h fuel'],
    images: [place('mumbai-skyline', 'Mumbai Skyline')],
  },
  {
    id: 'hampi',
    chapter: 'Chapter Seven',
    name: 'Hampi & Karnataka',
    region: 'Karnataka',
    country: 'India',
    tagline: 'A stone chariot in a boulder field',
    blurb:
      'South through the Deccan. Vijayanagara ruins, the Pattadakal cluster, a palace lit by 100,000 bulbs and a temple standing in the sea.',
    accent: '#a78bfa',
    glow: 'rgba(167,139,250,0.45)',
    emoji: '🗿',
    km: 5760,
    coord: [15.34, 76.46],
    facts: ['UNESCO site', '1,600 monuments', 'Boulder roads'],
    images: [
      place('vijaya-vittala-temple-hampi', 'Vijaya Vittala Temple'),
      place('mysore-palace', 'Mysore Palace'),
      place('pattadakal-temples-karnataka', 'Pattadakal Temples'),
      place('murudeshwar-temple-karnataka', 'Murudeshwar Temple'),
    ],
  },
  {
    id: 'tamilnadu',
    chapter: 'Chapter Eight',
    name: 'Tamil Nadu',
    region: 'Thanjavur · Mahabalipuram',
    country: 'India',
    tagline: 'Granite towers and a shore that carves',
    blurb:
      'The temple state. A 1,000-year-old vimana at Thanjavur, rock-cut shrines on the Coromandel shore, and gold leaf at Vellore.',
    accent: '#fbbf24',
    glow: 'rgba(251,191,36,0.5)',
    emoji: '🕉️',
    km: 6480,
    coord: [10.79, 79.13],
    facts: ['1,000 yrs old', '4 UNESCO sites', 'Coastal ECR'],
    images: [
      place('brihadisvara-temple-thanjavur', 'Brihadisvara Temple'),
      place('mahabalipuram', 'Mahabalipuram Shore'),
      place('sripuram-golden-temple-vellore', 'Sripuram Golden Temple'),
      place('matrimandir-auroville', 'Matrimandir'),
      place('tamil-nadu-heritage', 'Tamil Nadu Heritage'),
    ],
  },
  {
    id: 'kerala',
    chapter: 'Chapter Nine',
    name: 'Kerala',
    region: "God's Own Country",
    country: 'India',
    tagline: 'Swap the wheels for a backwater',
    blurb:
      'The west coast again, softer this time. Palm-lined canals at Alappuzha, a cliff beach at Varkala, and gold vaults under a temple floor.',
    accent: '#2dd4bf',
    glow: 'rgba(45,212,191,0.45)',
    emoji: '🌴',
    km: 7010,
    coord: [9.5, 76.34],
    facts: ['900 km canals', 'Monsoon green', 'Houseboat leg'],
    images: [
      place('alappuzha-kerala', 'Alappuzha Backwaters'),
      place('varkala-beach-kerala', 'Varkala Beach'),
      place('sree-padmanabhaswamy-temple', 'Padmanabhaswamy Temple'),
    ],
  },
  {
    id: 'kanyakumari',
    chapter: 'Final Chapter',
    name: 'Kanyakumari',
    region: "Land's End, Tamil Nadu",
    country: 'India',
    tagline: 'Three seas, one horizon, road over',
    blurb:
      'The last kilometre of the subcontinent. Three waters meet, the statue faces the sunrise, and the odometer finally stops.',
    accent: '#f472b6',
    glow: 'rgba(244,114,182,0.45)',
    emoji: '🌅',
    km: 7420,
    coord: [8.08, 77.55],
    facts: ['3 seas meet', 'Sunrise + sunset', 'Journey complete'],
    images: [place('thiruvalluvar-statue-kanyakumari', 'Thiruvalluvar Statue')],
  },
]

export const totalKm = journeyStops[journeyStops.length - 1].km

/**
 * Every photograph is a landmark standing beside the road. The car passes them
 * one after another — `side` puts it out of the left or the right window, and
 * `km` is the odometer reading as you draw level with it.
 */
export const landmarks = journeyStops.flatMap((stop, stopIndex) => {
  const next = journeyStops[Math.min(journeyStops.length - 1, stopIndex + 1)]
  return stop.images.map((img, photoIndex) => ({
    id: `${stop.id}-${photoIndex}`,
    title: img.title,
    img,
    stop,
    stopIndex,
    photoIndex,
    photoCount: stop.images.length,
    firstOfStop: photoIndex === 0,
    km: Math.round(stop.km + (next.km - stop.km) * (photoIndex / stop.images.length)),
  }))
}).map((lm, i) => ({ ...lm, index: i, side: i % 2 === 0 ? 'left' : 'right' }))

/** Index of the first landmark of each stop — used by the itinerary rail. */
export const stopEntryIndex = journeyStops.map((s) =>
  landmarks.findIndex((lm) => lm.stop.id === s.id)
)

/** What you can actually book — cars, not flights. */
export const fleet = [
  {
    id: 'hatchback',
    name: 'Hatchback',
    examples: 'Swift · WagonR · i10',
    seats: 4,
    bags: 2,
    rate: 11,
    emoji: '🚗',
    best: 'City runs & short hops',
  },
  {
    id: 'sedan',
    name: 'Sedan',
    examples: 'Dzire · Etios · Aura',
    seats: 4,
    bags: 3,
    rate: 13,
    emoji: '🚙',
    best: 'Outstation comfort',
    popular: true,
  },
  {
    id: 'suv',
    name: 'SUV',
    examples: 'Ertiga · Innova · XL6',
    seats: 7,
    bags: 4,
    rate: 16,
    emoji: '🚐',
    best: 'Families & hill roads',
  },
  {
    id: 'tempo',
    name: 'Tempo Traveller',
    examples: '12 & 17 seater',
    seats: 17,
    bags: 10,
    rate: 24,
    emoji: '🚌',
    best: 'Group pilgrimages',
  },
]

/** The kinds of trips people actually book a car for. */
export const tripTypes = [
  { icon: '🛕', label: 'Temple & pilgrimage', note: 'Tirupati, Shirdi, Kedarnath' },
  { icon: '🛣️', label: 'Outstation', note: 'One-way or round trip' },
  { icon: '⏱️', label: 'Hourly rental', note: 'From 1 hour to a full day' },
  { icon: '⛰️', label: 'Hill stations', note: 'Ooty, Munnar, Manali' },
  { icon: '💍', label: 'Weddings & events', note: 'Multiple cars, one booking' },
  { icon: '🏢', label: 'Corporate travel', note: 'Monthly billing available' },
]
