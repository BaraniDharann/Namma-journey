// The official formats every form accepts, mirroring the server's IndianFormats.java so a person
// is stopped while typing rather than by a refused request. Each field has:
//   clean(value)  - what the input keeps as they type (wrong characters never appear, length capped)
//   max           - the input's maxLength
//   mode          - the mobile keyboard to show (inputMode)
//   error(value)  - '' when valid, otherwise the message to show
const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5], [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7], [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3], [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
]
const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4], [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7], [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
]

/** 12 digits, not starting 0 or 1, last digit the Verhoeff check digit. */
export function validAadhaar(s) {
  const digits = String(s ?? '').replace(/\s+/g, '')
  if (!/^[2-9]\d{11}$/.test(digits)) return false
  let c = 0
  for (let i = 0; i < digits.length; i++) {
    c = D[c][P[i % 8][Number(digits[digits.length - 1 - i])]]
  }
  return c === 0
}

const digits = (v, n) => String(v ?? '').replace(/\D/g, '').slice(0, n)
const upperAlnum = (v, n) => String(v ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, n)

// PAN: 5 letters, 4 digits, 1 letter. Each typed character is kept only if it fits its position.
function cleanPan(v) {
  let out = ''
  for (const ch of String(v ?? '').toUpperCase()) {
    const i = out.length
    if (i >= 10) break
    const letter = /[A-Z]/.test(ch)
    const digit = /[0-9]/.test(ch)
    if ((i < 5 || i === 9) ? letter : digit) out += ch
  }
  return out
}

const PERSON = /^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u
const BUSINESS = /^[\p{L}\p{M}0-9][\p{L}\p{M}0-9 .,'&()/-]*$/u
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/

export const F = {
  aadhaar: {
    clean: (v) => digits(v, 12), max: 12, mode: 'numeric', placeholder: '12 digits',
    error: (v) => (!/^\d{12}$/.test(v || '') ? 'Aadhaar number must be exactly 12 digits'
      : !validAadhaar(v) ? 'Aadhaar number is not valid - check the 12 digits' : ''),
  },
  mobile: {
    clean: (v) => digits(v, 10), max: 10, mode: 'numeric', placeholder: '9876543210',
    error: (v) => (/^[6-9]\d{9}$/.test(v || '') ? '' : 'Enter a 10-digit Indian mobile number starting with 6, 7, 8 or 9'),
  },
  pan: {
    clean: cleanPan, max: 10, mode: 'text', placeholder: 'ABCPE1234F',
    error: (v) => (/^[A-Z]{3}[PCHFATBLJGE][A-Z][0-9]{4}[A-Z]$/.test(v || '') ? ''
      : 'PAN must be 10 characters like ABCPE1234F (5 letters, 4 digits, 1 letter)'),
  },
  licence: {
    clean: (v) => upperAlnum(v, 15), max: 15, mode: 'text', placeholder: 'TN0120190001234',
    error: (v) => (/^[A-Z]{2}[0-9]{2}(19|20)[0-9]{2}[0-9]{7}$/.test(v || '') ? ''
      : 'Driving licence number must be 15 characters like TN0120190001234 (state, RTO, year, 7-digit number)'),
  },
  plate: {
    clean: (v) => String(v ?? '').toUpperCase().replace(/[^A-Z0-9 ]/g, '').replace(/\s+/g, ' ').slice(0, 13),
    max: 13, mode: 'text', placeholder: 'TN 01 AB 1234',
    error: (v) => {
      const s = String(v ?? '').replace(/[\s-]/g, '').toUpperCase()
      return /^[A-Z]{2}(0[1-9]|[1-9][0-9]?)[A-Z]{0,3}(?!0000)[0-9]{4}$/.test(s) || /^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$/.test(s)
        ? '' : 'Vehicle number must look like TN 01 AB 1234 (or 22 BH 1234 AA)'
    },
  },
  ifsc: {
    clean: (v) => upperAlnum(v, 11), max: 11, mode: 'text', placeholder: 'SBIN0001234',
    error: (v) => (/^[A-Z]{4}0[A-Z0-9]{6}$/.test(v || '') ? '' : 'IFSC must be 11 characters like SBIN0001234'),
  },
  account: {
    clean: (v) => digits(v, 18), max: 18, mode: 'numeric', placeholder: '9 to 18 digits',
    error: (v) => (/^\d{9,18}$/.test(v || '') ? '' : 'Bank account number must be 9 to 18 digits'),
  },
  upi: {
    clean: (v) => String(v ?? '').replace(/\s/g, '').slice(0, 128), max: 128, mode: 'email', placeholder: 'name@bank',
    error: (v) => (/^[a-zA-Z0-9._-]{2,64}@[a-zA-Z][a-zA-Z0-9]{1,63}$/.test(v || '') ? '' : 'UPI ID must look like name@bank'),
  },
  name: {
    // Letters in any script, space, . ' - ; digits and symbols never appear.
    clean: (v) => String(v ?? '').replace(/[^\p{L}\p{M} .'-]/gu, '').replace(/\s{2,}/g, ' ').slice(0, 60),
    max: 60, mode: 'text',
    error: (v, what = 'Name') => {
      const s = String(v ?? '').trim()
      return s.length >= 2 && PERSON.test(s) ? '' : `${what} must be 2 to 60 letters (spaces, . ' - allowed)`
    },
  },
  business: {
    clean: (v) => String(v ?? '').replace(/[^\p{L}\p{M}0-9 .,'&()/-]/gu, '').replace(/\s{2,}/g, ' ').slice(0, 80),
    max: 80, mode: 'text',
    error: (v, what = 'Name') => {
      const s = String(v ?? '').trim()
      return s.length >= 2 && BUSINESS.test(s) ? '' : `${what} must be 2 to 80 letters or digits`
    },
  },
  email: {
    clean: (v) => String(v ?? '').replace(/\s/g, '').slice(0, 254), max: 254, mode: 'email', placeholder: 'you@example.com',
    error: (v) => (EMAIL.test(String(v ?? '').trim()) ? '' : 'Enter a valid email address'),
  },
  password: {
    clean: (v) => String(v ?? '').slice(0, 64), max: 64, mode: 'text',
    error: (v) => {
      const s = String(v ?? '')
      if (s.length < 8 || s.length > 64) return 'Password must be 8 to 64 characters'
      if (!/\p{L}/u.test(s) || !/\d/.test(s)) return 'Password must have at least one letter and one number'
      if (s !== s.trim()) return 'Password cannot start or end with a space'
      return ''
    },
  },
}

/** Props for an <input> of this kind: value cleaning on change, maxLength and keyboard. */
export function fieldProps(kind, value, onValue) {
  const f = F[kind]
  return {
    value: value ?? '',
    maxLength: f.max,
    inputMode: f.mode,
    onChange: (e) => onValue(f.clean(e.target.value)),
    ...(f.placeholder ? { placeholder: f.placeholder } : {}),
    ...(kind === 'pan' || kind === 'licence' || kind === 'ifsc' || kind === 'plate' ? { autoCapitalize: 'characters', spellCheck: false } : {}),
  }
}
