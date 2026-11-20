-- Namma Journeys: demo owner for public testers (LinkedIn), with ~7 weeks of history.
-- Run once on production:  psql -v ON_ERROR_STOP=1 -f seed-demo-owner.sql
-- Everything is in one transaction; it refuses to run twice.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL statement_timeout = 0;
SET LOCAL lock_timeout = '10s';

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM owners WHERE lower(email) = 'testowner@nammajourneys.site') THEN
    RAISE EXCEPTION 'Demo owner already exists - nothing changed';
  END IF;
END $$;

SELECT setseed(0.2026);

CREATE TEMP TABLE demo_seats (bus text, label text, deck int, row_no int, col_no int, kind text, berth text, pair_label text, ladies boolean) ON COMMIT DROP;
INSERT INTO demo_seats VALUES
('sleeper','L1',0,0,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','L2',0,0,2,'SLEEPER','DOUBLE','L3',false),
('sleeper','L3',0,0,3,'SLEEPER','DOUBLE','L2',false),
('sleeper','L4',0,2,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','L5',0,2,2,'SLEEPER','DOUBLE','L6',false),
('sleeper','L6',0,2,3,'SLEEPER','DOUBLE','L5',false),
('sleeper','L7',0,4,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','L8',0,4,2,'SLEEPER','DOUBLE','L9',false),
('sleeper','L9',0,4,3,'SLEEPER','DOUBLE','L8',false),
('sleeper','L10',0,6,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','L11',0,6,2,'SLEEPER','DOUBLE','L12',false),
('sleeper','L12',0,6,3,'SLEEPER','DOUBLE','L11',false),
('sleeper','L13',0,8,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','L14',0,8,2,'SLEEPER','DOUBLE','L15',false),
('sleeper','L15',0,8,3,'SLEEPER','DOUBLE','L14',false),
('sleeper','U1',1,0,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','U2',1,0,2,'SLEEPER','DOUBLE','U3',false),
('sleeper','U3',1,0,3,'SLEEPER','DOUBLE','U2',false),
('sleeper','U4',1,2,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','U5',1,2,2,'SLEEPER','DOUBLE','U6',false),
('sleeper','U6',1,2,3,'SLEEPER','DOUBLE','U5',false),
('sleeper','U7',1,4,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','U8',1,4,2,'SLEEPER','DOUBLE','U9',false),
('sleeper','U9',1,4,3,'SLEEPER','DOUBLE','U8',false),
('sleeper','U10',1,6,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','U11',1,6,2,'SLEEPER','DOUBLE','U12',false),
('sleeper','U12',1,6,3,'SLEEPER','DOUBLE','U11',false),
('sleeper','U13',1,8,0,'SLEEPER','SINGLE',NULL,false),
('sleeper','U14',1,8,2,'SLEEPER','DOUBLE','U15',false),
('sleeper','U15',1,8,3,'SLEEPER','DOUBLE','U14',false),
('seater','1',0,0,0,'SEATER',NULL,'2',false),
('seater','2',0,0,1,'SEATER',NULL,'1',false),
('seater','3',0,0,3,'SEATER',NULL,'4',false),
('seater','4',0,0,4,'SEATER',NULL,'3',false),
('seater','5',0,1,0,'SEATER',NULL,'6',false),
('seater','6',0,1,1,'SEATER',NULL,'5',false),
('seater','7',0,1,3,'SEATER',NULL,'8',false),
('seater','8',0,1,4,'SEATER',NULL,'7',false),
('seater','9',0,2,0,'SEATER',NULL,'10',false),
('seater','10',0,2,1,'SEATER',NULL,'9',false),
('seater','11',0,2,3,'SEATER',NULL,'12',false),
('seater','12',0,2,4,'SEATER',NULL,'11',false),
('seater','13',0,3,0,'SEATER',NULL,'14',false),
('seater','14',0,3,1,'SEATER',NULL,'13',false),
('seater','15',0,3,3,'SEATER',NULL,'16',false),
('seater','16',0,3,4,'SEATER',NULL,'15',false),
('seater','17',0,4,0,'SEATER',NULL,'18',false),
('seater','18',0,4,1,'SEATER',NULL,'17',false),
('seater','19',0,4,3,'SEATER',NULL,'20',false),
('seater','20',0,4,4,'SEATER',NULL,'19',false),
('seater','21',0,5,0,'SEATER',NULL,'22',false),
('seater','22',0,5,1,'SEATER',NULL,'21',false),
('seater','23',0,5,3,'SEATER',NULL,'24',false),
('seater','24',0,5,4,'SEATER',NULL,'23',false),
('seater','25',0,6,0,'SEATER',NULL,'26',false),
('seater','26',0,6,1,'SEATER',NULL,'25',false),
('seater','27',0,6,3,'SEATER',NULL,'28',false),
('seater','28',0,6,4,'SEATER',NULL,'27',false),
('seater','29',0,7,0,'SEATER',NULL,'30',false),
('seater','30',0,7,1,'SEATER',NULL,'29',false),
('seater','31',0,7,3,'SEATER',NULL,'32',false),
('seater','32',0,7,4,'SEATER',NULL,'31',false),
('seater','33',0,8,0,'SEATER',NULL,'34',false),
('seater','34',0,8,1,'SEATER',NULL,'33',false),
('seater','35',0,8,3,'SEATER',NULL,'36',false),
('seater','36',0,8,4,'SEATER',NULL,'35',false),
('seater','37',0,9,0,'SEATER',NULL,'38',false),
('seater','38',0,9,1,'SEATER',NULL,'37',false),
('seater','39',0,9,3,'SEATER',NULL,'40',false),
('seater','40',0,9,4,'SEATER',NULL,'39',false)
;

DO $seed$
DECLARE
  h_owner  CONSTANT text := '$2a$10$gT9o4GTRRFLadyydTvva4O5UnskJQeOlNiSaPMPHpv/pHuYT0RcG6'; -- TestOwner@123
  h_driver CONSTANT text := '$2a$10$1L67adZiRcaYXgovN28UNu4EH.fZmkyGvRnfEVkJjEbosx12hYMMW'; -- TestDriver@123
  h_user   CONSTANT text := '$2a$10$pl8ID6gsjvzCWZORefs3zu0.b3hgRmCGvQbqr8xdI7vx5CDIF4Zia'; -- TestUser@123

  today   date := current_date;
  start_d date := (date_trunc('month', current_date) - interval '1 month')::date;  -- 1st of last month
  span    int;

  v_owner bigint;
  v_code  text;
  v_city  bigint;
  v_comm  numeric;

  -- drivers 1,2 = City cars; 3,4,5 = Travel cars
  d_name  text[] := ARRAY['Arun Kumar','Karthik Raja','Suresh Babu','Vignesh Murugan','Prakash Selvam'];
  d_mob   text[] := ARRAY['9000000101','9000000102','9000000103','9000000104','9000000105'];
  d       bigint[] := '{}';
  -- vehicles, same order as drivers
  v_reg   text[] := ARRAY['TN09DM1001','TN09DM1002','TN09DM1003','TN09DM1004','TN09DM1005'];
  v_cat   text[] := ARRAY['SEDAN','HATCHBACK','SUV','TEMPO_TRAVELLER','SEDAN'];
  v_model text[] := ARRAY['Maruti Swift Dzire','Hyundai i20','Toyota Innova Crysta','Force Traveller','Honda City'];
  v_seats int[]  := ARRAY[4,4,7,12,4];
  v       bigint[] := '{}';

  u_name  text[] := ARRAY['Test User','Priya Raman','Rahul Krishnan','Divya Subramanian','Mohammed Irfan','Lakshmi Narayanan','Sanjay Kumar','Ananya Iyer'];
  u_mail  text[] := ARRAY['testuser@nammajourneys.site','priya.demo@example.com','rahul.demo@example.com','divya.demo@example.com','irfan.demo@example.com','lakshmi.demo@example.com','sanjay.demo@example.com','ananya.demo@example.com'];
  u_ph    text[] := ARRAY['9000000201','9000000202','9000000203','9000000204','9000000205','9000000206','9000000207','9000000208'];
  u       uuid[] := '{}';

  -- intercity trips from Chennai
  t_to    text[]   := ARRAY['Puducherry','Mahabalipuram','Tirupati','Vellore','Bengaluru','Kanchipuram','Madurai','Yelagiri'];
  t_lat   float8[] := ARRAY[11.9416,12.6208,13.6288,12.9165,12.9716,12.8342,9.9252,12.5786];
  t_lon   float8[] := ARRAY[79.8083,80.1945,79.4192,79.1325,77.5946,79.7036,78.1198,78.6386];
  t_km    int[]    := ARRAY[150,58,135,140,346,75,462,230];
  t_min   int[]    := ARRAY[210,90,200,180,390,105,480,270];

  -- Chennai localities for City rides
  c_name  text[]   := ARRAY['T Nagar','Anna Nagar','Velachery','Adyar','Guindy','Chennai Airport','Chennai Central','Tambaram','Sholinganallur','Mylapore','Porur','Egmore'];
  c_lat   float8[] := ARRAY[13.0418,13.0850,12.9815,13.0012,13.0067,12.9941,13.0827,12.9249,12.9010,13.0368,13.0382,13.0732];
  c_lon   float8[] := ARRAY[80.2341,80.2101,80.2180,80.2565,80.2206,80.1709,80.2754,80.1000,80.2279,80.2676,80.1565,80.2609];

  feedback text[] := ARRAY['Very punctual and polite driver.','Clean car, smooth drive. Will book again.','Driver knew the route well, good experience.',
                           'Comfortable ride for the family.','On time pickup, safe driving.','Good service, AC worked well throughout.',
                           'Slight delay at pickup but overall fine.','Excellent trip, highly recommended!'];
  bus_fb   text[] := ARRAY['Clean berths and on-time departure.','Good AC and blankets, slept well.','Staff were helpful at the boarding point.',
                           'Comfortable seats, smooth journey.','Bus reached 20 minutes late, otherwise fine.','Value for money, will travel again.'];
  p_name   text[] := ARRAY['Meena','Ravi','Kavya','Arjun','Sneha','Vijay','Deepa','Manoj','Revathi','Hari','Nithya','Gokul','Saranya','Bala','Keerthana','Ashok'];
  p_gender text[] := ARRAY['F','M','F','M','F','M','F','M','F','M','F','M','F','M','F','M'];
  methods  text[] := ARRAY['CASH','UPI','RAZORPAY','UPI','RAZORPAY'];

  i int; k int; r int; n int; j int; dd date;
  b_id uuid; p_id uuid; t_id uuid;
  ts timestamp; te timestamp;
  km float8; mins int; amt numeric; rate numeric; comm numeric; meth text; st text;
  a int; z int; days int; uix int;

  bus_s bigint; bus_t bigint; rt_s bigint; rt_t bigint; rt bigint; bus_cur bigint; bus_kind text;
  st0 bigint; st1 bigint; st2 bigint;
  v_trip bigint; dep timestamp; arr timestamp;
  seats text[]; sidx int; pax int; fs int; ts_ int; seat text; cls text; v_fare numeric; v_gst numeric;
  pkg bigint[] := '{}';
  pol jsonb := '[{"minHours":48,"percent":10},{"minHours":24,"percent":25},{"minHours":12,"percent":50},{"minHours":0,"percent":100}]';
  bal numeric;
BEGIN
  span := today - start_d;
  SELECT id, commission_percent INTO v_city, v_comm FROM cities WHERE name = 'Chennai';
  v_comm := coalesce(v_comm, 10);

  ------------------------------------------------------------------ owner
  v_code := 'NJO-' || lpad(nextval('owner_code_seq')::text, 4, '0');
  INSERT INTO owners (created_at, email, password, role, owner_code, status, travels_name, contact_name, phone,
                      aadhaar_last4, pan_masked, submitted_at, reviewed_at, must_change_password,
                      assignment_mode, fleet_type, bus_commission_percent, bank_account_holder, bank_ifsc, bank_name)
  VALUES (start_d - 6 + time '10:15', 'testowner@nammajourneys.site', h_owner, 'ROLE_OWNER', v_code, 'APPROVED',
          'Namma Demo Travels', 'Demo Owner', '9000000001', '0000', 'XXXXX0000X',
          start_d - 6 + time '10:15', start_d - 5 + time '12:00', false, 'MANUAL', 'BOTH', 8,
          'Namma Demo Travels', 'SBIN0000001', 'State Bank of India')
  RETURNING id INTO v_owner;

  INSERT INTO owner_service_areas (owner_id, area) VALUES (v_owner, 'STATE:Tamil Nadu'), (v_owner, 'CITY:Karnataka:Bengaluru');
  IF v_city IS NOT NULL THEN
    INSERT INTO owner_cities (owner_id, city_id) VALUES (v_owner, v_city);
  END IF;
  INSERT INTO owner_trip_rates (owner_id, category, per_km, per_hour) VALUES
    (v_owner, 'HATCHBACK', 12, 150), (v_owner, 'SEDAN', 14, 180), (v_owner, 'SUV', 18, 250), (v_owner, 'TEMPO_TRAVELLER', 24, 350);

  ------------------------------------------------------------------ drivers + cars
  FOR i IN 1..5 LOOP
    INSERT INTO drivers (created_at, license_number, mobile, name, password, role, status, email, first_login, email_verified,
                         vehicle_type, vehicle_model, vehicle_seats, vehicle_ac, serves_city, serves_trip, fleet,
                         verification_status, submitted_at, reviewed_at, owner_id, aadhaar_last4)
    VALUES (start_d - 4 + time '09:00', 'TN09DEMO' || lpad(i::text, 7, '0'), d_mob[i], d_name[i], h_driver, 'ROLE_DRIVER',
            -- City drivers stay offline so they are never offered real riders' City rides
            CASE WHEN i <= 2 THEN 'INACTIVE' ELSE 'ACTIVE' END,
            'driver' || i || '.demo@example.com', false, true,
            'CAR', v_model[i], v_seats[i], true, i <= 2, true, 'OWNER_FLEET',
            'APPROVED', start_d - 4 + time '09:00', start_d - 4 + time '11:00', v_owner, '000' || i)
    RETURNING id INTO a;
    d := d || a::bigint;
  END LOOP;

  FOR i IN 1..5 LOOP
    INSERT INTO vehicles (owner_id, category, registration_number, model, seats, ac, active, created_at, mode, city_driver_id)
    VALUES (v_owner, v_cat[i], v_reg[i], v_model[i], v_seats[i], true, true, start_d - 4 + time '09:30',
            CASE WHEN i <= 2 THEN 'CITY' ELSE 'TRAVEL' END, CASE WHEN i <= 2 THEN d[i] END)
    RETURNING id INTO a;
    v := v || a::bigint;
    UPDATE drivers SET default_vehicle_id = a, vehicle_number = NULL WHERE id = d[i];
  END LOOP;

  ------------------------------------------------------------------ customers
  FOR i IN 1..8 LOOP
    INSERT INTO users (id, created_at, email, login_type, name, phone, role, password)
    VALUES (gen_random_uuid(), start_d - 10 + (i || ' hours')::interval, u_mail[i], 'EMAIL', u_name[i], u_ph[i], 'ROLE_USER',
            CASE WHEN i = 1 THEN h_user END)
    RETURNING id INTO t_id;
    u := u || t_id;
  END LOOP;

  ------------------------------------------------------------------ past Travel trips
  FOR i IN 0..41 LOOP
    dd := start_d + (i * span) / 42;
    r := 1 + floor(random() * 8)::int;
    k := 3 + (i % 3);
    uix := 1 + floor(random() * 8)::int;
    days := CASE WHEN t_km[r] > 300 THEN 2 ELSE 1 END;
    SELECT per_km INTO rate FROM owner_trip_rates WHERE owner_id = v_owner AND category = v_cat[k];
    amt := round(t_km[r] * 2 * rate / 10) * 10;
    st := CASE WHEN random() < 0.1 THEN 'CANCELLED' ELSE 'COMPLETED' END;
    ts := dd - (1 + floor(random() * 6)::int) + time '10:00' + (floor(random() * 480) || ' minutes')::interval;
    te := dd + days - 1 + time '19:30';
    INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place, to_place, status, travel_days,
                                 user_id, user_name, user_phone, ac_type, from_date, to_date, travel_members, driver_id, total_amount,
                                 from_lat, from_lon, to_lat, to_lon, booking_type, version, dispatch_mode, service_mode, vehicle_type,
                                 owner_id, car_category, vehicle_id, trip_ended_at)
    VALUES (gen_random_uuid(), ts, t_km[r] * 2, t_min[r] * 2, 'Chennai', t_to[r], st, days,
            u[uix], u_name[uix], u_ph[uix], 'AC', dd, dd + days - 1, least(v_seats[k], 2 + floor(random() * 4)::int),
            d[k], amt, 13.0827, 80.2707, t_lat[r], t_lon[r], 'DISTANCE_BASED', 0, 'SCHEDULED', 'TRIP', 'CAR',
            v_owner, v_cat[k], v[k], CASE WHEN st = 'COMPLETED' THEN te END)
    RETURNING id INTO b_id;
    IF st = 'COMPLETED' THEN
      meth := methods[1 + floor(random() * 5)::int];
      INSERT INTO payments (id, amount, booking_id, created_at, driver_id, payment_date, payment_method, status, upi_transaction_id,
                            user_id, verified_date, verified_by, version, razorpay_order_id, razorpay_payment_id, purpose, owner_id)
      VALUES (gen_random_uuid(), amt, b_id, te, d[k], te, meth, 'VERIFIED',
              CASE WHEN meth = 'UPI' THEN 'DEMO' || upper(substr(md5(random()::text), 1, 10)) END,
              u[uix], te + interval '5 minutes', CASE WHEN meth = 'RAZORPAY' THEN 'razorpay' ELSE d_name[k] END, 0,
              CASE WHEN meth = 'RAZORPAY' THEN 'order_demo' || substr(md5(random()::text), 1, 14) END,
              CASE WHEN meth = 'RAZORPAY' THEN 'pay_demo' || substr(md5(random()::text), 1, 14) END, 'FARE', v_owner);
      IF random() < 0.7 THEN
        INSERT INTO reviews (id, booking_id, created_at, driver_id, driver_name, feedback, rating, user_id, user_name, owner_id)
        VALUES (gen_random_uuid(), b_id, te + interval '3 hours', d[k], d_name[k], feedback[1 + floor(random() * 8)::int],
                CASE WHEN random() < 0.65 THEN 5 WHEN random() < 0.85 THEN 4 ELSE 3 END, u[uix], u_name[uix], v_owner);
      END IF;
      INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, spent_on, category, amount, note, created_at)
      VALUES (v_owner, 'CAR', v[k], dd, 'FUEL', round(t_km[r] * 2 * (CASE WHEN k = 4 THEN 9 ELSE 7 END)), 'Diesel - trip to ' || t_to[r], te);
      IF t_km[r] > 100 THEN
        INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, spent_on, category, amount, note, created_at)
        VALUES (v_owner, 'CAR', v[k], dd, 'TOLL', 150 + floor(random() * 6) * 100, 'FASTag tolls - ' || t_to[r], te);
      END IF;
    END IF;
  END LOOP;

  -- car upkeep, once a month per car
  FOR i IN 1..5 LOOP
    INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, spent_on, category, amount, note, created_at) VALUES
      (v_owner, 'CAR', v[i], start_d + 12, 'MAINTENANCE', 2500 + floor(random() * 30) * 100, 'Periodic service', start_d + 12 + time '17:00'),
      (v_owner, 'CAR', v[i], (start_d + interval '1 month')::date - 1, 'DRIVER_PAY', 18000, 'Driver salary - ' || d_name[i], (start_d + interval '1 month')::date - 1 + time '18:00');
  END LOOP;

  ------------------------------------------------------------------ upcoming Travel trips (for testers to manage)
  FOR i IN 1..6 LOOP
    k := (ARRAY[3,4,5,3,0,0])[i];          -- 0 = no driver yet (owner must assign)
    r := (ARRAY[1,3,5,7,2,4])[i];
    dd := today + (ARRAY[2,4,6,11,3,8])[i];
    uix := (ARRAY[1,2,3,4,5,1])[i];
    days := CASE WHEN t_km[r] > 300 THEN 2 ELSE 1 END;
    SELECT per_km INTO rate FROM owner_trip_rates WHERE owner_id = v_owner
       AND category = CASE WHEN k = 0 THEN (ARRAY['SEDAN','SUV'])[1 + i % 2] ELSE v_cat[k] END;
    amt := round(t_km[r] * 2 * rate / 10) * 10;
    INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place, to_place, status, travel_days,
                                 user_id, user_name, user_phone, ac_type, from_date, to_date, travel_members, driver_id, total_amount,
                                 from_lat, from_lon, to_lat, to_lon, booking_type, version, dispatch_mode, service_mode, vehicle_type,
                                 owner_id, car_category, vehicle_id)
    VALUES (gen_random_uuid(), now() - ((i * 7) || ' hours')::interval, t_km[r] * 2, t_min[r] * 2, 'Chennai', t_to[r],
            CASE WHEN k = 0 THEN 'PENDING' ELSE 'CONFIRMED' END, days,
            u[uix], u_name[uix], u_ph[uix], 'AC', dd, dd + days - 1, 3,
            CASE WHEN k > 0 THEN d[k] END, amt, 13.0827, 80.2707, t_lat[r], t_lon[r], 'DISTANCE_BASED', 0, 'SCHEDULED', 'TRIP', 'CAR',
            v_owner, CASE WHEN k = 0 THEN (ARRAY['SEDAN','SUV'])[1 + i % 2] ELSE v_cat[k] END, CASE WHEN k > 0 THEN v[k] END)
    RETURNING id INTO b_id;
    INSERT INTO notifications (id, booking_id, created_at, message, read, recipient_id, recipient_role, title, type)
    VALUES (gen_random_uuid(), b_id::text, now() - ((i * 7) || ' hours')::interval,
            'New booking from ' || u_name[uix] || ' — Chennai to ' || t_to[r] || ' on ' || dd || '. Amount: ₹' || to_char(amt, 'FM999999.00'),
            false, v_owner::text, 'ROLE_OWNER', 'New Booking Received', 'BOOKING_CREATED');
    IF k = 0 THEN
      INSERT INTO notifications (id, booking_id, created_at, message, read, recipient_id, recipient_role, title, type)
      VALUES (gen_random_uuid(), b_id::text, now() - ((i * 7) || ' hours')::interval,
              'New trip from Chennai to ' || t_to[r] || ' on ' || dd || '. Assign a driver and car from your bookings.',
              false, v_owner::text, 'ROLE_OWNER', 'Trip Needs a Driver', 'TRIP_NEEDS_DRIVER');
    END IF;
  END LOOP;

  ------------------------------------------------------------------ past City rides (Chennai)
  FOR i IN 0..69 LOOP
    dd := start_d + (i * span) / 70;
    k := 1 + (i % 2);
    a := 1 + floor(random() * 12)::int;
    z := 1 + ((a + floor(random() * 10)::int) % 12);   -- never the same place as a
    km := round((sqrt(((c_lat[z] - c_lat[a]) * 111) ^ 2 + ((c_lon[z] - c_lon[a]) * 108) ^ 2) * 1.35)::numeric, 1);
    km := greatest(km, 3);
    mins := (km * 3.2 + 6)::int;
    amt := round(50 + km * (CASE WHEN k = 1 THEN 16 ELSE 13 END));
    uix := 1 + floor(random() * 8)::int;
    ts := dd + time '07:00' + (floor(random() * 900) || ' minutes')::interval;
    te := ts + (mins + 8 || ' minutes')::interval;
    st := CASE WHEN random() < 0.06 THEN 'CANCELLED' ELSE 'COMPLETED' END;
    INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place, to_place, status, travel_days,
                                 user_id, user_name, user_phone, ac_type, from_date, to_date, travel_members, driver_id, total_amount,
                                 from_lat, from_lon, to_lat, to_lon, booking_type, version, dispatch_mode, dispatch_state,
                                 dispatch_started_at, service_mode, vehicle_type, city_id, owner_id, car_category, vehicle_id, trip_ended_at)
    VALUES (gen_random_uuid(), ts, km, mins, c_name[a] || ', Chennai', c_name[z] || ', Chennai', st, 1,
            u[uix], u_name[uix], u_ph[uix], 'AC', dd, dd, 1 + floor(random() * 3)::int, d[k], amt,
            c_lat[a], c_lon[a], c_lat[z], c_lon[z], 'DISTANCE_BASED', 0, 'RIDE_NOW', 'MATCHED',
            ts, 'CITY', 'CAR', v_city, v_owner, v_cat[k], v[k], CASE WHEN st = 'COMPLETED' THEN te END)
    RETURNING id INTO b_id;
    IF st = 'COMPLETED' THEN
      meth := methods[1 + floor(random() * 5)::int];
      INSERT INTO payments (id, amount, booking_id, created_at, driver_id, payment_date, payment_method, status, upi_transaction_id,
                            user_id, verified_date, verified_by, version, razorpay_order_id, razorpay_payment_id, purpose, owner_id)
      VALUES (gen_random_uuid(), amt, b_id, te, d[k], te, meth, 'VERIFIED',
              CASE WHEN meth = 'UPI' THEN 'DEMO' || upper(substr(md5(random()::text), 1, 10)) END,
              u[uix], te + interval '1 minute', CASE WHEN meth = 'RAZORPAY' THEN 'razorpay' ELSE d_name[k] END, 0,
              CASE WHEN meth = 'RAZORPAY' THEN 'order_demo' || substr(md5(random()::text), 1, 14) END,
              CASE WHEN meth = 'RAZORPAY' THEN 'pay_demo' || substr(md5(random()::text), 1, 14) END, 'FARE', v_owner);
      comm := round(amt * v_comm / 100, 2);
      INSERT INTO owner_ledger_entries (owner_id, booking_id, driver_id, vehicle_id, type, amount, fare, commission_percent, commission, created_by, created_at)
      VALUES (v_owner, b_id, d[k], v[k], CASE WHEN meth = 'CASH' THEN 'CASH_COMMISSION' ELSE 'RIDE_EARNING' END,
              CASE WHEN meth = 'CASH' THEN -comm ELSE amt - comm END, amt, v_comm, comm, 'system', te + interval '1 minute');
      IF random() < 0.5 THEN
        INSERT INTO reviews (id, booking_id, created_at, driver_id, driver_name, feedback, rating, user_id, user_name, owner_id)
        VALUES (gen_random_uuid(), b_id, te + interval '20 minutes', d[k], d_name[k], feedback[1 + floor(random() * 8)::int],
                CASE WHEN random() < 0.6 THEN 5 WHEN random() < 0.85 THEN 4 ELSE 3 END, u[uix], u_name[uix], v_owner);
      END IF;
      IF i % 6 = 0 THEN
        INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, spent_on, category, amount, note, created_at)
        VALUES (v_owner, 'CAR', v[k], dd, 'FUEL', 1500 + floor(random() * 10) * 100, 'Petrol - city rides', te);
      END IF;
    END IF;
  END LOOP;

  -- the platform settled last month's City-ride balance early this month
  SELECT coalesce(sum(amount), 0) INTO bal FROM owner_ledger_entries
   WHERE owner_id = v_owner AND created_at < date_trunc('month', today);
  IF bal > 0 THEN
    INSERT INTO owner_ledger_entries (owner_id, type, amount, reference, note, created_by, created_at)
    VALUES (v_owner, 'PAYOUT', -bal, 'DEMOUTR' || to_char(today, 'YYYYMM') || '01', 'City-ride payout for ' || to_char(start_d, 'Mon YYYY'),
            'admin', date_trunc('month', today) + interval '2 days 11 hours');
  END IF;

  ------------------------------------------------------------------ packages
  INSERT INTO travel_packages (accommodation_details, accommodation_included, active, category, created_at, created_by, description,
                               duration_days, duration_nights, food_details, food_included, guide_included, highlights, itinerary,
                               max_group_size, name, places_included, price_per_person, sightseeing_included, state, toll_free,
                               transport_details, transport_included, owner_id)
  VALUES
   ('3-star hotel, twin sharing', true, true, 'HILL_STATION', start_d - 3, v_owner,
    'Cool weekend in the Princess of Hill Stations: lakes, pine forests and viewpoints.', 3, 2, 'Breakfast and dinner', true, false,
    'Kodai Lake boating, Coaker''s Walk, Pine Forest, Pillar Rocks',
    'Day 1: Chennai to Kodaikanal | Day 2: Local sightseeing | Day 3: Return to Chennai', 12,
    'Kodaikanal Weekend Escape', 'Kodaikanal, Pillar Rocks, Guna Caves', 6499, true, 'Tamil Nadu', true, 'AC Innova / Tempo Traveller', true, v_owner)
  RETURNING id INTO bus_s;
  pkg := pkg || bus_s;
  INSERT INTO travel_packages (accommodation_details, accommodation_included, active, category, created_at, created_by, description,
                               duration_days, duration_nights, food_details, food_included, guide_included, highlights, itinerary,
                               max_group_size, name, places_included, price_per_person, sightseeing_included, state, toll_free,
                               transport_details, transport_included, owner_id)
  VALUES
   ('Temple-side lodge', true, true, 'PILGRIMAGE', start_d - 3, v_owner,
    'Rameswaram and Dhanushkodi darshan trip with a local guide.', 2, 1, 'Vegetarian meals', true, true,
    'Ramanathaswamy Temple, Pamban Bridge, Dhanushkodi, APJ Abdul Kalam Memorial',
    'Day 1: Chennai to Rameswaram, temple darshan | Day 2: Dhanushkodi and return', 15,
    'Rameswaram Pilgrimage', 'Rameswaram, Dhanushkodi, Pamban', 3799, true, 'Tamil Nadu', false, 'AC Tempo Traveller', true, v_owner)
  RETURNING id INTO bus_s;
  pkg := pkg || bus_s;
  INSERT INTO travel_packages (accommodation_details, accommodation_included, active, category, created_at, created_by, description,
                               duration_days, duration_nights, food_details, food_included, guide_included, highlights, itinerary,
                               max_group_size, name, places_included, price_per_person, sightseeing_included, state, toll_free,
                               transport_details, transport_included, owner_id)
  VALUES
   ('Beach resort', true, true, 'BEACH', start_d - 3, v_owner,
    'Easy East Coast Road weekend: Mahabalipuram shore temples and Puducherry''s French Quarter.', 2, 1, 'Breakfast', true, false,
    'Shore Temple, Five Rathas, Promenade Beach, Auroville, French Quarter',
    'Day 1: Chennai - Mahabalipuram - Puducherry | Day 2: Auroville and return', 6,
    'ECR Beach Weekend', 'Mahabalipuram, Puducherry, Auroville', 4199, true, 'Tamil Nadu', true, 'AC Sedan / SUV', true, v_owner)
  RETURNING id INTO bus_s;
  pkg := pkg || bus_s;

  FOR i IN 1..12 LOOP
    r := 1 + (i % 3);
    uix := 1 + (i % 8);
    dd := CASE WHEN i <= 9 THEN start_d + (i * span) / 10 ELSE today + 5 * (i - 8) END;
    n := 2 + floor(random() * 3)::int;
    SELECT price_per_person, duration_days, duration_nights, category, name INTO amt, days, a, st, meth FROM travel_packages WHERE id = pkg[r];
    INSERT INTO package_bookings (id, booking_date, cancellation_reason, cancelled_at, confirmed_at, duration_days, duration_nights,
                                  number_of_persons, package_category, package_id, package_name, price_per_person, special_requests,
                                  status, total_amount, travel_date, user_email, user_id, user_name, user_phone, version, owner_id)
    VALUES (gen_random_uuid(), dd - 7 + time '12:00',
            CASE WHEN i = 4 THEN 'Change of plans' END, CASE WHEN i = 4 THEN dd - 3 + time '09:00' END,
            CASE WHEN i <> 4 THEN dd - 6 + time '10:00' END, days, a, n, st, pkg[r], meth, amt,
            CASE WHEN i % 4 = 1 THEN 'Need a child seat' END,
            CASE WHEN i = 4 THEN 'CANCELLED' WHEN i <= 9 THEN 'COMPLETED' WHEN i = 12 THEN 'PENDING' ELSE 'CONFIRMED' END,
            amt * n, dd, u_mail[uix], u[uix], u_name[uix], u_ph[uix], 0, v_owner);
  END LOOP;

  ------------------------------------------------------------------ buses
  INSERT INTO buses (owner_id, registration_number, name, ac, permit_type, amenities, status, submitted_at, reviewed_at, created_at)
  VALUES (v_owner, 'TN09DB7001', 'Namma Demo Night Rider', true, 'CONTRACT_CARRIAGE',
          'Charging point,Blanket,Water bottle,Reading light,CCTV,Emergency exit', 'APPROVED',
          start_d - 5 + time '15:00', start_d - 4 + time '12:00', start_d - 5 + time '14:00')
  RETURNING id INTO bus_s;
  INSERT INTO buses (owner_id, registration_number, name, ac, permit_type, amenities, status, submitted_at, reviewed_at, created_at)
  VALUES (v_owner, 'TN09DB7002', 'Namma Demo Day Express', true, 'CONTRACT_CARRIAGE',
          'Charging point,Water bottle,Push-back seats,CCTV', 'APPROVED',
          start_d - 5 + time '15:30', start_d - 4 + time '12:30', start_d - 5 + time '14:30')
  RETURNING id INTO bus_t;

  INSERT INTO bus_seats (bus_id, owner_id, label, deck, row_no, col_no, kind, berth, pair_label, ladies_only)
  SELECT CASE WHEN bus = 'sleeper' THEN bus_s ELSE bus_t END, v_owner, label, deck, row_no, col_no, kind, berth, pair_label, ladies
    FROM demo_seats;

  INSERT INTO bus_documents (bus_id, owner_id, type, path, expires_on, uploaded_at)
  SELECT b, v_owner, t, 'demo/bus-docs/' || lower(t) || '-' || b || '.pdf', (today + interval '10 months')::date, start_d - 5 + time '15:00'
    FROM unnest(ARRAY[bus_s, bus_t]) b, unnest(ARRAY['RC','PERMIT','INSURANCE','FITNESS']) t;

  -- Route 1: Chennai - Trichy - Madurai, overnight sleeper
  INSERT INTO bus_routes (owner_id, bus_id, name, run_days, active_from, max_seats_per_booking, cancellation_policy, status, created_at)
  VALUES (v_owner, bus_s, 'Chennai - Madurai Night Sleeper', 127, start_d - 2, 6, pol, 'PUBLISHED', start_d - 3 + time '16:00')
  RETURNING id INTO rt_s;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_s, v_owner, 0, id FROM bus_places WHERE lower(name) = 'chennai' RETURNING id INTO st0;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_s, v_owner, 1, id FROM bus_places WHERE lower(name) = 'trichy' RETURNING id INTO st1;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_s, v_owner, 2, id FROM bus_places WHERE lower(name) = 'madurai' RETURNING id INTO st2;
  IF st0 IS NULL OR st1 IS NULL OR st2 IS NULL THEN RAISE EXCEPTION 'Bus places Chennai/Trichy/Madurai missing'; END IF;
  INSERT INTO bus_route_points (stop_id, owner_id, kind, name, address, landmark, lat, lng, day_offset, "time") VALUES
    (st0, v_owner, 'BOARDING', 'Koyambedu Omni Bus Stand', 'Jawaharlal Nehru Salai, Koyambedu', 'Near CMBT Metro', 13.0694, 80.1948, 0, '21:00'),
    (st0, v_owner, 'BOARDING', 'Guindy', 'GST Road, Guindy', 'Near Kathipara Junction', 13.0067, 80.2206, 0, '21:40'),
    (st0, v_owner, 'BOARDING', 'Tambaram', 'GST Road, Tambaram', 'Tambaram Railway Station', 12.9249, 80.1000, 0, '22:10'),
    (st1, v_owner, 'BOTH', 'Trichy Central Bus Stand', 'Rockins Road, Cantonment', 'Central Bus Stand', 10.7985, 78.6836, 1, '02:45'),
    (st2, v_owner, 'DROPPING', 'Mattuthavani Bus Stand', 'Mattuthavani, Madurai', 'MGR Bus Stand', 9.9406, 78.1528, 1, '05:30'),
    (st2, v_owner, 'DROPPING', 'Periyar Bus Stand', 'Periyar, Madurai', 'Near Meenakshi Temple', 9.9160, 78.1132, 1, '05:55');
  INSERT INTO bus_route_fares (route_id, owner_id, from_seq, to_seq, seat_class, fare)
  SELECT rt_s, v_owner, s.f, s.t, c.cls, round(c.base * s.mult / 10) * 10
    FROM (VALUES (0, 2, 1.0), (0, 1, 0.7), (1, 2, 0.4)) s(f, t, mult),
         (VALUES ('LOWER_SINGLE', 1250), ('LOWER_DOUBLE', 1150), ('UPPER_SINGLE', 1100), ('UPPER_DOUBLE', 999)) c(cls, base)
   WHERE c.cls IN (SELECT DISTINCT CASE WHEN deck = 0 THEN 'LOWER_' ELSE 'UPPER_' END || berth FROM demo_seats WHERE bus = 'sleeper');

  -- Route 2: Chennai - Salem - Coimbatore, day seater
  INSERT INTO bus_routes (owner_id, bus_id, name, run_days, active_from, max_seats_per_booking, cancellation_policy, status, created_at)
  VALUES (v_owner, bus_t, 'Chennai - Coimbatore Day Express', 127, start_d - 2, 6, pol, 'PUBLISHED', start_d - 3 + time '16:30')
  RETURNING id INTO rt_t;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_t, v_owner, 0, id FROM bus_places WHERE lower(name) = 'chennai' RETURNING id INTO st0;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_t, v_owner, 1, id FROM bus_places WHERE lower(name) = 'salem' RETURNING id INTO st1;
  INSERT INTO bus_route_stops (route_id, owner_id, seq, place_id) SELECT rt_t, v_owner, 2, id FROM bus_places WHERE lower(name) = 'coimbatore' RETURNING id INTO st2;
  IF st0 IS NULL OR st1 IS NULL OR st2 IS NULL THEN RAISE EXCEPTION 'Bus places Chennai/Salem/Coimbatore missing'; END IF;
  INSERT INTO bus_route_points (stop_id, owner_id, kind, name, address, landmark, lat, lng, day_offset, "time") VALUES
    (st0, v_owner, 'BOARDING', 'Koyambedu Omni Bus Stand', 'Jawaharlal Nehru Salai, Koyambedu', 'Near CMBT Metro', 13.0694, 80.1948, 0, '07:00'),
    (st0, v_owner, 'BOARDING', 'Porur', 'Mount-Poonamallee Road, Porur', 'Porur Junction', 13.0382, 80.1565, 0, '07:30'),
    (st1, v_owner, 'BOTH', 'Salem New Bus Stand', 'Omalur Main Road, Salem', 'New Bus Stand', 11.6720, 78.1395, 0, '12:15'),
    (st2, v_owner, 'DROPPING', 'Gandhipuram Bus Stand', 'Gandhipuram, Coimbatore', 'Omni Bus Stand', 11.0183, 76.9674, 0, '15:30'),
    (st2, v_owner, 'DROPPING', 'Avinashi Road', 'Avinashi Road, Coimbatore', 'Near Hope College', 11.0262, 77.0247, 0, '15:50');
  INSERT INTO bus_route_fares (route_id, owner_id, from_seq, to_seq, seat_class, fare) VALUES
    (rt_t, v_owner, 0, 2, 'SEATER', 850), (rt_t, v_owner, 0, 1, 'SEATER', 560), (rt_t, v_owner, 1, 2, 'SEATER', 350);

  ------------------------------------------------------------------ bus trips, tickets, payouts
  FOREACH rt IN ARRAY ARRAY[rt_s, rt_t] LOOP
    bus_cur := CASE WHEN rt = rt_s THEN bus_s ELSE bus_t END;
    bus_kind := CASE WHEN rt = rt_s THEN 'sleeper' ELSE 'seater' END;
    FOR dd IN SELECT generate_series(start_d, today + 14, interval '1 day')::date LOOP
      SELECT dd + min(p.day_offset) + min(p."time") INTO dep
        FROM bus_route_points p JOIN bus_route_stops s ON s.id = p.stop_id WHERE s.route_id = rt AND s.seq = 0;
      SELECT dd + max(p.day_offset) + max(p."time") INTO arr
        FROM bus_route_points p JOIN bus_route_stops s ON s.id = p.stop_id WHERE s.route_id = rt AND s.seq = 2;
      INSERT INTO bus_trips (route_id, bus_id, owner_id, travel_date, departure_at, arrival_at, status, driver_name, driver_phone,
                             conductor_name, conductor_phone, reminder_sent_at, created_at)
      VALUES (rt, bus_cur, v_owner, dd, dep, arr,
              CASE WHEN arr < now() THEN 'COMPLETED' WHEN dep < now() THEN 'DEPARTED' ELSE 'SCHEDULED' END,
              CASE WHEN rt = rt_s THEN 'Murugan K' ELSE 'Senthil P' END, CASE WHEN rt = rt_s THEN '9000000301' ELSE '9000000302' END,
              CASE WHEN rt = rt_s THEN 'Rajesh V' ELSE 'Dinesh R' END, CASE WHEN rt = rt_s THEN '9000000303' ELSE '9000000304' END,
              CASE WHEN dep < now() THEN dep - interval '3 hours' END, least(dd - 30, start_d - 2) + time '01:00')
      RETURNING id INTO v_trip;

      -- how many bookings: busy history, some for the next week, none further out
      n := CASE WHEN dd < today THEN 4 + floor(random() * 7)::int
                WHEN dd <= today + 7 THEN 1 + floor(random() * 4)::int ELSE 0 END;
      IF extract(isodow FROM dd) IN (5, 6, 7) AND n > 0 THEN n := n + 2; END IF;
      SELECT array_agg(label ORDER BY random()) INTO seats FROM demo_seats WHERE bus = bus_kind;
      sidx := 1;
      FOR j IN 1..n LOOP
        pax := 1 + floor(random() * 3)::int;
        EXIT WHEN sidx + pax - 1 > array_length(seats, 1);
        a := floor(random() * 20)::int;
        fs := CASE WHEN a < 15 THEN 0 WHEN a < 18 THEN 0 ELSE 1 END;
        ts_ := CASE WHEN a < 15 THEN 2 WHEN a < 18 THEN 1 ELSE 2 END;
        uix := 1 + floor(random() * 8)::int;
        ts := CASE WHEN dd < today THEN dep - ((1 + floor(random() * 9)::int) || ' days')::interval - ((floor(random() * 600)) || ' minutes')::interval
                   ELSE least(now() - interval '30 minutes', dep - interval '1 day') - ((floor(random() * 4000)) || ' minutes')::interval END;
        t_id := gen_random_uuid();
        INSERT INTO bus_tickets (id, ticket_no, user_id, owner_id, trip_id, from_seq, to_seq, boarding_point_id, dropping_point_id,
                                 contact_phone, contact_email, fare_total, gst_percent, gst_total, total_amount, commission_percent,
                                 cancellation_policy, status, created_at, confirmed_at, reminder_sent_at, idempotency_key)
        VALUES (t_id, 'NJB-' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8)), u[uix], v_owner, v_trip, fs, ts_,
                (SELECT p.id FROM bus_route_points p JOIN bus_route_stops s ON s.id = p.stop_id
                  WHERE s.route_id = rt AND s.seq = fs AND p.kind IN ('BOARDING', 'BOTH') ORDER BY random() LIMIT 1),
                (SELECT p.id FROM bus_route_points p JOIN bus_route_stops s ON s.id = p.stop_id
                  WHERE s.route_id = rt AND s.seq = ts_ AND p.kind IN ('DROPPING', 'BOTH') ORDER BY random() LIMIT 1),
                u_ph[uix], u_mail[uix], 0, 5, 0, 0, 8, pol,
                CASE WHEN dep < now() - interval '6 hours' AND arr < now() THEN 'COMPLETED' ELSE 'CONFIRMED' END,
                ts, ts + interval '2 minutes', CASE WHEN dep < now() THEN dep - interval '2 hours' END, 'demo-' || t_id);
        FOR z IN 0..pax - 1 LOOP
          seat := seats[sidx + z];
          SELECT CASE WHEN kind = 'SEATER' THEN 'SEATER' WHEN deck = 0 THEN 'LOWER_' || berth ELSE 'UPPER_' || berth END
            INTO cls FROM demo_seats WHERE bus = bus_kind AND label = seat;
          SELECT f.fare INTO v_fare FROM bus_route_fares f WHERE f.route_id = rt AND f.from_seq = fs AND f.to_seq = ts_ AND f.seat_class = cls;
          v_gst := round(v_fare * 5 / 100, 2);
          a := CASE WHEN z = 0 THEN 0 ELSE 1 + floor(random() * 16)::int END;
          INSERT INTO bus_ticket_passengers (ticket_id, owner_id, seat_label, seat_class, name, age, gender, fare, gst, status)
          VALUES (t_id, v_owner, seat, cls,
                  CASE WHEN a = 0 THEN u_name[uix] ELSE p_name[a] END,
                  18 + floor(random() * 45)::int,
                  CASE WHEN a = 0 THEN (CASE WHEN uix IN (2, 4, 6, 8) THEN 'F' ELSE 'M' END) ELSE p_gender[a] END,
                  v_fare, v_gst, 'CONFIRMED');
          INSERT INTO bus_seat_allocations (trip_id, owner_id, seat_label, from_seq, to_seq, status, gender, ticket_id, created_at)
          VALUES (v_trip, v_owner, seat, fs, ts_, 'CONFIRMED',
                  CASE WHEN a = 0 THEN (CASE WHEN uix IN (2, 4, 6, 8) THEN 'F' ELSE 'M' END) ELSE p_gender[a] END, t_id, ts);
        END LOOP;
        sidx := sidx + pax;
        UPDATE bus_tickets t SET fare_total = x.f, gst_total = x.g, total_amount = x.f + x.g
          FROM (SELECT sum(fare) f, sum(gst) g FROM bus_ticket_passengers WHERE ticket_id = t_id) x WHERE t.id = t_id;
        INSERT INTO bus_payments (id, ticket_id, user_id, owner_id, amount, status, razorpay_order_id, razorpay_payment_id, created_at, captured_at)
        SELECT gen_random_uuid(), t_id, u[uix], v_owner, total_amount, 'CAPTURED',
               'order_demo' || substr(md5(t_id::text || 'o'), 1, 14), 'pay_demo' || substr(md5(t_id::text || 'p'), 1, 14),
               ts, ts + interval '1 minute'
          FROM bus_tickets WHERE id = t_id;
        IF dd < today AND random() < 0.25 THEN
          INSERT INTO bus_reviews (ticket_id, bus_id, owner_id, user_id, rating, comment, created_at)
          VALUES (t_id, bus_cur, v_owner, u[uix], CASE WHEN random() < 0.55 THEN 5 WHEN random() < 0.85 THEN 4 ELSE 3 END,
                  bus_fb[1 + floor(random() * 6)::int], arr + interval '5 hours');
        END IF;
      END LOOP;

      IF arr < now() THEN
        -- owner payout for the trip: last month's are paid, this month's are due
        INSERT INTO bus_owner_payouts (owner_id, trip_id, fares_travelled, charges_kept, gross, commission_percent, commission, net,
                                       status, utr, paid_at, created_at)
        SELECT v_owner, v_trip, f, 0, f, 8, round(f * 0.08, 2), f - round(f * 0.08, 2),
               CASE WHEN dd < date_trunc('month', today) THEN 'PAID' ELSE 'DUE' END,
               CASE WHEN dd < date_trunc('month', today) THEN 'DEMOBUS' || to_char(dd, 'YYYYMMDD') || bus_cur END,
               CASE WHEN dd < date_trunc('month', today) THEN date_trunc('month', today) + interval '3 days 12 hours' END,
               arr + interval '1 hour'
          FROM (SELECT coalesce(sum(p.fare), 0) f FROM bus_ticket_passengers p JOIN bus_tickets t ON t.id = p.ticket_id
                 WHERE t.trip_id = v_trip AND p.status = 'CONFIRMED') x
         WHERE f > 0;
        INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, bus_trip_id, spent_on, category, amount, note, created_at) VALUES
          (v_owner, 'BUS', bus_cur, v_trip, dd, 'FUEL', CASE WHEN rt = rt_s THEN 8800 ELSE 9400 END + floor(random() * 8) * 100, 'Diesel', arr),
          (v_owner, 'BUS', bus_cur, v_trip, dd, 'TOLL', CASE WHEN rt = rt_s THEN 1650 ELSE 1420 END, 'FASTag tolls', arr);
      END IF;
    END LOOP;

    INSERT INTO vehicle_expenses (owner_id, vehicle_kind, vehicle_id, spent_on, category, amount, note, created_at) VALUES
      (v_owner, 'BUS', bus_cur, start_d + 1, 'PERMIT_TAX', 21500, 'Quarterly road tax', start_d + 1 + time '12:00'),
      (v_owner, 'BUS', bus_cur, (start_d + interval '1 month')::date - 1, 'DRIVER_PAY', 52000, 'Driver + conductor salary', (start_d + interval '1 month')::date - 1 + time '18:00'),
      (v_owner, 'BUS', bus_cur, start_d + 18, 'MAINTENANCE', 7800, 'Tyre rotation and AC service', start_d + 18 + time '16:00');
  END LOOP;

  RAISE NOTICE 'Demo owner created: % (id %), login testowner@nammajourneys.site', v_code, v_owner;
END
$seed$;

-- what was created
SELECT o.owner_code, o.email, o.travels_name,
       (SELECT count(*) FROM drivers WHERE owner_id = o.id) AS drivers,
       (SELECT count(*) FROM vehicles WHERE owner_id = o.id) AS cars,
       (SELECT count(*) FROM buses WHERE owner_id = o.id) AS buses,
       (SELECT count(*) FROM travel_bookings WHERE owner_id = o.id) AS car_bookings,
       (SELECT count(*) FROM payments WHERE owner_id = o.id) AS payments,
       (SELECT count(*) FROM bus_trips WHERE owner_id = o.id) AS bus_trips,
       (SELECT count(*) FROM bus_tickets WHERE owner_id = o.id) AS bus_tickets,
       (SELECT count(*) FROM vehicle_expenses WHERE owner_id = o.id) AS expenses
  FROM owners o WHERE lower(o.email) = 'testowner@nammajourneys.site';

COMMIT;
