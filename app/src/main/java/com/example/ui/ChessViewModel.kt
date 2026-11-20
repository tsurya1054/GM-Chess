package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.AnalysisResult
import com.example.chess.ChessBoard
import com.example.chess.ChessEngine
import com.example.chess.ChessMove
import com.example.chess.GameStatus
import com.example.chess.MoveGenerator
import com.example.chess.MoveRecord
import com.example.chess.Piece
import com.example.chess.PieceType
import com.example.chess.Side
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AnalysisSpeed(val budgetMs: Long) {
  FAST(420L),
  DEEP(950L),
}

sealed interface EditorTool {
  data object Move : EditorTool
  data object Erase : EditorTool
  data class Place(val piece: Int) : EditorTool
}

data class ChessUiState(
  val squares: List<Int> = List(64) { 0 },
  val sideToMove: Side = Side.WHITE,
  val playerColor: Side = Side.WHITE,
  val analysisSpeed: AnalysisSpeed = AnalysisSpeed.FAST,
  val isBoardFlipped: Boolean = false,
  val selectedSquare: Int? = null,
  val legalMovesForSelected: List<ChessMove> = emptyList(),
  val pendingPromotionOptions: List<ChessMove>? = null,
  val lastMoveFrom: Int? = null,
  val lastMoveTo: Int? = null,
  val checkedKingSquare: Int? = null,
  val gameStatus: GameStatus = GameStatus.ONGOING,
  val moveHistory: List<MoveRecord> = emptyList(),
  val analysis: AnalysisResult? = null,
  val lastEvalCentipawnsWhite: Int = 0,
  val lastMateInMovesWhite: Int? = null,
  val isAnalyzing: Boolean = false,
  val isEditMode: Boolean = false,
  val editSquares: List<Int> = List(64) { 0 },
  val editSideToMove: Side = Side.WHITE,
  val selectedEditorTool: EditorTool = EditorTool.Move,
  val editSelectedSquare: Int? = null,
  val editValidationError: String? = null,
  val editInlineTip: String? = null,
) {
  val isPlayersTurn: Boolean
    get() = !gameStatus.isGameOver && sideToMove == playerColor
}

class ChessViewModel : ViewModel() {

  private val board = ChessBoard()
  private val engine = ChessEngine()
  private val allLegalMovesCache = ArrayList<ChessMove>(64)
  private var analysisJob: Job? = null

  private val _uiState = MutableStateFlow(ChessUiState())
  val uiState: StateFlow<ChessUiState> = _uiState.asStateFlow()

  init {
    syncBoardStateAndAnalyzeIfPlayersTurn(history = emptyList(), resetEval = true)
  }

  fun setPlayerColor(color: Side) {
    if (_uiState.value.playerColor == color) return
    engine.cancelRequested = true
    analysisJob?.cancel()

    _uiState.update {
      it.copy(
        playerColor = color,
        isBoardFlipped = (color == Side.BLACK),
        selectedSquare = null,
        legalMovesForSelected = emptyList(),
        pendingPromotionOptions = null,
        analysis = null,
        isAnalyzing = false,
      )
    }
    if (!_uiState.value.isEditMode) {
      analyzeOnlyForPlayerTurn()
    }
  }

  fun toggleAnalysisSpeed() {
    val next = if (_uiState.value.analysisSpeed == AnalysisSpeed.FAST) {
      AnalysisSpeed.DEEP
    } else {
      AnalysisSpeed.FAST
    }
    _uiState.update { it.copy(analysisSpeed = next) }
    if (!_uiState.value.isEditMode && _uiState.value.isPlayersTurn) {
      analyzeOnlyForPlayerTurn()
    }
  }

  fun flipBoard() {
    _uiState.update { it.copy(isBoardFlipped = !it.isBoardFlipped) }
  }

  fun dismissPromotion() {
    _uiState.update { it.copy(pendingPromotionOptions = null) }
  }

