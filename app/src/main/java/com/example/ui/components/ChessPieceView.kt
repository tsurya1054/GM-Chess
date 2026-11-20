package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

private val WhiteFill = Color(0xFFFFFDF7)
private val WhiteStroke = Color(0xFF0F172A)
private val WhiteDetail = Color(0xFF1E293B)

private val BlackFill = Color(0xFF141C2B)
private val BlackStroke = Color(0xFFF8FAFC)
private val BlackDetail = Color(0xFFCBD5E1)
private val PieceShadow = Color(0x38000000)

/**
 * Crisp tournament-style Staunton vector chess piece renderer using DrawScope dimensions.
 * Guarantees uniform, razor-sharp contrast across all Android devices without emoji font issues.
 */
@Composable
fun ChessPieceCanvas(
  piece: Int,
  modifier: Modifier = Modifier,
) {
  if (piece == 0) return
  val isWhite = piece > 0
  val pieceType = abs(piece)

  Canvas(modifier = modifier) {
    val s = size.minDimension / 100f
    val ox = (size.width - size.minDimension) / 2f
    val oy = (size.height - size.minDimension) / 2f

    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s

    val fill = if (isWhite) WhiteFill else BlackFill
    val outline = if (isWhite) WhiteStroke else BlackStroke
    val detail = if (isWhite) WhiteDetail else BlackDetail
    val strokeW = (4.2f * s).coerceAtLeast(1.5f)
    val detailW = (2.8f * s).coerceAtLeast(1.1f)

    // Subtle ground shadow
    drawOval(
      color = PieceShadow,
      topLeft = Offset(px(18f), py(84f)),
      size = Size(64f * s, 9f * s),
    )

    when (pieceType) {
      1 -> drawPawn(::px, ::py, s, fill, outline, detail, strokeW, detailW)
      2 -> drawKnight(::px, ::py, s, fill, outline, detail, strokeW, detailW)
      3 -> drawBishop(::px, ::py, s, fill, outline, detail, strokeW, detailW)
      4 -> drawRook(::px, ::py, s, fill, outline, detail, strokeW, detailW)
      5 -> drawQueen(::px, ::py, s, fill, outline, detail, strokeW, detailW)
      6 -> drawKing(::px, ::py, s, fill, outline, detail, strokeW, detailW)
    }
  }
}

