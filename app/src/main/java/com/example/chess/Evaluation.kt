package com.example.chess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Grandmaster-grade Tapered PeSTO + Positional + Pawn Structure + King Safety + Endgame Mop-Up Evaluator.
 * Returns score in centipawns from [board.sideToMove]'s perspective.
 */
object Evaluation {

  private val MG_VALUE = intArrayOf(0, 82, 337, 365, 477, 1025, 0)
  private val EG_VALUE = intArrayOf(0, 94, 281, 297, 512, 936, 0)
  private val PHASE_INC = intArrayOf(0, 0, 1, 1, 2, 4, 0)

  private val PASSED_PAWN_MG = intArrayOf(0, 10, 20, 36, 68, 125, 205, 0)
  private val PASSED_PAWN_EG = intArrayOf(0, 18, 38, 72, 130, 220, 340, 0)

  private val CENTER_MANHATTAN = IntArray(64) { sq ->
    val r = sq ushr 3
    val f = sq and 7
    val dr = max(3 - r, r - 4).coerceAtLeast(0)
    val df = max(3 - f, f - 4).coerceAtLeast(0)
    dr + df
  }

  private val KNIGHT_ATTACK_BB = LongArray(64) { sq ->
    ChessBoard.KNIGHT_MOVES[sq].fold(0L) { mask, to -> mask or (1L shl to) }
  }

  private val KING_ZONE_BB = LongArray(64) { sq ->
    var mask = 1L shl sq
    for (adj in ChessBoard.KING_MOVES[sq]) {
      mask = mask or (1L shl adj)
    }
    mask
  }

