# Self-hosted OSRM routing

The public OSRM demo server (`router.project-osrm.org`) is rate-limited and not meant for
production traffic. `docker-compose.prod.yml` runs your own OSRM instead, which the backend reaches at
`http://osrm:5000` and the browser reaches through nginx at `/osrm`.

OSRM needs its map data prepared **once**, before the `osrm` service can start, and again whenever
you want fresher roads (monthly is plenty).

## 1. Pick a region

Smaller extracts prepare faster and need less memory. Use the smallest one that covers your cities.
India extracts are on [Geofabrik](https://download.geofabrik.de/asia/india.html).

| Extract | Covers | RAM to prepare (rough) |
|---|---|---|
| `southern-zone` | Tamil Nadu, Kerala, Karnataka, AP, Telangana, Puducherry | 6–8 GB |
| `india` | Whole country | 24 GB+ |

## 2. Prepare it

Run these from the repository root. The output goes to `./osrm-data` (or set `OSRM_DATA_DIR`).

```bash
mkdir -p osrm-data
curl -L -o osrm-data/region.osm.pbf \
  https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf

IMG=ghcr.io/project-osrm/osrm-backend:v5.27.1
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-partition /data/region.osrm
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-customize /data/region.osrm
```

The file names must stay `region.*`: the compose service starts `osrm-routed /data/region.osrm`.

## 3. Start and check

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d osrm
# Chennai Central -> Marina Beach; expect "code":"Ok"
docker compose exec osrm wget -qO- \
  "http://localhost:5000/route/v1/driving/80.2757,13.0827;80.2830,13.0500?overview=false"
```

## 4. Refresh the roads

Repeat step 2 into a new folder, point `OSRM_DATA_DIR` at it, then
`docker compose ... up -d osrm`. The old data keeps serving until the switch.
