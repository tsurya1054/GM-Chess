package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.chess.Side
import com.example.ui.components.ActionControlBar
import com.example.ui.components.AnalysisBanner
import com.example.ui.components.BoardEditorPanel
import com.example.ui.components.ChessBoardView
import com.example.ui.components.EvaluationBar
import com.example.ui.components.MoveLogPanel
import com.example.ui.components.PromotionPickerDialog
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Obsidian900
import com.example.ui.theme.Obsidian950
import kotlinx.coroutines.launch

data class ChessLayoutMetrics(
  val useSideBySide: Boolean,
  val boardSize: androidx.compose.ui.unit.Dp,
  val columnWidth: androidx.compose.ui.unit.Dp,
  val hPad: androidx.compose.ui.unit.Dp,
  val vPad: androidx.compose.ui.unit.Dp,
  val gap: androidx.compose.ui.unit.Dp,
  val needsScroll: Boolean,
)

fun computeChessLayoutMetrics(
  availW: androidx.compose.ui.unit.Dp,
  availH: androidx.compose.ui.unit.Dp,
): ChessLayoutMetrics {
  val useSideBySide = (availW > availH && availW >= 520.dp) || availW >= 840.dp
  return if (useSideBySide) {
    val outerHPad = if (availW >= 900.dp) 24.dp else 12.dp
    val outerVPad = if (availH >= 560.dp) 14.dp else 8.dp
    val paneGap = if (availW >= 900.dp) 24.dp else 14.dp
    val innerW = (availW - outerHPad * 2).coerceAtMost(1180.dp)
    val innerH = availH - outerVPad * 2

    val maxBoardW = (innerW - paneGap) * 0.50f
    val maxBoardH = (innerH - 28.dp).coerceAtLeast(180.dp)
    val boardSize = minOf(maxBoardW, maxBoardH, 620.dp)

    ChessLayoutMetrics(
      useSideBySide = true,
      boardSize = boardSize,
      columnWidth = innerW,
      hPad = outerHPad,
      vPad = outerVPad,
      gap = paneGap,
      needsScroll = innerH < 310.dp,
    )
  } else {
    val hPad = if (availW >= 600.dp) 24.dp else 12.dp
    val vPad = if (availH >= 700.dp) 8.dp else 4.dp
    val gap = if (availH >= 700.dp) 8.dp else 5.dp

    // Non-board UI height: Header(56) + Eval(20) + Analysis(104) + Actions(48) + MinLog(52) + 5*gap + 2*vPad
    val reservedControlsH = 280.dp + gap * 5 + vPad * 2
    val maxBoardByWidth = (availW - hPad * 2).coerceAtMost(560.dp)
    val maxBoardByHeight = (availH - reservedControlsH).coerceAtLeast(200.dp)
    val boardSize = minOf(maxBoardByWidth, maxBoardByHeight)
    val columnWidth = maxOf(boardSize, minOf(maxBoardByWidth, 340.dp))

    ChessLayoutMetrics(
      useSideBySide = false,
      boardSize = boardSize,
      columnWidth = columnWidth,
      hPad = hPad,
      vPad = vPad,
      gap = gap,
      needsScroll = availH < (reservedControlsH + 200.dp),
    )
  }
}

fun computeEditLayoutMetrics(
  availW: androidx.compose.ui.unit.Dp,
  availH: androidx.compose.ui.unit.Dp,
): ChessLayoutMetrics {
  val useSideBySide = (availW > availH && availW >= 520.dp) || availW >= 840.dp
  return if (useSideBySide) {
    val outerHPad = if (availW >= 900.dp) 24.dp else 12.dp
    val outerVPad = if (availH >= 560.dp) 14.dp else 8.dp
    val paneGap = if (availW >= 900.dp) 24.dp else 14.dp
    val innerW = (availW - outerHPad * 2).coerceAtMost(1180.dp)
    val innerH = availH - outerVPad * 2

    val maxBoardW = (innerW - paneGap) * 0.50f
    val maxBoardH = (innerH - 8.dp).coerceAtLeast(180.dp)
    val boardSize = minOf(maxBoardW, maxBoardH, 620.dp)

    ChessLayoutMetrics(
      useSideBySide = true,
      boardSize = boardSize,
      columnWidth = innerW,
      hPad = outerHPad,
      vPad = outerVPad,
      gap = paneGap,
      needsScroll = innerH < 360.dp,
    )
  } else {
    val hPad = if (availW >= 600.dp) 24.dp else 12.dp
    val vPad = if (availH >= 700.dp) 8.dp else 4.dp
    val gap = if (availH >= 700.dp) 8.dp else 5.dp

    // Header(56) + BoardEditorPanel(~272) + 2*gap + 2*vPad
    val reservedEditorH = 328.dp + gap * 2 + vPad * 2
    val maxBoardByWidth = (availW - hPad * 2).coerceAtMost(560.dp)
    val maxBoardByHeight = (availH - reservedEditorH).coerceAtLeast(210.dp)
    val boardSize = minOf(maxBoardByWidth, maxBoardByHeight)
    val columnWidth = maxOf(boardSize, minOf(maxBoardByWidth, 340.dp))

    ChessLayoutMetrics(
      useSideBySide = false,
      boardSize = boardSize,
      columnWidth = columnWidth,
      hPad = hPad,
      vPad = vPad,
      gap = gap,
      needsScroll = availH < (reservedEditorH + 210.dp),
    )
  }
}

