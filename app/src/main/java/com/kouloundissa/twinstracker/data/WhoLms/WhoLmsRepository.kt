package com.kouloundissa.twinstracker.data.WhoLms

import android.content.Context
import com.kouloundissa.twinstracker.data.Gender
import kotlin.math.pow

data class LmsPoint(val ageDays: Int, val l: Double, val m: Double, val s: Double)

object WhoLmsRepository {
    private val cache = mutableMapOf<String, List<LmsPoint>>()

    // Update the filename pattern in WhoLmsRepository
    fun loadLms(context: Context, measure: String, gender: Gender): List<LmsPoint> {
        val key = "${measure}_${gender.name.lowercase()}"
        return cache.getOrPut(key) {
            // Use the enhanced CSV files
            val filename = "who_${measure}_lms_${gender.name.lowercase()}_enhanced.csv"
            try {
                context.assets.open(filename).bufferedReader().useLines { lines ->
                    lines.drop(1).map { line ->
                        val parts = line.split(',').map(String::trim)
                        LmsPoint(
                            ageDays = parts[0].toInt(),
                            l = parts[1].toDouble(),
                            m = parts[2].toDouble(),
                            s = parts[3].toDouble()
                        )
                    }.toList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    fun percentileCurveInRange(
        context: Context,
        measure: String,
        gender: Gender,
        percentile: Double,
        startAge: Int,
        endAge: Int
    ): List<Pair<Float, Float>> {
        val z = percentileToZ(percentile)
        val lmsPoints = loadLms(context, measure, gender)  // Sorted by ageDays

        // Generate list of days in range
        val daysRange = (startAge..endAge).toList()

        // Interpolate values for each day in 7-day range
        return daysRange.map { day ->
            // Find LMS segment enclosing day
            val before = lmsPoints.lastOrNull { it.ageDays <= day } ?: lmsPoints.first()
            val after = lmsPoints.firstOrNull { it.ageDays >= day } ?: lmsPoints.last()

            val (l, m, s) = if (before.ageDays == after.ageDays) {
                Triple(before.l, before.m, before.s)
            } else {
                // Linear interpolation of L, M, S
                val ratio = (day - before.ageDays).toDouble() / (after.ageDays - before.ageDays)
                val lInterp = before.l + ratio * (after.l - before.l)
                val mInterp = before.m + ratio * (after.m - before.m)
                val sInterp = before.s + ratio * (after.s - before.s)
                Triple(lInterp, mInterp, sInterp)
            }

            val value = if (kotlin.math.abs(l) < 0.001) {
                m * kotlin.math.exp(s * z)
            } else {
                m * (1 + l * s * z).pow(1 / l)
            }
            day.toFloat() to value.toFloat()
        }
    }


    /** Computes value at given percentile using LMS formula:
     *  X = M * (1 + L*S*Z)^(1/L)  if L!=0; else X = M*exp(S*Z)
     */
    fun percentileCurve(
        context: Context,
        measure: String,
        gender: Gender,
        percentile: Double
    ): List<Pair<Float, Float>> {
        val z = percentileToZ(percentile)
        return loadLms(context, measure, gender).map { pt ->
            val x = if (kotlin.math.abs(pt.l) < 0.001) {
                pt.m * kotlin.math.exp(pt.s * z)
            } else {
                pt.m * (1 + pt.l * pt.s * z).pow(1 / pt.l)
            }
            pt.ageDays.toFloat() to x.toFloat()
        }
    }

    private fun percentileToZ(percentile: Double): Double {
        val p = percentile / 100.0

        require(p > 0.0 && p < 1.0) { "Percentile must be between 0 and 100 (exclusive)" }

        // Coefficients for the approximation
        val c0 = 2.515517
        val c1 = 0.802853
        val c2 = 0.010328
        val d1 = 1.432788
        val d2 = 0.189269
        val d3 = 0.001308

        val q = if (p < 0.5) p else 1.0 - p
        val t = kotlin.math.sqrt(-2.0 * kotlin.math.ln(q))
        val numerator = c0 + c1 * t + c2 * t * t
        val denominator = 1.0 + d1 * t + d2 * t * t + d3 * t * t * t
        val z = t - numerator / denominator

        return if (p > 0.5) z else -z
    }

}
