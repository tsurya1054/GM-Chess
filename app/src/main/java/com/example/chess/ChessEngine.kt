package com.example.chess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed offline Grandmaster Chess Engine using:
 * - Iterative Deepening Principal Variation Search (PVS / Negamax Alpha-Beta)
 * - Lock-free Zobrist Transposition Table (262,144 entries, zero GC allocations)
 * - Quiescence Search with Check Evasion & X-Ray Static Exchange Evaluation (SEE)
 * - Null Move Pruning, Check Extensions, Late Move Reductions (LMR)
 * - Killer Moves, History Heuristic, Anti-Repetition Contempt, and Opening Book integration
 */
class ChessEngine {

  companion object {
    const val INF = 30000
    const val MATE_SCORE = 29000
    const val MATE_THRESHOLD = 28000
    private const val MAX_PLY = 64

    private const val TT_BITS = 18
    private const val TT_SIZE = 1 shl TT_BITS
    private const val TT_MASK = TT_SIZE - 1

    private const val TT_EXACT = 1
    private const val TT_LOWER = 2
    private const val TT_UPPER = 3

    private val SEE_VAL = intArrayOf(0, 100, 320, 335, 500, 950, 20000)
  }

  private val ttKey = LongArray(TT_SIZE)
  private val ttMove = IntArray(TT_SIZE)
  private val ttScore = IntArray(TT_SIZE)
  private val ttDepth = IntArray(TT_SIZE)
  private val ttFlag = IntArray(TT_SIZE)

  private val killers = Array(MAX_PLY) { IntArray(2) }
  private val history = Array(2) { Array(64) { IntArray(64) } }
  private val plyMoves = Array(MAX_PLY + 32) { IntArray(256) }
  private val plyOrderScores = Array(MAX_PLY + 32) { IntArray(256) }
  private val searchPathHashes = LongArray(512)

  private var gameHistoryCount = 0
  private var nodes = 0L
  private var deadlineNs = 0L
  private var aborted = false
  private var rootSide = 1
  private var currentRootDepth = 1
  private val minRequiredDepth = 5

  @Volatile
  var cancelRequested: Boolean = false

  fun clearCache() {
    ttKey.fill(0L)
    ttFlag.fill(0)
  }