@Composable
fun ChessScreen(
  viewModel: ChessViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val copiedMsg = stringResource(R.string.pgn_copied)
  val editAppliedMsg = stringResource(R.string.edit_applied_toast)

  var showResetDialog by remember { mutableStateOf(false) }

  BackHandler(enabled = uiState.isEditMode) {
    viewModel.cancelEditMode()
  }

  val legalTargetSet by remember(uiState.legalMovesForSelected) {
    derivedStateOf { uiState.legalMovesForSelected.map { it.to }.toSet() }
  }

  val playerOnlyArrowMove = remember(uiState.analysis, uiState.sideToMove, uiState.playerColor, uiState.gameStatus) {
    if (!uiState.gameStatus.isGameOver && uiState.sideToMove == uiState.playerColor) {
      uiState.analysis?.takeIf { it.analyzedForSide == uiState.playerColor }?.bestMove
    } else {
      null
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      val metrics = if (uiState.isEditMode) {
        computeEditLayoutMetrics(maxWidth, maxHeight)
      } else {
        computeChessLayoutMetrics(maxWidth, maxHeight)
      }

      val displayedSquares = if (uiState.isEditMode) uiState.editSquares else uiState.squares
      val displayedSelected = if (uiState.isEditMode) uiState.editSelectedSquare else uiState.selectedSquare
      val displayedLegalTargets = if (uiState.isEditMode) emptySet() else legalTargetSet
      val displayedLastFrom = if (uiState.isEditMode) null else uiState.lastMoveFrom
      val displayedLastTo = if (uiState.isEditMode) null else uiState.lastMoveTo
      val displayedCheckedKing = if (uiState.isEditMode) null else uiState.checkedKingSquare
      val displayedArrow = if (uiState.isEditMode) null else playerOnlyArrowMove

      if (metrics.useSideBySide) {
        Row(
          modifier = Modifier
            .width(metrics.columnWidth + metrics.hPad * 2)
            .fillMaxHeight()
            .padding(horizontal = metrics.hPad, vertical = metrics.vPad),
          horizontalArrangement = Arrangement.spacedBy(metrics.gap, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.width(metrics.boardSize),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            if (!uiState.isEditMode) {
              EvaluationBar(
                evalCentipawnsWhite = uiState.lastEvalCentipawnsWhite,
                mateInMovesWhite = uiState.lastMateInMovesWhite,
                playerColor = uiState.playerColor,
                modifier = Modifier.width(metrics.boardSize),
              )
              Spacer(modifier = Modifier.height(6.dp))
            }
            ChessBoardView(
              squares = displayedSquares,
              isFlipped = uiState.isBoardFlipped,
              selectedSquare = displayedSelected,
              legalTargets = displayedLegalTargets,
              lastMoveFrom = displayedLastFrom,
              lastMoveTo = displayedLastTo,
              checkedKingSquare = displayedCheckedKing,
              recommendedMove = displayedArrow,
              onSquareClick = viewModel::onSquareClicked,
              modifier = Modifier.size(metrics.boardSize),
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .widthIn(max = 520.dp)
              .fillMaxHeight()
              .then(if (metrics.needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GmHeaderBar(
              playerColor = uiState.playerColor,
              openingName = if (uiState.isEditMode) stringResource(R.string.edit_mode_badge) else uiState.analysis?.openingName,
              onSelectColor = viewModel::setPlayerColor,
            )

            if (uiState.isEditMode) {
              BoardEditorPanel(
                sideToMove = uiState.editSideToMove,
                selectedTool = uiState.selectedEditorTool,
                validationError = uiState.editValidationError,
                inlineTip = uiState.editInlineTip,
                onSelectTool = viewModel::selectEditorTool,
                onSelectSideToMove = viewModel::setEditSideToMove,
                onSetStandard = viewModel::setEditToStartPos,
                onClearBoard = viewModel::clearEditBoard,
                onFlipBoard = viewModel::flipBoard,
                onCancel = viewModel::cancelEditMode,
                onApply = {
                  if (viewModel.applyEditBoard()) {
                    scope.launch { snackbarHostState.showSnackbar(editAppliedMsg) }
                  }
                },
              )
            } else {
              AnalysisBanner(
                gameStatus = uiState.gameStatus,
                sideToMove = uiState.sideToMove,
                playerColor = uiState.playerColor,
                analysis = uiState.analysis,
                isAnalyzing = uiState.isAnalyzing,
                onPlayBestMove = viewModel::playRecommendedMove,
              )

              ActionControlBar(
                canUndo = uiState.moveHistory.isNotEmpty(),
                analysisSpeed = uiState.analysisSpeed,
                onUndo = viewModel::undoMove,
                onFlipBoard = viewModel::flipBoard,
                onEditBoard = viewModel::enterEditMode,
                onToggleSpeed = viewModel::toggleAnalysisSpeed,
                onResetRequest = {
                  if (uiState.moveHistory.isEmpty()) viewModel.resetGame()
                  else showResetDialog = true
                },
              )

              MoveLogPanel(
                moveHistory = uiState.moveHistory,
                onCopyPgn = {
                  copyToClipboard(context, viewModel.buildPgnText())
                  scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                },
                modifier = if (metrics.needsScroll) {
                  Modifier.height(90.dp)
                } else {
                  Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                },
              )
            }
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .then(if (metrics.needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = metrics.hPad, vertical = metrics.vPad),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(metrics.gap),
        ) {
          GmHeaderBar(
            playerColor = uiState.playerColor,
            openingName = if (uiState.isEditMode) stringResource(R.string.edit_mode_badge) else uiState.analysis?.openingName,
            onSelectColor = viewModel::setPlayerColor,
            modifier = Modifier.width(metrics.columnWidth),
          )

          if (!uiState.isEditMode) {
            EvaluationBar(
              evalCentipawnsWhite = uiState.lastEvalCentipawnsWhite,
              mateInMovesWhite = uiState.lastMateInMovesWhite,
              playerColor = uiState.playerColor,
              modifier = Modifier.width(metrics.columnWidth),
            )

            AnalysisBanner(
              gameStatus = uiState.gameStatus,
              sideToMove = uiState.sideToMove,
              playerColor = uiState.playerColor,
              analysis = uiState.analysis,
              isAnalyzing = uiState.isAnalyzing,
              onPlayBestMove = viewModel::playRecommendedMove,
              modifier = Modifier.width(metrics.columnWidth),
            )
          }

          ChessBoardView(
            squares = displayedSquares,
            isFlipped = uiState.isBoardFlipped,
            selectedSquare = displayedSelected,
            legalTargets = displayedLegalTargets,
            lastMoveFrom = displayedLastFrom,
            lastMoveTo = displayedLastTo,
            checkedKingSquare = displayedCheckedKing,
            recommendedMove = displayedArrow,
            onSquareClick = viewModel::onSquareClicked,
            modifier = Modifier.size(metrics.boardSize),
          )

          if (uiState.isEditMode) {
            BoardEditorPanel(
              sideToMove = uiState.editSideToMove,
              selectedTool = uiState.selectedEditorTool,
              validationError = uiState.editValidationError,
              inlineTip = uiState.editInlineTip,
              onSelectTool = viewModel::selectEditorTool,
              onSelectSideToMove = viewModel::setEditSideToMove,
              onSetStandard = viewModel::setEditToStartPos,
              onClearBoard = viewModel::clearEditBoard,
              onFlipBoard = viewModel::flipBoard,
              onCancel = viewModel::cancelEditMode,
              onApply = {
                if (viewModel.applyEditBoard()) {
                  scope.launch { snackbarHostState.showSnackbar(editAppliedMsg) }
                }
              },
              modifier = Modifier.width(metrics.columnWidth),
            )
          } else {
            ActionControlBar(
              canUndo = uiState.moveHistory.isNotEmpty(),
              analysisSpeed = uiState.analysisSpeed,
              onUndo = viewModel::undoMove,
              onFlipBoard = viewModel::flipBoard,
              onEditBoard = viewModel::enterEditMode,
              onToggleSpeed = viewModel::toggleAnalysisSpeed,
              onResetRequest = {
                if (uiState.moveHistory.isEmpty()) viewModel.resetGame()
                else showResetDialog = true
              },
              modifier = Modifier.width(metrics.columnWidth),
            )

            MoveLogPanel(
              moveHistory = uiState.moveHistory,
              onCopyPgn = {
                copyToClipboard(context, viewModel.buildPgnText())
                scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
              },
              modifier = if (metrics.needsScroll) {
                Modifier
                  .width(metrics.columnWidth)
                  .height(80.dp)
              } else {
                Modifier
                  .width(metrics.columnWidth)
                  .weight(1f)
                  .heightIn(min = 52.dp)
              },
            )
          }
        }
      }
    }
  }

  if (uiState.pendingPromotionOptions != null) {
    PromotionPickerDialog(
      side = uiState.sideToMove,
      onSelect = viewModel::onPromotionSelected,
      onDismiss = viewModel::dismissPromotion,
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = {
        Text(
          text = stringResource(R.string.reset_confirm_title),
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.reset_confirm_desc),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showResetDialog = false
            viewModel.resetGame()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_reset_button"),
        ) {
          Text(stringResource(R.string.confirm_yes))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showResetDialog = false },
          modifier = Modifier.testTag("cancel_reset_button"),
        ) {
          Text(stringResource(R.string.confirm_cancel))
        }
      },
    )
  }
}

/**
 * Compact single-row Grandmaster Header Bar (56.dp) combining App Identity + Putih/Hitam Selector.
 * Uses proper weight(1f) on the title column so long opening names truncate gracefully and NEVER push buttons!
 */
@Composable
private fun GmHeaderBar(
  playerColor: Side,
  openingName: String?,
  onSelectColor: (Side) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp),
    shape = MaterialTheme.shapes.medium,
    color = Obsidian900,
    shadowElevation = 3.dp,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(id = R.drawable.img_gm_header_bg),
        contentDescription = stringResource(R.string.cd_header_bg),
        contentScale = ContentScale.Crop,
        alpha = 0.32f,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.horizontalGradient(
              colors = listOf(
                Obsidian950.copy(alpha = 0.92f),
                Obsidian900.copy(alpha = 0.78f),
              )
            )
          )
      )

      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // Left: Weighted Title & Subtitle (never overflows into buttons!)
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              imageVector = Icons.Filled.OfflineBolt,
              contentDescription = null,
              tint = Emerald500,
              modifier = Modifier.size(15.dp),
            )
          }
          Text(
            text = openingName ?: stringResource(R.string.subtitle_offline_gm),
            style = MaterialTheme.typography.labelSmall,
            color = if (openingName != null) Amber400 else Color(0xFFCBD5E1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Right: Compact Segmented Putih / Hitam Selector with 48dp touch height
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B))
            .padding(2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          ColorSegmentButton(
            label = stringResource(R.string.color_white),
            isWhiteChip = true,
            selected = playerColor == Side.WHITE,
            onClick = { onSelectColor(Side.WHITE) },
            tag = "color_white_button",
          )
          ColorSegmentButton(
            label = stringResource(R.string.color_black),
            isWhiteChip = false,
            selected = playerColor == Side.BLACK,
            onClick = { onSelectColor(Side.BLACK) },
            tag = "color_black_button",
          )
        }
      }
    }
  }
}

@Composable
private fun ColorSegmentButton(
  label: String,
  isWhiteChip: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String,
) {
  val bgColor = if (selected) Emerald500.copy(alpha = 0.24f) else Color.Transparent
  val borderColor = if (selected) Emerald500 else Color.Transparent

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier
      .height(48.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(bgColor)
      .border(width = if (selected) 1.5.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 10.dp)
      .testTag(tag),
  ) {
    Box(
      modifier = Modifier
        .size(12.dp)
        .clip(CircleShape)
        .background(if (isWhiteChip) Color(0xFFFFFDF7) else Color(0xFF0F172A))
        .border(1.5.dp, if (isWhiteChip) Color(0xFF475569) else Color(0xFFF8FAFC), CircleShape),
    )
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) Color.White else Color(0xFFCBD5E1),
      maxLines = 1,
    )
  }
}

private fun copyToClipboard(context: Context, text: String) {
  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  cm.setPrimaryClip(ClipData.newPlainText("GM Catur PGN", text))
}
