package fr.messagerie.coffre

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size

@Composable
fun Icone(type: String, couleur: Color, taille: Dp) {
    Canvas(modifier = Modifier.size(taille)) {
        val w = size.width; val h = size.height
        val ep = w * 0.09f
        val trait = Stroke(width = ep, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(w * x, h * y)
        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(couleur, pt(x1, y1), pt(x2, y2), strokeWidth = ep, cap = StrokeCap.Round)
        fun brise(vararg p: Float) {
            val c = Path(); c.moveTo(w * p[0], h * p[1]); var i = 2
            while (i < p.size) { c.lineTo(w * p[i], h * p[i + 1]); i += 2 }
            drawPath(c, couleur, style = trait)
        }
        fun cadre(x: Float, y: Float, lw: Float, lh: Float, r: Float = 0.12f) =
            drawRoundRect(couleur, topLeft = pt(x, y), size = Size(w * lw, h * lh), cornerRadius = CornerRadius(w * r, w * r), style = trait)
        when (type) {
            "retour" -> { ligne(0.2f, 0.5f, 0.8f, 0.5f); brise(0.45f, 0.25f, 0.2f, 0.5f, 0.45f, 0.75f) }
            "chevronDroit" -> brise(0.38f, 0.25f, 0.63f, 0.5f, 0.38f, 0.75f)
            "plus" -> { ligne(0.5f, 0.2f, 0.5f, 0.8f); ligne(0.2f, 0.5f, 0.8f, 0.5f) }
            "fermer" -> { ligne(0.25f, 0.25f, 0.75f, 0.75f); ligne(0.75f, 0.25f, 0.25f, 0.75f) }
            "valider" -> brise(0.2f, 0.52f, 0.42f, 0.74f, 0.8f, 0.28f)
            "recherche" -> { drawCircle(couleur, radius = w * 0.26f, center = pt(0.44f, 0.44f), style = trait); ligne(0.64f, 0.64f, 0.84f, 0.84f) }
            "reglages" -> { ligne(0.15f, 0.3f, 0.85f, 0.3f); ligne(0.15f, 0.55f, 0.85f, 0.55f); ligne(0.15f, 0.8f, 0.85f, 0.8f); drawCircle(couleur, w * 0.09f, pt(0.65f, 0.3f)); drawCircle(couleur, w * 0.09f, pt(0.35f, 0.55f)); drawCircle(couleur, w * 0.09f, pt(0.6f, 0.8f)) }
            "importer" -> { ligne(0.5f, 0.18f, 0.5f, 0.6f); brise(0.33f, 0.44f, 0.5f, 0.62f, 0.67f, 0.44f); brise(0.2f, 0.72f, 0.2f, 0.84f, 0.8f, 0.84f, 0.8f, 0.72f) }
            "crayon" -> { brise(0.25f, 0.75f, 0.7f, 0.3f, 0.78f, 0.38f, 0.33f, 0.83f, 0.25f, 0.75f); ligne(0.25f, 0.75f, 0.22f, 0.86f) }
            "oeil" -> { brise(0.14f, 0.5f, 0.3f, 0.3f, 0.7f, 0.3f, 0.86f, 0.5f, 0.7f, 0.7f, 0.3f, 0.7f, 0.14f, 0.5f); drawCircle(couleur, w * 0.12f, pt(0.5f, 0.5f), style = trait) }
            "oeilBarre" -> { brise(0.14f, 0.5f, 0.3f, 0.32f, 0.7f, 0.32f, 0.86f, 0.5f); drawCircle(couleur, w * 0.11f, pt(0.5f, 0.5f), style = trait); ligne(0.2f, 0.24f, 0.8f, 0.78f) }
            "copier" -> { cadre(0.32f, 0.12f, 0.5f, 0.5f); brise(0.68f, 0.62f, 0.18f, 0.62f, 0.18f, 0.24f) }
            "corbeille" -> { ligne(0.22f, 0.3f, 0.78f, 0.3f); cadre(0.3f, 0.3f, 0.4f, 0.55f); ligne(0.42f, 0.2f, 0.58f, 0.2f) }
            "des" -> { cadre(0.2f, 0.2f, 0.6f, 0.6f, 0.18f); drawCircle(couleur, w * 0.05f, pt(0.37f, 0.37f)); drawCircle(couleur, w * 0.05f, pt(0.63f, 0.63f)); drawCircle(couleur, w * 0.05f, pt(0.5f, 0.5f)) }
            "cle" -> { drawCircle(couleur, w * 0.16f, pt(0.34f, 0.34f), style = trait); brise(0.45f, 0.45f, 0.78f, 0.78f); ligne(0.66f, 0.66f, 0.74f, 0.58f); ligne(0.74f, 0.74f, 0.82f, 0.66f) }
            "carte" -> { cadre(0.14f, 0.28f, 0.72f, 0.44f); ligne(0.14f, 0.42f, 0.86f, 0.42f) }
            "croix" -> { ligne(0.5f, 0.2f, 0.5f, 0.8f); ligne(0.2f, 0.5f, 0.8f, 0.5f) }
            "note" -> { cadre(0.22f, 0.15f, 0.56f, 0.7f); ligne(0.33f, 0.35f, 0.67f, 0.35f); ligne(0.33f, 0.5f, 0.67f, 0.5f); ligne(0.33f, 0.65f, 0.55f, 0.65f) }
            "bouclier" -> { brise(0.5f, 0.16f, 0.82f, 0.28f, 0.82f, 0.54f, 0.5f, 0.84f, 0.18f, 0.54f, 0.18f, 0.28f, 0.5f, 0.16f); drawCircle(couleur, w * 0.08f, pt(0.5f, 0.45f), style = trait); ligne(0.5f, 0.5f, 0.5f, 0.62f) }
        }
    }
}
