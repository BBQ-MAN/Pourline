package com.timemoredot.protocol

enum class WeightUnit(val raw: Int) { Gram(0x00), Ounce(0x01) }       // C-06-1

/** 1 oz = 28.349523125 g (정확값). */
const val GRAMS_PER_OUNCE = 28.349523125
