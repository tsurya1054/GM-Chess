package com.example.chess

import kotlin.math.abs

object MoveGenerator {

  fun generateLegalMoves(
    board: ChessBoard,
    outMoves: IntArray,
    tacticalOnly: Boolean = false,
  ): Int {
    val pseudo = IntArray(256)
    val pseudoCount = generatePseudoLegal(board, pseudo, tacticalOnly)
    val moverSide = board.sideToMove
    var legalCount = 0

    for (i in 0 until pseudoCount) {
      val m = pseudo[i]
      board.makeMove(m)
      val kingSafe = !board.isInCheck(moverSide)
      board.unmakeMove(m)
      if (kingSafe) {
        outMoves[legalCount++] = m
      }
    }
    return legalCount
  }

  fun hasAnyLegalMove(board: ChessBoard): Boolean {
    val pseudo = IntArray(256)
    val pseudoCount = generatePseudoLegal(board, pseudo, tacticalOnly = false)
    val moverSide = board.sideToMove
    for (i in 0 until pseudoCount) {
      val m = pseudo[i]
      board.makeMove(m)
      val safe = !board.isInCheck(moverSide)
      board.unmakeMove(m)
      if (safe) return true
    }
    return false
  }

  private fun generatePseudoLegal(
    board: ChessBoard,
    out: IntArray,
    tacticalOnly: Boolean,
  ): Int {
    var count = 0
    val us = board.sideToMove
    val squares = board.squares

    for (sq in 0..63) {
      val p = squares[sq]
      if (p == Piece.EMPTY || p * us <= 0) continue

      when (abs(p)) {
        1 -> count = genPawnMoves(board, sq, us, out, count, tacticalOnly)
        2 -> {
          for (to in ChessBoard.KNIGHT_MOVES[sq]) {
            val target = squares[to]
            if (target == Piece.EMPTY) {
              if (!tacticalOnly) out[count++] = MoveCodec.encode(sq, to, 2)
            } else if (target * us < 0) {
              out[count++] = MoveCodec.encode(sq, to, 2, capturedType = abs(target))
            }
          }
        }
        3 -> count = genSlidingMoves(squares, sq, us, 3, 4, 7, out, count, tacticalOnly)
        4 -> count = genSlidingMoves(squares, sq, us, 4, 0, 3, out, count, tacticalOnly)
        5 -> count = genSlidingMoves(squares, sq, us, 5, 0, 7, out, count, tacticalOnly)
        6 -> {
          for (to in ChessBoard.KING_MOVES[sq]) {
            val target = squares[to]
            if (target == Piece.EMPTY) {
              if (!tacticalOnly) out[count++] = MoveCodec.encode(sq, to, 6)
            } else if (target * us < 0) {
              out[count++] = MoveCodec.encode(sq, to, 6, capturedType = abs(target))
            }
          }
          if (!tacticalOnly) {
            count = genCastlingMoves(board, sq, us, out, count)
          }
        }
      }
    }
    return count
  }

