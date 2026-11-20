package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chess.ChessMove
import com.example.chess.MoveCodec
import com.example.chess.PieceType
import com.example.chess.Side
import com.example.ui.theme.BoardCheckRed
import com.example.ui.theme.BoardDarkSquare
import com.example.ui.theme.BoardLastMoveDark
import com.example.ui.theme.BoardLastMoveLight
import com.example.ui.theme.BoardLightSquare
import com.example.ui.theme.BoardSelectedSquare
import com.example.ui.theme.Emerald500
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChessBoardView(
  squares: List<Int>,
  isFlipped: Boolean,
  selectedSquare: Int?,
  legalTargets: Set<Int>,
  lastMoveFrom: Int?,
  lastMoveTo: Int?,
  checkedKingSquare: Int?,
  recommendedMove: ChessMove?,
  onSquareClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.testTag("chess_board_container"),
    shape = RoundedCornerShape(12.dp),
    shadowElevation = 5.dp,
    color = Color(0xFF1E293B),
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(4.dp)
        .clip(RoundedCornerShape(8.dp)),
    ) {
      val coordFontSp = if (maxWidth >= 460.dp) 11.sp else 9.sp
      val piecePad = if (maxWidth >= 460.dp) 4.dp else 2.5.dp

      Column(modifier = Modifier.fillMaxSize()) {
        for (visualRow in 0..7) {
          val boardRank = if (isFlipped) visualRow else (7 - visualRow)
          Row(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
          ) {
            for (visualCol in 0..7) {
              val boardFile = if (isFlipped) (7 - visualCol) else visualCol
              val sq = boardRank * 8 + boardFile
              val isLight = ((boardRank + boardFile) and 1) == 1
              val piece = squares.getOrElse(sq) { 0 }
              val isSelected = selectedSquare == sq
              val isLastMove = lastMoveFrom == sq || lastMoveTo == sq
              val isLegalTarget = sq in legalTargets
              val isCheckedKing = checkedKingSquare == sq

              val bgColor = when {
                isSelected -> BoardSelectedSquare
                isLastMove -> if (isLight) BoardLastMoveLight else BoardLastMoveDark
                else -> if (isLight) BoardLightSquare else BoardDarkSquare
              }

              val sqName = MoveCodec.squareName(sq)
              val pieceDesc = PieceType.fromCode(piece)?.let { pt ->
                val sideStr = if (piece > 0) "Putih" else "Hitam"
                "${pt.indoName} $sideStr"
              } ?: "Kosong"

              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxSize()
                  .background(bgColor)
                  .then(
                    if (isCheckedKing) {
                      Modifier.background(
                        Brush.radialGradient(
                          colors = listOf(
                            BoardCheckRed.copy(alpha = 0.88f),
                            BoardCheckRed.copy(alpha = 0.25f),
                            Color.Transparent,
                          )
                        )
                      )
                    } else {
                      Modifier
                    }
                  )
                  .semantics { contentDescription = "Petak $sqName, $pieceDesc" }
                  .testTag("square_$sqName")
                  .clickable(role = Role.Button) { onSquareClick(sq) },
              ) {
                if (visualCol == 0) {
                  Text(
                    text = ('1' + boardRank).toString(),
                    fontSize = coordFontSp,
                    lineHeight = coordFontSp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLight) BoardDarkSquare else BoardLightSquare,
                    modifier = Modifier
                      .align(Alignment.TopStart)
                      .padding(start = 2.dp, top = 1.dp),
                  )
                }

                if (visualRow == 7) {
                  Text(
                    text = ('a' + boardFile).toString(),
                    fontSize = coordFontSp,
                    lineHeight = coordFontSp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLight) BoardDarkSquare else BoardLightSquare,
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(end = 2.dp, bottom = 1.dp),
                  )
                }

                if (piece != 0) {
                  ChessPieceCanvas(
                    piece = piece,
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(piecePad),
                  )
                }

                if (isLegalTarget) {
                  Canvas(modifier = Modifier.fillMaxSize()) {
                    if (piece != 0) {
                      drawCircle(
                        color = Color(0xAA0F172A),
                        radius = size.minDimension * 0.43f,
                        style = Stroke(width = size.minDimension * 0.09f),
                      )
                    } else {
                      drawCircle(
                        color = Color(0x880F172A),
                        radius = size.minDimension * 0.17f,
                        style = Fill,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Grandmaster Best-Move Tactical Arrow Overlay (Player's turn only)
      if (recommendedMove != null) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val cellW = size.width / 8f
          val cellH = size.height / 8f

          fun squareCenter(sq: Int): Offset {
            val r = sq ushr 3
            val f = sq and 7
            val col = if (isFlipped) (7 - f) else f
            val row = if (isFlipped) r else (7 - r)
            return Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
          }

          val start = squareCenter(recommendedMove.from)
          val end = squareCenter(recommendedMove.to)
          val arrowColor = Emerald500.copy(alpha = 0.88f)

          drawCircle(
            color = arrowColor,
            radius = cellW * 0.42f,
            center = start,
            style = Stroke(width = cellW * 0.08f),
          )

          val angle = atan2(end.y - start.y, end.x - start.x)
          val headLen = cellW * 0.44f
          val shaftStart = Offset(
            start.x + cos(angle) * (cellW * 0.18f),
            start.y + sin(angle) * (cellH * 0.18f),
          )
          val shaftEnd = Offset(
            end.x - cos(angle) * (headLen * 0.68f),
            end.y - sin(angle) * (headLen * 0.68f),
          )

          drawLine(
            color = Color(0x55000000),
            start = shaftStart,
            end = shaftEnd,
            strokeWidth = cellW * 0.22f,
            cap = StrokeCap.Round,
          )
          drawLine(
            color = arrowColor,
            start = shaftStart,
            end = shaftEnd,
            strokeWidth = cellW * 0.17f,
            cap = StrokeCap.Round,
          )

          val wingAngle = 0.55f
          val p1 = Offset(
            end.x - headLen * cos(angle - wingAngle),
            end.y - headLen * sin(angle - wingAngle),
          )
          val p2 = Offset(
            end.x - headLen * cos(angle + wingAngle),
            end.y - headLen * sin(angle + wingAngle),
          )
          val headPath = Path().apply {
            moveTo(end.x, end.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            close()
          }
          drawPath(path = headPath, color = arrowColor, style = Fill)
        }
      }
    }
  }
}

@Composable
fun PromotionPickerDialog(
  side: Side,
  onSelect: (PieceType) -> Unit,
  onDismiss: () -> Unit,
) {
  val choices = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.promotion_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (pt in choices) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .weight(1f)
              .aspectRatio(0.85f)
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
              .clickable(role = Role.Button) { onSelect(pt) }
              .padding(6.dp)
              .testTag("promo_${pt.sanChar}"),
            verticalArrangement = Arrangement.Center,
          ) {
            ChessPieceCanvas(
              piece = pt.code * side.sign,
              modifier = Modifier.size(38.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = pt.indoName,
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.confirm_cancel))
      }
    },
  )
}
