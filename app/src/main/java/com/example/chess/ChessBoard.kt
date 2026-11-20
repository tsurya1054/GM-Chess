package com.example.chess

import kotlin.math.abs

class ChessBoard {
  val squares = IntArray(64)
  var sideToMove: Int = 1 // 1 = White, -1 = Black
  var castlingRights: Int = 15 // 1=WK, 2=WQ, 4=BK, 8=BQ
  var enPassantSq: Int = -1
  var halfMoveClock: Int = 0
  var fullMoveNumber: Int = 1
  var zobristKey: Long = 0L
  var whiteKingSq: Int = 4
  var blackKingSq: Int = 60

  // Pre-allocated undo stack for zero-GC search make/unmake
  private var stackPtr = 0
  private val histCastling = IntArray(MAX_PLY_STACK)
  private val histEp = IntArray(MAX_PLY_STACK)
  private val histHalfMove = IntArray(MAX_PLY_STACK)
  private val histHash = LongArray(MAX_PLY_STACK)

  init {
    resetToStartPos()
  }

  fun copy(): ChessBoard {
    val b = ChessBoard()
    System.arraycopy(squares, 0, b.squares, 0, 64)
    b.sideToMove = sideToMove
    b.castlingRights = castlingRights
    b.enPassantSq = enPassantSq
    b.halfMoveClock = halfMoveClock
    b.fullMoveNumber = fullMoveNumber
    b.zobristKey = zobristKey
    b.whiteKingSq = whiteKingSq
    b.blackKingSq = blackKingSq
    return b
  }

  fun resetToStartPos() {
    loadFen(START_FEN)
  }

  fun loadFen(fen: String) {
    squares.fill(Piece.EMPTY)
    stackPtr = 0
    val parts = fen.trim().split(Regex("\\s+"))
    val rows = parts[0].split("/")
    for (rIdx in 0 until 8) {
      val rank = 7 - rIdx
      var file = 0
      for (ch in rows[rIdx]) {
        if (ch.isDigit()) {
          file += ch.digitToInt()
        } else {
          val piece = charToPiece(ch)
          val sq = rank * 8 + file
          squares[sq] = piece
          if (piece == Piece.WK) whiteKingSq = sq
          if (piece == Piece.BK) blackKingSq = sq
          file++
        }
      }
    }
    sideToMove = if (parts.getOrNull(1) == "b") -1 else 1
    var cr = 0
    val cStr = parts.getOrNull(2) ?: "-"
    if ('K' in cStr) cr = cr or CASTLE_WK
    if ('Q' in cStr) cr = cr or CASTLE_WQ
    if ('k' in cStr) cr = cr or CASTLE_BK
    if ('q' in cStr) cr = cr or CASTLE_BQ
    castlingRights = cr

    val epStr = parts.getOrNull(3) ?: "-"
    enPassantSq = if (epStr.length == 2) {
      val f = epStr[0] - 'a'
      val r = epStr[1] - '1'
      if (f in 0..7 && r in 0..7) r * 8 + f else -1
    } else -1

    halfMoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
    fullMoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
    recomputeHash()
  }

  fun loadCustomPosition(
    newSquares: IntArray,
    newSideToMove: Int,
    newCastlingRights: Int = inferCastlingRights(newSquares),
  ) {
    System.arraycopy(newSquares, 0, squares, 0, 64)
    stackPtr = 0
    whiteKingSq = -1
    blackKingSq = -1
    for (sq in 0..63) {
      if (squares[sq] == Piece.WK) whiteKingSq = sq
      else if (squares[sq] == Piece.BK) blackKingSq = sq
    }
    sideToMove = newSideToMove
    castlingRights = newCastlingRights
    enPassantSq = -1
    halfMoveClock = 0
    fullMoveNumber = 1
    recomputeHash()
  }