  private fun genPawnMoves(
    board: ChessBoard,
    sq: Int,
    us: Int,
    out: IntArray,
    startCount: Int,
    tacticalOnly: Boolean,
  ): Int {
    var count = startCount
    val squares = board.squares
    val rank = sq ushr 3
    val file = sq and 7
    val forward = if (us == 1) 8 else -8
    val promoRank = if (us == 1) 6 else 1 // moving FROM this rank promotes!
    val startRank = if (us == 1) 1 else 6

    // Single & Double Pushes
    val oneStep = sq + forward
    if (oneStep in 0..63 && squares[oneStep] == Piece.EMPTY) {
      if (rank == promoRank) {
        out[count++] = MoveCodec.encode(sq, oneStep, 1, promoType = 5)
        out[count++] = MoveCodec.encode(sq, oneStep, 1, promoType = 4)
        out[count++] = MoveCodec.encode(sq, oneStep, 1, promoType = 3)
        out[count++] = MoveCodec.encode(sq, oneStep, 1, promoType = 2)
      } else if (!tacticalOnly) {
        out[count++] = MoveCodec.encode(sq, oneStep, 1)
        if (rank == startRank) {
          val twoStep = sq + forward * 2
          if (squares[twoStep] == Piece.EMPTY) {
            out[count++] = MoveCodec.encode(sq, twoStep, 1, flag = MoveCodec.FLAG_DOUBLE_PAWN)
          }
        }
      }
    }

    // Diagonal Captures & En Passant
    val capOffsets = if (us == 1) intArrayOf(7, 9) else intArrayOf(-9, -7)
    for (idx in 0..1) {
      if (idx == 0 && file == 0) continue
      if (idx == 1 && file == 7) continue
      val to = sq + capOffsets[idx]
      if (to !in 0..63) continue
      val target = squares[to]
      if (target * us < 0) {
        val capType = abs(target)
        if (rank == promoRank) {
          out[count++] = MoveCodec.encode(sq, to, 1, capturedType = capType, promoType = 5)
          out[count++] = MoveCodec.encode(sq, to, 1, capturedType = capType, promoType = 4)
          out[count++] = MoveCodec.encode(sq, to, 1, capturedType = capType, promoType = 3)
          out[count++] = MoveCodec.encode(sq, to, 1, capturedType = capType, promoType = 2)
        } else {
          out[count++] = MoveCodec.encode(sq, to, 1, capturedType = capType)
        }
      } else if (to == board.enPassantSq) {
        out[count++] = MoveCodec.encode(
          from = sq,
          to = to,
          movedType = 1,
          capturedType = 1,
          flag = MoveCodec.FLAG_EN_PASSANT,
        )
      }
    }
    return count
  }

  private fun genSlidingMoves(
    squares: IntArray,
    sq: Int,
    us: Int,
    pieceType: Int,
    dirStart: Int,
    dirEnd: Int,
    out: IntArray,
    startCount: Int,
    tacticalOnly: Boolean,
  ): Int {
    var count = startCount
    for (dir in dirStart..dirEnd) {
      val ray = ChessBoard.RAYS[dir][sq]
      for (to in ray) {
        val target = squares[to]
        if (target == Piece.EMPTY) {
          if (!tacticalOnly) out[count++] = MoveCodec.encode(sq, to, pieceType)
        } else {
          if (target * us < 0) {
            out[count++] = MoveCodec.encode(sq, to, pieceType, capturedType = abs(target))
          }
          break
        }
      }
    }
    return count
  }

  private fun genCastlingMoves(
    board: ChessBoard,
    sq: Int,
    us: Int,
    out: IntArray,
    startCount: Int,
  ): Int {
    var count = startCount
    val cr = board.castlingRights
    val squares = board.squares
    val enemy = -us

    if (us == 1 && sq == 4) {
      if ((cr and ChessBoard.CASTLE_WK) != 0 &&
        squares[5] == Piece.EMPTY &&
        squares[6] == Piece.EMPTY &&
        squares[7] == Piece.WR &&
        !board.isSquareAttacked(4, enemy) &&
        !board.isSquareAttacked(5, enemy) &&
        !board.isSquareAttacked(6, enemy)
      ) {
        out[count++] = MoveCodec.encode(4, 6, 6, flag = MoveCodec.FLAG_KING_CASTLE)
      }
      if ((cr and ChessBoard.CASTLE_WQ) != 0 &&
        squares[3] == Piece.EMPTY &&
        squares[2] == Piece.EMPTY &&
        squares[1] == Piece.EMPTY &&
        squares[0] == Piece.WR &&
        !board.isSquareAttacked(4, enemy) &&
        !board.isSquareAttacked(3, enemy) &&
        !board.isSquareAttacked(2, enemy)
      ) {
        out[count++] = MoveCodec.encode(4, 2, 6, flag = MoveCodec.FLAG_QUEEN_CASTLE)
      }
    } else if (us == -1 && sq == 60) {
      if ((cr and ChessBoard.CASTLE_BK) != 0 &&
        squares[61] == Piece.EMPTY &&
        squares[62] == Piece.EMPTY &&
        squares[63] == Piece.BR &&
        !board.isSquareAttacked(60, enemy) &&
        !board.isSquareAttacked(61, enemy) &&
        !board.isSquareAttacked(62, enemy)
      ) {
        out[count++] = MoveCodec.encode(60, 62, 6, flag = MoveCodec.FLAG_KING_CASTLE)
      }
      if ((cr and ChessBoard.CASTLE_BQ) != 0 &&
        squares[59] == Piece.EMPTY &&
        squares[58] == Piece.EMPTY &&
        squares[57] == Piece.EMPTY &&
        squares[56] == Piece.BR &&
        !board.isSquareAttacked(60, enemy) &&
        !board.isSquareAttacked(59, enemy) &&
        !board.isSquareAttacked(58, enemy)
      ) {
        out[count++] = MoveCodec.encode(60, 58, 6, flag = MoveCodec.FLAG_QUEEN_CASTLE)
      }
    }
    return count
  }