  fun analyze(
    sourceBoard: ChessBoard,
    gameHashes: LongArray,
    lastOwnMoveFrom: Int = -1,
    lastOwnMoveTo: Int = -1,
    timeBudgetMs: Long = 450L,
    useOpeningBook: Boolean = true,
  ): AnalysisResult {
    cancelRequested = false
    val startNs = System.nanoTime()
    val board = sourceBoard.copy()
    val sideEnum = Side.fromSign(board.sideToMove)
    rootSide = board.sideToMove

    val rootMoves = IntArray(256)
    val rootCount = MoveGenerator.generateLegalMoves(board, rootMoves)
    if (rootCount == 0) {
      val inCheck = board.isInCheck()
      val evalWhite = if (inCheck) -MATE_SCORE * board.sideToMove else 0
      val mateWhite = if (inCheck) 0 else null
      return AnalysisResult(
        analyzedForSide = sideEnum,
        bestMove = null,
        candidates = emptyList(),
        evalCentipawnsWhite = evalWhite,
        mateInMovesWhite = mateWhite,
        depthReached = 0,
        nodesSearched = 0L,
        elapsedMs = 1L,
        openingName = null,
        tacticalSummary = if (inCheck) "Skakmat di atas papan." else "Posisi remis (Stalemate).",
      )
    }

    val bookEntry = if (useOpeningBook && board.fullMoveNumber <= 10) {
      OpeningBook.lookup(board)
    } else {
      null
    }

    // Load game hash history for strict repetition prevention
    gameHistoryCount = min(gameHashes.size, 250)
    for (i in 0 until gameHistoryCount) {
      searchPathHashes[i] = gameHashes[i]
    }

    // Reset per-search heuristics
    for (p in 0 until MAX_PLY) {
      killers[p][0] = 0
      killers[p][1] = 0
    }
    for (s in 0..1) {
      for (f in 0..63) {
        for (t in 0..63) {
          history[s][f][t] = history[s][f][t] ushr 2
        }
      }
    }

    nodes = 0L
    aborted = false
    // Fast search if book move exists, full search otherwise
    val effectiveBudgetMs = if (bookEntry != null) min(timeBudgetMs, 220L) else timeBudgetMs
    deadlineNs = startNs + effectiveBudgetMs * 1_000_000L

    val rootScores = IntArray(rootCount)
    val bestRootOrder = IntArray(rootCount) { it }
    var completedDepth = 1
    val maxTargetDepth = if (timeBudgetMs >= 800L) 11 else 9

    // Iterative Deepening Loop
    for (depth in 1..maxTargetDepth) {
      currentRootDepth = depth
      if (depth > minRequiredDepth && (cancelRequested || System.nanoTime() >= deadlineNs)) {
        break
      }

      val iterScores = IntArray(rootCount)
      var alpha = -INF
      val beta = INF
      var iterAborted = false

      for (idx in 0 until rootCount) {
        val mIdx = bestRootOrder[idx]
        val move = rootMoves[mIdx]

        val isCapture = MoveCodec.isCapture(move)
        val from = MoveCodec.from(move)
        val movedType = MoveCodec.movedType(move)
        val capturedType = MoveCodec.capturedType(move)
        val see = if (isCapture) seeCapture(board, move) else 0
        val wasOurPieceAttacked = if (isCapture) board.isSquareAttacked(from, -board.sideToMove) else false

        // Discourage premature / unbeneficial equal liquidation:
        // When our piece is safe, voluntarily initiating an equal exchange into an enemy-defended square
        // eliminates our attacking pressure and simplifies for the opponent without winning material.
        val equalTradePenalty = if (isCapture && see == 0 && capturedType >= 2 && !wasOurPieceAttacked) {
          when {
            movedType == 5 && capturedType == 5 -> 38 // Prevent premature Queen trades
            movedType in 2..3 && capturedType in 2..3 -> 24 // Prevent unnecessary minor piece trades
            movedType == 4 && capturedType == 4 -> 20 // Prevent unnecessary rook trades
            else -> 0
          }
        } else if (isCapture && see in -250..-100 && movedType == 4 && capturedType in 2..3) {
          // Penalize reckless exchange sacrifice (Rook for Bishop/Knight) unless deep search proves it's winning
          55
        } else 0

        searchPathHashes[gameHistoryCount] = board.zobristKey
        board.makeMove(move)

        val repPenalty = if (countHashOccurrences(board.zobristKey, gameHistoryCount) >= 1) {
          45 // Discourage repeating positions when we have constructive alternatives
        } else 0

        // Slight penalty for pointless back-and-forth reverse move if quiet
        val reverseShufflePenalty = if (
          !MoveCodec.isTactical(move) &&
          lastOwnMoveFrom >= 0 &&
          MoveCodec.from(move) == lastOwnMoveTo &&
          MoveCodec.to(move) == lastOwnMoveFrom &&
          !board.isInCheck()
        ) 18 else 0

        val score: Int = if (idx == 0 || depth <= 2) {
          -negamax(board, depth - 1, -beta, -alpha, 1, allowNull = true)
        } else {
          var s = -negamax(board, depth - 1, -alpha - 1, -alpha, 1, allowNull = true)
          if (s > alpha && !aborted) {
            s = -negamax(board, depth - 1, -beta, -alpha, 1, allowNull = true)
          }
          s
        }

        board.unmakeMove(move)

        if (aborted) {
          iterAborted = true
          break
        }

        val adjustedScore = score - repPenalty - reverseShufflePenalty - equalTradePenalty
        iterScores[mIdx] = adjustedScore
        if (adjustedScore > alpha) {
          alpha = adjustedScore
        }
      }

      if (!iterAborted) {
        completedDepth = depth
        System.arraycopy(iterScores, 0, rootScores, 0, rootCount)
        // Sort root moves descending by exact evaluated score
        val sortedIndices = bestRootOrder.sortedByDescending { rootScores[it] }
        for (i in 0 until rootCount) bestRootOrder[i] = sortedIndices[i]

        // Store #1 move in TT
        storeTT(board.zobristKey, rootMoves[bestRootOrder[0]], rootScores[bestRootOrder[0]], depth, TT_EXACT, 0)

        // Stop early if forced mate is already proven
        if (rootScores[bestRootOrder[0]] >= MATE_THRESHOLD || rootCount == 1) {
          break
        }
      } else {
        break
      }
    }

    // Integrate Grandmaster Opening Book preference ONLY if verified tactically sound!
    if (bookEntry != null && rootCount > 1) {
      val topEngineScore = rootScores[bestRootOrder[0]]
      for (bookUci in bookEntry.bestUciMoves) {
        val bIdx = (0 until rootCount).firstOrNull { MoveCodec.toUci(rootMoves[it]) == bookUci }
        if (bIdx != null) {
          val bookMoveScore = rootScores[bIdx]
          // Accept book move only if within 35 centipawns of engine's best tactical score (never blunders!)
          if (bookMoveScore >= topEngineScore - 35 && topEngineScore < MATE_THRESHOLD) {
            val posInOrder = bestRootOrder.indexOf(bIdx)
            if (posInOrder > 0) {
              for (k in posInOrder downTo 1) {
                bestRootOrder[k] = bestRootOrder[k - 1]
              }
              bestRootOrder[0] = bIdx
            }
            break
          }
        }
      }
    }

    val topCount = min(3, rootCount)
    val candidates = ArrayList<CandidateMove>(topCount)
    for (rank in 0 until topCount) {
      val mIdx = bestRootOrder[rank]
      val rawMove = rootMoves[mIdx]
      val scoreSide = rootScores[mIdx]
      val scoreWhite = scoreSide * board.sideToMove
      val mateWhite = computeMateMovesWhite(scoreSide, board.sideToMove)
      val chessMove = MoveGenerator.toChessMove(board, rawMove, rootMoves, rootCount)
      val pvSan = extractPvSan(board, rawMove, maxLen = 5)
      val reason = buildMoveReasonIndo(
        board = board,
        rawMove = rawMove,
        chessMove = chessMove,
        scoreSide = scoreSide,
        openingName = if (rank == 0) bookEntry?.openingName else null,
      )
      candidates.add(
        CandidateMove(
          move = chessMove,
          evalCentipawnsWhite = scoreWhite,
          mateInMovesWhite = mateWhite,
          pvSan = pvSan,
          reasonIndo = reason,
        )
      )
    }

    val bestCandidate = candidates.first()
    val elapsedMs = max(1L, (System.nanoTime() - startNs) / 1_000_000L)
    val detectedOpening = bookEntry?.openingName ?: OpeningBook.detectOpeningName(board)

    return AnalysisResult(
      analyzedForSide = sideEnum,
      bestMove = bestCandidate.move,
      candidates = candidates,
      evalCentipawnsWhite = bestCandidate.evalCentipawnsWhite,
      mateInMovesWhite = bestCandidate.mateInMovesWhite,
      depthReached = completedDepth,
      nodesSearched = nodes,
      elapsedMs = elapsedMs,
      openingName = detectedOpening,
      tacticalSummary = bestCandidate.reasonIndo,
    )
  }

