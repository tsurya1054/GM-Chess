package com.example.chess

/**
 * Deterministic 64-bit Zobrist hash tables for transposition table & repetition detection.
 */
object Zobrist {
  // Piece index 0..12 maps piece (-6..+6) via (piece + 6)
  val pieceSquare: Array<LongArray> = Array(13) { LongArray(64) }
  val castling: LongArray = LongArray(16)
  // File 0..7 for active en-passant file, index 8 = none
  val enPassantFile: LongArray = LongArray(9)
  val blackToMove: Long

  init {
    var seed = 0x9E3779B97F4A7C15UL
    fun nextLong(): Long {
      seed += 0x9E3779B97F4A7C15UL
      var z = seed
      z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
      z = (z xor (z shr 27)) * 0x94D049BB133111EBUL
      return (z xor (z shr 31)).toLong()
    }

    for (p in 0..12) {
      if (p == 6) continue // EMPTY = 0 -> hash 0
      for (sq in 0..63) {
        pieceSquare[p][sq] = nextLong()
      }
    }
    for (c in 0..15) {
      castling[c] = nextLong()
    }
    for (f in 0..7) {
      enPassantFile[f] = nextLong()
    }
    enPassantFile[8] = 0L
    blackToMove = nextLong()
  }

  fun pieceKey(piece: Int, sq: Int): Long = pieceSquare[piece + 6][sq]
}
