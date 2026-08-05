import React, { useEffect, useRef, useState } from 'react'
import {
  motion,
  useAnimation,
  useInView,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useTransform,
} from 'framer-motion'

/** Shared easing — the whole UI moves on this curve. */
export const EASE = [0.16, 1, 0.3, 1]
export const EASE_OUT = [0.22, 0.61, 0.36, 1]

/**
 * Scroll-reveal wrapper. Fades + lifts a block the first time it enters view.
 * Honours prefers-reduced-motion by rendering the block statically.
 */
// Blur defaults to 0: animating `filter` on many elements at once is the most
// expensive thing on this page and tanks low-end devices. Opt in per-element.
export function Reveal({
  children,
  delay = 0,
  y = 34,
  x = 0,
  blur = 0,
  scale = 1,
  duration = 0.8,
  once = true,
  amount = 0.25,
  as = 'div',
  ...rest
}) {
  const reduced = useReducedMotion()
  const MotionTag = motion[as] || motion.div

  if (reduced) {
    const Tag = as
    return <Tag {...rest}>{children}</Tag>
  }

  const from = { opacity: 0, y, x, scale }
  const to = { opacity: 1, y: 0, x: 0, scale: 1 }
  if (blur > 0) {
    from.filter = `blur(${blur}px)`
    to.filter = 'blur(0px)'
  }

  return (
    <MotionTag
      initial={from}
      whileInView={to}
      viewport={{ once, amount }}
      transition={{ duration, delay, ease: EASE }}
      {...rest}
    >
      {children}
    </MotionTag>
  )
}

/**
 * Word-by-word headline reveal. Each word rises out of its own mask.
 */
export function SplitText({ text, delay = 0, stagger = 0.06, className, style, once = true }) {
  const reduced = useReducedMotion()
  const words = String(text).split(' ')

  if (reduced) return <span className={className} style={style}>{text}</span>

  return (
    <motion.span
      className={className}
      style={{ display: 'inline-block', ...style }}
      initial="hidden"
      whileInView="show"
      viewport={{ once, amount: 0.5 }}
      transition={{ staggerChildren: stagger, delayChildren: delay }}
    >
      {words.map((word, i) => (
        <span
          key={`${word}-${i}`}
          style={{ display: 'inline-block', overflow: 'hidden', verticalAlign: 'bottom' }}
        >
          <motion.span
            style={{ display: 'inline-block', willChange: 'transform' }}
            variants={{
              hidden: { y: '110%', opacity: 0, rotate: 4 },
              show: { y: '0%', opacity: 1, rotate: 0 },
            }}
            transition={{ duration: 0.85, ease: EASE }}
          >
            {word}
          </motion.span>
          {i < words.length - 1 && ' '}
        </span>
      ))}
    </motion.span>
  )
}

/**
 * Cursor-follow "magnetic" element — the card leans toward the pointer.
 */
export function Magnetic({ children, strength = 18, className, style, ...rest }) {
  const reduced = useReducedMotion()
  const ref = useRef(null)
  const x = useMotionValue(0)
  const y = useMotionValue(0)
  const sx = useSpring(x, { stiffness: 200, damping: 18, mass: 0.4 })
  const sy = useSpring(y, { stiffness: 200, damping: 18, mass: 0.4 })

  const onMove = (e) => {
    if (reduced || !ref.current) return
    const r = ref.current.getBoundingClientRect()
    x.set(((e.clientX - r.left) / r.width - 0.5) * strength)
    y.set(((e.clientY - r.top) / r.height - 0.5) * strength)
  }

  return (
    <motion.div
      ref={ref}
      className={className}
      style={{ x: sx, y: sy, ...style }}
      onMouseMove={onMove}
      onMouseLeave={() => { x.set(0); y.set(0) }}
      {...rest}
    >
      {children}
    </motion.div>
  )
}

/**
 * 3D tilt card — rotates on pointer position, springs back on exit.
 */