  fun onPromotionSelected(pieceType: PieceType) {
    val options = _uiState.value.pendingPromotionOptions ?: return
    val chosen = options.firstOrNull { it.promotionType == pieceType } ?: options.first()
    _uiState.update { it.copy(pendingPromotionOptions = null) }
    executeMove(chosen)
  }

  // --- Board Setup / Piece Arrangement Editor ---

  fun enterEditMode() {
    engine.cancelRequested = true
    analysisJob?.cancel()

    val currentSquares = board.squares.copyOf()
    val currentTurn = Side.fromSign(board.sideToMove)
    val error = ChessBoard.validateSetup(currentSquares, currentTurn)

    _uiState.update {
      it.copy(
        isEditMode = true,
        editSquares = currentSquares.toList(),
        editSideToMove = currentTurn,
        selectedEditorTool = EditorTool.Move,
        editSelectedSquare = null,
        editValidationError = error,
        editInlineTip = null,
        selectedSquare = null,
        legalMovesForSelected = emptyList(),
        pendingPromotionOptions = null,
        isAnalyzing = false,
      )
    }
  }

  fun cancelEditMode() {
    if (!_uiState.value.isEditMode) return
    _uiState.update {
      it.copy(
        isEditMode = false,
        editSelectedSquare = null,
        editValidationError = null,
        editInlineTip = null,
      )
    }
    if (!_uiState.value.gameStatus.isGameOver) {
      analyzeOnlyForPlayerTurn()
    }
  }

  fun selectEditorTool(tool: EditorTool) {
    val state = _uiState.value
    if (!state.isEditMode) return

    // If a board square is selected in Move mode and user taps Erase, immediately remove that piece
    if (tool is EditorTool.Erase && state.editSelectedSquare != null) {
      val arr = state.editSquares.toIntArray()
      arr[state.editSelectedSquare] = Piece.EMPTY
      val err = ChessBoard.validateSetup(arr, state.editSideToMove)
      _uiState.update {
        it.copy(
          editSquares = arr.toList(),
          selectedEditorTool = tool,
          editSelectedSquare = null,
          editValidationError = err,
          editInlineTip = null,
        )
      }
      return
    }

    _uiState.update {
      it.copy(
        selectedEditorTool = tool,
        editSelectedSquare = null,
        editInlineTip = null,
      )
    }
  }

  fun setEditSideToMove(side: Side) {
    val state = _uiState.value
    if (!state.isEditMode || state.editSideToMove == side) return
    val err = ChessBoard.validateSetup(state.editSquares.toIntArray(), side)
    _uiState.update {
      it.copy(
        editSideToMove = side,
        editValidationError = err,
        editInlineTip = null,
      )
    }
  }

  fun setEditToStartPos() {
    if (!_uiState.value.isEditMode) return
    val std = ChessBoard()
    val err = ChessBoard.validateSetup(std.squares, Side.WHITE)
    _uiState.update {
      it.copy(
        editSquares = std.squares.toList(),
        editSideToMove = Side.WHITE,
        editSelectedSquare = null,
        editValidationError = err,
        editInlineTip = null,
      )
    }
  }

  fun clearEditBoard() {
    if (!_uiState.value.isEditMode) return
    val empty = IntArray(64)
    val err = ChessBoard.validateSetup(empty, _uiState.value.editSideToMove)
    _uiState.update {
      it.copy(
        editSquares = empty.toList(),
        editSelectedSquare = null,
        editValidationError = err,
        editInlineTip = null,
      )
    }
  }

  fun applyEditBoard(): Boolean {
    val state = _uiState.value
    if (!state.isEditMode) return false
    val arr = state.editSquares.toIntArray()
    val err = ChessBoard.validateSetup(arr, state.editSideToMove)
    if (err != null) {
      _uiState.update { it.copy(editValidationError = err) }
      return false
    }

    engine.cancelRequested = true
    analysisJob?.cancel()
    engine.clearCache()

    board.loadCustomPosition(arr, state.editSideToMove.sign)
    _uiState.update {
      it.copy(
        isEditMode = false,
        editSelectedSquare = null,
        editValidationError = null,
        editInlineTip = null,
      )
    }
    syncBoardStateAndAnalyzeIfPlayersTurn(history = emptyList(), resetEval = true)
    return true
  }

