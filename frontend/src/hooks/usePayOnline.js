import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import { useAuth } from '../context/AuthContext'
import { createRazorpayOrder, verifyRazorpayPayment } from '../utils/api'
import { openRazorpayCheckout } from '../utils/razorpay'
import { rememberPayment, useCelebrate } from '../components/celebrate/Celebration'
import { inr, shortPlace } from '../dash/metrics'

/**
 * Right after a Trip is booked: take the advance that turns the hold into a booking. Closing
 * Checkout is fine - the booking waits (with a "Pay advance" button) until the hold runs out.
 */
export function useCollectAdvance() {
  const payOnline = usePayOnline()
  const celebrate = useCelebrate()
  const navigate = useNavigate()

  return useCallback(async (booking) => {
    const label = `${shortPlace(booking.fromPlace)} → ${shortPlace(booking.toPlace)}`
    const { status } = await payOnline(booking, label)
    const toBookings = { actionLabel: 'View my bookings', onAction: () => navigate('/user/bookings') }
    if (status === 'paid') {
      celebrate({ title: 'Trip booked', message: `${inr(booking.advanceAmount)} advance paid for ${label}. A verified driver will be confirmed shortly.`, ...toBookings })
    } else if (status === 'confirming') {
      celebrate({ tone: 'calm', title: 'Payment received', message: "We're confirming your advance with Razorpay. Your trip will show as booked in a moment.", ...toBookings })
    } else {
      const mins = Math.max(1, Math.round((booking.holdSecondsLeft || 0) / 60))
      celebrate({ tone: 'calm', title: 'Trip held for you', message: `Pay the ${inr(booking.advanceAmount)} advance within ${mins} minutes to keep it. You can pay from My Bookings.`, ...toBookings })
    }
  }, [payOnline, celebrate, navigate])
}

/**
 * Pays whatever a booking owes next, online: the Trip advance while it is due, otherwise the fare
 * (or its balance). The server decides which; this only runs the order -> Checkout -> verify loop.
 *
 * Resolves to:
 *   { status: 'paid', order }      signature verified, the server settled it
 *   { status: 'confirming', order } Razorpay took the money but our verify call failed; the
 *                                   webhook settles it and the payment watcher celebrates later
 *   { status: 'closed' }            the passenger closed Checkout, or it could not start
 */
export function usePayOnline() {
  const { user } = useAuth()

  return useCallback(async (booking, label = '') => {
    let order
    try {
      order = (await createRazorpayOrder(user.userId, booking.bookingId)).data
    } catch { return { status: 'closed' } } // the API layer already toasted the reason

    let checkout
    try {
      checkout = await openRazorpayCheckout(order, { name: user.name, email: user.email, contact: user.mobile })
    } catch (e) {
      toast.error(e?.message || 'Payment failed. You have not been charged.')
      return { status: 'closed', order }
    }
    if (!checkout) return { status: 'closed', order }

    try {
      await verifyRazorpayPayment(user.userId, booking.bookingId, {
        razorpayOrderId: checkout.razorpay_order_id,
        razorpayPaymentId: checkout.razorpay_payment_id,
        razorpaySignature: checkout.razorpay_signature,
      })
      return { status: 'paid', order }
    } catch {
      rememberPayment(user.userId, { paymentId: order.paymentId, amount: order.amount, label })
      return { status: 'confirming', order }
    }
  }, [user])
}