  fun toFen(): String {
    val sb = StringBuilder(80)
    for (rank in 7 downTo 0) {
      var emptyCount = 0
      for (file in 0..7) {
        val p = squares[rank * 8 + file]
        if (p == Piece.EMPTY) {
          emptyCount++
        } else {
          if (emptyCount > 0) {
            sb.append(emptyCount)
            emptyCount = 0
          }
          sb.append(pieceToChar(p))
        }
      }
      if (emptyCount > 0) sb.append(emptyCount)
      if (rank > 0) sb.append('/')
    }
    sb.append(if (sideToMove == 1) " w " else " b ")
    if (castlingRights == 0) {
      sb.append('-')
    } else {
      if ((castlingRights and CASTLE_WK) != 0) sb.append('K')
      if ((castlingRights and CASTLE_WQ) != 0) sb.append('Q')
      if ((castlingRights and CASTLE_BK) != 0) sb.append('k')
      if ((castlingRights and CASTLE_BQ) != 0) sb.append('q')
    }
    sb.append(' ')
    sb.append(if (enPassantSq in 0..63) MoveCodec.squareName(enPassantSq) else "-")
    sb.append(' ').append(halfMoveClock).append(' ').append(fullMoveNumber)
    return sb.toString()
  }

  fun toOpeningKey(): String {
    val full = toFen()
    val parts = full.split(' ')
    return "${parts[0]} ${parts[1]} ${parts[2]}"
  }

  private fun recomputeHash() {
    var h = 0L
    for (sq in 0..63) {
      val p = squares[sq]
      if (p != Piece.EMPTY) h = h xor Zobrist.pieceKey(p, sq)
    }
    if (sideToMove == -1) h = h xor Zobrist.blackToMove
    h = h xor Zobrist.castling[castlingRights]
    if (enPassantSq in 0..63) h = h xor Zobrist.enPassantFile[enPassantSq and 7]
    zobristKey = h
  }

  fun isInCheck(side: Int = sideToMove): Boolean {
    val kSq = if (side == 1) whiteKingSq else blackKingSq
    return isSquareAttacked(kSq, -side)
  }

  /**
   * Returns true if [sq] is attacked by any piece of [bySide] (1 = White, -1 = Black).
   */
  fun isSquareAttacked(sq: Int, bySide: Int): Boolean {
    // 1. Pawn attacks
    if (bySide == 1) {
      val rank = sq ushr 3
      val file = sq and 7
      if (rank > 0) {
        if (file > 0 && squares[sq - 9] == Piece.WP) return true
        if (file < 7 && squares[sq - 7] == Piece.WP) return true
      }
    } else {
      val rank = sq ushr 3
      val file = sq and 7
      if (rank < 7) {
        if (file > 0 && squares[sq + 7] == Piece.BP) return true
        if (file < 7 && squares[sq + 9] == Piece.BP) return true
      }
    }

    // 2. Knight attacks
    val targetKnight = Piece.WN * bySide
    for (nSq in KNIGHT_MOVES[sq]) {
      if (squares[nSq] == targetKnight) return true
    }

    // 3. King attacks
    val targetKing = Piece.WK * bySide
    for (kSq in KING_MOVES[sq]) {
      if (squares[kSq] == targetKing) return true
    }

    // 4. Orthogonal rays (Rook / Queen)
    val targetRook = Piece.WR * bySide
    val targetQueen = Piece.WQ * bySide
    for (dir in 0..3) {
      val ray = RAYS[dir][sq]
      for (rSq in ray) {
        val p = squares[rSq]
        if (p != Piece.EMPTY) {
          if (p == targetRook || p == targetQueen) return true
          break
        }
      }
    }

    // 5. Diagonal rays (Bishop / Queen)
    val targetBishop = Piece.WB * bySide
    for (dir in 4..7) {
      val ray = RAYS[dir][sq]
      for (rSq in ray) {
        val p = squares[rSq]
        if (p != Piece.EMPTY) {
          if (p == targetBishop || p == targetQueen) return true
          break
        }
      }
    }

    return false
  }

