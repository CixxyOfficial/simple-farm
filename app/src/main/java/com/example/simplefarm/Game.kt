package com.example.simplefarm

/**
 * Logika game. Semua perubahan state dipanggil dari thread GL
 * (lewat GLSurfaceView.queueEvent) supaya tidak balapan dengan renderer.
 */
class Game {

    companion object {
        const val N = 32            // dunia N x N petak
        const val ORG = 16          // petak x/z berkisar -ORG .. ORG-1

        const val NONE = 0
        const val SEED = 1
        const val CROP = 2

        const val WET_TIME = 30f    // detik tanah tetap basah setelah disiram

        val NAMES = arrayOf("Gandum", "Wortel", "Labu")
        val SEED_PRICE = intArrayOf(5, 10, 25)
        val SELL_PRICE = intArrayOf(9, 20, 55)
        val GROW_TIME = floatArrayOf(15f, 25f, 40f)   // detik (hanya tumbuh saat tanah basah)
        val COLORS = arrayOf(
            floatArrayOf(0.93f, 0.78f, 0.20f),
            floatArrayOf(0.95f, 0.50f, 0.10f),
            floatArrayOf(0.92f, 0.42f, 0.05f)
        )
    }

    val soil = BooleanArray(N * N)
    val crop = IntArray(N * N) { -1 }
    val growth = FloatArray(N * N)
    val wet = FloatArray(N * N)

    val seeds = IntArray(3)
    val produce = IntArray(3)

    var coins = 30
        private set

    /** 0 = cangkul, 1 = penyiram, 2 = slot item (bibit / hasil panen dari tas) */
    var selected = 0
        private set
    var slotKind = NONE
        private set
    var slotType = 0
        private set

    var nearShop = false
        private set

    var onChange: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null

    fun idx(tx: Int, tz: Int): Int = (tz + ORG) * N + (tx + ORG)

    fun inBounds(tx: Int, tz: Int): Boolean =
        tx >= -ORG && tx < ORG && tz >= -ORG && tz < ORG

    /** Area yang boleh dicangkul (di luar itu: toko, pohon, pagar). */
    fun canTill(tx: Int, tz: Int): Boolean =
        tx >= -12 && tx <= 11 && tz >= -9 && tz <= 13

    private fun message(m: String) {
        onMessage?.invoke(m)
    }

    private fun changed() {
        onChange?.invoke()
    }

    fun slotCount(): Int = when (slotKind) {
        SEED -> seeds[slotType]
        CROP -> produce[slotType]
        else -> 0
    }

    fun setNearShop(value: Boolean) {
        if (value != nearShop) {
            nearShop = value
            changed()
        }
    }

    fun update(dt: Float) {
        for (i in 0 until N * N) {
            val c = crop[i]
            if (c >= 0 && growth[i] < 1f && wet[i] > 0f) {
                growth[i] += dt / GROW_TIME[c]
                if (growth[i] >= 1f) {
                    growth[i] = 1f
                    message("${NAMES[c]} siap dipanen!")
                }
            }
            if (wet[i] > 0f) {
                wet[i] -= dt
                if (wet[i] < 0f) wet[i] = 0f
            }
        }
    }

    fun selectSlot(i: Int) {
        selected = i
        changed()
    }

    /** Pasang item dari tas ke slot 3. */
    fun setSlot(kind: Int, type: Int) {
        val count = if (kind == SEED) seeds[type] else produce[type]
        if (count <= 0) return
        slotKind = kind
        slotType = type
        selected = 2
        message("Dipasang di slot 3")
        changed()
    }

    private fun clearSlotIfEmpty() {
        if (slotKind != NONE && slotCount() <= 0) {
            slotKind = NONE
        }
    }

    /** Dipanggil saat layar diketuk dan crosshair mengarah ke petak (tx, tz). */
    fun use(tx: Int, tz: Int) {
        if (!inBounds(tx, tz)) return
        val i = idx(tx, tz)
        val c = crop[i]

        // tanaman matang dipanen dengan item apa pun
        if (c >= 0 && growth[i] >= 1f) {
            crop[i] = -1
            growth[i] = 0f
            produce[c] += 1
            message("Panen ${NAMES[c]}! Jual di toko")
            changed()
            return
        }

        when (selected) {
            0 -> {
                if (c >= 0) {
                    message("Tanaman belum matang")
                } else if (soil[i]) {
                    message("Tanah sudah dicangkul")
                } else if (!canTill(tx, tz)) {
                    message("Tidak bisa dicangkul di sini")
                } else {
                    soil[i] = true
                    message("Tanah dicangkul")
                }
            }
            1 -> {
                if (!soil[i]) {
                    message("Cangkul tanahnya dulu")
                } else {
                    wet[i] = WET_TIME
                    message("Tanah disiram")
                }
            }
            else -> {
                when (slotKind) {
                    SEED -> {
                        if (!soil[i]) {
                            message("Cangkul tanahnya dulu")
                        } else if (c >= 0) {
                            message("Sudah ada tanaman")
                        } else {
                            crop[i] = slotType
                            growth[i] = 0f
                            seeds[slotType] -= 1
                            message("Menanam ${NAMES[slotType]}. Siram supaya tumbuh!")
                            clearSlotIfEmpty()
                            changed()
                        }
                    }
                    CROP -> message("Hasil panen dijual di toko")
                    else -> message("Slot kosong. Ambil item dari tas")
                }
            }
        }
    }

    fun buySeed(type: Int) {
        if (coins < SEED_PRICE[type]) {
            message("Koin tidak cukup")
            return
        }
        coins -= SEED_PRICE[type]
        seeds[type] += 1
        if (slotKind == NONE) {
            slotKind = SEED
            slotType = type
            selected = 2
        }
        message("Beli bibit ${NAMES[type]}")
        changed()
    }

    fun sellCrop(type: Int) {
        val n = produce[type]
        if (n <= 0) return
        val gain = n * SELL_PRICE[type]
        coins += gain
        produce[type] = 0
        if (slotKind == CROP && slotType == type) slotKind = NONE
        message("Jual ${NAMES[type]} x$n: +$gain koin")
        changed()
    }

    fun sellAll() {
        var gain = 0
        for (t in 0 until 3) {
            gain += produce[t] * SELL_PRICE[t]
            produce[t] = 0
        }
        if (gain <= 0) {
            message("Tidak ada hasil panen")
            return
        }
        coins += gain
        if (slotKind == CROP) slotKind = NONE
        message("Semua hasil panen terjual: +$gain koin")
        changed()
    }
}