  fun toChessMove(board: ChessBoard, rawMove: Int, legalMovesBuffer: IntArray? = null, legalCount: Int = -1): ChessMove {
    val san = toSan(board, rawMove, legalMovesBuffer, legalCount)
    val from = MoveCodec.from(rawMove)
    val to = MoveCodec.to(rawMove)
    val movedType = PieceType.fromCode(MoveCodec.movedType(rawMove)) ?: PieceType.PAWN
    val promoType = PieceType.fromCode(MoveCodec.promoType(rawMove))
    val flag = MoveCodec.flag(rawMove)
    val isCap = MoveCodec.isCapture(rawMove)

    val readable = when (flag) {
      MoveCodec.FLAG_KING_CASTLE -> "Rokade Pendek (O-O)"
      MoveCodec.FLAG_QUEEN_CASTLE -> "Rokade Panjang (O-O-O)"
      else -> {
        val action = if (isCap) "×" else "→"
        val promoSuffix = if (promoType != null) " = ${promoType.indoName}" else ""
        "${movedType.indoName} ${MoveCodec.squareName(from)} $action ${MoveCodec.squareName(to)}$promoSuffix"
      }
    }

    return ChessMove(
      raw = rawMove,
      from = from,
      to = to,
      san = san,
      readableIndo = readable,
      isCapture = isCap,
      isPromotion = promoType != null,
      promotionType = promoType,
    )
  }

  fun toSan(
    board: ChessBoard,
    move: Int,
    existingLegal: IntArray? = null,
    existingCount: Int = -1,
  ): String {
    val flag = MoveCodec.flag(move)
    val baseSan = if (flag == MoveCodec.FLAG_KING_CASTLE) {
      "O-O"
    } else if (flag == MoveCodec.FLAG_QUEEN_CASTLE) {
      "O-O-O"
    } else {
      val from = MoveCodec.from(move)
      val to = MoveCodec.to(move)
      val movedType = MoveCodec.movedType(move)
      val isCap = MoveCodec.isCapture(move)
      val promo = MoveCodec.promoType(move)

      val sb = StringBuilder()
      if (movedType == 1) {
        if (isCap) sb.append('a' + (from and 7))
      } else {
        sb.append(PieceType.fromCode(movedType)?.sanChar ?: "")
        val legals = existingLegal ?: IntArray(256)
        val count = if (existingCount >= 0 && existingLegal != null) {
          existingCount
        } else {
          generateLegalMoves(board, legals)
        }
        var sameDest = false
        var sameFile = false
        var sameRank = false
        for (i in 0 until count) {
          val m2 = legals[i]
          val f2 = MoveCodec.from(m2)
          if (f2 != from && MoveCodec.to(m2) == to && MoveCodec.movedType(m2) == movedType) {
            sameDest = true
            if ((f2 and 7) == (from and 7)) sameFile = true
            if ((f2 ushr 3) == (from ushr 3)) sameRank = true
          }
        }
        if (sameDest) {
          if (!sameFile) {
            sb.append('a' + (from and 7))
          } else if (!sameRank) {
            sb.append('1' + (from ushr 3))
          } else {
            sb.append(MoveCodec.squareName(from))
          }
        }
      }
      if (isCap) sb.append('x')
      sb.append(MoveCodec.squareName(to))
      if (promo != 0) {
        sb.append('=').append(PieceType.fromCode(promo)?.sanChar ?: "Q")
      }
      sb.toString()
    }

    board.makeMove(move)
    val inCheck = board.isInCheck(board.sideToMove)
    val suffix = if (inCheck) {
      if (!hasAnyLegalMove(board)) "#" else "+"
    } else ""
    board.unmakeMove(move)
    return baseSan + suffix
  }
}