  fun makeMove(move: Int) {
    val sp = stackPtr++
    histCastling[sp] = castlingRights
    histEp[sp] = enPassantSq
    histHalfMove[sp] = halfMoveClock
    histHash[sp] = zobristKey

    var hash = zobristKey
    if (enPassantSq in 0..63) {
      hash = hash xor Zobrist.enPassantFile[enPassantSq and 7]
    }
    hash = hash xor Zobrist.castling[castlingRights]

    val from = MoveCodec.from(move)
    val to = MoveCodec.to(move)
    val movedType = MoveCodec.movedType(move)
    val capturedType = MoveCodec.capturedType(move)
    val promoType = MoveCodec.promoType(move)
    val flag = MoveCodec.flag(move)
    val us = sideToMove
    val movingPiece = movedType * us

    // Remove piece from origin
    squares[from] = Piece.EMPTY
    hash = hash xor Zobrist.pieceKey(movingPiece, from)

    // Handle captures
    if (flag == MoveCodec.FLAG_EN_PASSANT) {
      val capSq = if (us == 1) to - 8 else to + 8
      val capPiece = -us // enemy pawn
      squares[capSq] = Piece.EMPTY
      hash = hash xor Zobrist.pieceKey(capPiece, capSq)
    } else if (capturedType != 0) {
      val capPiece = -capturedType * us
      hash = hash xor Zobrist.pieceKey(capPiece, to)
    }

    // Place moved or promoted piece on destination
    val placedPiece = if (promoType != 0) promoType * us else movingPiece
    squares[to] = placedPiece
    hash = hash xor Zobrist.pieceKey(placedPiece, to)

    // Update king square & castling rook transfers
    if (movedType == 6) {
      if (us == 1) whiteKingSq = to else blackKingSq = to
      if (flag == MoveCodec.FLAG_KING_CASTLE) {
        val rFrom = if (us == 1) 7 else 63
        val rTo = if (us == 1) 5 else 61
        val rook = Piece.WR * us
        squares[rFrom] = Piece.EMPTY
        squares[rTo] = rook
        hash = hash xor Zobrist.pieceKey(rook, rFrom) xor Zobrist.pieceKey(rook, rTo)
      } else if (flag == MoveCodec.FLAG_QUEEN_CASTLE) {
        val rFrom = if (us == 1) 0 else 56
        val rTo = if (us == 1) 3 else 59
        val rook = Piece.WR * us
        squares[rFrom] = Piece.EMPTY
        squares[rTo] = rook
        hash = hash xor Zobrist.pieceKey(rook, rFrom) xor Zobrist.pieceKey(rook, rTo)
      }
    }

    // Update castling rights & en passant
    castlingRights = castlingRights and CASTLE_MASK[from] and CASTLE_MASK[to]
    hash = hash xor Zobrist.castling[castlingRights]

    enPassantSq = if (flag == MoveCodec.FLAG_DOUBLE_PAWN) {
      if (us == 1) from + 8 else from - 8
    } else -1
    if (enPassantSq in 0..63) {
      hash = hash xor Zobrist.enPassantFile[enPassantSq and 7]
    }

    halfMoveClock = if (movedType == 1 || capturedType != 0) 0 else halfMoveClock + 1
    if (us == -1) fullMoveNumber++
    sideToMove = -us
    hash = hash xor Zobrist.blackToMove
    zobristKey = hash
  }

  fun unmakeMove(move: Int) {
    val sp = --stackPtr
    val us = -sideToMove // side that made the move
    sideToMove = us
    if (us == -1) fullMoveNumber--
    castlingRights = histCastling[sp]
    enPassantSq = histEp[sp]
    halfMoveClock = histHalfMove[sp]
    zobristKey = histHash[sp]

    val from = MoveCodec.from(move)
    val to = MoveCodec.to(move)
    val movedType = MoveCodec.movedType(move)
    val capturedType = MoveCodec.capturedType(move)
    val flag = MoveCodec.flag(move)

    squares[from] = movedType * us
    if (flag == MoveCodec.FLAG_EN_PASSANT) {
      squares[to] = Piece.EMPTY
      val capSq = if (us == 1) to - 8 else to + 8
      squares[capSq] = -us
    } else {
      squares[to] = if (capturedType != 0) -capturedType * us else Piece.EMPTY
    }

    if (movedType == 6) {
      if (us == 1) whiteKingSq = from else blackKingSq = from
      if (flag == MoveCodec.FLAG_KING_CASTLE) {
        val rFrom = if (us == 1) 7 else 63
        val rTo = if (us == 1) 5 else 61
        squares[rFrom] = Piece.WR * us
        squares[rTo] = Piece.EMPTY
      } else if (flag == MoveCodec.FLAG_QUEEN_CASTLE) {
        val rFrom = if (us == 1) 0 else 56
        val rTo = if (us == 1) 3 else 59
        squares[rFrom] = Piece.WR * us
        squares[rTo] = Piece.EMPTY
      }
    }
  }

