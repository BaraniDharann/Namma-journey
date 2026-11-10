// Razorpay Checkout runs from Razorpay's own script (it must: card and UPI entry happen inside
// their PCI-compliant iframe). Loaded on first use only, so pages that never pay never fetch it.
const CHECKOUT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'

let loading = null

// Puts UPI at the top of Checkout with the common apps named, then the default blocks (cards,
// netbanking, wallets). Razorpay shows each app button only where it can actually open the app
// (a phone with that app installed, live keys); elsewhere the block falls back to the QR code and
// the "enter UPI ID" field, so this never hides a way to pay.
export const UPI_FIRST = {
  display: {
    blocks: {
      upi: {
        name: 'Pay with any UPI app',
        instruments: [
          { method: 'upi', flows: ['intent'], apps: ['google_pay', 'phonepe', 'paytm'] },
          { method: 'upi', flows: ['qr', 'collect'] },
        ],
      },
    },
    sequence: ['block.upi'],
    preferences: { show_default_blocks: true },
  },
}

export function loadRazorpayCheckout() {
  if (typeof window !== 'undefined' && window.Razorpay) return Promise.resolve(window.Razorpay)
  if (loading) return loading
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_SRC
    script.async = true
    script.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay unavailable')))
    script.onerror = () => { loading = null; script.remove(); reject(new Error('Could not load Razorpay')) }
    document.body.appendChild(script)
  })
  return loading
}

/**
 * Opens Checkout for an order the server created. Resolves with Razorpay's
 * { razorpay_payment_id, razorpay_order_id, razorpay_signature } on success, null if the
 * passenger closes the popup, and rejects when the payment fails.
 */
export async function openRazorpayCheckout(order, prefill = {}) {
  const Razorpay = await loadRazorpayCheckout()
  return new Promise((resolve, reject) => {
    const rzp = new Razorpay({
      key: order.keyId,
      order_id: order.orderId,
      amount: order.amountPaise,
      currency: order.currency || 'INR',
      name: order.merchantName,
      description: order.description,
      prefill,
      config: UPI_FIRST,
      // Lets the UPI-app buttons open GPay / PhonePe / Paytm when the site runs inside an
      // in-app browser (Instagram, WhatsApp, etc.) instead of a full browser.
      webview_intent: true,
      theme: { color: '#0f766e' },
      handler: (response) => resolve(response),
      modal: { ondismiss: () => resolve(null) },
    })
    rzp.on('payment.failed', (resp) => reject(new Error(resp?.error?.description || 'Payment failed')))
    rzp.open()
  })
}