  /**
   * Handles placing/editing pieces when in Edit Mode,
   * placing the enemy's manual move (when sideToMove != playerColor),
   * and executing the user's own move (when sideToMove == playerColor).
   */
  fun onSquareClicked(square: Int) {
    val state = _uiState.value
    if (state.isEditMode) {
      handleEditSquareClick(square)
      return
    }
    if (state.gameStatus.isGameOver) return

    val currentSelected = state.selectedSquare
    if (currentSelected != null) {
      if (currentSelected == square) {
        _uiState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
        return
      }
      val matchingMoves = state.legalMovesForSelected.filter { it.to == square }
      if (matchingMoves.isNotEmpty()) {
        if (matchingMoves.size > 1 && matchingMoves.first().isPromotion) {
          _uiState.update { it.copy(pendingPromotionOptions = matchingMoves) }
        } else {
          executeMove(matchingMoves.first())
        }
        return
      }
    }

    // Select a piece belonging to the side currently to move (User on user's turn, Enemy on enemy's turn)
    val piece = board.squares[square]
    if (piece * board.sideToMove > 0) {
      val movesFromSq = allLegalMovesCache.filter { it.from == square }
      _uiState.update {
        it.copy(
          selectedSquare = square,
          legalMovesForSelected = movesFromSq,
        )
      }
    } else {
      _uiState.update { it.copy(selectedSquare = null, legalMovesForSelected = emptyList()) }
    }
  }

  private fun handleEditSquareClick(square: Int) {
    val state = _uiState.value
    val arr = state.editSquares.toIntArray()
    val rank = square ushr 3

    when (val tool = state.selectedEditorTool) {
      is EditorTool.Move -> {
        val sel = state.editSelectedSquare
        if (sel == null) {
          if (arr[square] != Piece.EMPTY) {
            _uiState.update { it.copy(editSelectedSquare = square, editInlineTip = null) }
          }
        } else if (sel == square) {
          _uiState.update { it.copy(editSelectedSquare = null, editInlineTip = null) }
        } else {
          val movingPiece = arr[sel]
          if (abs(movingPiece) == 1 && (rank == 0 || rank == 7)) {
            _uiState.update {
              it.copy(editInlineTip = "Bidak (pion) tidak dapat diletakkan di baris 1 atau 8.")
            }
            return
          }
          arr[square] = movingPiece
          arr[sel] = Piece.EMPTY
          val err = ChessBoard.validateSetup(arr, state.editSideToMove)
          _uiState.update {
            it.copy(
              editSquares = arr.toList(),
              editSelectedSquare = null,
              editValidationError = err,
              editInlineTip = null,
            )
          }
        }
      }
      is EditorTool.Erase -> {
        if (arr[square] != Piece.EMPTY) {
          arr[square] = Piece.EMPTY
          val err = ChessBoard.validateSetup(arr, state.editSideToMove)
          _uiState.update {
            it.copy(
              editSquares = arr.toList(),
              editSelectedSquare = null,
              editValidationError = err,
              editInlineTip = null,
            )
          }
        }
      }
      is EditorTool.Place -> {
        val targetPiece = tool.piece
        if (arr[square] == targetPiece) {
          // Toggle off if tapping the exact same piece
          arr[square] = Piece.EMPTY
        } else {
          if (abs(targetPiece) == 1 && (rank == 0 || rank == 7)) {
            _uiState.update {
              it.copy(editInlineTip = "Bidak (pion) tidak dapat diletakkan di baris 1 atau 8.")
            }
            return
          }
          // Enforce single King per color seamlessly by moving the existing King
          if (targetPiece == Piece.WK || targetPiece == Piece.BK) {
            for (i in 0..63) {
              if (arr[i] == targetPiece) arr[i] = Piece.EMPTY
            }
          }
          arr[square] = targetPiece
        }
        val err = ChessBoard.validateSetup(arr, state.editSideToMove)
        _uiState.update {
          it.copy(
            editSquares = arr.toList(),
            editSelectedSquare = null,
            editValidationError = err,
            editInlineTip = null,
          )
        }
      }
    }
  }

