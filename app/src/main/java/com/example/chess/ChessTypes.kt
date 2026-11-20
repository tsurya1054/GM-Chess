package com.example.chess

import kotlin.math.abs

enum class Side(val sign: Int) {
  WHITE(1),
  BLACK(-1);

  fun opposite(): Side = if (this == WHITE) BLACK else WHITE

  companion object {
    fun fromSign(sign: Int): Side = if (sign >= 0) WHITE else BLACK
  }
}

enum class PieceType(val code: Int, val sanChar: String, val indoName: String, val baseValue: Int) {
  PAWN(1, "", "Bidak", 100),
  KNIGHT(2, "N", "Kuda", 320),
  BISHOP(3, "B", "Gajah", 335),
  ROOK(4, "R", "Benteng", 500),
  QUEEN(5, "Q", "Menteri", 950),
  KING(6, "K", "Raja", 20000);

  companion object {
    fun fromCode(code: Int): PieceType? =
      when (abs(code)) {
        1 -> PAWN
        2 -> KNIGHT
        3 -> BISHOP
        4 -> ROOK
        5 -> QUEEN
        6 -> KING
        else -> null
      }
  }
}

object Piece {
  const val EMPTY = 0
  const val WP = 1
  const val WN = 2
  const val WB = 3
  const val WR = 4
  const val WQ = 5
  const val WK = 6

  const val BP = -1
  const val BN = -2
  const val BB = -3
  const val BR = -4
  const val BQ = -5
  const val BK = -6

  fun typeOf(piece: Int): Int = abs(piece)
  fun sideOf(piece: Int): Int = if (piece > 0) 1 else if (piece < 0) -1 else 0
}

/**
 * Zero-allocation 32-bit packed move encoding for high-speed search.
 */
object MoveCodec {
  const val NO_MOVE = 0

  const val FLAG_NORMAL = 0
  const val FLAG_DOUBLE_PAWN = 1
  const val FLAG_KING_CASTLE = 2
  const val FLAG_QUEEN_CASTLE = 3
  const val FLAG_EN_PASSANT = 4

  fun encode(
    from: Int,
    to: Int,
    movedType: Int,
    capturedType: Int = 0,
    promoType: Int = 0,
    flag: Int = FLAG_NORMAL,
  ): Int {
    return (from and 0x3F) or
      ((to and 0x3F) shl 6) or
      ((movedType and 0xF) shl 12) or
      ((capturedType and 0xF) shl 16) or
      ((promoType and 0xF) shl 20) or
      ((flag and 0xF) shl 24)
  }

  fun from(move: Int): Int = move and 0x3F
  fun to(move: Int): Int = (move ushr 6) and 0x3F
  fun movedType(move: Int): Int = (move ushr 12) and 0xF
  fun capturedType(move: Int): Int = (move ushr 16) and 0xF
  fun promoType(move: Int): Int = (move ushr 20) and 0xF
  fun flag(move: Int): Int = (move ushr 24) and 0xF
  fun isCapture(move: Int): Boolean = capturedType(move) != 0
  fun isPromotion(move: Int): Boolean = promoType(move) != 0
  fun isTactical(move: Int): Boolean = capturedType(move) != 0 || promoType(move) != 0

  fun squareName(sq: Int): String {
    if (sq !in 0..63) return "-"
    val file = 'a' + (sq and 7)
    val rank = '1' + (sq ushr 3)
    return "$file$rank"
  }

  fun toUci(move: Int): String {
    if (move == NO_MOVE) return ""
    val base = "${squareName(from(move))}${squareName(to(move))}"
    val promo = when (promoType(move)) {
      2 -> "n"
      3 -> "b"
      4 -> "r"
      5 -> "q"
      else -> ""
    }
    return base + promo
  }
}

data class ChessMove(
  val raw: Int,
  val from: Int,
  val to: Int,
  val san: String,
  val readableIndo: String,
  val isCapture: Boolean,
  val isPromotion: Boolean,
  val promotionType: PieceType?,
)

data class CandidateMove(
  val move: ChessMove,
  val evalCentipawnsWhite: Int,
  val mateInMovesWhite: Int?,
  val pvSan: List<String>,
  val reasonIndo: String,
)

data class AnalysisResult(
  val analyzedForSide: Side,
  val bestMove: ChessMove?,
  val candidates: List<CandidateMove>,
  val evalCentipawnsWhite: Int,
  val mateInMovesWhite: Int?,
  val depthReached: Int,
  val nodesSearched: Long,
  val elapsedMs: Long,
  val openingName: String?,
  val tacticalSummary: String,
)

enum class GameStatus {
  ONGOING,
  CHECKMATE_WHITE_WINS,
  CHECKMATE_BLACK_WINS,
  STALEMATE,
  DRAW_REPETITION,
  DRAW_FIFTY_MOVE,
  DRAW_INSUFFICIENT_MATERIAL;

  val isGameOver: Boolean
    get() = this != ONGOING
}

data class MoveRecord(
  val plyIndex: Int,
  val fullMoveNumber: Int,
  val side: Side,
  val move: ChessMove,
  val fenBefore: String,
  val hashBefore: Long,
)
