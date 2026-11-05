import React from 'react'
import { motion } from 'framer-motion'
import BusSearchForm from '../components/bus/BusSearchForm'
import { EASE } from './sectionParts'

/** Landing hero in Bus mode: one line of story, then the search form across the width. */
export default function BusHero({ switcher }) {
  return (
    <section id="drive" className="bus-hero" aria-labelledby="bus-title">
      <div className="ws-wrap bus-hero-in">
        <motion.div initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease: EASE, delay: 0.1 }}>
          {switcher}
          <h1 id="bus-title">Every operator, one seat map. <em>Pick your berth.</em></h1>
          <p>Compare intercity buses across Tamil Nadu by price, time and rating, choose the exact seat, and get your ticket by email.</p>
        </motion.div>
        <motion.div className="pc" style={{ background: 'transparent' }}
          initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, ease: EASE, delay: 0.25 }}>
          <BusSearchForm idPrefix="hero-bus" />
        </motion.div>
      </div>
    </section>
  )
}
