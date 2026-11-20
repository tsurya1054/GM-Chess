package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.chess.MoveCodec
import com.example.chess.MoveRecord
import com.example.chess.Side
import com.example.ui.AnalysisSpeed

data class MovePairRow(
  val moveNumber: Int,
  val whiteMove: MoveRecord?,
  val blackMove: MoveRecord?,
)

/**
 * Adaptive 4-button toolbar: switches automatically between icon-over-label (on narrow phones < 400.dp)
 * and icon-beside-label (on wide phones & tablets >= 400.dp), keeping >= 48.dp touch targets everywhere.
 */
@Composable
fun ActionControlBar(
  canUndo: Boolean,
  analysisSpeed: AnalysisSpeed,
  onUndo: () -> Unit,
  onFlipBoard: () -> Unit,
  onEditBoard: () -> Unit,
  onToggleSpeed: () -> Unit,
  onResetRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val compactButtons = maxWidth < 460.dp
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AdaptiveActionButton(
        icon = Icons.AutoMirrored.Filled.Undo,
        label = stringResource(R.string.btn_undo),
        enabled = canUndo,
        highlighted = canUndo,
        compact = compactButtons,
        onClick = onUndo,
        tag = "undo_button",
        modifier = Modifier.weight(1f),
      )

      AdaptiveActionButton(
        icon = Icons.Filled.SwapVert,
        label = stringResource(R.string.btn_flip),
        enabled = true,
        highlighted = false,
        compact = compactButtons,
        onClick = onFlipBoard,
        tag = "flip_board_button",
        modifier = Modifier.weight(1f),
      )

      AdaptiveActionButton(
        icon = Icons.Filled.Edit,
        label = stringResource(R.string.btn_edit_board),
        enabled = true,
        highlighted = false,
        compact = compactButtons,
        onClick = onEditBoard,
        tag = "edit_board_button",
        modifier = Modifier.weight(1f),
      )

      AdaptiveActionButton(
        icon = Icons.Filled.Speed,
        label = if (analysisSpeed == AnalysisSpeed.FAST) {
          stringResource(R.string.speed_fast)
        } else {
          stringResource(R.string.speed_deep)
        },
        enabled = true,
        highlighted = analysisSpeed == AnalysisSpeed.DEEP,
        compact = compactButtons,
        onClick = onToggleSpeed,
        tag = "speed_toggle_button",
        modifier = Modifier.weight(1f),
      )

      AdaptiveActionButton(
        icon = Icons.Filled.Refresh,
        label = stringResource(R.string.btn_reset),
        enabled = true,
        highlighted = false,
        compact = compactButtons,
        onClick = onResetRequest,
        tag = "reset_button",
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun AdaptiveActionButton(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  highlighted: Boolean,
  compact: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  val bgColor = if (highlighted) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surface
  }
  val contentColor = if (highlighted) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val borderColor = if (highlighted) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  } else {
    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .height(48.dp)
      .alpha(if (enabled) 1f else 0.45f)
      .clip(RoundedCornerShape(10.dp))
      .background(bgColor)
      .border(1.dp, borderColor, RoundedCornerShape(10.dp))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 4.dp)
      .testTag(tag),
  ) {
    if (compact) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = contentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = contentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Adaptive Move Log Panel:
 * - On tall portrait phones, tablets, and landscape side-panes (availableHeight >= 110.dp):
 *   Renders a full, independently scrollable two-column Chess Move Table!
 * - On short/compact screens (availableHeight < 110.dp):
 *   Renders a sleek horizontal auto-scrolling Move Ribbon + 1-tap full-log modal dialog!
 */
@Composable
fun MoveLogPanel(
  moveHistory: List<MoveRecord>,
  onCopyPgn: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showFullModal by remember { mutableStateOf(false) }

  val pairedRows = remember(moveHistory) {
    buildMovePairRows(moveHistory)
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("move_log_panel"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isCompactRibbon = maxHeight < 110.dp

      if (isCompactRibbon) {
        // Ultra-clean horizontal ribbon for short screens (zero vertical overflow!)
        val rowState = rememberLazyListState()
        LaunchedEffect(pairedRows.size) {
          if (pairedRows.isNotEmpty()) {
            rowState.animateScrollToItem(pairedRows.size - 1)
          }
        }

        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))

          if (pairedRows.isEmpty()) {
            Text(
              text = "${stringResource(R.string.move_log_title)}: ${stringResource(R.string.move_log_empty)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          } else {
            LazyRow(
              state = rowState,
              modifier = Modifier.weight(1f),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              contentPadding = PaddingValues(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              items(pairedRows, key = { "${it.moveNumber}_${it.whiteMove?.plyIndex}_${it.blackMove?.plyIndex}" }) { row ->
                val wText = row.whiteMove?.move?.san ?: "…"
                val bText = row.blackMove?.move?.san?.let { " $it" } ?: ""
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                ) {
                  Text(
                    text = "${row.moveNumber}. $wText$bText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                  )
                }
              }
            }

            IconButton(
              onClick = onCopyPgn,
              modifier = Modifier
                .size(48.dp)
                .testTag("copy_pgn_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_moves),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
              )
            }

            IconButton(
              onClick = { showFullModal = true },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = stringResource(R.string.expand_moves),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
              )
            }
          }
        }
      } else {
        // Full 2-Column Scrollable Chess Notation Table
        val listState = rememberLazyListState()
        LaunchedEffect(pairedRows.size) {
          if (pairedRows.isNotEmpty()) {
            listState.animateScrollToItem(pairedRows.size - 1)
          }
        }

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "${stringResource(R.string.move_log_title)} (${moveHistory.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
              )
            }

            if (moveHistory.isNotEmpty()) {
              IconButton(
                onClick = onCopyPgn,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("copy_pgn_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.ContentCopy,
                  contentDescription = stringResource(R.string.cd_copy_moves),
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(17.dp),
                )
              }
            }
          }

          if (pairedRows.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.move_log_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              items(pairedRows, key = { "${it.moveNumber}_${it.whiteMove?.plyIndex}_${it.blackMove?.plyIndex}" }) { row ->
                MoveTableRowItem(row = row, lastPlyIndex = moveHistory.lastIndex)
              }
            }
          }
        }
      }
    }
  }

  if (showFullModal) {
    AlertDialog(
      onDismissRequest = { showFullModal = false },
      title = {
        Text(
          text = "${stringResource(R.string.move_log_title)} (${moveHistory.size})",
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(pairedRows) { row ->
            MoveTableRowItem(row = row, lastPlyIndex = moveHistory.lastIndex)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          onCopyPgn()
          showFullModal = false
        }) {
          Text(stringResource(R.string.copy_pgn))
        }
      },
      dismissButton = {
        TextButton(onClick = { showFullModal = false }) {
          Text(stringResource(R.string.close_dialog))
        }
      },
    )
  }
}