  fun makeNullMove() {
    val sp = stackPtr++
    histCastling[sp] = castlingRights
    histEp[sp] = enPassantSq
    histHalfMove[sp] = halfMoveClock
    histHash[sp] = zobristKey

    var hash = zobristKey
    if (enPassantSq in 0..63) {
      hash = hash xor Zobrist.enPassantFile[enPassantSq and 7]
    }
    enPassantSq = -1
    halfMoveClock++
    sideToMove = -sideToMove
    hash = hash xor Zobrist.blackToMove
    zobristKey = hash
  }

  fun unmakeNullMove() {
    val sp = --stackPtr
    sideToMove = -sideToMove
    castlingRights = histCastling[sp]
    enPassantSq = histEp[sp]
    halfMoveClock = histHalfMove[sp]
    zobristKey = histHash[sp]
  }

  fun hasNonPawnMaterial(side: Int): Boolean {
    for (sq in 0..63) {
      val p = squares[sq]
      if (p * side > 1 && abs(p) < 6) return true
    }
    return false
  }

  fun isInsufficientMaterial(): Boolean {
    var whiteBishops = 0
    var blackBishops = 0
    var whiteKnights = 0
    var blackKnights = 0
    var whiteBishopSquareColor = -1
    var blackBishopSquareColor = -1

    for (sq in 0..63) {
      val p = squares[sq]
      if (p == Piece.EMPTY) continue
      when (p) {
        Piece.WP, Piece.BP, Piece.WR, Piece.BR, Piece.WQ, Piece.BQ -> return false
        Piece.WN -> whiteKnights++
        Piece.BN -> blackKnights++
        Piece.WB -> {
          whiteBishops++
          whiteBishopSquareColor = ((sq ushr 3) + (sq and 7)) and 1
        }
        Piece.BB -> {
          blackBishops++
          blackBishopSquareColor = ((sq ushr 3) + (sq and 7)) and 1
        }
      }
    }
    val totalMinor = whiteBishops + blackBishops + whiteKnights + blackKnights
    if (totalMinor <= 1) return true
    if (totalMinor == 2 && whiteBishops == 1 && blackBishops == 1 && whiteBishopSquareColor == blackBishopSquareColor) {
      return true
    }
    return false
  }

