// India's 28 states and 8 union territories with their cities and towns, for an owner choosing
// where they run trips. The southern states, where most owners are, list every district
// headquarters plus well-known towns; the rest list their main cities. A town that is missing can
// still be typed in. State names must match ServiceAreas.STATES on the server.

export const STATES = [
  { name: 'Tamil Nadu', cities: [
    'Chennai', 'Coimbatore', 'Madurai', 'Tiruchirappalli', 'Salem', 'Tirunelveli', 'Tiruppur', 'Erode', 'Vellore',
    'Thoothukudi', 'Thanjavur', 'Dindigul', 'Kanchipuram', 'Nagercoil', 'Karur', 'Cuddalore', 'Kumbakonam',
    'Hosur', 'Sivakasi', 'Pudukkottai', 'Namakkal', 'Krishnagiri', 'Dharmapuri', 'Villupuram', 'Tiruvannamalai',
    'Ramanathapuram', 'Rameswaram', 'Virudhunagar', 'Sivaganga', 'Karaikudi', 'Theni', 'Nagapattinam',
    'Mayiladuthurai', 'Tiruvarur', 'Perambalur', 'Ariyalur', 'Kallakurichi', 'Chengalpattu', 'Tiruvallur',
    'Ranipet', 'Tirupattur', 'Tenkasi', 'Udhagamandalam', 'Coonoor', 'Kodaikanal', 'Pollachi', 'Mettupalayam',
    'Palani', 'Kanyakumari', 'Velankanni', 'Chidambaram', 'Mahabalipuram', 'Yercaud', 'Tiruchendur',
    'Srivilliputhur', 'Gobichettipalayam', 'Ambur', 'Vaniyambadi', 'Arakkonam', 'Attur', 'Rajapalayam',
    'Aruppukottai', 'Kovilpatti', 'Sankarankovil', 'Udumalaipettai', 'Dharapuram', 'Bhavani', 'Tiruchengode',
    'Neyveli', 'Vridhachalam', 'Kumarapalayam', 'Paramakudi',
  ] },
  { name: 'Kerala', cities: [
    'Thiruvananthapuram', 'Kochi', 'Kozhikode', 'Thrissur', 'Kollam', 'Kannur', 'Alappuzha', 'Kottayam',
    'Palakkad', 'Malappuram', 'Kasaragod', 'Pathanamthitta', 'Idukki', 'Painavu', 'Kalpetta', 'Ernakulam',
    'Munnar', 'Thekkady', 'Varkala', 'Kovalam', 'Guruvayur', 'Sabarimala', 'Kumarakom', 'Wayanad', 'Sultan Bathery',
    'Thalassery', 'Vadakara', 'Tirur', 'Perinthalmanna', 'Ponnani', 'Chalakudy', 'Kodungallur', 'Aluva',
    'Angamaly', 'Muvattupuzha', 'Thodupuzha', 'Changanassery', 'Pala', 'Kayamkulam', 'Cherthala', 'Punalur',
    'Neyyattinkara', 'Attingal', 'Ottapalam', 'Shoranur', 'Payyanur', 'Kanhangad',
  ] },
  { name: 'Karnataka', cities: [
    'Bengaluru', 'Mysuru', 'Mangaluru', 'Hubballi', 'Dharwad', 'Belagavi', 'Kalaburagi', 'Ballari', 'Vijayapura',
    'Shivamogga', 'Tumakuru', 'Davanagere', 'Udupi', 'Hassan', 'Mandya', 'Chikkamagaluru', 'Madikeri', 'Kodagu',
    'Chitradurga', 'Raichur', 'Bidar', 'Bagalkot', 'Gadag', 'Haveri', 'Koppal', 'Karwar', 'Chamarajanagar',
    'Kolar', 'Chikkaballapur', 'Ramanagara', 'Yadgir', 'Hosapete', 'Hampi', 'Gokarna', 'Murudeshwar',
    'Sakleshpur', 'Coorg', 'Bhatkal', 'Puttur', 'Sirsi', 'Dandeli', 'Channapatna',
  ] },
  { name: 'Andhra Pradesh', cities: [
    'Visakhapatnam', 'Vijayawada', 'Guntur', 'Nellore', 'Kurnool', 'Tirupati', 'Kakinada', 'Rajahmundry',
    'Kadapa', 'Anantapur', 'Eluru', 'Ongole', 'Chittoor', 'Srikakulam', 'Vizianagaram', 'Machilipatnam',
    'Bhimavaram', 'Amaravati', 'Puttaparthi', 'Srisailam', 'Tenali', 'Proddatur', 'Hindupur', 'Madanapalle',
    'Nandyal', 'Adoni', 'Narasaraopet', 'Bapatla', 'Paderu', 'Rayachoti', 'Amalapuram', 'Parvathipuram',
    'Araku Valley',
  ] },
  { name: 'Telangana', cities: [
    'Hyderabad', 'Secunderabad', 'Warangal', 'Hanamkonda', 'Karimnagar', 'Nizamabad', 'Khammam', 'Mahbubnagar',
    'Nalgonda', 'Adilabad', 'Ramagundam', 'Siddipet', 'Suryapet', 'Mancherial', 'Medak', 'Sangareddy',
    'Kamareddy', 'Jagtial', 'Peddapalli', 'Nirmal', 'Wanaparthy', 'Nagarkurnool', 'Jangaon', 'Bhadradri Kothagudem',
    'Kothagudem', 'Bhupalpally', 'Vikarabad', 'Medchal', 'Shamshabad', 'Miryalaguda', 'Bhongir', 'Narayanpet',
    'Mahabubabad', 'Mulugu', 'Gadwal', 'Asifabad', 'Sircilla',
  ] },
  { name: 'Puducherry', cities: ['Puducherry', 'Karaikal', 'Mahe', 'Yanam'] },
  { name: 'Andaman and Nicobar Islands', cities: ['Port Blair', 'Havelock Island', 'Neil Island', 'Diglipur', 'Car Nicobar'] },
  { name: 'Arunachal Pradesh', cities: ['Itanagar', 'Naharlagun', 'Tawang', 'Pasighat', 'Ziro', 'Bomdila', 'Tezu', 'Along'] },
  { name: 'Assam', cities: ['Guwahati', 'Dibrugarh', 'Silchar', 'Jorhat', 'Tezpur', 'Nagaon', 'Tinsukia', 'Bongaigaon', 'Dhubri', 'Sivasagar', 'Diphu', 'Kaziranga', 'Goalpara', 'North Lakhimpur'] },
  { name: 'Bihar', cities: ['Patna', 'Gaya', 'Bodh Gaya', 'Bhagalpur', 'Muzaffarpur', 'Darbhanga', 'Purnia', 'Arrah', 'Begusarai', 'Katihar', 'Munger', 'Chhapra', 'Hajipur', 'Sasaram', 'Rajgir', 'Nalanda', 'Motihari', 'Siwan'] },
  { name: 'Chandigarh', cities: ['Chandigarh'] },
  { name: 'Chhattisgarh', cities: ['Raipur', 'Bhilai', 'Durg', 'Bilaspur', 'Korba', 'Rajnandgaon', 'Jagdalpur', 'Ambikapur', 'Raigarh', 'Dhamtari'] },
  { name: 'Dadra and Nagar Haveli and Daman and Diu', cities: ['Daman', 'Diu', 'Silvassa'] },
  { name: 'Delhi', cities: ['New Delhi', 'Delhi', 'Dwarka', 'Rohini', 'Saket', 'Karol Bagh', 'Connaught Place'] },
  { name: 'Goa', cities: ['Panaji', 'Margao', 'Vasco da Gama', 'Mapusa', 'Ponda', 'Calangute', 'Old Goa', 'Canacona'] },
  { name: 'Gujarat', cities: ['Ahmedabad', 'Surat', 'Vadodara', 'Rajkot', 'Bhavnagar', 'Jamnagar', 'Gandhinagar', 'Junagadh', 'Anand', 'Bhuj', 'Navsari', 'Valsad', 'Bharuch', 'Mehsana', 'Morbi', 'Porbandar', 'Dwarka', 'Somnath', 'Palanpur', 'Gandhidham'] },
  { name: 'Haryana', cities: ['Gurugram', 'Faridabad', 'Panipat', 'Ambala', 'Karnal', 'Hisar', 'Rohtak', 'Sonipat', 'Panchkula', 'Yamunanagar', 'Kurukshetra', 'Sirsa', 'Rewari', 'Bhiwani'] },
  { name: 'Himachal Pradesh', cities: ['Shimla', 'Manali', 'Dharamshala', 'Kullu', 'Mandi', 'Solan', 'Dalhousie', 'Kasauli', 'Chamba', 'Bilaspur', 'Hamirpur', 'Una', 'Kangra', 'Kasol', 'Spiti'] },
  { name: 'Jammu and Kashmir', cities: ['Srinagar', 'Jammu', 'Gulmarg', 'Pahalgam', 'Sonamarg', 'Katra', 'Anantnag', 'Baramulla', 'Udhampur', 'Kathua'] },
  { name: 'Jharkhand', cities: ['Ranchi', 'Jamshedpur', 'Dhanbad', 'Bokaro', 'Deoghar', 'Hazaribagh', 'Giridih', 'Ramgarh', 'Dumka', 'Daltonganj'] },
  { name: 'Ladakh', cities: ['Leh', 'Kargil', 'Nubra Valley', 'Pangong'] },
  { name: 'Lakshadweep', cities: ['Kavaratti', 'Agatti', 'Minicoy'] },
  { name: 'Madhya Pradesh', cities: ['Indore', 'Bhopal', 'Jabalpur', 'Gwalior', 'Ujjain', 'Sagar', 'Rewa', 'Satna', 'Ratlam', 'Dewas', 'Khajuraho', 'Pachmarhi', 'Omkareshwar', 'Mandu', 'Chhindwara', 'Katni', 'Burhanpur', 'Khandwa', 'Vidisha', 'Orchha'] },
  { name: 'Maharashtra', cities: ['Mumbai', 'Pune', 'Nagpur', 'Nashik', 'Thane', 'Navi Mumbai', 'Aurangabad', 'Solapur', 'Kolhapur', 'Amravati', 'Nanded', 'Sangli', 'Jalgaon', 'Akola', 'Latur', 'Ahmednagar', 'Satara', 'Ratnagiri', 'Lonavala', 'Mahabaleshwar', 'Shirdi', 'Alibag', 'Chandrapur', 'Dhule', 'Kalyan', 'Panvel', 'Malvan'] },
  { name: 'Manipur', cities: ['Imphal', 'Thoubal', 'Churachandpur', 'Ukhrul', 'Moirang'] },
  { name: 'Meghalaya', cities: ['Shillong', 'Cherrapunji', 'Tura', 'Jowai', 'Dawki', 'Mawlynnong'] },
  { name: 'Mizoram', cities: ['Aizawl', 'Lunglei', 'Champhai', 'Serchhip'] },
  { name: 'Nagaland', cities: ['Kohima', 'Dimapur', 'Mokokchung', 'Mon', 'Wokha'] },
  { name: 'Odisha', cities: ['Bhubaneswar', 'Cuttack', 'Puri', 'Rourkela', 'Berhampur', 'Sambalpur', 'Balasore', 'Konark', 'Koraput', 'Baripada', 'Bhadrak', 'Jharsuguda', 'Angul', 'Gopalpur'] },
  { name: 'Punjab', cities: ['Ludhiana', 'Amritsar', 'Jalandhar', 'Patiala', 'Bathinda', 'Mohali', 'Pathankot', 'Hoshiarpur', 'Moga', 'Firozpur', 'Phagwara', 'Anandpur Sahib'] },
  { name: 'Rajasthan', cities: ['Jaipur', 'Jodhpur', 'Udaipur', 'Kota', 'Ajmer', 'Bikaner', 'Jaisalmer', 'Pushkar', 'Mount Abu', 'Alwar', 'Bharatpur', 'Bhilwara', 'Sikar', 'Chittorgarh', 'Sawai Madhopur', 'Ranthambore', 'Pali', 'Barmer', 'Tonk', 'Neemrana'] },
  { name: 'Sikkim', cities: ['Gangtok', 'Pelling', 'Namchi', 'Lachung', 'Ravangla', 'Mangan'] },
  { name: 'Tripura', cities: ['Agartala', 'Udaipur', 'Dharmanagar', 'Kailashahar', 'Belonia'] },
  { name: 'Uttar Pradesh', cities: ['Lucknow', 'Kanpur', 'Varanasi', 'Agra', 'Prayagraj', 'Ghaziabad', 'Noida', 'Greater Noida', 'Meerut', 'Bareilly', 'Aligarh', 'Moradabad', 'Gorakhpur', 'Mathura', 'Vrindavan', 'Ayodhya', 'Jhansi', 'Saharanpur', 'Firozabad', 'Faizabad', 'Muzaffarnagar', 'Rae Bareli', 'Sitapur', 'Mirzapur', 'Etawah'] },
  { name: 'Uttarakhand', cities: ['Dehradun', 'Haridwar', 'Rishikesh', 'Nainital', 'Mussoorie', 'Haldwani', 'Roorkee', 'Almora', 'Kedarnath', 'Badrinath', 'Auli', 'Rudrapur', 'Kashipur', 'Pithoragarh', 'Ranikhet', 'Jim Corbett'] },
  { name: 'West Bengal', cities: ['Kolkata', 'Howrah', 'Siliguri', 'Darjeeling', 'Durgapur', 'Asansol', 'Kharagpur', 'Haldia', 'Digha', 'Kalimpong', 'Malda', 'Bardhaman', 'Krishnanagar', 'Jalpaiguri', 'Shantiniketan', 'Cooch Behar', 'Bankura'] },
]

