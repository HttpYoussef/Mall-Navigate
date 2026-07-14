package com.example.mallar.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ParkingOcrParser {
    fun parse(text: String): ParsedParking {
        val logTag = "ParkingOcrParser"
        android.util.Log.d(logTag, "Starting parsing on text:\n$text")
        
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        android.util.Log.d(logTag, "Parsed lines: $lines")
        
        var zone: String = ""
        var slot: String = ""
        var floor: String = ""

        // Regex for Floor: e.g. P1, P2, Floor 2, Level 3, B2, Floor P1, P-1
        val floorRegex = Regex("""\b(P-?[1-9]|Floor\s*-?[P1-9]|Level\s*-?[1-9]|B-?[1-9])\b""", RegexOption.IGNORE_CASE)
        // Regex for Zone-Slot: e.g. B-12, A-07, B12, C 03, Zone B
        val zoneSlotRegex = Regex("""\b([A-Z])\s*[-–]?\s*([0-9]{1,4})\b""", RegexOption.IGNORE_CASE)
        // Regex for standalone Zone: e.g. "Zone A" or "Zone: C"
        val standaloneZoneRegex = Regex("""\bZone\s*:?\s*([A-Z])\b""", RegexOption.IGNORE_CASE)

        // Step 1: Scan for explicit patterns (Zone-Slot and Floor)
        for (line in lines) {
            android.util.Log.d(logTag, "Processing line for regex matching: '$line'")
            
            // Check for zone-slot match
            val zoneSlotMatch = zoneSlotRegex.find(line)
            if (zoneSlotMatch != null) {
                val matchedZone = zoneSlotMatch.groupValues[1].uppercase()
                val matchedSlot = zoneSlotMatch.groupValues[2]
                android.util.Log.d(logTag, "Found zone-slot match: zone='$matchedZone', slot='$matchedSlot' in line '$line'")
                if (zone.isEmpty()) zone = matchedZone
                if (slot.isEmpty()) slot = matchedSlot
            }

            // Check for floor match
            val floorMatch = floorRegex.find(line)
            if (floorMatch != null) {
                val matchedFloor = floorMatch.groupValues[1].uppercase().replace(" ", "")
                android.util.Log.d(logTag, "Found floor match: '$matchedFloor' in line '$line'")
                if (floor.isEmpty()) floor = matchedFloor
            }

            // Check for standalone zone match
            val zoneMatch = standaloneZoneRegex.find(line)
            if (zoneMatch != null) {
                val matchedZone = zoneMatch.groupValues[1].uppercase()
                android.util.Log.d(logTag, "Found standalone zone match: '$matchedZone' in line '$line'")
                if (zone.isEmpty()) zone = matchedZone
            }
        }

        // Step 2: Fallback extraction for Zone and Slot if they are still empty
        if (zone.isEmpty() || slot.isEmpty()) {
            android.util.Log.d(logTag, "Zone or Slot is empty after regex pass. Running fallbacks...")
            for (line in lines) {
                val cleanLine = line.replace(" ", "")
                android.util.Log.d(logTag, "Evaluating line for fallback: '$line' (clean: '$cleanLine')")
                
                // If a line is just letter+digits (e.g., "B12", "A05")
                if (cleanLine.length in 2..5) {
                    val firstChar = cleanLine.firstOrNull()
                    if (firstChar != null && firstChar.isLetter()) {
                        val remaining = cleanLine.substring(1)
                        if (remaining.all { it.isDigit() }) {
                            val matchedZone = firstChar.toString().uppercase()
                            val matchedSlot = remaining
                            android.util.Log.d(logTag, "Fallback matched letter-digits pattern: zone='$matchedZone', slot='$matchedSlot'")
                            if (zone.isEmpty()) zone = matchedZone
                            if (slot.isEmpty()) slot = matchedSlot
                            continue
                        }
                    }
                }

                // If a line is a standalone number (e.g. "14", "102") and we don't have a slot yet
                if (slot.isEmpty() && cleanLine.all { it.isDigit() } && cleanLine.length in 1..4) {
                    android.util.Log.d(logTag, "Fallback matched standalone slot number: '$cleanLine'")
                    slot = cleanLine
                    continue
                }

                // If a line is a standalone letter (e.g., "A", "B") and we don't have a zone yet
                if (zone.isEmpty() && cleanLine.length == 1 && cleanLine[0].isLetter()) {
                    val matchedZone = cleanLine.uppercase()
                    android.util.Log.d(logTag, "Fallback matched standalone zone letter: '$matchedZone'")
                    zone = matchedZone
                    continue
                }
            }
        }

        // Step 3: Fallback extraction for Floor if it is still empty
        if (floor.isEmpty()) {
            android.util.Log.d(logTag, "Floor is empty after regex pass. Running floor fallbacks...")
            for (line in lines) {
                val clean = line.replace(" ", "").uppercase()
                android.util.Log.d(logTag, "Evaluating line for floor fallback: '$line' (clean: '$clean')")
                if (clean == "P1" || clean == "P2" || clean == "P3" || clean == "B1" || clean == "B2") {
                    android.util.Log.d(logTag, "Floor fallback matched standard code: '$clean'")
                    floor = clean
                    break
                }
                
                // If it's a negative number (e.g., "-3" or "-2") or a single digit floor number (e.g., "1" or "2" or "3")
                val isNegativeNumber = clean.startsWith("-") && clean.substring(1).all { it.isDigit() }
                val isSingleDigitFloor = clean.length == 1 && clean[0].isDigit()
                if (isNegativeNumber || isSingleDigitFloor) {
                    android.util.Log.d(logTag, "Floor fallback matched number format: '$clean'")
                    floor = clean
                    break
                }
            }
        }

        // Step 4: Detect fallbacks without pre-filling fake defaults in fields
        val isZoneFallback = zone.isEmpty()
        val isSlotFallback = slot.isEmpty()
        val isFloorFallback = floor.isEmpty()

        android.util.Log.d(logTag, "Final parsing output: Zone='$zone', Slot='$slot', Floor='$floor', ZoneFallback=$isZoneFallback, SlotFallback=$isSlotFallback, FloorFallback=$isFloorFallback")
        return ParsedParking(
            zone = zone,
            slot = slot,
            floor = floor,
            rawOcrText = text,
            isZoneFallback = isZoneFallback,
            isSlotFallback = isSlotFallback,
            isFloorFallback = isFloorFallback
        )
    }
}

data class ParsedParking(
    val zone: String,
    val slot: String,
    val floor: String,
    val rawOcrText: String,
    val isZoneFallback: Boolean,
    val isSlotFallback: Boolean,
    val isFloorFallback: Boolean
)

object ParkingOcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): ParsedParking = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                android.util.Log.d("ParkingOcrEngine", "OCR successfully recognized text:\n$text")
                val parsed = ParkingOcrParser.parse(text)
                android.util.Log.d("ParkingOcrEngine", "Parsed results: Zone='${parsed.zone}', Slot='${parsed.slot}', Floor='${parsed.floor}'")
                continuation.resume(parsed)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ParkingOcrEngine", "OCR processing failed with exception", e)
                continuation.resume(
                    ParsedParking(
                        zone = "",
                        slot = "",
                        floor = "",
                        rawOcrText = "Error: ${e.localizedMessage ?: "OCR failed"}",
                        isZoneFallback = true,
                        isSlotFallback = true,
                        isFloorFallback = true
                    )
                )
            }
    }
}