private fun DrawScope.drawBasePedestal(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  drawRoundRect(
    color = fill,
    topLeft = Offset(px(20f), py(80f)),
    size = Size(60f * s, 9f * s),
    cornerRadius = CornerRadius(4f * s, 4f * s),
    style = Fill,
  )
  drawRoundRect(
    color = outline,
    topLeft = Offset(px(20f), py(80f)),
    size = Size(60f * s, 9f * s),
    cornerRadius = CornerRadius(4f * s, 4f * s),
    style = Stroke(width = strokeW),
  )
  drawLine(
    color = detail,
    start = Offset(px(26f), py(80f)),
    end = Offset(px(74f), py(80f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
}

private fun DrawScope.drawPawn(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  val body = Path().apply {
    moveTo(px(26f), py(80f))
    cubicTo(px(28f), py(66f), px(38f), py(58f), px(40f), py(47f))
    lineTo(px(60f), py(47f))
    cubicTo(px(62f), py(58f), px(72f), py(66f), px(74f), py(80f))
    close()
  }
  drawPath(body, fill, style = Fill)
  drawPath(body, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  // Collar
  drawRoundRect(
    color = fill,
    topLeft = Offset(px(34f), py(43f)),
    size = Size(32f * s, 6.5f * s),
    cornerRadius = CornerRadius(3.5f * s, 3.5f * s),
    style = Fill,
  )
  drawRoundRect(
    color = outline,
    topLeft = Offset(px(34f), py(43f)),
    size = Size(32f * s, 6.5f * s),
    cornerRadius = CornerRadius(3.5f * s, 3.5f * s),
    style = Stroke(width = strokeW),
  )

  // Head
  drawCircle(color = fill, radius = 13.5f * s, center = Offset(px(50f), py(30f)), style = Fill)
  drawCircle(color = outline, radius = 13.5f * s, center = Offset(px(50f), py(30f)), style = Stroke(width = strokeW))

  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}

private fun DrawScope.drawRook(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  val tower = Path().apply {
    moveTo(px(26f), py(80f))
    lineTo(px(31f), py(40f))
    lineTo(px(24f), py(34f))
    lineTo(px(24f), py(20f))
    lineTo(px(35f), py(20f))
    lineTo(px(35f), py(27f))
    lineTo(px(44f), py(27f))
    lineTo(px(44f), py(20f))
    lineTo(px(56f), py(20f))
    lineTo(px(56f), py(27f))
    lineTo(px(65f), py(27f))
    lineTo(px(65f), py(20f))
    lineTo(px(76f), py(20f))
    lineTo(px(76f), py(34f))
    lineTo(px(69f), py(40f))
    lineTo(px(74f), py(80f))
    close()
  }
  drawPath(tower, fill, style = Fill)
  drawPath(tower, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  drawLine(
    color = detail,
    start = Offset(px(31f), py(40f)),
    end = Offset(px(69f), py(40f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = detail,
    start = Offset(px(29f), py(71f)),
    end = Offset(px(71f), py(71f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}

private fun DrawScope.drawKnight(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  val horse = Path().apply {
    moveTo(px(26f), py(80f))
    cubicTo(px(27f), py(66f), px(34f), py(58f), px(40f), py(50f))
    lineTo(px(28f), py(53f))
    cubicTo(px(21f), py(54f), px(19f), py(46f), px(23f), py(40f))
    lineTo(px(34f), py(25f))
    lineTo(px(33f), py(15f))
    lineTo(px(44f), py(21f))
    cubicTo(px(58f), py(19f), px(72f), py(29f), px(74f), py(48f))
    cubicTo(px(75f), py(61f), px(74f), py(71f), px(74f), py(80f))
    close()
  }
  drawPath(horse, fill, style = Fill)
  drawPath(horse, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  // Eye & mane accent
  drawCircle(color = detail, radius = 2.6f * s, center = Offset(px(38f), py(32f)))
  val mane = Path().apply {
    moveTo(px(53f), py(27f))
    cubicTo(px(63f), py(34f), px(66f), py(49f), px(65f), py(68f))
  }
  drawPath(mane, detail, style = Stroke(width = detailW, cap = StrokeCap.Round))

  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}

private fun DrawScope.drawBishop(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  val mitre = Path().apply {
    moveTo(px(27f), py(80f))
    cubicTo(px(30f), py(67f), px(36f), py(62f), px(38f), py(56f))
    cubicTo(px(28f), py(46f), px(30f), py(28f), px(50f), py(19f))
    cubicTo(px(70f), py(28f), px(72f), py(46f), px(62f), py(56f))
    cubicTo(px(64f), py(62f), px(70f), py(67f), px(73f), py(80f))
    close()
  }
  drawPath(mitre, fill, style = Fill)
  drawPath(mitre, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  // Top finial orb
  drawCircle(color = fill, radius = 5.2f * s, center = Offset(px(50f), py(14f)), style = Fill)
  drawCircle(color = outline, radius = 5.2f * s, center = Offset(px(50f), py(14f)), style = Stroke(width = strokeW))

  // Cross / mitre emblem inside head
  drawLine(
    color = detail,
    start = Offset(px(50f), py(31f)),
    end = Offset(px(50f), py(45f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = detail,
    start = Offset(px(43f), py(38f)),
    end = Offset(px(57f), py(38f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = detail,
    start = Offset(px(36f), py(57f)),
    end = Offset(px(64f), py(57f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )

  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}

private fun DrawScope.drawQueen(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  val crown5 = Path().apply {
    moveTo(px(25f), py(80f))
    lineTo(px(16f), py(32f))
    lineTo(px(32f), py(51f))
    lineTo(px(34f), py(24f))
    lineTo(px(45f), py(48f))
    lineTo(px(50f), py(20f))
    lineTo(px(55f), py(48f))
    lineTo(px(66f), py(24f))
    lineTo(px(68f), py(51f))
    lineTo(px(84f), py(32f))
    lineTo(px(75f), py(80f))
    close()
  }
  drawPath(crown5, fill, style = Fill)
  drawPath(crown5, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  // Crown pearls
  val pearls = listOf(16f to 28f, 34f to 20f, 50f to 16f, 66f to 20f, 84f to 28f)
  for ((cx, cy) in pearls) {
    drawCircle(color = fill, radius = 4.2f * s, center = Offset(px(cx), py(cy)), style = Fill)
    drawCircle(color = outline, radius = 4.2f * s, center = Offset(px(cx), py(cy)), style = Stroke(width = strokeW * 0.85f))
  }

  drawLine(
    color = detail,
    start = Offset(px(27f), py(69f)),
    end = Offset(px(73f), py(69f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}

private fun DrawScope.drawKing(
  px: (Float) -> Float,
  py: (Float) -> Float,
  s: Float,
  fill: Color,
  outline: Color,
  detail: Color,
  strokeW: Float,
  detailW: Float,
) {
  // Royal Cross at top
  drawLine(
    color = outline,
    start = Offset(px(50f), py(9f)),
    end = Offset(px(50f), py(25f)),
    strokeWidth = strokeW * 1.35f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = outline,
    start = Offset(px(42f), py(16f)),
    end = Offset(px(58f), py(16f)),
    strokeWidth = strokeW * 1.35f,
    cap = StrokeCap.Round,
  )

  val body = Path().apply {
    moveTo(px(26f), py(80f))
    lineTo(px(21f), py(46f))
    cubicTo(px(19f), py(32f), px(36f), py(28f), px(50f), py(39f))
    cubicTo(px(64f), py(28f), px(81f), py(32f), px(79f), py(46f))
    lineTo(px(74f), py(80f))
    close()
  }
  drawPath(body, fill, style = Fill)
  drawPath(body, outline, style = Stroke(width = strokeW, join = StrokeJoin.Round))

  // Central crown jewel arch
  val arch = Path().apply {
    moveTo(px(50f), py(25f))
    cubicTo(px(40f), py(25f), px(39f), py(38f), px(50f), py(46f))
    cubicTo(px(61f), py(38f), px(60f), py(25f), px(50f), py(25f))
    close()
  }
  drawPath(arch, fill, style = Fill)
  drawPath(arch, outline, style = Stroke(width = strokeW * 0.9f, join = StrokeJoin.Round))

  drawLine(
    color = detail,
    start = Offset(px(27f), py(68f)),
    end = Offset(px(73f), py(68f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = detail,
    start = Offset(px(50f), py(47f)),
    end = Offset(px(50f), py(64f)),
    strokeWidth = detailW,
    cap = StrokeCap.Round,
  )
  drawBasePedestal(px, py, s, fill, outline, detail, strokeW, detailW)
}