/** Quick picks shown above the search: the states most owners serve. */
export const POPULAR_STATES = ['Tamil Nadu', 'Kerala', 'Karnataka', 'Andhra Pradesh', 'Telangana', 'Puducherry']

/** Older or everyday names people type, so "Madras" finds Chennai. */
export const ALIASES = {
  madras: 'Chennai', trichy: 'Tiruchirappalli', tiruchi: 'Tiruchirappalli', tuticorin: 'Thoothukudi',
  ooty: 'Udhagamandalam', kanniyakumari: 'Kanyakumari', tanjore: 'Thanjavur', conjeevaram: 'Kanchipuram',
  pondicherry: 'Puducherry', pondy: 'Puducherry', trivandrum: 'Thiruvananthapuram', cochin: 'Kochi',
  calicut: 'Kozhikode', trichur: 'Thrissur', alleppey: 'Alappuzha', quilon: 'Kollam', cannanore: 'Kannur',
  palghat: 'Palakkad', bangalore: 'Bengaluru', mysore: 'Mysuru', mangalore: 'Mangaluru', hubli: 'Hubballi',
  belgaum: 'Belagavi', gulbarga: 'Kalaburagi', bellary: 'Ballari', bijapur: 'Vijayapura', shimoga: 'Shivamogga',
  tumkur: 'Tumakuru', vizag: 'Visakhapatnam', rajamahendravaram: 'Rajahmundry', cuddapah: 'Kadapa',
  bombay: 'Mumbai', poona: 'Pune', calcutta: 'Kolkata', gurgaon: 'Gurugram', allahabad: 'Prayagraj',
  banaras: 'Varanasi', benares: 'Varanasi', baroda: 'Vadodara', orissa: 'Odisha', simla: 'Shimla',
}
