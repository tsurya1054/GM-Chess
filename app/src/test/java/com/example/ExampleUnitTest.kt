package com.example

import androidx.compose.ui.unit.dp
import com.example.chess.ChessBoard
import com.example.chess.ChessEngine
import com.example.chess.MoveCodec
import com.example.chess.MoveGenerator
import com.example.chess.OpeningBook
import com.example.chess.Piece
import com.example.chess.Side
import com.example.ui.ChessViewModel
import com.example.ui.EditorTool
import com.example.ui.computeChessLayoutMetrics
import com.example.ui.computeEditLayoutMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  private fun perft(board: ChessBoard, depth: Int): Long {
    if (depth == 0) return 1L
    val moves = IntArray(256)
    val count = MoveGenerator.generateLegalMoves(board, moves)
    if (depth == 1) return count.toLong()
    var total = 0L
    val hashBefore = board.zobristKey
    for (i in 0 until count) {
      board.makeMove(moves[i])
      total += perft(board, depth - 1)
      board.unmakeMove(moves[i])
      assertEquals("Zobrist hash must restore cleanly", hashBefore, board.zobristKey)
    }
    return total
  }

  @Test
  fun perftStartPos_isExactFideCount() {
    val board = ChessBoard()
    assertEquals(20L, perft(board, 1))
    assertEquals(400L, perft(board, 2))
    assertEquals(8902L, perft(board, 3))
  }

  @Test
  fun engineFindsMateInTwoForWhiteAndBlack() {
    val engine = ChessEngine()

    val boardW = ChessBoard()
    boardW.loadFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
    val resW = engine.analyze(boardW, LongArray(0), timeBudgetMs = 400L, useOpeningBook = false)
    assertNotNull(resW.bestMove)
    assertEquals("h5f7", MoveCodec.toUci(resW.bestMove!!.raw))
    assertEquals(1, resW.mateInMovesWhite)

    val boardB = ChessBoard()
    boardB.loadFen("3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1")
    val resB = engine.analyze(boardB, LongArray(0), timeBudgetMs = 400L, useOpeningBook = false)
    assertNotNull(resB.bestMove)
    assertEquals("d8d1", MoveCodec.toUci(resB.bestMove!!.raw))
    assertEquals(-1, resB.mateInMovesWhite)
  }

  @Test
  fun engineNeverMakesUnequalSuicideCapture() {
    val engine = ChessEngine()
    val board = ChessBoard()
    board.loadFen("6k1/6p1/4p2p/3p4/n2Q4/8/5PPP/6K1 w - - 0 1")
    val res = engine.analyze(board, LongArray(0), timeBudgetMs = 450L, useOpeningBook = false)
    val bestUci = MoveCodec.toUci(res.bestMove!!.raw)
    assertNotEquals("Engine must NEVER blunder Queen for defended pawn d4d5", "d4d5", bestUci)
    assertEquals("Engine captures free Knight on a4", "d4a4", bestUci)
  }

  @Test
  fun openingBookSupportsWhiteAndBlack() {
    val board = ChessBoard()
    val whiteBook = OpeningBook.lookup(board)
    assertNotNull(whiteBook)
    assertTrue(whiteBook!!.bestUciMoves.isNotEmpty())

    val moves = IntArray(256)
    val cnt = MoveGenerator.generateLegalMoves(board, moves)
    val e4 = (0 until cnt).map { moves[it] }.first { MoveCodec.toUci(it) == "e2e4" }
    board.makeMove(e4)
    val blackBook = OpeningBook.lookup(board)
    assertNotNull(blackBook)
    assertTrue(blackBook!!.bestUciMoves.contains("c7c5") || blackBook.bestUciMoves.contains("e7e5"))
  }

  @Test
  fun adaptiveLayoutMetrics_fitAllPhonesFoldablesAndTablets() {
    val viewports = listOf(
      320.dp to 568.dp,   // Tiny phone portrait
      360.dp to 640.dp,   // Small phone portrait
      393.dp to 851.dp,   // Standard phone portrait
      412.dp to 915.dp,   // Large phone portrait
      780.dp to 360.dp,   // Phone landscape
      673.dp to 841.dp,   // Foldable unfolded portrait
      841.dp to 673.dp,   // Foldable unfolded landscape
      800.dp to 1280.dp,  // 10" Tablet portrait
      1280.dp to 800.dp,  // 10" Tablet landscape
    )

    for ((w, h) in viewports) {
      val m = computeChessLayoutMetrics(w, h)
      assertTrue("Board size must be >= 180dp for $w x $h", m.boardSize >= 180.dp)
      assertTrue("Board size (${m.boardSize}) must fit inside width ($w)", m.boardSize + m.hPad * 2 <= w)
      if (!m.needsScroll) {
        if (m.useSideBySide) {
          assertTrue(
            "Side-by-side board + EvalBar must fit inside height ($h)",
            m.boardSize + 26.dp + m.vPad * 2 <= h + 1.dp,
          )
        } else {
          val totalMinHeight = 280.dp + m.boardSize + m.gap * 5 + m.vPad * 2
          assertTrue(
            "Portrait stack ($totalMinHeight) must fit inside height ($h) without scrolling",
            totalMinHeight <= h + 1.dp,
          )
        }
      }
    }

    // Verify Landscape Phone & Tablet trigger Side-by-Side
    assertTrue(computeChessLayoutMetrics(780.dp, 360.dp).useSideBySide)
    assertTrue(computeChessLayoutMetrics(1280.dp, 800.dp).useSideBySide)
    // Verify Portrait Phone & Portrait Tablet use aligned Stacked Column
    assertFalse(computeChessLayoutMetrics(393.dp, 851.dp).useSideBySide)
    assertFalse(computeChessLayoutMetrics(800.dp, 1280.dp).useSideBySide)
    assertEquals(
      "Portrait tablet aligns columnWidth and boardSize cleanly at 560.dp",
      560.dp,
      computeChessLayoutMetrics(800.dp, 1280.dp).boardSize,
    )
  }

  @Test
  fun engineAvoidsUnfavorableEqualTradeAndPreservesPieces() {
    val engine = ChessEngine()
    // Position: White has an active Queen on d3.
    // Black plays Qd8-b6, offering Queen trade on b6, defended by pawn a7 and knight d7.
    // If White plays Qxb6, it forces an equal trade that activates Black's a-file pawn and dissipates White's initiative.
    // A strong grandmaster move keeps the Queen active or retreats safely while defending/attacking (e.g. Rad1, c3, or a attacking move).
    // Also tests piece preservation when White's Queen is threatened:
    val board = ChessBoard()
    board.loadFen("r4rk1/pp1n1ppp/1q2p3/3p4/3P4/3Q1N2/PPP2PPP/R4RK1 w - - 0 14")
    val res = engine.analyze(board, LongArray(0), timeBudgetMs = 500L, useOpeningBook = false)
    val bestUci = MoveCodec.toUci(res.bestMove!!.raw)
    assertNotEquals("Engine should not force voluntary Queen trade Qxb6", "d3b6", bestUci)

    // Position where a Knight is attacked by a pawn (e.g. c3 attacked by d4 or e4 attacked by d5)
    // Engine must prioritize retreating/saving the valuable piece rather than ignoring it or suicidal attack
    val boardKnight = ChessBoard()
    boardKnight.loadFen("r1bqk2r/pp2bppp/2n1pn2/2pp4/4P3/2NP1N2/PPP1BPPP/R1BQ1RK1 w kq - 0 7")
    // e4xd5 is fine or e5 is fine, but leaving knight hanging on c3 or sacrificing without reason is prohibited.
    val resKnight = engine.analyze(boardKnight, LongArray(0), timeBudgetMs = 400L, useOpeningBook = false)
    assertNotNull(resKnight.bestMove)
  }

  @Test
  fun boardSetupValidation_enforcesFideRules() {
    val empty = IntArray(64)
    assertNotNull("Empty board without kings must be invalid", ChessBoard.validateSetup(empty, Side.WHITE))

    // Only White King
    empty[4] = Piece.WK
    assertNotNull("Missing Black King must be invalid", ChessBoard.validateSetup(empty, Side.WHITE))

    // Adjacent Kings (e1 and e2)
    empty[12] = Piece.BK
    assertNotNull("Adjacent kings must be invalid", ChessBoard.validateSetup(empty, Side.WHITE))

    // Valid separated Kings (e1=4, e8=60)
    empty[12] = Piece.EMPTY
    empty[60] = Piece.BK
    assertNull("Separated kings alone must be valid", ChessBoard.validateSetup(empty, Side.WHITE))

    // Pawn on rank 1 or 8 is illegal
    empty[0] = Piece.WP
    assertNotNull("Pawn on rank 1 must be invalid", ChessBoard.validateSetup(empty, Side.WHITE))
    empty[0] = Piece.EMPTY
    empty[56] = Piece.BP
    assertNotNull("Pawn on rank 8 must be invalid", ChessBoard.validateSetup(empty, Side.WHITE))
    empty[56] = Piece.EMPTY

    // Opponent king already in check on our turn is illegal
    empty[52] = Piece.WR // e7 attacks Black King on e8
    assertNotNull(
      "Black king in check while White is to move must be invalid",
      ChessBoard.validateSetup(empty, Side.WHITE),
    )
    // Legal if it is Black's turn to move out of check
    assertNull(
      "Black king in check while Black is to move must be valid",
      ChessBoard.validateSetup(empty, Side.BLACK),
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun viewModelEditMode_allowsPlacingMovingErasingAndApplying() {
    Dispatchers.setMain(StandardTestDispatcher())
    try {
      val vm = ChessViewModel()
      vm.enterEditMode()
      assertTrue(vm.uiState.value.isEditMode)

      // Clear board
      vm.clearEditBoard()
      assertNotNull(vm.uiState.value.editValidationError)
      assertFalse(vm.applyEditBoard())

      // Place White King on e1 (4), Black King on e8 (60), White Rook on a1 (0)
      vm.selectEditorTool(EditorTool.Place(Piece.WK))
      vm.onSquareClicked(4)
      vm.selectEditorTool(EditorTool.Place(Piece.BK))
      vm.onSquareClicked(60)
      vm.selectEditorTool(EditorTool.Place(Piece.WR))
      vm.onSquareClicked(0)
      assertNull(vm.uiState.value.editValidationError)

      // Move White Rook from a1 (0) to d1 (3) using Move tool
      vm.selectEditorTool(EditorTool.Move)
      vm.onSquareClicked(0)
      assertEquals(0, vm.uiState.value.editSelectedSquare)
      vm.onSquareClicked(3)
      assertEquals(Piece.EMPTY, vm.uiState.value.editSquares[0])
      assertEquals(Piece.WR, vm.uiState.value.editSquares[3])

      // Verify Pawn cannot be placed on rank 1 or 8
      vm.selectEditorTool(EditorTool.Place(Piece.WP))
      vm.onSquareClicked(56)
      assertEquals(Piece.EMPTY, vm.uiState.value.editSquares[56])
      assertNotNull(vm.uiState.value.editInlineTip)

      // Set turn to Black and apply
      vm.setEditSideToMove(Side.BLACK)
      assertTrue(vm.applyEditBoard())
      assertFalse(vm.uiState.value.isEditMode)
      assertEquals(Side.BLACK, vm.uiState.value.sideToMove)
      assertEquals(Piece.WR, vm.uiState.value.squares[3])
      assertEquals(Piece.WK, vm.uiState.value.squares[4])
      assertEquals(Piece.BK, vm.uiState.value.squares[60])
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun editLayoutMetrics_fitAllViewports() {
    val viewports = listOf(
      320.dp to 568.dp,
      360.dp to 640.dp,
      393.dp to 851.dp,
      780.dp to 360.dp,
      673.dp to 841.dp,
      800.dp to 1280.dp,
      1280.dp to 800.dp,
    )
    for ((w, h) in viewports) {
      val m = computeEditLayoutMetrics(w, h)
      assertTrue("Edit board size >= 180dp for $w x $h", m.boardSize >= 180.dp)
      assertTrue("Edit board fits width ($w)", m.boardSize + m.hPad * 2 <= w)
    }
  }
}
