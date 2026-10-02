/*
 * JemmaPdfExporter.kt — Lot 14.6
 *
 * Encapsule la génération et le partage du PDF A4 Pocket Pass
 * pliable en 4 pour le réutiliser tant depuis QrViewerFragment
 * que directement depuis le ProfileDetailFragment.
 *
 * Redessiné en document 2 pages avec 100% de thèmes clairs (Éco-Encre) :
 * Page 1 : Pass Pocket pliable avec couverture arrière Jemma Pass (Fond clair éco).
 *         Summaries cliniques sur 2 colonnes avec infos minimales + QR Code Langue intégré en-dessous !
 * Page 2 : Page de sauvegarde dédiée avec QR Codes géants haute résolution, 100% sans aplats d'encre sombre.
 */
package be.heyman.android.jemmapassdemo.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import be.heyman.android.jemmapassdemo.kb.HydratedProfile
import be.heyman.android.jemmapassdemo.kb.HydratedAllergy
import be.heyman.android.jemmapassdemo.kb.HydratedMedication
import be.heyman.android.jemmapassdemo.kb.HydratedGenericEntry
import be.heyman.android.jemmapassdemo.kb.JemmaProfileHydrator
import be.heyman.android.jemmapassdemo.pdf.PdfPillarLayout
import be.heyman.android.jemmapassdemo.R
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object JemmaPdfExporter {

    private const val TAG = "JEMMA-PDF-EXPORTER"

    /**
     * Génère et partage le PDF d'un pass médical pliable.
     */
    suspend fun exportPdf(context: Context, raw: JemmaProfileJ, hydrator: JemmaProfileHydrator) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.pdf_export_in_progress, Toast.LENGTH_SHORT).show()
        }

        try {
            // Determine default language based on profile preference first, falling back to system locale
            val profileLang = raw.p?.lang?.lowercase()
            val currentLang = when {
                profileLang?.startsWith("ja") == true -> JemmaTextPayloadBuilder.Lang.JA
                profileLang?.startsWith("fr") == true -> JemmaTextPayloadBuilder.Lang.FR
                Locale.getDefault().language.lowercase() == "ja" -> JemmaTextPayloadBuilder.Lang.JA
                Locale.getDefault().language.lowercase() == "fr" -> JemmaTextPayloadBuilder.Lang.FR
                else -> JemmaTextPayloadBuilder.Lang.EN
            }

            // Hydrate for the target local language
            val hydratedLocal = withContext(Dispatchers.IO) {
                hydrator.hydrate(raw, uiLang = currentLang.isoCode)
            }

            // Hydrate for English language
            val hydratedEn = withContext(Dispatchers.IO) {
                hydrator.hydrate(raw, uiLang = JemmaTextPayloadBuilder.Lang.EN.isoCode)
            }

            // 1. Local Language Text Payload & QR Code bitmap (400x400 px for the booklet quadrant)
            val textLocal = withContext(Dispatchers.Default) {
                JemmaTextPayloadBuilder.build(hydratedLocal, currentLang)
            }
            val qrTextLocalBmp = withContext(Dispatchers.Default) {
                JemmaQrBitmapEncoder.encode(textLocal, sizePx = 400, errorCorrection = ErrorCorrectionLevel.Q)
            }

            // 2. English Language Text Payload & QR Code bitmap
            val textEn = withContext(Dispatchers.Default) {
                JemmaTextPayloadBuilder.build(hydratedEn, JemmaTextPayloadBuilder.Lang.EN)
            }
            val qrTextEnBmp = withContext(Dispatchers.Default) {
                JemmaQrBitmapEncoder.encode(textEn, sizePx = 400, errorCorrection = ErrorCorrectionLevel.Q)
            }

            // 3. Pruned FHIR/Compact QR Code for Page 2 (Large backup sheet)
            val compactPayload = JemmaPayloadCodec.encode(raw)
            val qrCompactBmp = if (compactPayload is JemmaPayloadCodec.EncodeResult.Success) {
                withContext(Dispatchers.Default) {
                    JemmaQrBitmapEncoder.encode(compactPayload.payload, sizePx = 600, errorCorrection = ErrorCorrectionLevel.Q)
                }
            } else null

            // 4. Generate PDF document
            val document = PdfDocument()

            // --- PAGE 1: Foldable A4 Pocket Pass booklet ---
            val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page1 = document.startPage(pageInfo1)
            val canvas1 = page1.canvas

            // Quadrant 1 (Top Right, Cover, Rotated 180)
            canvas1.save()
            canvas1.translate(595f, 421f)
            canvas1.rotate(180f)
            drawCoverQuadrant(canvas1, raw, currentLang)
            canvas1.restore()

            // Quadrant 2 (Top Left, Brand Cover / Back cover, Rotated 180)
            canvas1.save()
            canvas1.translate(297.5f, 421f)
            canvas1.rotate(180f)
            drawBrandCoverQuadrant(canvas1, context, raw, qrCompactBmp, currentLang)
            canvas1.restore()

            // Quadrant 3 (Bottom Left, Clinical Local summary with integrated QR Code)
            canvas1.save()
            canvas1.translate(0f, 421f)
            drawClinicalQuadrant(canvas1, hydratedLocal, currentLang, qrTextLocalBmp)
            canvas1.restore()

            // Quadrant 4 (Bottom Right, Clinical English summary with integrated QR Code)
            canvas1.save()
            canvas1.translate(297.5f, 421f)
            drawClinicalQuadrant(canvas1, hydratedEn, JemmaTextPayloadBuilder.Lang.EN, qrTextEnBmp)
            canvas1.restore()

            // Draw cutting/folding dashed guidelines for Page 1
            val linePaint = Paint().apply {
                color = Color.parseColor("#CFD8DC")
                strokeWidth = 1f
                style = Paint.Style.STROKE
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            // Vertical fold line
            canvas1.drawLine(297.5f, 0f, 297.5f, 842f, linePaint)
            // Horizontal fold line
            canvas1.drawLine(0f, 421f, 595f, 421f, linePaint)

            document.finishPage(page1)


            // --- PAGE 2: Large High-Definition QR Codes backup sheet ---
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = document.startPage(pageInfo2)
            val canvas2 = page2.canvas

            // Page 2 uses high-res qrTextLocalBmp as QR1 and qrCompactBmp as QR2
            drawQrCodesPage(canvas2, context, raw, qrTextLocalBmp, qrCompactBmp, currentLang)

            document.finishPage(page2)


            // 5. Save PDF to a temporary file
            val prefix = raw.p?.gn?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "profile"
            val pdfFile = File(context.cacheDir, "jemma_pocket_pass_${prefix}.pdf")
            withContext(Dispatchers.IO) {
                FileOutputStream(pdfFile).use { out ->
                    document.writeTo(out)
                }
            }
            document.close()

            // 6. Share / Print Intent
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Partager le pass PDF / Imprimer"))
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error generating pocket pass PDF", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.pdf_export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun drawCoverQuadrant(canvas: Canvas, raw: JemmaProfileJ, currentLang: JemmaTextPayloadBuilder.Lang) {
        val p = raw.p ?: return

        // Background boundary card (Clean light theme)
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CFD8DC")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, bgPaint)
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, borderPaint)

        // Medical Header Band (Light/medium teal border line, no heavy dark fills)
        val headerPaint = Paint().apply {
            color = Color.parseColor("#E0F2F1") // Light teal fill
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(10f, 10f, 287.5f, 50f, 12f, 12f, headerPaint)
        canvas.drawRect(10f, 30f, 287.5f, 50f, headerPaint)
        
        canvas.drawLine(10f, 50f, 287.5f, 50f, Paint().apply {
            color = Color.parseColor("#00796B")
            strokeWidth = 1.5f
            isAntiAlias = true
        })

        // Header Title
        val titlePaint = Paint().apply {
            color = Color.parseColor("#004D40")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("JEMMA PASS", 148.75f, 34f, titlePaint)

        // Emergency medical pass subtitle
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#00796B")
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val subTitleStr = JemmaTranslations.getPdfLabel(currentLang, "sub_title")
        canvas.drawText(subTitleStr, 148.75f, 68f, subTitlePaint)

        // Demographic labels
        val textPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 10.5f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#78909C")
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // Full name
        val fullName = "${p.gn ?: ""} ${p.fn ?: ""}".trim().ifEmpty { "Patient Profile" }
        val namePaint = Paint().apply {
            color = Color.parseColor("#004D40")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(fullName, 25f, 94f, namePaint)

        // Divider
        val divPaint = Paint().apply {
            color = Color.parseColor("#B2DFDB")
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawLine(25f, 104f, 272.5f, 104f, divPaint)

        // Rows (Airy demographics)
        var yPos = 124f
        fun drawRow(label: String, value: String) {
            canvas.drawText(label, 25f, yPos, labelPaint)
            canvas.drawText(value, 25f, yPos + 13f, textPaint)
            yPos += 34f
        }

        val genderStr = when (p.gs?.uppercase()) {
            "M" -> "${JemmaTranslations.getPdfLabel(currentLang, "gender_m")} / Male"
            "F" -> "${JemmaTranslations.getPdfLabel(currentLang, "gender_f")} / Female"
            "O" -> "${JemmaTranslations.getPdfLabel(currentLang, "gender_o")} / Other"
            else -> "${JemmaTranslations.getPdfLabel(currentLang, "gender_u")} / Unknown"
        }
        val dobLabel = "${JemmaTranslations.getPdfLabel(currentLang, "born").uppercase()} / DATE OF BIRTH"
        val genderLabel = "${JemmaTranslations.getPdfLabel(currentLang, "gender").uppercase()} / GENDER"
        val idLabel = "${JemmaTranslations.getPdfLabel(currentLang, "national_id").uppercase()} / NATIONAL ID"
        drawRow(dobLabel, p.bd ?: "--")
        drawRow(genderLabel, genderStr)
        drawRow(idLabel, p.idn ?: "--")

        // Blood type prominent badge on the right (Light Red border/fill, eco-friendly)
        val bloodType = p.bt ?: "?"
        val badgeBgPaint = Paint().apply {
            color = Color.parseColor("#FFEBEE") // Very light red fill
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeBorderPaint = Paint().apply {
            color = Color.parseColor("#E57373") // Soft red stroke
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawRoundRect(195f, 116f, 268f, 172f, 8f, 8f, badgeBgPaint)
        canvas.drawRoundRect(195f, 116f, 268f, 172f, 8f, 8f, badgeBorderPaint)
        
        val badgeTitlePaint = Paint().apply {
            color = Color.parseColor("#C62828")
            textSize = 7.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val bloodTitleStr = JemmaTranslations.getPdfLabel(currentLang, "blood_type").uppercase()
        canvas.drawText(bloodTitleStr, 231.5f, 129f, badgeTitlePaint)
        val badgeValuePaint = Paint().apply {
            color = Color.parseColor("#D32F2F")
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(bloodType, 231.5f, 160f, badgeValuePaint)

        // Pocket Pass Booklet footer notice
        val footerPaint = Paint().apply {
            color = Color.parseColor("#78909C")
            textSize = 7f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val footerTextStr = JemmaTranslations.getPdfLabel(currentLang, "fold_instruction")
        canvas.drawText(footerTextStr, 148.75f, 395f, footerPaint)
    }

    private fun drawBrandCoverQuadrant(
        canvas: Canvas,
        context: Context,
        raw: JemmaProfileJ,
        qrCompact: Bitmap?,
        currentLang: JemmaTextPayloadBuilder.Lang
    ) {
        // Background card (100% Light mode Eco-friendly!)
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CFD8DC")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC") // Clean light slate background
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, bgPaint)
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, borderPaint)

        // Brand Title at the top of the quadrant (Teal on light background)
        val brandPaint = Paint().apply {
            color = Color.parseColor("#00796B")
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("JEMMA PASS", 148.75f, 32f, brandPaint)

        val passportTitle = JemmaTranslations.getPdfLabel(currentLang, "passport_title")
        val descPaint = Paint().apply {
            color = Color.parseColor("#37474F")
            textSize = 7.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(passportTitle, 148.75f, 44f, descPaint.apply { isFakeBoldText = true; color = Color.parseColor("#004D40") })

        // Draw Premium High-Density Pruned QR Code with Logo center-overlay
        if (qrCompact != null) {
            val qrSize = 140
            val qrX = 148 - (qrSize / 2) // Centered horizontally: 78
            val qrY = 60
            val qrRect = Rect(qrX, qrY, qrX + qrSize, qrY + qrSize)
            canvas.drawBitmap(qrCompact, null, qrRect, Paint(Paint.FILTER_BITMAP_FLAG))
            
            // QR Code border
            canvas.drawRect(
                (qrX - 1).toFloat(),
                (qrY - 1).toFloat(),
                (qrX + qrSize + 1).toFloat(),
                (qrY + qrSize + 1).toFloat(),
                Paint().apply {
                    color = Color.parseColor("#CBD5E1")
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
            )

            // Draw center white badge and Jemma logo overlay inside the QR code center!
            val centerBadgePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            // Size: 34x34 centered at (148f, 130f)
            canvas.drawRoundRect(131f, 113f, 165f, 147f, 8f, 8f, centerBadgePaint)

            try {
                val logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.jemmapass_icon)
                if (logoBmp != null) {
                    val logoRect = Rect(136, 118, 160, 142)
                    canvas.drawBitmap(logoBmp, null, logoRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing center logo inside QR", e)
            }

            // Caption directly below the QR code
            val captionPaint = Paint().apply {
                color = Color.parseColor("#00796B")
                textSize = 6.8f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val captionStr = JemmaTranslations.getPdfLabel(currentLang, "caption_pruned")
            canvas.drawText(captionStr, 148.75f, (qrY + qrSize + 11).toFloat(), captionPaint)

        } else {
            // Fallback: draw soft teal glow circle with big JemmaPass logo in center
            val glowPaint = Paint().apply {
                color = Color.parseColor("#1A0D9688")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(148.75f, 140f, 65f, glowPaint)

            try {
                val logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.jemmapass_icon)
                if (logoBmp != null) {
                    val dstRect = Rect(118, 110, 178, 170)
                    canvas.drawBitmap(logoBmp, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading logo for Brand Cover", e)
            }
        }

        val descriptionLines = JemmaTranslations.getCoverDescLines(currentLang)
        var yPos = 222f
        for (line in descriptionLines) {
            if (line.isEmpty()) {
                yPos += 8f
                continue
            }
            canvas.drawText(line, 148.75f, yPos, descPaint.apply { isFakeBoldText = false; color = Color.parseColor("#475569"); textSize = 8.5f })
            yPos += 12f
        }

        // Tagline at the bottom
        val tagPaint = Paint().apply {
            color = Color.parseColor("#00796B")
            textSize = 8f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val taglineStr = JemmaTranslations.getPdfLabel(currentLang, "mesh_tagline")
        canvas.drawText(taglineStr, 148.75f, 385f, tagPaint)
    }

    private fun drawClinicalQuadrant(
        canvas: Canvas,
        hydrated: HydratedProfile,
        lang: JemmaTextPayloadBuilder.Lang,
        qrBmp: Bitmap?
    ) {
        val p = hydrated.raw.p ?: return

        // 1. Background card (Light Eco theme)
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CFD8DC")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FAFAFA")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, bgPaint)
        canvas.drawRoundRect(10f, 10f, 287.5f, 411f, 12f, 12f, borderPaint)

        // 2. Section Header
        val titlePaint = Paint().apply {
            color = Color.parseColor("#00796B") // Teal
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val titleStr = JemmaTranslations.getLabel(lang, "header")
        canvas.drawText(titleStr, 148.75f, 28f, titlePaint)

        // 3. Two columns layout parameters
        val xCol1 = 20f
        val xCol2 = 152f

        val normalPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 7f
            isAntiAlias = true
        }

        val boldPaint = Paint().apply {
            color = Color.parseColor("#004D40")
            textSize = 7.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        // Dedicated paints: never recolour normalPaint, later lines reuse it.
        val grayPaint = Paint(normalPaint).apply { color = Color.GRAY }
        // "+N" overflow rows (UC-PDF-002/003/004): bold so the reader sees the list is incomplete.
        val overflowPaint = Paint(normalPaint).apply {
            color = Color.parseColor("#B71C1C")
            isFakeBoldText = true
        }

        // Rows shared by allergies and conditions; ordering, overflow and criticality
        // labels are decided by PdfPillarLayout (pure, unit-tested).
        val column1Rows = PdfPillarLayout.column1Rows(hydrated.allergies.size, hydrated.conditions.size)

        // --- COLUMN 1: Patient, Allergies, Conditions ---
        var y1 = 48f

        // Patient demographics (minimal display)
        val patientLabel = "👤 ${JemmaTranslations.getLabel(lang, "patient_title")}"
        canvas.drawText(patientLabel, xCol1, y1, boldPaint)
        y1 += 9f
        val name = "${p.gn ?: ""} ${p.fn ?: ""}".trim().ifEmpty { "Patient Profile" }
        canvas.drawText(if (name.length > 22) name.substring(0, 20) + ".." else name, xCol1, y1, normalPaint)
        y1 += 8f
        val bornStr = "${JemmaTranslations.getPdfLabel(lang, "born")}: ${p.bd ?: "--"}"
        canvas.drawText(bornStr, xCol1, y1, normalPaint)
        y1 += 13f

        // Allergies (the first pillar)
        val allergiesLabel = "⚠️ ${JemmaTranslations.getLabel(lang, "allergies_title")}"
        canvas.drawText(allergiesLabel, xCol1, y1, boldPaint)
        y1 += 9f
        if (hydrated.allergies.isNotEmpty()) {
            // Highest criticality first; what does not fit is counted, never dropped silently.
            val allergyPlan = PdfPillarLayout.planAllergies(hydrated.allergies, column1Rows.allergyRows) {
                it.criticality.name
            }
            for (a in allergyPlan.shown) {
                val nameShort = a.displayLocalized.ifBlank { a.raw.c.orEmpty() }
                val cleanName = if (nameShort.length > 18) nameShort.substring(0, 16) + ".." else nameShort
                // Unknown criticality prints "?", never "L" (UC-PDF-005).
                val crit = PdfPillarLayout.criticalityLabel(a.criticality.name)
                canvas.drawText("• $cleanName ($crit)", xCol1, y1, normalPaint)
                y1 += 8f
            }
            if (allergyPlan.hasOverflow) {
                val noun = JemmaTranslations.getLabel(lang, "allergies_title").lowercase()
                canvas.drawText("• ${PdfPillarLayout.overflowText(allergyPlan.overflowCount, noun)}", xCol1, y1, overflowPaint)
                y1 += 8f
            }
        } else {
            val emptyStr = JemmaTranslations.getLabel(lang, "empty")
            canvas.drawText(emptyStr, xCol1, y1, grayPaint)
            y1 += 8f
        }
        y1 += 5f

        // Conditions (the third pillar)
        val conditionsLabel = "🩺 ${JemmaTranslations.getLabel(lang, "conditions_title")}"
        canvas.drawText(conditionsLabel, xCol1, y1, boldPaint)
        y1 += 9f
        if (hydrated.conditions.isNotEmpty()) {
            val conditionPlan = PdfPillarLayout.plan(hydrated.conditions, column1Rows.conditionRows)
            for (c in conditionPlan.shown) {
                val condName = c.displayLocalized.ifBlank { c.raw.c.orEmpty() }
                val cleanName = if (condName.length > 22) condName.substring(0, 20) + ".." else condName
                canvas.drawText("• $cleanName", xCol1, y1, normalPaint)
                y1 += 8f
            }
            if (conditionPlan.hasOverflow) {
                val noun = JemmaTranslations.getLabel(lang, "conditions_title").lowercase()
                canvas.drawText("• ${PdfPillarLayout.overflowText(conditionPlan.overflowCount, noun)}", xCol1, y1, overflowPaint)
                y1 += 8f
            }
        } else {
            val emptyStr = JemmaTranslations.getLabel(lang, "empty")
            canvas.drawText(emptyStr, xCol1, y1, grayPaint)
            y1 += 8f
        }


        // --- COLUMN 2: Medications (the second pillar, only minimum info!) ---
        var y2 = 48f
        val medsLabel = "💊 ${JemmaTranslations.getLabel(lang, "medications_title")}"
        canvas.drawText(medsLabel, xCol2, y2, boldPaint)
        y2 += 9f

        if (hydrated.medications.isNotEmpty()) {
            val medicationPlan = PdfPillarLayout.plan(hydrated.medications, PdfPillarLayout.MEDICATION_ROWS)
            for (m in medicationPlan.shown) {
                val medName = m.displayLocalized.ifBlank { m.raw.c.orEmpty() }
                val cleanName = if (medName.length > 18) medName.substring(0, 16) + ".." else medName
                val dose = listOfNotNull(
                    m.doseValue?.takeIf { it.isNotBlank() },
                    m.doseUnit?.takeIf { it.isNotBlank() }
                ).joinToString("")
                val medLine = if (dose.isNotEmpty()) "• $cleanName $dose" else "• $cleanName"
                canvas.drawText(medLine, xCol2, y2, normalPaint)
                y2 += 8f
            }
            if (medicationPlan.hasOverflow) {
                val noun = when (lang) {
                    JemmaTextPayloadBuilder.Lang.FR -> "médicaments"
                    JemmaTextPayloadBuilder.Lang.JA -> "種類の薬剤"
                    else -> JemmaTranslations.getLabel(lang, "medications_title").lowercase()
                }
                canvas.drawText("• ${PdfPillarLayout.overflowText(medicationPlan.overflowCount, noun)}", xCol2, y2, overflowPaint)
            }
        } else {
            val emptyStr = JemmaTranslations.getLabel(lang, "empty")
            canvas.drawText(emptyStr, xCol2, y2, grayPaint)
        }


        // 4. Draw Language QR Code directly below (centered in bottom half)
        if (qrBmp != null) {
            val qrSize = 170
            val qrX = 148 - (qrSize / 2) // Centered horizontally
            val qrY = 210
            val qrRect = Rect(qrX, qrY, qrX + qrSize, qrY + qrSize)
            canvas.drawBitmap(qrBmp, null, qrRect, Paint(Paint.FILTER_BITMAP_FLAG))
            // QR Code border
            canvas.drawRect(
                (qrX - 1).toFloat(),
                (qrY - 1).toFloat(),
                (qrX + qrSize + 1).toFloat(),
                (qrY + qrSize + 1).toFloat(),
                Paint().apply {
                    color = Color.parseColor("#CBD5E1")
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
            )

            // Caption under the QR code
            val captionPaint = Paint().apply {
                color = Color.parseColor("#78909C")
                textSize = 7f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val captionStr = JemmaTranslations.getPdfLabel(lang, "caption_scan")
            canvas.drawText(captionStr, 148.75f, (qrY + qrSize + 12).toFloat(), captionPaint)
        }

        // Footer at the very bottom
        val footerPaint = Paint().apply {
            color = Color.parseColor("#90A4AE")
            textSize = 6f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val footerStr = JemmaTranslations.getPdfLabel(lang, "page2_footer")
        canvas.drawText(footerStr, 148.75f, 403f, footerPaint)
    }

    private fun drawQrCodesPage(
        canvas: Canvas,
        context: Context,
        raw: JemmaProfileJ,
        qrText: Bitmap?,
        qrCompact: Bitmap?,
        currentLang: JemmaTextPayloadBuilder.Lang
    ) {
        val p = raw.p ?: return

        // 1. Draw Page Background (Pure white)
        val pageBgPaint = Paint().apply {
            color = Color.parseColor("#FFFFFF")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 842f, pageBgPaint)

        // 2. Draw Premium Header Banner (Eco Light theme, no heavy dark fills)
        val headerRectPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9") // Soft grey
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, 595f, 100f, headerRectPaint)
        
        canvas.drawLine(0f, 100f, 595f, 100f, Paint().apply {
            color = Color.parseColor("#00796B")
            strokeWidth = 1.5f
            isAntiAlias = true
        })

        // Draw JemmaPass logo in the header
        try {
            val logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.jemmapass_icon)
            if (logoBmp != null) {
                val dstRect = Rect(24, 25, 74, 75)
                canvas.drawBitmap(logoBmp, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading logo for Page 2 Header", e)
        }

        // Header Titles (Dark blue/grey on light banner)
        val headerTitlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val pageTitle = JemmaTranslations.getPdfLabel(currentLang, "page_title")
        canvas.drawText(pageTitle, 88f, 48f, headerTitlePaint)

        val patientName = "${p.gn ?: ""} ${p.fn ?: ""}".trim().ifEmpty { "Patient Profile" }
        val headerSubPaint = Paint().apply {
            color = Color.parseColor("#00796B")
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subText = "${JemmaTranslations.getPdfLabel(currentLang, "vital_profile").ifEmpty { "VITAL PROFILE" }}: $patientName  ·  ${JemmaTranslations.getPdfLabel(currentLang, "born").uppercase()}: ${p.bd ?: "--"}"
        canvas.drawText(subText, 88f, 70f, headerSubPaint)

        // 3. Draw QR 1 Card (Universal Emoji Summary)
        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val cardBgPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC") // slate-50 fill
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Card 1 Rect: x from 20 to 575, y from 130 to 450
        canvas.drawRoundRect(20f, 130f, 575f, 450f, 16f, 16f, cardBgPaint)
        canvas.drawRoundRect(20f, 130f, 575f, 450f, 16f, 16f, cardBorderPaint)

        // Title and description on the left of QR 1
        val cardTitlePaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val card1Title = JemmaTranslations.getPdfLabel(currentLang, "card1_title")
        canvas.drawText(card1Title, 45f, 175f, cardTitlePaint)

        val cardDescPaint = Paint().apply {
            color = Color.parseColor("#475569")
            textSize = 9.5f
            isAntiAlias = true
        }

        val descLines1 = JemmaTranslations.getDescLines1(currentLang)
        var textY = 205f
        for (line in descLines1) {
            canvas.drawText(line, 45f, textY, cardDescPaint)
            textY += 16f
        }

        // Draw QR Code 1 (HUGE!)
        if (qrText != null) {
            val qrRect = Rect(330, 160, 540, 370) // 210 x 210 size!
            canvas.drawBitmap(qrText, null, qrRect, Paint(Paint.FILTER_BITMAP_FLAG))
            // QR Code border
            canvas.drawRect(328f, 158f, 542f, 372f, Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            })
        } else {
            canvas.drawText("[QR Code Generation Failed]", 360f, 250f, cardTitlePaint.apply { color = Color.RED })
        }

        // 4. Draw QR 2 Card (Compact FHIR Record)
        // Card 2 Rect: x from 20 to 575, y from 480 to 800
        canvas.drawRoundRect(20f, 480f, 575f, 800f, 16f, 16f, cardBgPaint)
        canvas.drawRoundRect(20f, 480f, 575f, 800f, 16f, 16f, cardBorderPaint)

        // Title and description on the left of QR 2
        val card2Title = JemmaTranslations.getPdfLabel(currentLang, "card2_title")
        canvas.drawText(card2Title, 45f, 525f, cardTitlePaint.apply { color = Color.parseColor("#0F172A") })

        val descLines2 = JemmaTranslations.getDescLines2(currentLang)
        textY = 555f
        for (line in descLines2) {
            canvas.drawText(line, 45f, textY, cardDescPaint)
            textY += 16f
        }

        // Draw QR Code 2 (HUGE!)
        if (qrCompact != null) {
            val qrRect = Rect(330, 510, 540, 720) // 210 x 210 size!
            canvas.drawBitmap(qrCompact, null, qrRect, Paint(Paint.FILTER_BITMAP_FLAG))
            // QR Code border
            canvas.drawRect(328f, 508f, 542f, 722f, Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
                style = Paint.Style.STROKE
            })
        } else {
            canvas.drawText("[QR Code Generation Failed]", 360f, 600f, cardTitlePaint.apply { color = Color.RED })
        }

        // 5. Draw Page 2 Footer
        val pageFooterPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val page2FooterText = JemmaTranslations.getPdfLabel(currentLang, "page2_footer")
        canvas.drawText(page2FooterText, 297.5f, 825f, pageFooterPaint)
    }
}
