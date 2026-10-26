import React from 'react'
import { motion } from 'framer-motion'

/** The landing page's shared section pieces, used by both the Trip and the City ride sections. */

export const EASE = [0.16, 1, 0.3, 1]

export function SectionHead({ title, children }) {
  return (
    <motion.div
      className="ws-head"
      initial={{ opacity: 0, y: 22 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.4 }}
      transition={{ duration: 0.7, ease: EASE }}
    >
      <h2>{title}</h2>
      {children && <p>{children}</p>}
    </motion.div>
  )
}

export const rise = {
  hidden: { opacity: 0, y: 24 },
  show: (i = 0) => ({ opacity: 1, y: 0, transition: { duration: 0.6, delay: i * 0.06, ease: EASE } }),
}
