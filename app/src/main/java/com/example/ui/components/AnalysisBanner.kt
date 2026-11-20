package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.chess.AnalysisResult
import com.example.chess.GameStatus
import com.example.chess.Side
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Obsidian900
import kotlin.math.abs
import kotlin.math.exp

@Composable
fun EvaluationBar(
  evalCentipawnsWhite: Int,
  mateInMovesWhite: Int?,
  playerColor: Side,
  modifier: Modifier = Modifier,
) {
  val rawWhiteFraction = when {
    mateInMovesWhite != null -> if (mateInMovesWhite >= 0) 0.98f else 0.02f
    else -> (1.0 / (1.0 + exp(-evalCentipawnsWhite / 320.0))).toFloat().coerceIn(0.05f, 0.95f)
  }

  val animatedWhiteFraction by animateFloatAsState(
    targetValue = rawWhiteFraction,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "eval_bar_fraction",
  )

  val evalText = formatEvalScore(evalCentipawnsWhite, mateInMovesWhite, playerColor)
  val playerAdvantage = if (playerColor == Side.WHITE) evalCentipawnsWhite else -evalCentipawnsWhite

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .height(20.dp)
      .testTag("evaluation_bar"),
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(Obsidian900)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(animatedWhiteFraction)
          .background(Color(0xFFF8FAFC)),
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Surface(
      shape = RoundedCornerShape(6.dp),
      color = when {
        playerAdvantage >= 60 -> Emerald500.copy(alpha = 0.18f)
        playerAdvantage <= -60 -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant
      },
    ) {
      Text(
        text = evalText,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        color = when {
          playerAdvantage >= 60 -> MaterialTheme.colorScheme.primary
          playerAdvantage <= -60 -> MaterialTheme.colorScheme.error
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
      )
    }
  }
}

/**
 * Fixed-height (102.dp) Grandmaster Suggestion Card so the chessboard NEVER shifts or jumps
 * when switching between Player Turn, Calculating State, and Manual Enemy Turn.
 */
@Composable
fun AnalysisBanner(
  gameStatus: GameStatus,
  sideToMove: Side,
  playerColor: Side,
  analysis: AnalysisResult?,
  isAnalyzing: Boolean,
  onPlayBestMove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isPlayersTurn = !gameStatus.isGameOver && sideToMove == playerColor

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(104.dp)
      .testTag("analysis_banner"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 2.dp,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      if (gameStatus.isGameOver) {
        val statusTitle = when (gameStatus) {
          GameStatus.CHECKMATE_WHITE_WINS -> stringResource(R.string.status_checkmate_white)
          GameStatus.CHECKMATE_BLACK_WINS -> stringResource(R.string.status_checkmate_black)
          GameStatus.STALEMATE -> stringResource(R.string.status_stalemate)
          GameStatus.DRAW_REPETITION -> stringResource(R.string.status_draw_repetition)
          GameStatus.DRAW_FIFTY_MOVE -> stringResource(R.string.status_draw_fifty)
          GameStatus.DRAW_INSUFFICIENT_MATERIAL -> stringResource(R.string.status_draw_material)
          GameStatus.ONGOING -> ""
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = Amber400,
            modifier = Modifier.size(28.dp),
          )
          Text(
            text = statusTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else if (!isPlayersTurn) {
        // Opponent's Turn: Calm, stable prompt for manual enemy move placement
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.TouchApp,
              contentDescription = null,
              tint = Amber400,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (sideToMove == Side.WHITE) {
                stringResource(R.string.turn_enemy_white)
              } else {
                stringResource(R.string.turn_enemy_black)
              },
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = stringResource(R.string.turn_opponent_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else {
        // Player's Turn: Show #1 Best Move + 1-Tap Play + Scrollable Candidates without wrapping
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          // Header Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = if (playerColor == Side.WHITE) {
                  stringResource(R.string.best_move_for_you_white)
                } else {
                  stringResource(R.string.best_move_for_you_black)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            if (isAnalyzing) {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            } else if (analysis != null) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "D${analysis.depthReached} • ${analysis.elapsedMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }

          val bestMove = analysis?.takeIf { it.analyzedForSide == playerColor }?.bestMove
          if (bestMove != null && analysis != null) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(
                modifier = Modifier.weight(1f),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = bestMove.readableIndo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                  )
                  Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                  ) {
                    Text(
                      text = bestMove.san,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                      maxLines = 1,
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                  }
                }
                Text(
                  text = analysis.tacticalSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Spacer(modifier = Modifier.width(8.dp))

              Button(
                onClick = onPlayBestMove,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                  .height(48.dp)
                  .testTag("play_best_move_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.CheckCircle,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = stringResource(R.string.btn_play_best),
                  style = MaterialTheme.typography.labelLarge,
                  maxLines = 1,
                )
              }
            }

            // Horizontally scrollable candidate row so narrow phones never wrap or clip
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              analysis.candidates.take(3).forEachIndexed { idx, cand ->
                val scoreStr = formatEvalScore(
                  cand.evalCentipawnsWhite,
                  cand.mateInMovesWhite,
                  playerColor,
                )
                Surface(
                  shape = RoundedCornerShape(5.dp),
                  color = if (idx == 0) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                  } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                  },
                ) {
                  Text(
                    text = "${idx + 1}. ${cand.move.san} ($scoreStr)",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = if (idx == 0) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                  )
                }
              }
            }
          } else {
            // Calculating state placeholder preserving exact vertical rhythm
            Text(
              text = stringResource(R.string.analyzing_position),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              modifier = Modifier.padding(vertical = 12.dp),
            )
          }
        }
      }
    }
  }
}

private fun formatEvalScore(
  evalCentipawnsWhite: Int,
  mateInMovesWhite: Int?,
  perspective: Side,
): String {
  if (mateInMovesWhite != null) {
    val perspMate = if (perspective == Side.WHITE) mateInMovesWhite else -mateInMovesWhite
    return if (perspMate == 0) "#"
    else if (perspMate > 0) "+M$perspMate"
    else "-M${abs(perspMate)}"
  }
  val cp = if (perspective == Side.WHITE) evalCentipawnsWhite else -evalCentipawnsWhite
  val pawns = cp / 100.0
  return if (cp >= 0) "+%.1f".format(pawns) else "%.1f".format(pawns)
}