@Composable
private fun MoveTableRowItem(
  row: MovePairRow,
  lastPlyIndex: Int,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
      .padding(horizontal = 8.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "${row.moveNumber}.",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(28.dp),
      maxLines = 1,
    )
    MoveCell(
      record = row.whiteMove,
      isLatest = row.whiteMove?.plyIndex == lastPlyIndex,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(8.dp))
    MoveCell(
      record = row.blackMove,
      isLatest = row.blackMove?.plyIndex == lastPlyIndex,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun MoveCell(
  record: MoveRecord?,
  isLatest: Boolean,
  modifier: Modifier = Modifier,
) {
  if (record == null) {
    Spacer(modifier = modifier)
    return
  }
  val coord = "${MoveCodec.squareName(record.move.from)}→${MoveCodec.squareName(record.move.to)}"
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text(
      text = record.move.san,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (isLatest) FontWeight.Bold else FontWeight.SemiBold,
      color = if (isLatest) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      maxLines = 1,
    )
    Text(
      text = "($coord)",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun buildMovePairRows(moveHistory: List<MoveRecord>): List<MovePairRow> {
  val rows = ArrayList<MovePairRow>()
  var idx = 0
  while (idx < moveHistory.size) {
    val current = moveHistory[idx]
    if (current.side == Side.WHITE) {
      val next = moveHistory.getOrNull(idx + 1)?.takeIf { it.side == Side.BLACK }
      rows.add(MovePairRow(current.fullMoveNumber, current, next))
      idx += if (next != null) 2 else 1
    } else {
      rows.add(MovePairRow(current.fullMoveNumber, null, current))
      idx++
    }
  }
  return rows
}
