package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.chess.Piece
import com.example.chess.PieceType
import com.example.chess.Side
import com.example.ui.EditorTool

private val WHITE_PALETTE = intArrayOf(
  Piece.WK, Piece.WQ, Piece.WR, Piece.WB, Piece.WN, Piece.WP,
)

private val BLACK_PALETTE = intArrayOf(
  Piece.BK, Piece.BQ, Piece.BR, Piece.BB, Piece.BN, Piece.BP,
)

@Composable
fun BoardEditorPanel(
  sideToMove: Side,
  selectedTool: EditorTool,
  validationError: String?,
  inlineTip: String?,
  onSelectTool: (EditorTool) -> Unit,
  onSelectSideToMove: (Side) -> Unit,
  onSetStandard: () -> Unit,
  onClearBoard: () -> Unit,
  onFlipBoard: () -> Unit,
  onCancel: () -> Unit,
  onApply: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("board_editor_panel"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    shadowElevation = 2.dp,
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      val isCompact = maxWidth < 380.dp

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        // Header strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.edit_mode_badge),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (!isCompact) {
                Text(
                  text = stringResource(R.string.edit_mode_hint),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .height(48.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
              .clickable(role = Role.Button, onClick = onFlipBoard)
              .padding(horizontal = 10.dp)
              .testTag("edit_flip_board_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.SwapVert,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = stringResource(R.string.btn_flip),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
            )
          }
        }

        // White Piece Palette
        PiecePaletteRow(
          pieces = WHITE_PALETTE,
          selectedTool = selectedTool,
          onSelectPiece = { piece -> onSelectTool(EditorTool.Place(piece)) },
        )

        // Black Piece Palette
        PiecePaletteRow(
          pieces = BLACK_PALETTE,
          selectedTool = selectedTool,
          onSelectPiece = { piece -> onSelectTool(EditorTool.Place(piece)) },
        )

        // Tools (Pindah / Hapus) + Side To Move (Putih / Hitam)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EditorToolChip(
            icon = Icons.Filled.OpenWith,
            label = stringResource(R.string.edit_tool_move),
            selected = selectedTool is EditorTool.Move,
            compact = isCompact,
            onClick = { onSelectTool(EditorTool.Move) },
            tag = "tool_move",
            modifier = Modifier.weight(1f),
          )

          EditorToolChip(
            icon = Icons.Filled.DeleteOutline,
            label = stringResource(R.string.edit_tool_erase),
            selected = selectedTool is EditorTool.Erase,
            compact = isCompact,
            isDestructiveTint = true,
            onClick = { onSelectTool(EditorTool.Erase) },
            tag = "tool_erase",
            modifier = Modifier.weight(1f),
          )

          // Turn to Move Segmented Control
          Row(
            modifier = Modifier
              .weight(2f)
              .height(48.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
              .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TurnSelectChip(
              label = stringResource(R.string.color_white),
              isWhite = true,
              selected = sideToMove == Side.WHITE,
              onClick = { onSelectSideToMove(Side.WHITE) },
              tag = "edit_turn_white",
              modifier = Modifier.weight(1f),
            )
            TurnSelectChip(
              label = stringResource(R.string.color_black),
              isWhite = false,
              selected = sideToMove == Side.BLACK,
              onClick = { onSelectSideToMove(Side.BLACK) },
              tag = "edit_turn_black",
              modifier = Modifier.weight(1f),
            )
          }
        }

        // Inline feedback / validation banner
        val feedbackMessage = inlineTip ?: validationError
        AnimatedVisibility(visible = feedbackMessage != null) {
          if (feedbackMessage != null) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                  shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("edit_validation_banner"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = feedbackMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Bottom Presets & Apply / Cancel Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EditorActionChip(
            icon = Icons.Filled.RestartAlt,
            label = stringResource(R.string.edit_preset_standard),
            compact = isCompact,
            onClick = onSetStandard,
            tag = "edit_preset_standard",
            modifier = Modifier.weight(1f),
          )

          EditorActionChip(
            icon = Icons.Filled.ClearAll,
            label = stringResource(R.string.edit_preset_clear),
            compact = isCompact,
            onClick = onClearBoard,
            tag = "edit_preset_clear",
            modifier = Modifier.weight(1f),
          )

          EditorActionChip(
            icon = Icons.Filled.Close,
            label = stringResource(R.string.edit_cancel),
            compact = isCompact,
            onClick = onCancel,
            tag = "edit_cancel_button",
            modifier = Modifier.weight(1f),
          )

          Button(
            onClick = onApply,
            enabled = validationError == null,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
              .weight(1.15f)
              .height(48.dp)
              .testTag("edit_apply_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.edit_apply),
              style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PiecePaletteRow(
  pieces: IntArray,
  selectedTool: EditorTool,
  onSelectPiece: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    for (piece in pieces) {
      val selected = selectedTool is EditorTool.Place && selectedTool.piece == piece
      val pt = PieceType.fromCode(piece)
      val colorName = if (piece > 0) "Putih" else "Hitam"
      val tagSide = if (piece > 0) "w" else "b"
      val tagChar = pt?.sanChar?.lowercase()?.ifEmpty { "p" } ?: "x"

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(
            if (selected) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            }
          )
          .border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
            shape = RoundedCornerShape(10.dp),
          )
          .semantics {
            contentDescription = "Bidak ${pt?.indoName ?: ""} $colorName"
          }
          .testTag("palette_${tagSide}_$tagChar")
          .clickable(role = Role.RadioButton) { onSelectPiece(piece) },
      ) {
        ChessPieceCanvas(
          piece = piece,
          modifier = Modifier.size(34.dp),
        )
      }
    }
  }
}

@Composable
private fun EditorToolChip(
  icon: ImageVector,
  label: String,
  selected: Boolean,
  compact: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
  isDestructiveTint: Boolean = false,
) {
  val activeBg = if (isDestructiveTint) {
    MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
  } else {
    MaterialTheme.colorScheme.primaryContainer
  }
  val activeBorder = if (isDestructiveTint) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.primary
  }
  val activeContent = if (isDestructiveTint) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.onPrimaryContainer
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(if (selected) activeBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) activeBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
      )
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 4.dp)
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
          tint = if (selected) activeContent else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          color = if (selected) activeContent else MaterialTheme.colorScheme.onSurface,
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
          tint = if (selected) activeContent else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          color = if (selected) activeContent else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun TurnSelectChip(
  label: String,
  isWhite: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier
      .height(42.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
      )
      .border(
        width = if (selected) 1.5.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
      )
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 4.dp)
      .testTag(tag),
  ) {
    Box(
      modifier = Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(if (isWhite) Color(0xFFFFFDF7) else Color(0xFF0F172A))
        .border(1.dp, if (isWhite) Color(0xFF475569) else Color(0xFFF8FAFC), CircleShape),
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun EditorActionChip(
  icon: ImageVector,
  label: String,
  compact: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 4.dp)
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
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
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
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
