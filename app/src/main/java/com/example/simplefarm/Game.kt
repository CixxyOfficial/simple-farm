package com.example.simplefarm

import kotlin.math.abs

/** Logika game: kebun 4x4, tanam -> tumbuh -> panen -> koin. */
class Game {

    companion object {
        const val GRID = 4
        const val PITCH = 2.0f      // jarak antar pusat lahan
        const val HALF = 0.95f      // setengah ukuran area sentuh lahan
        const val SEED_COST = 5
        const val SELL_PRICE = 12
        const val GROW_SECONDS = 10f

        const val EMPTY = 0
        const val GROWING = 1
        const val RIPE = 2
    }

    val count = GRID * GRID
    val state = IntArray(count)
    val growth = FloatArray(count)

    var coins = 20
        private set
    var harvested = 0
        private set

    /** (koin, panen, pesan) dipanggil setiap ada perubahan. */
    var listener: ((Int, Int, String?) -> Unit)? = null

    fun plotX(i: Int): Float = ((i % GRID) - (GRID - 1) / 2f) * PITCH
    fun plotZ(i: Int): Float = ((i / GRID) - (GRID - 1) / 2f) * PITCH

    fun refresh(message: String? = null) {
        listener?.invoke(coins, harvested, message)
    }

    fun update(dt: Float) {
        for (i in 0 until count) {
            if (state[i] == GROWING) {
                growth[i] += dt / GROW_SECONDS
                if (growth[i] >= 1f) {
                    growth[i] = 1f
                    state[i] = RIPE
                    refresh("Ada tanaman siap panen!")
                }
            }
        }
    }

    /** Dipanggil saat layar diketuk; (x, z) = titik sentuh di tanah. */
    fun tapWorld(x: Float, z: Float) {
        for (i in 0 until count) {
            if (abs(x - plotX(i)) <= HALF && abs(z - plotZ(i)) <= HALF) {
                interact(i)
                return
            }
        }
    }

    private fun interact(i: Int) {
        when (state[i]) {
            EMPTY -> {
                if (coins >= SEED_COST) {
                    coins -= SEED_COST
                    state[i] = GROWING
                    growth[i] = 0f
                    refresh("Menanam bibit (-$SEED_COST koin)")
                } else if (state.all { it == EMPTY }) {
                    // supaya game tidak buntu kalau koin habis
                    coins += SEED_COST
                    refresh("Bantuan petani: +$SEED_COST koin")
                } else {
                    refresh("Koin tidak cukup untuk beli bibit")
                }
            }
            GROWING -> refresh("Tanaman masih tumbuh... sabar ya")
            RIPE -> {
                state[i] = EMPTY
                growth[i] = 0f
                coins += SELL_PRICE
                harvested += 1
                refresh("Panen! +$SELL_PRICE koin")
            }
        }
    }
}