export function TiltCard({ children, max = 9, className, style, glare = true, ...rest }) {
  const reduced = useReducedMotion()
  const ref = useRef(null)
  const rx = useSpring(useMotionValue(0), { stiffness: 180, damping: 16 })
  const ry = useSpring(useMotionValue(0), { stiffness: 180, damping: 16 })
  const gx = useMotionValue(50)
  const gy = useMotionValue(50)
  const glareBg = useTransform(
    [gx, gy],
    ([px, py]) => `radial-gradient(300px circle at ${px}% ${py}%, rgba(255,255,255,0.22), transparent 60%)`
  )

  const onMove = (e) => {
    if (reduced || !ref.current) return
    const r = ref.current.getBoundingClientRect()
    const px = (e.clientX - r.left) / r.width
    const py = (e.clientY - r.top) / r.height
    ry.set((px - 0.5) * max * 2)
    rx.set((0.5 - py) * max * 2)
    gx.set(px * 100)
    gy.set(py * 100)
  }

  return (
    <motion.div
      ref={ref}
      className={className}
      onMouseMove={onMove}
      onMouseLeave={() => { rx.set(0); ry.set(0) }}
      style={{
        rotateX: rx,
        rotateY: ry,
        transformStyle: 'preserve-3d',
        transformPerspective: 900,
        position: 'relative',
        ...style,
      }}
      {...rest}
    >
      {children}
      {glare && !reduced && (
        <motion.span
          aria-hidden
          style={{
            position: 'absolute',
            inset: 0,
            borderRadius: 'inherit',
            pointerEvents: 'none',
            background: glareBg,
            mixBlendMode: 'soft-light',
          }}
        />
      )}
    </motion.div>
  )
}

/**
 * Odometer-style number that rolls up when scrolled into view.
 */
export function Counter({ value, duration = 1.8, suffix = '', prefix = '', decimals = 0, style, className }) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, amount: 0.6 })
  const [display, setDisplay] = useState(0)
  const reduced = useReducedMotion()

  useEffect(() => {
    if (!inView) return
    if (reduced) { setDisplay(value); return }
    let raf
    const start = performance.now()
    const tick = (now) => {
      const t = Math.min(1, (now - start) / (duration * 1000))
      const eased = 1 - Math.pow(1 - t, 3)
      setDisplay(value * eased)
      if (t < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [inView, value, duration, reduced])

  return (
    <span ref={ref} className={className} style={style}>
      {prefix}
      {display.toLocaleString(undefined, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      })}
      {suffix}
    </span>
  )
}

/**
 * Staggered list container — pair with <StaggerItem/>.
 */
export function Stagger({ children, stagger = 0.08, delay = 0, amount = 0.2, className, style, once = true }) {
  return (
    <motion.div
      className={className}
      style={style}
      initial="hidden"
      whileInView="show"
      viewport={{ once, amount }}
      variants={{
        hidden: {},
        show: { transition: { staggerChildren: stagger, delayChildren: delay } },
      }}
    >
      {children}
    </motion.div>
  )
}

export const staggerItem = {
  hidden: { opacity: 0, y: 28, scale: 0.97 },
  show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.65, ease: EASE } },
}

export function StaggerItem({ children, className, style, ...rest }) {
  return (
    <motion.div className={className} style={style} variants={staggerItem} {...rest}>
      {children}
    </motion.div>
  )
}

/** True once the browser is idle — used to defer heavy 3D work. */
export function useIdleReady(timeout = 300) {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    const idle = window.requestIdleCallback || ((cb) => setTimeout(cb, timeout))
    const cancel = window.cancelIdleCallback || clearTimeout
    const handle = idle(() => setReady(true), { timeout: 1200 })
    return () => cancel(handle)
  }, [timeout])
  return ready
}

/**
 * Is the element anywhere near the viewport? Used to park WebGL render loops
 * that have scrolled away instead of burning GPU on them.
 */
export function useOnScreen(ref, rootMargin = '250px') {
  const [onScreen, setOnScreen] = useState(true)
  useEffect(() => {
    const el = ref.current
    if (!el || typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting), { rootMargin })
    io.observe(el)
    return () => io.disconnect()
  }, [ref, rootMargin])
  return onScreen
}

/** Coarse pointer / small screen check — we drop 3D on low-power devices. */
export function useIsCompact(query = '(max-width: 820px)') {
  const [compact, setCompact] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(query).matches
  )
  useEffect(() => {
    const mq = window.matchMedia(query)
    const on = (e) => setCompact(e.matches)
    mq.addEventListener('change', on)
    return () => mq.removeEventListener('change', on)
  }, [query])
  return compact
}

/** Re-export so consumers pull everything from one place. */
export { motion, useAnimation, useInView, useReducedMotion, useTransform, useSpring, useMotionValue }