  private fun negamax(
    board: ChessBoard,
    depthInput: Int,
    alphaInput: Int,
    betaInput: Int,
    ply: Int,
    allowNull: Boolean,
  ): Int {
    if ((nodes and 2047L) == 0L && ply > 0) {
      if (cancelRequested || (currentRootDepth > minRequiredDepth && System.nanoTime() >= deadlineNs)) {
        aborted = true
      }
    }
    if (aborted) return 0

    nodes++
    val pathIdx = gameHistoryCount + ply

    // Draw / Repetition check
    if (board.halfMoveClock >= 100 || board.isInsufficientMaterial()) {
      return 0
    }
    if (ply > 0 && countHashOccurrences(board.zobristKey, pathIdx) >= 1) {
      // Contempt: root side avoids sterile repetition unless losing
      return if (board.sideToMove == rootSide) -40 else 40
    }

    val inCheck = board.isInCheck()
    // Check extension prevents tactical horizon blunders
    val depth = if (inCheck && ply < MAX_PLY - 4) depthInput + 1 else depthInput

    if (depth <= 0 || ply >= MAX_PLY - 1) {
      return quiescence(board, alphaInput, betaInput, ply, 0)
    }

    var alpha = alphaInput
    var beta = betaInput
    val isPv = (beta - alpha) > 1

    // Mate distance pruning
    alpha = max(alpha, -MATE_SCORE + ply)
    beta = min(beta, MATE_SCORE - ply - 1)
    if (alpha >= beta) return alpha

    // Transposition Table Lookup
    val hash = board.zobristKey
    val ttIdx = (hash.toInt() xor (hash ushr 32).toInt()) and TT_MASK
    var hashMove = MoveCodec.NO_MOVE
    if (ttKey[ttIdx] == hash && ttFlag[ttIdx] != 0) {
      hashMove = ttMove[ttIdx]
      if (!isPv && ttDepth[ttIdx] >= depth) {
        val ttVal = scoreFromTT(ttScore[ttIdx], ply)
        when (ttFlag[ttIdx]) {
          TT_EXACT -> return ttVal
          TT_LOWER -> if (ttVal >= beta) return ttVal
          TT_UPPER -> if (ttVal <= alpha) return ttVal
        }
      }
    }

    // Null Move Pruning (never in check, never in pawn-only endgames)
    if (allowNull && !isPv && !inCheck && depth >= 3 && board.hasNonPawnMaterial(board.sideToMove)) {
      val staticEval = Evaluation.evaluate(board)
      if (staticEval >= beta) {
        searchPathHashes[pathIdx] = hash
        board.makeNullMove()
        val r = if (depth >= 6) 3 else 2
        val nullScore = -negamax(board, depth - 1 - r, -beta, -beta + 1, ply + 1, allowNull = false)
        board.unmakeNullMove()
        if (aborted) return 0
        if (nullScore >= beta && nullScore < MATE_THRESHOLD) {
          return beta
        }
      }
    }

    val moves = plyMoves[ply]
    val orderScores = plyOrderScores[ply]
    val count = MoveGenerator.generateLegalMoves(board, moves, tacticalOnly = false)
    if (count == 0) {
      return if (inCheck) -MATE_SCORE + ply else 0
    }

    scoreMoves(board, moves, orderScores, count, hashMove, ply)

    var bestScore = -INF
    var bestMove = moves[0]
    val origAlpha = alpha
    val sideIdx = if (board.sideToMove == 1) 0 else 1

    for (i in 0 until count) {
      pickNextMove(moves, orderScores, count, i)
      val move = moves[i]
      val isTactical = MoveCodec.isTactical(move)

      searchPathHashes[pathIdx] = hash
      board.makeMove(move)
      val givesCheck = board.isInCheck()

      var score: Int
      if (i == 0) {
        score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, allowNull = true)
      } else {
        // Late Move Reduction (LMR) for quiet, non-checking late moves
        var reduction = 0
        if (depth >= 3 && i >= 4 && !inCheck && !givesCheck && !isTactical) {
          reduction = if (i >= 10 && depth >= 5) 2 else 1
        }
        score = -negamax(board, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, allowNull = true)
        if (score > alpha && reduction > 0) {
          score = -negamax(board, depth - 1, -alpha - 1, -alpha, ply + 1, allowNull = true)
        }
        if (score > alpha && score < beta) {
          score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, allowNull = true)
        }
      }