  companion object {
    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    const val CASTLE_WK = 1
    const val CASTLE_WQ = 2
    const val CASTLE_BK = 4
    const val CASTLE_BQ = 8
    private const val MAX_PLY_STACK = 256

    val CASTLE_MASK = IntArray(64) { 15 }.apply {
      this[0] = 15 xor CASTLE_WQ
      this[4] = 15 xor (CASTLE_WK or CASTLE_WQ)
      this[7] = 15 xor CASTLE_WK
      this[56] = 15 xor CASTLE_BQ
      this[60] = 15 xor (CASTLE_BK or CASTLE_BQ)
      this[63] = 15 xor CASTLE_BK
    }

    val KNIGHT_MOVES: Array<IntArray> = Array(64) { sq ->
      val r = sq ushr 3
      val f = sq and 7
      val dr = intArrayOf(-2, -2, -1, -1, 1, 1, 2, 2)
      val df = intArrayOf(-1, 1, -2, 2, -2, 2, -1, 1)
      val list = ArrayList<Int>(8)
      for (i in 0..7) {
        val nr = r + dr[i]
        val nf = f + df[i]
        if (nr in 0..7 && nf in 0..7) list.add(nr * 8 + nf)
      }
      list.toIntArray()
    }

    val KING_MOVES: Array<IntArray> = Array(64) { sq ->
      val r = sq ushr 3
      val f = sq and 7
      val list = ArrayList<Int>(8)
      for (dr in -1..1) {
        for (df in -1..1) {
          if (dr == 0 && df == 0) continue
          val nr = r + dr
          val nf = f + df
          if (nr in 0..7 && nf in 0..7) list.add(nr * 8 + nf)
        }
      }
      list.toIntArray()
    }

    // Directions: 0=N, 1=S, 2=E, 3=W (orthogonal), 4=NE, 5=NW, 6=SE, 7=SW (diagonal)
    val RAYS: Array<Array<IntArray>> = Array(8) { dir ->
      val dr = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)[dir]
      val df = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)[dir]
      Array(64) { sq ->
        var r = (sq ushr 3) + dr
        var f = (sq and 7) + df
        val list = ArrayList<Int>(7)
        while (r in 0..7 && f in 0..7) {
          list.add(r * 8 + f)
          r += dr
          f += df
        }
        list.toIntArray()
      }
    }

    fun inferCastlingRights(squares: IntArray): Int {
      var cr = 0
      if (squares[4] == Piece.WK) {
        if (squares[7] == Piece.WR) cr = cr or CASTLE_WK
        if (squares[0] == Piece.WR) cr = cr or CASTLE_WQ
      }
      if (squares[60] == Piece.BK) {
        if (squares[63] == Piece.BR) cr = cr or CASTLE_BK
        if (squares[56] == Piece.BR) cr = cr or CASTLE_BQ
      }
      return cr
    }

    /**
     * Validates a custom board setup according to FIDE rules.
     * Returns null when valid, or an Indonesian error explanation when invalid.
     */
    fun validateSetup(squares: IntArray, sideToMove: Side): String? {
      var wkCount = 0
      var bkCount = 0
      var wkSq = -1
      var bkSq = -1
      var whitePieces = 0
      var blackPieces = 0

      for (sq in 0..63) {
        val p = squares[sq]
        if (p == Piece.EMPTY) continue
        if (p > 0) whitePieces++ else blackPieces++

        if (p == Piece.WK) {
          wkCount++
          wkSq = sq
        } else if (p == Piece.BK) {
          bkCount++
          bkSq = sq
        } else if (abs(p) == 1) {
          val rank = sq ushr 3
          if (rank == 0 || rank == 7) {
            return "Bidak (pion) tidak boleh berada di baris 1 atau 8."
          }
        }
      }

      if (wkCount != 1 || bkCount != 1) {
        return "Harus ada tepat 1 Raja Putih dan 1 Raja Hitam."
      }
      if (whitePieces > 16 || blackPieces > 16) {
        return "Maksimal 16 bidak untuk masing-masing warna."
      }

      val dr = abs((wkSq ushr 3) - (bkSq ushr 3))
      val df = abs((wkSq and 7) - (bkSq and 7))
      if (dr <= 1 && df <= 1) {
        return "Raja Putih dan Raja Hitam tidak boleh bersebelahan."
      }

      val probe = ChessBoard()
      probe.loadCustomPosition(squares, sideToMove.sign, 0)
      if (probe.isInCheck(-sideToMove.sign)) {
        val opponentName = if (sideToMove == Side.WHITE) "Hitam" else "Putih"
        return "Raja $opponentName tidak boleh dalam posisi skak saat bukan gilirannya."
      }
      return null
    }

    private fun charToPiece(c: Char): Int = when (c) {
      'P' -> Piece.WP; 'N' -> Piece.WN; 'B' -> Piece.WB; 'R' -> Piece.WR; 'Q' -> Piece.WQ; 'K' -> Piece.WK
      'p' -> Piece.BP; 'n' -> Piece.BN; 'b' -> Piece.BB; 'r' -> Piece.BR; 'q' -> Piece.BQ; 'k' -> Piece.BK
      else -> Piece.EMPTY
    }

    private fun pieceToChar(p: Int): Char = when (p) {
      Piece.WP -> 'P'; Piece.WN -> 'N'; Piece.WB -> 'B'; Piece.WR -> 'R'; Piece.WQ -> 'Q'; Piece.WK -> 'K'
      Piece.BP -> 'p'; Piece.BN -> 'n'; Piece.BB -> 'b'; Piece.BR -> 'r'; Piece.BQ -> 'q'; Piece.BK -> 'k'
      else -> '.'
    }
  }
}