  fun evaluate(board: ChessBoard): Int {
    val squares = board.squares
    var mgWhite = 0
    var egWhite = 0
    var mgBlack = 0
    var egBlack = 0
    var gamePhase = 0

    var whiteBishops = 0
    var blackBishops = 0

    val wpCount = IntArray(8)
    val bpCount = IntArray(8)
    val wpMaxRank = IntArray(8) { -1 }
    val bpMinRank = IntArray(8) { 8 }

    // Pass 1: Material, PeSTO Piece-Square Tables, and Pawn File Maps
    for (sq in 0..63) {
      val p = squares[sq]
      if (p == Piece.EMPTY) continue
      val pt = abs(p)
      gamePhase += PHASE_INC[pt]

      if (p > 0) {
        val idx = sq xor 56
        mgWhite += MG_VALUE[pt] + MG_PST[pt][idx]
        egWhite += EG_VALUE[pt] + EG_PST[pt][idx]
        if (pt == 3) whiteBishops++
        if (pt == 1) {
          val f = sq and 7
          val r = sq ushr 3
          wpCount[f]++
          if (r > wpMaxRank[f]) wpMaxRank[f] = r
        }
      } else {
        val idx = sq
        mgBlack += MG_VALUE[pt] + MG_PST[pt][idx]
        egBlack += EG_VALUE[pt] + EG_PST[pt][idx]
        if (pt == 3) blackBishops++
        if (pt == 1) {
          val f = sq and 7
          val r = sq ushr 3
          bpCount[f]++
          if (r < bpMinRank[f]) bpMinRank[f] = r
        }
      }
    }

    // Bishop Pair Bonus
    if (whiteBishops >= 2) {
      mgWhite += 34
      egWhite += 54
    }
    if (blackBishops >= 2) {
      mgBlack += 34
      egBlack += 54
    }

    // Doubled & Isolated Pawns per file
    for (f in 0..7) {
      if (wpCount[f] > 1) {
        val extra = wpCount[f] - 1
        mgWhite -= 13 * extra
        egWhite -= 20 * extra
      }
      if (wpCount[f] > 0) {
        val left = if (f > 0) wpCount[f - 1] else 0
        val right = if (f < 7) wpCount[f + 1] else 0
        if (left == 0 && right == 0) {
          mgWhite -= 14 * wpCount[f]
          egWhite -= 18 * wpCount[f]
        }
      }

      if (bpCount[f] > 1) {
        val extra = bpCount[f] - 1
        mgBlack -= 13 * extra
        egBlack -= 20 * extra
      }
      if (bpCount[f] > 0) {
        val left = if (f > 0) bpCount[f - 1] else 0
        val right = if (f < 7) bpCount[f + 1] else 0
        if (left == 0 && right == 0) {
          mgBlack -= 14 * bpCount[f]
          egBlack -= 18 * bpCount[f]
        }
      }
    }

    // Target King Zones for coordinated attacks
    val bk = board.blackKingSq
    var bKingZone = KING_ZONE_BB[bk]
    if ((bk ushr 3) >= 6) {
      if (bk >= 8) bKingZone = bKingZone or (1L shl (bk - 8))
      if (bk >= 9 && (bk and 7) > 0) bKingZone = bKingZone or (1L shl (bk - 9))
      if (bk >= 7 && (bk and 7) < 7) bKingZone = bKingZone or (1L shl (bk - 7))
    }

    val wk = board.whiteKingSq
    var wKingZone = KING_ZONE_BB[wk]
    if ((wk ushr 3) <= 1) {
      if (wk <= 55) wKingZone = wKingZone or (1L shl (wk + 8))
      if (wk <= 54 && (wk and 7) < 7) wKingZone = wKingZone or (1L shl (wk + 9))
      if (wk <= 56 && (wk and 7) > 0) wKingZone = wKingZone or (1L shl (wk + 7))
    }

    var whiteAttackersCount = 0
    var whiteAttackUnits = 0
    var blackAttackersCount = 0
    var blackAttackUnits = 0

    // Pass 2: Piece Safety, Pawn Anchors, Attacks, Rooks on Open/7th, Outposts & Mobility
    for (sq in 0..63) {
      val p = squares[sq]
      if (p == Piece.EMPTY) continue
      val f = sq and 7
      val r = sq ushr 3

      when (p) {
        Piece.WP -> {
          if (isWhitePassedPawn(squares, f, r)) {
            mgWhite += PASSED_PAWN_MG[r]
            egWhite += PASSED_PAWN_EG[r]
            if (sq + 8 < 64 && squares[sq + 8] == Piece.EMPTY) {
              egWhite += PASSED_PAWN_EG[r] / 5
            }
          }
          // Protected pawn chain
          if (r > 1 && ((f > 0 && squares[sq - 9] == Piece.WP) || (f < 7 && squares[sq - 7] == Piece.WP))) {
            mgWhite += 8
            egWhite += 6
          }
        }
        Piece.BP -> {
          if (isBlackPassedPawn(squares, f, r)) {
            val relRank = 7 - r
            mgBlack += PASSED_PAWN_MG[relRank]
            egBlack += PASSED_PAWN_EG[relRank]
            if (sq - 8 >= 0 && squares[sq - 8] == Piece.EMPTY) {
              egBlack += PASSED_PAWN_EG[relRank] / 5
            }
          }
          if (r < 6 && ((f > 0 && squares[sq + 7] == Piece.BP) || (f < 7 && squares[sq + 9] == Piece.BP))) {
            mgBlack += 8
            egBlack += 6
          }
        }
        Piece.WN -> {
          // Pawn protection & Outpost
          val defByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (defByPawn) {
            mgWhite += 16
            egWhite += 14
            if (r in 3..5 && f in 1..6) mgWhite += 14
          }
          // Attacked by enemy pawn
          val attackedByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (attackedByPawn) {
            mgWhite -= 28
            egWhite -= 20
          }
          // Hanging piece check
          if (board.isSquareAttacked(sq, bySide = -1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = 1)) {
              mgWhite -= 45
              egWhite -= 45
            }
          } else if (!defByPawn && r in 2..5 && !board.isSquareAttacked(sq, bySide = 1)) {
            mgWhite -= 8 // Loose piece penalty
          }
          // Coordinated King Attack
          if ((KNIGHT_ATTACK_BB[sq] and bKingZone) != 0L) {
            whiteAttackersCount++
            whiteAttackUnits += 2
          }
          var mob = 0
          for (to in ChessBoard.KNIGHT_MOVES[sq]) {
            if (squares[to] <= 0) mob++
          }
          mgWhite += (mob - 4) * 4
          egWhite += (mob - 4) * 3
        }
        Piece.BN -> {
          val defByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (defByPawn) {
            mgBlack += 16
            egBlack += 14
            if (r in 2..4 && f in 1..6) mgBlack += 14
          }
          val attackedByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (attackedByPawn) {
            mgBlack -= 28
            egBlack -= 20
          }
          if (board.isSquareAttacked(sq, bySide = 1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = -1)) {
              mgBlack -= 45
              egBlack -= 45
            }
          } else if (!defByPawn && r in 2..5 && !board.isSquareAttacked(sq, bySide = -1)) {
            mgBlack -= 8
          }
          if ((KNIGHT_ATTACK_BB[sq] and wKingZone) != 0L) {
            blackAttackersCount++
            blackAttackUnits += 2
          }
          var mob = 0
          for (to in ChessBoard.KNIGHT_MOVES[sq]) {
            if (squares[to] >= 0) mob++
          }
          mgBlack += (mob - 4) * 4
          egBlack += (mob - 4) * 3
        }
        Piece.WB -> {
          val defByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (defByPawn) {
            mgWhite += 16
            egWhite += 14
          }
          val attackedByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (attackedByPawn) {
            mgWhite -= 28
            egWhite -= 20
          }
          if (board.isSquareAttacked(sq, bySide = -1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = 1)) {
              mgWhite -= 45
              egWhite -= 45
            }
          } else if (!defByPawn && r in 2..5 && !board.isSquareAttacked(sq, bySide = 1)) {
            mgWhite -= 8
          }
          var mob = 0
          var attacksKingZone = false
          for (dir in 4..7) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and bKingZone != 0L) attacksKingZone = true
              val t = squares[to]
              if (t == Piece.EMPTY) mob++ else {
                if (t < 0) mob++
                break
              }
            }
          }
          if (attacksKingZone) {
            whiteAttackersCount++
            whiteAttackUnits += 2
          }
          mgWhite += (mob - 6) * 4
          egWhite += (mob - 6) * 4
        }
        Piece.BB -> {
          val defByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (defByPawn) {
            mgBlack += 16
            egBlack += 14
          }
          val attackedByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (attackedByPawn) {
            mgBlack -= 28
            egBlack -= 20
          }
          if (board.isSquareAttacked(sq, bySide = 1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = -1)) {
              mgBlack -= 45
              egBlack -= 45
            }
          } else if (!defByPawn && r in 2..5 && !board.isSquareAttacked(sq, bySide = -1)) {
            mgBlack -= 8
          }
          var mob = 0
          var attacksKingZone = false
          for (dir in 4..7) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and wKingZone != 0L) attacksKingZone = true
              val t = squares[to]
              if (t == Piece.EMPTY) mob++ else {
                if (t > 0) mob++
                break
              }
            }
          }
          if (attacksKingZone) {
            blackAttackersCount++
            blackAttackUnits += 2
          }
          mgBlack += (mob - 6) * 4
          egBlack += (mob - 6) * 4
        }
        Piece.WR -> {
          val defByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (defByPawn) {
            mgWhite += 12
            egWhite += 10
          }
          val attackedByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (attackedByPawn) {
            mgWhite -= 45
            egWhite -= 35
          }
          if (board.isSquareAttacked(sq, bySide = -1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = 1)) {
              mgWhite -= 70
              egWhite -= 70
            }
          }
          if (wpCount[f] == 0) {
            if (bpCount[f] == 0) {
              mgWhite += 24
              egWhite += 15
            } else {
              mgWhite += 12
              egWhite += 9
            }
          }
          if (r == 6 && (bpCount.sum() > 0 || (board.blackKingSq ushr 3) == 7)) {
            mgWhite += 24
            egWhite += 30
          }
          var attacksKingZone = false
          for (dir in 0..3) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and bKingZone != 0L) attacksKingZone = true
              if (squares[to] != Piece.EMPTY) break
            }
          }
          if (attacksKingZone) {
            whiteAttackersCount++
            whiteAttackUnits += 3
          }
        }
        Piece.BR -> {
          val defByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (defByPawn) {
            mgBlack += 12
            egBlack += 10
          }
          val attackedByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (attackedByPawn) {
            mgBlack -= 45
            egBlack -= 35
          }
          if (board.isSquareAttacked(sq, bySide = 1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = -1)) {
              mgBlack -= 70
              egBlack -= 70
            }
          }
          if (bpCount[f] == 0) {
            if (wpCount[f] == 0) {
              mgBlack += 24
              egBlack += 15
            } else {
              mgBlack += 12
              egBlack += 9
            }
          }
          if (r == 1 && (wpCount.sum() > 0 || (board.whiteKingSq ushr 3) == 0)) {
            mgBlack += 24
            egBlack += 30
          }
          var attacksKingZone = false
          for (dir in 0..3) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and wKingZone != 0L) attacksKingZone = true
              if (squares[to] != Piece.EMPTY) break
            }
          }
          if (attacksKingZone) {
            blackAttackersCount++
            blackAttackUnits += 3
          }
        }
        Piece.WQ -> {
          val defByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (defByPawn) {
            mgWhite += 10
            egWhite += 8
          }
          val attackedByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (attackedByPawn) {
            mgWhite -= 65
            egWhite -= 50
          }
          if (board.isSquareAttacked(sq, bySide = -1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = 1)) {
              mgWhite -= 95
              egWhite -= 95
            }
          }
          var attacksKingZone = false
          for (dir in 0..7) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and bKingZone != 0L) attacksKingZone = true
              if (squares[to] != Piece.EMPTY) break
            }
          }
          if (attacksKingZone) {
            whiteAttackersCount++
            whiteAttackUnits += 4
          }
        }
        Piece.BQ -> {
          val defByPawn = (r < 7 && f > 0 && squares[sq + 7] == Piece.BP) || (r < 7 && f < 7 && squares[sq + 9] == Piece.BP)
          if (defByPawn) {
            mgBlack += 10
            egBlack += 8
          }
          val attackedByPawn = (r > 0 && f > 0 && squares[sq - 9] == Piece.WP) || (r > 0 && f < 7 && squares[sq - 7] == Piece.WP)
          if (attackedByPawn) {
            mgBlack -= 65
            egBlack -= 50
          }
          if (board.isSquareAttacked(sq, bySide = 1)) {
            if (!defByPawn && !board.isSquareAttacked(sq, bySide = -1)) {
              mgBlack -= 95
              egBlack -= 95
            }
          }
          var attacksKingZone = false
          for (dir in 0..7) {
            for (to in ChessBoard.RAYS[dir][sq]) {
              if ((1L shl to) and wKingZone != 0L) attacksKingZone = true
              if (squares[to] != Piece.EMPTY) break
            }
          }
          if (attacksKingZone) {
            blackAttackersCount++
            blackAttackUnits += 4
          }
        }
      }
    }

    // Coordinated King Attack Bonus (only awarded when 2+ pieces attack together)
    if (gamePhase >= 8) {
      if (whiteAttackersCount >= 2) {
        val bonus = min(130, whiteAttackUnits * whiteAttackUnits * 2)
        mgWhite += bonus
      }
      if (blackAttackersCount >= 2) {
        val bonus = min(130, blackAttackUnits * blackAttackUnits * 2)
        mgBlack += bonus
      }
    }

    // King Pawn Shield in Middlegame
    if (gamePhase >= 10) {
      mgWhite += evalWhiteKingShield(squares, board.whiteKingSq, wpCount)
      mgBlack += evalBlackKingShield(squares, board.blackKingSq, bpCount)
    }

    val mgPhase = min(gamePhase, 24)
    val egPhase = 24 - mgPhase
    val mgScore = mgWhite - mgBlack
    val egScore = egWhite - egBlack
    var whiteEval = (mgScore * mgPhase + egScore * egPhase) / 24

    // Endgame Mop-Up: Drive enemy lone king to corner for decisive checkmate
    if (egPhase >= 16) {
      val wk = board.whiteKingSq
      val bk = board.blackKingSq
      val kingDist = abs((wk ushr 3) - (bk ushr 3)) + abs((wk and 7) - (bk and 7))
      if (whiteEval >= 280) {
        whiteEval += CENTER_MANHATTAN[bk] * 14 + (14 - kingDist) * 6
      } else if (whiteEval <= -280) {
        whiteEval -= CENTER_MANHATTAN[wk] * 14 + (14 - kingDist) * 6
      }
    }

    // Tempo bonus
    whiteEval += if (board.sideToMove == 1) 12 else -12

    return if (board.sideToMove == 1) whiteEval else -whiteEval
  }

  private fun isWhitePassedPawn(squares: IntArray, file: Int, rank: Int): Boolean {
    val fMin = max(0, file - 1)
    val fMax = min(7, file + 1)
    for (r in (rank + 1)..6) {
      for (f in fMin..fMax) {
        if (squares[r * 8 + f] == Piece.BP) return false
      }
    }
    return true
  }

  private fun isBlackPassedPawn(squares: IntArray, file: Int, rank: Int): Boolean {
    val fMin = max(0, file - 1)
    val fMax = min(7, file + 1)
    for (r in 1 until rank) {
      for (f in fMin..fMax) {
        if (squares[r * 8 + f] == Piece.WP) return false
      }
    }
    return true
  }

  private fun evalWhiteKingShield(squares: IntArray, kSq: Int, wpCount: IntArray): Int {
    val r = kSq ushr 3
    val f = kSq and 7
    if (r > 1) return -22
    var bonus = 0
    if (f >= 5 || f <= 2) {
      val fStart = max(0, f - 1)
      val fEnd = min(7, f + 1)
      for (sf in fStart..fEnd) {
        if (squares[8 + sf] == Piece.WP) bonus += 12
        else if (squares[16 + sf] == Piece.WP) bonus += 6
        else if (wpCount[sf] == 0) bonus -= 16
      }
    }
    return bonus
  }

  private fun evalBlackKingShield(squares: IntArray, kSq: Int, bpCount: IntArray): Int {
    val r = kSq ushr 3
    val f = kSq and 7
    if (r < 6) return -22
    var bonus = 0
    if (f >= 5 || f <= 2) {
      val fStart = max(0, f - 1)
      val fEnd = min(7, f + 1)
      for (sf in fStart..fEnd) {
        if (squares[48 + sf] == Piece.BP) bonus += 12
        else if (squares[40 + sf] == Piece.BP) bonus += 6
        else if (bpCount[sf] == 0) bonus -= 16
      }
    }
    return bonus
  }

  // PeSTO Middlegame & Endgame Piece-Square Tables (indexed a8=0 .. h1=63)
  private val MG_PST = arrayOf(
    IntArray(64),
    // 1: PAWN
    intArrayOf(
      0, 0, 0, 0, 0, 0, 0, 0,
      98, 134, 61, 95, 68, 126, 34, -11,
      -6, 7, 26, 31, 65, 56, 25, -20,
      -14, 13, 6, 21, 23, 12, 17, -23,
      -27, -2, -5, 12, 17, 6, 10, -25,
      -26, -4, -4, -10, 3, 3, 33, -12,
      -35, -1, -20, -23, -15, 24, 38, -22,
      0, 0, 0, 0, 0, 0, 0, 0,
    ),
    // 2: KNIGHT
    intArrayOf(
      -167, -89, -34, -49, 61, -97, -15, -107,
      -73, -41, 72, 36, 23, 62, 7, -17,
      -47, 60, 37, 65, 84, 129, 73, 44,
      -9, 17, 19, 53, 37, 69, 18, 22,
      -13, 4, 16, 13, 28, 19, 21, -8,
      -23, -9, 12, 10, 19, 17, 25, -16,
      -29, -53, -12, -3, -1, 18, -14, -19,
      -105, -21, -58, -33, -17, -28, -19, -23,
    ),
    // 3: BISHOP
    intArrayOf(
      -29, 4, -82, -37, -25, -42, 7, -8,
      -26, 16, -18, -13, 30, 59, 18, -47,
      -16, 37, 43, 40, 35, 50, 37, -2,
      -4, 5, 19, 50, 37, 37, 7, -2,
      -6, 13, 13, 26, 34, 12, 10, 4,
      0, 15, 15, 15, 14, 27, 18, 10,
      4, 15, 16, 0, 7, 21, 33, 1,
      -33, -3, -14, -21, -13, -12, -39, -21,
    ),
    // 4: ROOK
    intArrayOf(
      32, 42, 32, 51, 63, 9, 31, 43,
      27, 32, 58, 62, 80, 67, 26, 44,
      -5, 19, 26, 36, 17, 45, 61, 16,
      -24, -11, 7, 26, 24, 35, -8, -20,
      -36, -26, -12, -1, 9, -7, 6, -23,
      -45, -25, -16, -17, 3, 0, -5, -33,
      -44, -16, -20, -9, -1, 11, -6, -71,
      -19, -13, 1, 17, 16, 7, -37, -26,
    ),
    // 5: QUEEN
    intArrayOf(
      -28, 0, 29, 12, 59, 44, 43, 45,
      -24, -39, -5, 1, -16, 57, 28, 54,
      -13, -17, 7, 8, 29, 56, 47, 57,
      -27, -27, -16, -16, -1, 17, -2, 1,
      -9, -26, -9, -10, -2, -4, 3, -3,
      -14, 2, -11, -2, -5, 2, 14, 5,
      -35, -8, 11, 2, 8, 15, -3, 1,
      -1, -18, -9, 10, -15, -25, -31, -50,
    ),
    // 6: KING
    intArrayOf(
      -65, 23, 16, -15, -56, -34, 2, 13,
      29, -1, -20, -7, -8, -4, -38, -29,
      -9, 24, 2, -16, -20, 6, 22, -22,
      -17, -20, -12, -27, -30, -25, -14, -36,
      -49, -1, -27, -39, -46, -44, -33, -51,
      -14, -14, -22, -46, -44, -30, -15, -27,
      1, 7, -8, -64, -43, -16, 9, 8,
      -15, 36, 12, -54, 8, -28, 24, 14,
    ),
  )

  private val EG_PST = arrayOf(
    IntArray(64),
    // 1: PAWN
    intArrayOf(
      0, 0, 0, 0, 0, 0, 0, 0,
      178, 173, 158, 134, 147, 132, 165, 187,
      94, 100, 85, 67, 56, 53, 82, 84,
      32, 24, 13, 5, -2, 4, 17, 17,
      13, 9, -3, -7, -7, -8, 3, -1,
      4, 7, -6, 1, 0, -5, -1, -8,
      13, 8, 8, 10, 13, 0, 2, -7,
      0, 0, 0, 0, 0, 0, 0, 0,
    ),
    // 2: KNIGHT
    intArrayOf(
      -58, -38, -13, -28, -31, -27, -63, -99,
      -25, -8, -25, -2, -9, -25, -24, -52,
      -24, -20, 10, 9, -1, -9, -19, -41,
      -17, 3, 22, 22, 22, 11, 8, -18,
      -18, -6, 16, 25, 16, 17, 4, -18,
      -23, -3, -1, 15, 10, -3, -20, -22,
      -42, -20, -10, -5, -2, -20, -23, -44,
      -29, -51, -23, -15, -22, -18, -50, -64,
    ),
    // 3: BISHOP
    intArrayOf(
      -14, -21, -11, -8, -7, -9, -17, -24,
      -8, -4, 7, -12, -3, -13, -4, -14,
      2, -8, 0, -1, -2, 6, 0, 4,
      -3, 9, 12, 9, 14, 10, 3, 2,
      -6, 3, 13, 19, 7, 10, -3, -9,
      -12, -3, 8, 10, 13, 3, -7, -15,
      -14, -18, -7, -1, 4, -9, -15, -27,
      -23, -9, -23, -5, -9, -16, -5, -17,
    ),
    // 4: ROOK
    intArrayOf(
      13, 10, 18, 15, 12, 12, 8, 5,
      11, 13, 13, 11, -3, 3, 8, 3,
      7, 7, 7, 5, 4, -3, -5, -3,
      4, 3, 13, 1, 2, 1, -1, 2,
      3, 5, 8, 4, -5, -6, -8, -11,
      -4, 0, -5, -1, -7, -12, -8, -16,
      -6, -6, 0, 2, -9, -9, -11, -3,
      -9, 2, 3, -1, -5, -13, 4, -20,
    ),
    // 5: QUEEN
    intArrayOf(
      -9, 22, 22, 27, 27, 19, 10, 20,
      -17, 20, 32, 41, 58, 25, 30, 0,
      -20, 6, 9, 49, 47, 35, 19, 9,
      3, 22, 24, 45, 57, 40, 57, 36,
      -18, 28, 19, 47, 31, 34, 39, 23,
      -16, -27, 15, 6, 9, 17, 10, 5,
      -22, -23, -30, -16, -16, -23, -36, -32,
      -33, -28, -22, -43, -5, -32, -20, -41,
    ),
    // 6: KING
    intArrayOf(
      -74, -35, -18, -18, -11, 15, 4, -17,
      -12, 17, 14, 17, 17, 38, 23, 11,
      10, 17, 23, 15, 20, 45, 44, 13,
      -8, 22, 24, 27, 26, 33, 26, 3,
      -18, -4, 21, 24, 27, 23, 9, -11,
      -19, -3, 11, 21, 23, 16, 7, -9,
      -27, -11, 4, 13, 14, 4, -5, -17,
      -53, -34, -21, -11, -28, -14, -24, -43,
    ),
  )
}