  fun playRecommendedMove() {
    val state = _uiState.value
    // Strictly execute recommended move ONLY on the player's own turn
    if (state.isEditMode || !state.isPlayersTurn) return
    val best = state.analysis?.bestMove ?: return
    if (state.analysis.analyzedForSide != state.playerColor) return
    val verified = allLegalMovesCache.firstOrNull { it.raw == best.raw } ?: return
    executeMove(verified)
  }

  fun undoMove() {
    if (_uiState.value.isEditMode) return
    val currentHistory = _uiState.value.moveHistory
    if (currentHistory.isEmpty()) return

    engine.cancelRequested = true
    analysisJob?.cancel()

    val targetSize = (currentHistory.size - 1).coerceAtLeast(0)
    val restoredFen = currentHistory[targetSize].fenBefore
    val remainingHistory = currentHistory.subList(0, targetSize)

    board.loadFen(restoredFen)
    syncBoardStateAndAnalyzeIfPlayersTurn(
      history = remainingHistory,
      resetEval = remainingHistory.isEmpty(),
    )
  }

  fun resetGame() {
    engine.cancelRequested = true
    analysisJob?.cancel()
    engine.clearCache()
    board.resetToStartPos()
    _uiState.update {
      it.copy(
        isEditMode = false,
        editSelectedSquare = null,
        editValidationError = null,
        editInlineTip = null,
      )
    }
    syncBoardStateAndAnalyzeIfPlayersTurn(history = emptyList(), resetEval = true)
  }

  fun buildPgnText(): String {
    val moves = _uiState.value.moveHistory
    if (moves.isEmpty()) return ""
    val sb = StringBuilder()
    var i = 0
    while (i < moves.size) {
      val rec = moves[i]
      if (rec.side == Side.WHITE) {
        sb.append(rec.fullMoveNumber).append(". ").append(rec.move.san)
        if (i + 1 < moves.size && moves[i + 1].side == Side.BLACK) {
          sb.append(' ').append(moves[i + 1].move.san).append("  ")
          i += 2
        } else {
          sb.append(' ')
          i++
        }
      } else {
        sb.append(rec.fullMoveNumber).append("... ").append(rec.move.san).append("  ")
        i++
      }
    }
    return sb.toString().trim()
  }

  private fun executeMove(chessMove: ChessMove) {
    engine.cancelRequested = true
    analysisJob?.cancel()

    val record = MoveRecord(
      plyIndex = _uiState.value.moveHistory.size,
      fullMoveNumber = board.fullMoveNumber,
      side = Side.fromSign(board.sideToMove),
      move = chessMove,
      fenBefore = board.toFen(),
      hashBefore = board.zobristKey,
    )
    board.makeMove(chessMove.raw)
    val updatedHistory = _uiState.value.moveHistory + record
    syncBoardStateAndAnalyzeIfPlayersTurn(history = updatedHistory, resetEval = false)
  }