      board.unmakeMove(move)
      if (aborted) return 0

      if (score > bestScore) {
        bestScore = score
        bestMove = move
        if (score > alpha) {
          alpha = score
          if (alpha >= beta) {
            if (!isTactical) {
              if (killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0]
                killers[ply][0] = move
              }
              val bonus = min(depth * depth, 400)
              val f = MoveCodec.from(move)
              val t = MoveCodec.to(move)
              history[sideIdx][f][t] = min(20_000, history[sideIdx][f][t] + bonus)
            }
            break
          }
        }
      }
    }

    val flag = when {
      bestScore <= origAlpha -> TT_UPPER
      bestScore >= beta -> TT_LOWER
      else -> TT_EXACT
    }
    storeTT(hash, bestMove, bestScore, depth, flag, ply)
    return bestScore
  }

  private fun quiescence(
    board: ChessBoard,
    alphaInput: Int,
    beta: Int,
    ply: Int,
    qDepth: Int,
  ): Int {
    nodes++
    if (ply >= MAX_PLY + 28 || qDepth >= 12) {
      return Evaluation.evaluate(board)
    }

    val inCheck = board.isInCheck()
    var alpha = alphaInput

    if (!inCheck) {
      val standPat = Evaluation.evaluate(board)
      if (standPat >= beta) return beta
      if (standPat > alpha) alpha = standPat

      val moves = plyMoves[ply]
      val scores = plyOrderScores[ply]
      val count = MoveGenerator.generateLegalMoves(board, moves, tacticalOnly = true)
      if (count == 0) return alpha

      for (i in 0 until count) {
        val m = moves[i]
        val seeVal = if (MoveCodec.isPromotion(m)) {
          SEE_VAL[MoveCodec.promoType(m)]
        } else {
          seeCapture(board, m)
        }
        scores[i] = seeVal * 16 + SEE_VAL[MoveCodec.capturedType(m)]
      }

      for (i in 0 until count) {
        pickNextMove(moves, scores, count, i)
        val move = moves[i]
        // Prune losing captures in QSearch so unequal trades are immediately rejected
        if (!MoveCodec.isPromotion(move) && scores[i] < -100) {
          continue
        }
        // Delta pruning
        val maxGain = SEE_VAL[MoveCodec.capturedType(move)] +
          (if (MoveCodec.isPromotion(move)) 850 else 0) + 180
        if (standPat + maxGain < alpha) continue

        board.makeMove(move)
        val score = -quiescence(board, -beta, -alpha, ply + 1, qDepth + 1)
        board.unmakeMove(move)

        if (score >= beta) return beta
        if (score > alpha) alpha = score
      }
      return alpha
    } else {
      // When in check at leaf, search ALL legal check evasions!
      val moves = plyMoves[ply]
      val scores = plyOrderScores[ply]
      val count = MoveGenerator.generateLegalMoves(board, moves, tacticalOnly = false)
      if (count == 0) return -MATE_SCORE + ply

      scoreMoves(board, moves, scores, count, MoveCodec.NO_MOVE, min(ply, MAX_PLY - 1))
      for (i in 0 until count) {
        pickNextMove(moves, scores, count, i)
        board.makeMove(moves[i])
        val score = -quiescence(board, -beta, -alpha, ply + 1, qDepth + 1)
        board.unmakeMove(moves[i])
        if (score >= beta) return beta
        if (score > alpha) alpha = score
      }
      return alpha
    }
  }

  /**
   * Static Exchange Evaluation (SEE) with X-Ray slider discovery on target square.
   * Returns net centipawn material gain/loss of initiating [move].
   * Prevents bad trades (e.g., QxP defended, RxN defended).
   */
  fun seeCapture(board: ChessBoard, move: Int): Int {
    val from = MoveCodec.from(move)
    val to = MoveCodec.to(move)
    val flag = MoveCodec.flag(move)
    if (flag == MoveCodec.FLAG_EN_PASSANT) return 100

    val capType = MoveCodec.capturedType(move)
    val promoType = MoveCodec.promoType(move)
    if (capType == 0 && promoType == 0) return 0

    val gain = IntArray(32)
    var d = 0
    val movedType = MoveCodec.movedType(move)
    var attackerType = if (promoType != 0) promoType else movedType
    gain[0] = SEE_VAL[capType] + (if (promoType != 0) SEE_VAL[promoType] - 100 else 0)

    val removed = BooleanArray(64)
    removed[from] = true
    var side = -board.sideToMove

    while (d < 30) {
      val nextSq = findLeastValuableAttacker(board.squares, to, side, removed)
      if (nextSq < 0) break
      d++
      gain[d] = SEE_VAL[attackerType] - gain[d - 1]
      if (max(-gain[d - 1], gain[d]) < 0) break
      attackerType = abs(board.squares[nextSq])
      // Pawn capture onto back rank promotes
      if (attackerType == 1 && ((to ushr 3) == 0 || (to ushr 3) == 7)) {
        attackerType = 5
        gain[d] += 800
      }
      removed[nextSq] = true
      side = -side
    }

    while (d > 0) {
      gain[d - 1] = -max(-gain[d - 1], gain[d])
      d--
    }
    return gain[0]
  }

  private fun findLeastValuableAttacker(
    squares: IntArray,
    targetSq: Int,
    bySide: Int,
    removed: BooleanArray,
  ): Int {
    val tr = targetSq ushr 3
    val tf = targetSq and 7

    // 1. Pawn attackers
    if (bySide == 1 && tr > 0) {
      if (tf > 0 && !removed[targetSq - 9] && squares[targetSq - 9] == Piece.WP) return targetSq - 9
      if (tf < 7 && !removed[targetSq - 7] && squares[targetSq - 7] == Piece.WP) return targetSq - 7
    } else if (bySide == -1 && tr < 7) {
      if (tf > 0 && !removed[targetSq + 7] && squares[targetSq + 7] == Piece.BP) return targetSq + 7
      if (tf < 7 && !removed[targetSq + 9] && squares[targetSq + 9] == Piece.BP) return targetSq + 9
    }

    // 2. Knight attackers
    val targetKnight = Piece.WN * bySide
    for (sq in ChessBoard.KNIGHT_MOVES[targetSq]) {
      if (!removed[sq] && squares[sq] == targetKnight) return sq
    }

    // 3. Bishop (and X-ray diagonal) attackers
    val targetBishop = Piece.WB * bySide
    val targetQueen = Piece.WQ * bySide
    var queenCandidate = -1
    for (dir in 4..7) {
      for (sq in ChessBoard.RAYS[dir][targetSq]) {
        if (removed[sq]) continue
        val p = squares[sq]
        if (p != Piece.EMPTY) {
          if (p == targetBishop) return sq
          if (p == targetQueen && queenCandidate < 0) queenCandidate = sq
          break
        }
      }
    }

    // 4. Rook (and X-ray orthogonal) attackers
    val targetRook = Piece.WR * bySide
    for (dir in 0..3) {
      for (sq in ChessBoard.RAYS[dir][targetSq]) {
        if (removed[sq]) continue
        val p = squares[sq]
        if (p != Piece.EMPTY) {
          if (p == targetRook) return sq
          if (p == targetQueen && queenCandidate < 0) queenCandidate = sq
          break
        }
      }
    }

    // 5. Queen attacker
    if (queenCandidate >= 0) return queenCandidate

    // 6. King attacker
    val targetKing = Piece.WK * bySide
    for (sq in ChessBoard.KING_MOVES[targetSq]) {
      if (!removed[sq] && squares[sq] == targetKing) return sq
    }

    return -1
  }

  private fun scoreMoves(
    board: ChessBoard,
    moves: IntArray,
    scores: IntArray,
    count: Int,
    hashMove: Int,
    ply: Int,
  ) {
    val sideIdx = if (board.sideToMove == 1) 0 else 1
    val k0 = killers[ply][0]
    val k1 = killers[ply][1]

    for (i in 0 until count) {
      val m = moves[i]
      scores[i] = when {
        m == hashMove -> 2_000_000
        MoveCodec.isPromotion(m) -> 1_200_000 + SEE_VAL[MoveCodec.promoType(m)] * 10
        MoveCodec.isCapture(m) -> {
          val seeVal = seeCapture(board, m)
          val mvvLva = SEE_VAL[MoveCodec.capturedType(m)] * 10 - MoveCodec.movedType(m)
          when {
            seeVal > 0 -> 1_000_000 + seeVal * 10 + mvvLva
            seeVal == 0 -> 720_000 + mvvLva // Equal captures after killers
            else -> -200_000 + seeVal * 10 + mvvLva
          }
        }
        m == k0 -> 800_000
        m == k1 -> 750_000
        else -> history[sideIdx][MoveCodec.from(m)][MoveCodec.to(m)]
      }
    }
  }

  private fun pickNextMove(moves: IntArray, scores: IntArray, count: Int, startIdx: Int) {
    var bestIdx = startIdx
    var bestScore = scores[startIdx]
    for (j in (startIdx + 1) until count) {
      if (scores[j] > bestScore) {
        bestScore = scores[j]
        bestIdx = j
      }
    }
    if (bestIdx != startIdx) {
      val tmpM = moves[startIdx]
      moves[startIdx] = moves[bestIdx]
      moves[bestIdx] = tmpM
      val tmpS = scores[startIdx]
      scores[startIdx] = scores[bestIdx]
      scores[bestIdx] = tmpS
    }
  }

  private fun countHashOccurrences(targetHash: Long, limitExclusive: Int): Int {
    var occ = 0
    for (i in 0 until limitExclusive) {
      if (searchPathHashes[i] == targetHash) occ++
    }
    return occ
  }

  private fun storeTT(hash: Long, move: Int, score: Int, depth: Int, flag: Int, ply: Int) {
    val idx = (hash.toInt() xor (hash ushr 32).toInt()) and TT_MASK
    if (ttKey[idx] != hash || depth >= ttDepth[idx] || flag == TT_EXACT) {
      ttKey[idx] = hash
      if (move != MoveCodec.NO_MOVE) ttMove[idx] = move
      ttScore[idx] = scoreToTT(score, ply)
      ttDepth[idx] = depth
      ttFlag[idx] = flag
    }
  }

  private fun scoreToTT(score: Int, ply: Int): Int = when {
    score >= MATE_THRESHOLD -> score + ply
    score <= -MATE_THRESHOLD -> score - ply
    else -> score
  }

  private fun scoreFromTT(score: Int, ply: Int): Int = when {
    score >= MATE_THRESHOLD -> score - ply
    score <= -MATE_THRESHOLD -> score + ply
    else -> score
  }

  private fun computeMateMovesWhite(scoreSide: Int, sideToMove: Int): Int? {
    return when {
      scoreSide >= MATE_THRESHOLD -> {
        val plies = max(1, MATE_SCORE - scoreSide)
        val moves = (plies + 1) / 2
        if (sideToMove == 1) moves else -moves
      }
      scoreSide <= -MATE_THRESHOLD -> {
        val plies = max(1, MATE_SCORE + scoreSide)
        val moves = (plies + 1) / 2
        if (sideToMove == 1) -moves else moves
      }
      else -> null
    }
  }

  private fun extractPvSan(sourceBoard: ChessBoard, firstMove: Int, maxLen: Int): List<String> {
    val pv = ArrayList<String>(maxLen)
    val b = sourceBoard.copy()
    val buf = IntArray(256)

    pv.add(MoveGenerator.toSan(b, firstMove))
    b.makeMove(firstMove)

    val visited = HashSet<Long>()
    visited.add(b.zobristKey)

    for (step in 1 until maxLen) {
      val h = b.zobristKey
      val idx = (h.toInt() xor (h ushr 32).toInt()) and TT_MASK
      if (ttKey[idx] != h || ttFlag[idx] == 0) break
      val cand = ttMove[idx]
      if (cand == MoveCodec.NO_MOVE) break

      val cnt = MoveGenerator.generateLegalMoves(b, buf)
      var found = false
      for (i in 0 until cnt) {
        if (buf[i] == cand) {
          found = true
          break
        }
      }
      if (!found) break
      pv.add(MoveGenerator.toSan(b, cand, buf, cnt))
      b.makeMove(cand)
      if (!visited.add(b.zobristKey)) break
    }
    return pv
  }

  private fun buildMoveReasonIndo(
    board: ChessBoard,
    rawMove: Int,
    chessMove: ChessMove,
    scoreSide: Int,
    openingName: String?,
  ): String {
    if (scoreSide >= MATE_THRESHOLD) {
      val plies = max(1, MATE_SCORE - scoreSide)
      val m = (plies + 1) / 2
      return if (m == 1) "Skakmat langsung! Mengakhiri permainan seketika."
      else "Jalur paksa menuju Skakmat dalam $m langkah!"
    }
    if (scoreSide <= -MATE_THRESHOLD) {
      return "Pertahanan terkuat untuk memperpanjang perlawanan dari ancaman skakmat."
    }

    val wasInCheck = board.isInCheck()
    if (wasInCheck) {
      return "Menyelamatkan Raja dari skak sekaligus menjaga struktur pertahanan terkuat."
    }

    if (openingName != null) {
      return "Langkah teori Grandmaster: $openingName."
    }

    val flag = MoveCodec.flag(rawMove)
    if (flag == MoveCodec.FLAG_KING_CASTLE || flag == MoveCodec.FLAG_QUEEN_CASTLE) {
      return "Mengamankan Raja melalui rokade dan menghubungkan Benteng ke jalur pusat."
    }

    if (MoveCodec.isPromotion(rawMove)) {
      val pName = chessMove.promotionType?.indoName ?: "Menteri"
      return "Promosi bidak menjadi $pName untuk keunggulan materi penentu!"
    }

    val from = MoveCodec.from(rawMove)
    val to = MoveCodec.to(rawMove)
    val movedType = MoveCodec.movedType(rawMove)

    // Deteksi apakah petak asal sedang diancam musuh
    val wasUnderThreat = board.isSquareAttacked(from, -board.sideToMove)
    val isDestinationProtected = board.isSquareAttacked(to, board.sideToMove)

    if (MoveCodec.isCapture(rawMove)) {
      val seeGain = seeCapture(board, rawMove)
      val capName = PieceType.fromCode(MoveCodec.capturedType(rawMove))?.indoName ?: "materi"
      return when {
        seeGain >= 280 -> "Memenangkan $capName lawan dengan pertukaran materi yang sangat menguntungkan!"
        seeGain >= 80 -> "Merebut bidak/materi bersih tanpa risiko serangan balik."
        wasUnderThreat -> "Menyelamatkan perwira yang terancam sekaligus melenyapkan perwira musuh."
        else -> "Mempertahankan ketegangan taktis dengan pertukaran terkontrol tanpa melemahkan struktur."
      }
    }

    board.makeMove(rawMove)
    val givesCheck = board.isInCheck()
    board.unmakeMove(rawMove)
    if (givesCheck) {
      return "Memberikan skak aktif untuk merebut inisiatif dan mengacaukan koordinasi Raja lawan."
    }

    // Menyelamatkan perwira berharga yang terancam
    if (wasUnderThreat && movedType in 2..5) {
      val pieceName = PieceType.fromCode(movedType)?.indoName ?: "Perwira"
      return "Mengamankan $pieceName dari ancaman lawan ke petak yang kokoh dan terlindungi."
    }

    val toRank = to ushr 3
    val toFile = to and 7

    if (movedType in 2..3 && ((board.sideToMove == 1 && (from ushr 3) == 0) ||
        (board.sideToMove == -1 && (from ushr 3) == 7))
    ) {
      return "Mengembangkan perwira ke petak aktif terlindungi dan menguasai jalur tengah."
    }

    if (movedType == 4) {
      return "Mengaktifkan Benteng ke lajur terbuka/semi-terbuka untuk menekan pertahanan lawan."
    }

    if (movedType == 5 && isDestinationProtected) {
      return "Manuver Menteri yang terlindungi untuk mengontrol ruang tanpa risiko jebakan perwira."
    }

    if (toRank in 2..5 && toFile in 2..5) {
      return "Menguasai petak pusat strategis dan memperkuat jangkar perlindungan antar perwira."
    }

    return if (scoreSide >= 150) {
      "Membangun serangan terkoordinasi dan mempererat tekanan ke pertahanan musuh."
    } else {
      "Langkah posisional solid yang mempertahankan perlindungan perwira berharga."
    }
  }
}
