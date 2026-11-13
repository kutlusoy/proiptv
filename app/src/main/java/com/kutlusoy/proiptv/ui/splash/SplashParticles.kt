package com.kutlusoy.proiptv.ui.splash

import kotlin.random.Random

/** Dust motes that drift upwards in three depths. Positions are fractions of the screen. */
data class Particle(val x: Float, val y: Float, val radius: Float, val depth: Float, val phase: Float)

object SplashParticles {
    const val DRIFT_PER_SECOND = 0.035f

    fun create(count: Int, seed: Int): List<Particle> {
        val random = Random(seed)
        return List(count) {
            val depth = listOf(0.35f, 0.65f, 1f)[random.nextInt(3)]
            Particle(random.nextFloat(), random.nextFloat(), 1.2f + 2.8f * depth * random.nextFloat(), depth, random.nextFloat() * 6.28f)
        }
    }

    fun y(particle: Particle, elapsedSeconds: Float): Float {
        val moved = particle.y - DRIFT_PER_SECOND * particle.depth * elapsedSeconds
        return moved - kotlin.math.floor(moved)
    }
}
