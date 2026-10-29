const fs=require('fs')
const ed=(p,pairs)=>{let s=fs.readFileSync(p,'utf8');for(const [a,b] of pairs){if(!s.includes(a))throw new Error('missing '+a.slice(0,40));s=s.replace(a,()=>b)}fs.writeFileSync(p,s)}
ed('src/components/driver/applicationGate.test.js',[["import { gateView } from './gateView'","import { gateView, getCachedVerification, setCachedVerification, clearVerificationCache } from './gateView'"]])
ed('src/components/driver/ApplicationGate.jsx',[
["import { gateView, DOCUMENTS }","import { gateView, DOCUMENTS, getCachedVerification, setCachedVerification }"],
["useState(null)\n  const [failed","useState(() => getCachedVerification(driverId))\n  const [failed"],
["      setVerification(data)\n","      setCachedVerification(driverId, data)\n      setVerification(data)\n"],
[`  useEffect(() => {
    if (!driverId) return undefined
    check()
    const onFocus = () => check()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [driverId, check])

  const view = gateView(verification)
`,`  const view = gateView(verification)

  // Always confirm in the background, but once approved there is nothing left to watch for.
  useEffect(() => {
    if (!driverId) return undefined
    check()
    if (view === 'app') return undefined
    const onFocus = () => check()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [driverId, check, view === 'app']) // eslint-disable-line react-hooks/exhaustive-deps
`]])
ed('src/components/auth/EmailCodeScreen.jsx',[["      setError(err.response?.data?.error || err.response?.data?.message || 'Could not resend the code. Please try again.')","      setError(err.response?.data?.error || err.response?.data?.message || err.message || 'Could not resend the code. Please try again.')"]])
ed('src/pages/DriverSignupPage.jsx',[
["import EmailCodeScreen","import { assertCodeResent } from '../components/auth/codeResend'\nimport EmailCodeScreen"],
["const resend = () => driverLogin({ mobile: about.mobile, password: about.password })","const resend = () => driverLogin({ mobile: about.mobile, password: about.password }).then(assertCodeResent)"]])
ed('src/pages/DriverLoginPage.jsx',[
["import EmailCodeScreen","import { assertCodeResent } from '../components/auth/codeResend'\nimport EmailCodeScreen"],
["onResend={() => driverLogin(form)}","onResend={() => driverLogin(form).then(assertCodeResent)}"]])
ed('src/context/AuthContext.jsx',[
["import { logoutEverywhere } from '../utils/api'","import { logoutEverywhere } from '../utils/api'\nimport { clearVerificationCache } from '../components/driver/gateView'"],
["    setToken(null)\n    setUser(null)\n    safeStorage(() => localStorage.removeItem('nj_token'))","    clearVerificationCache()\n    setToken(null)\n    setUser(null)\n    safeStorage(() => localStorage.removeItem('nj_token'))"]])