  private fun syncBoardStateAndAnalyzeIfPlayersTurn(
    history: List<MoveRecord>,
    resetEval: Boolean,
  ) {
    val rawBuf = IntArray(256)
    val legalCount = MoveGenerator.generateLegalMoves(board, rawBuf)
    allLegalMovesCache.clear()
    for (i in 0 until legalCount) {
      allLegalMovesCache.add(MoveGenerator.toChessMove(board, rawBuf[i], rawBuf, legalCount))
    }

    val inCheck = board.isInCheck()
    val checkedSq = if (inCheck) {
      if (board.sideToMove == 1) board.whiteKingSq else board.blackKingSq
    } else null

    val currentHash = board.zobristKey
    val priorSameHashCount = history.count { it.hashBefore == currentHash }

    val status = when {
      legalCount == 0 && inCheck -> {
        if (board.sideToMove == 1) GameStatus.CHECKMATE_BLACK_WINS else GameStatus.CHECKMATE_WHITE_WINS
      }
      legalCount == 0 -> GameStatus.STALEMATE
      priorSameHashCount >= 2 -> GameStatus.DRAW_REPETITION
      board.halfMoveClock >= 100 -> GameStatus.DRAW_FIFTY_MOVE
      board.isInsufficientMaterial() -> GameStatus.DRAW_INSUFFICIENT_MATERIAL
      else -> GameStatus.ONGOING
    }

    val lastRec = history.lastOrNull()
    _uiState.update {
      it.copy(
        squares = board.squares.toList(),
        sideToMove = Side.fromSign(board.sideToMove),
        selectedSquare = null,
        legalMovesForSelected = emptyList(),
        pendingPromotionOptions = null,
        lastMoveFrom = lastRec?.move?.from,
        lastMoveTo = lastRec?.move?.to,
        checkedKingSquare = checkedSq,
        gameStatus = status,
        moveHistory = history,
        // Clear previous move recommendation immediately so opponent NEVER gets a move suggestion
        analysis = null,
        lastEvalCentipawnsWhite = if (resetEval) 0 else it.lastEvalCentipawnsWhite,
        lastMateInMovesWhite = if (resetEval) null else it.lastMateInMovesWhite,
        isAnalyzing = false,
      )
    }

    if (!status.isGameOver) {
      analyzeOnlyForPlayerTurn()
    }
  }

  /**
   * Runs the Grandmaster Engine EXCLUSIVELY when it is the user's turn (sideToMove == playerColor).
   * When it is the opponent's turn, no recommendation or hint is ever generated or shown.
   */
  private fun analyzeOnlyForPlayerTurn() {
    engine.cancelRequested = true
    analysisJob?.cancel()

    val state = _uiState.value
    val activeSide = Side.fromSign(board.sideToMove)
    if (state.gameStatus.isGameOver || activeSide != state.playerColor) {
      _uiState.update {
        it.copy(
          analysis = null,
          isAnalyzing = false,
        )
      }
      return
    }

    val boardSnapshot = board.copy()
    val hashes = LongArray(state.moveHistory.size + 1)
    for (i in state.moveHistory.indices) {
      hashes[i] = state.moveHistory[i].hashBefore
    }
    hashes[state.moveHistory.size] = boardSnapshot.zobristKey

    // Track player's own previous move (2 plies ago) to prevent sterile back-and-forth shuffling
    val prevOwnRec = state.moveHistory.lastOrNull { it.side == state.playerColor }
    val prevFrom = prevOwnRec?.move?.from ?: -1
    val prevTo = prevOwnRec?.move?.to ?: -1
    val budget = state.analysisSpeed.budgetMs

    _uiState.update {
      it.copy(
        analysis = null,
        isAnalyzing = true,
      )
    }

    analysisJob = viewModelScope.launch {
      val result = withContext(Dispatchers.Default) {
        engine.analyze(
          sourceBoard = boardSnapshot,
          gameHashes = hashes,
          lastOwnMoveFrom = prevFrom,
          lastOwnMoveTo = prevTo,
          timeBudgetMs = budget,
          useOpeningBook = true,
        )
      }

      if (engine.cancelRequested) return@launch

      // Double-check it is still the exact same board position and still the user's turn
      if (board.zobristKey == boardSnapshot.zobristKey &&
        Side.fromSign(board.sideToMove) == _uiState.value.playerColor
      ) {
        _uiState.update {
          it.copy(
            analysis = result,
            lastEvalCentipawnsWhite = result.evalCentipawnsWhite,
            lastMateInMovesWhite = result.mateInMovesWhite,
            isAnalyzing = false,
          )
        }
      }
    }
  }
}
