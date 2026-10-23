// City rides come by bike, auto or car; Trips always by car. One place for how each is named and drawn.
// maxPassengers mirrors VehicleType.maxPassengers on the server, which has the final say.
export const VEHICLES = {
  BIKE: { label: 'Bike', icon: 'bike', blurb: 'Quick and cheap, 1 rider', maxPassengers: 1 },
  AUTO: { label: 'Auto', icon: 'auto', blurb: 'Up to 3 passengers', maxPassengers: 3 },
  CAR: { label: 'Car', icon: 'car', blurb: 'Comfort, up to 7', maxPassengers: 7 },
}

export const VEHICLE_ORDER = ['BIKE', 'AUTO', 'CAR']

export const vehicleLabel = (type) => VEHICLES[type]?.label || 'Car'
export const vehicleIcon = (type) => VEHICLES[type]?.icon || 'car'

/** "City ride · Auto" or "Trip". */
export const modeLabel = (b) =>
  b?.serviceMode === 'CITY' ? `City ride · ${vehicleLabel(b.vehicleType)}` : 'Trip'
