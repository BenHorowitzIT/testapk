package com.example.engine

import android.content.res.Resources
import android.os.Build
import android.os.Process
import java.io.File
import java.util.Locale

/**
 * Chooses which split APKs of an XAPK/APKS this phone actually needs, the way the
 * Play Store and SAI do: the base, feature splits, one CPU (ABI) split, one screen
 * density split, and the phone's language + English. Everything else is skipped,
 * which makes merging much lighter.
 */
object SplitPicker {

    private val ABIS = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86", "x86_64", "mips", "mips64")

    private val DENSITIES = linkedMapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
        "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640
    )

    private sealed class Kind {
        object Base : Kind()
        data class Abi(val abi: String) : Kind()
        data class Density(val dpi: Int) : Kind()
        data class Language(val lang: String) : Kind()
        object Other : Kind()
    }

    private fun kindOf(entry: String, baseEntry: String?): Kind {
        if (entry == baseEntry) return Kind.Base
        var name = File(entry).name.removeSuffix(".apk").removeSuffix(".APK")
        name = name.removePrefix("split_")
        if (!name.startsWith("config.")) return Kind.Other // base or feature split: always keep
        val q = name.removePrefix("config.")
        return when {
            q in ABIS -> Kind.Abi(q)
            DENSITIES.containsKey(q) -> Kind.Density(DENSITIES.getValue(q))
            q.length in 2..3 && q.all { it.isLetter() } -> Kind.Language(q.lowercase(Locale.ROOT))
            q.matches(Regex("[a-zA-Z]{2,3}[_-][a-zA-Z0-9]{2,4}")) ->
                Kind.Language(q.substring(0, q.indexOfFirst { it == '_' || it == '-' }).lowercase(Locale.ROOT))
            else -> Kind.Other
        }
    }

    fun pick(apkEntries: List<String>, baseEntry: String?): List<String> {
        if (apkEntries.size <= 1) return apkEntries
        val kinds = apkEntries.associateWith { kindOf(it, baseEntry) }

        // CPU: the sandbox runs apps with VelvetBox's own bitness
        val wantedAbis = (if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS)
            .map { it.replace('-', '_') }
        val abiSplits = kinds.filterValues { it is Kind.Abi }.keys
        val chosenAbi: Set<String> = run {
            for (abi in wantedAbis) {
                val match = abiSplits.filter { (kinds[it] as Kind.Abi).abi == abi }
                if (match.isNotEmpty()) return@run match.toSet()
            }
            abiSplits // none match: keep them so the engine can explain the problem
        }

        // Screen density: closest to this phone, preferring the next one up
        val deviceDpi = Resources.getSystem().displayMetrics.densityDpi
        val densitySplits = kinds.filterValues { it is Kind.Density }.keys
        val chosenDensity: Set<String> = if (densitySplits.isEmpty()) emptySet() else {
            val best = densitySplits.minByOrNull { entry ->
                val dpi = (kinds[entry] as Kind.Density).dpi
                if (dpi >= deviceDpi) dpi - deviceDpi else (deviceDpi - dpi) * 2 + 10_000
            }
            setOfNotNull(best)
        }

        // Languages: phone language + English
        val wantedLangs = setOf(Locale.getDefault().language.lowercase(Locale.ROOT), "en")
        val langSplits = kinds.filterValues { it is Kind.Language }.keys
        val chosenLang: Set<String> = langSplits
            .filter { (kinds[it] as Kind.Language).lang in wantedLangs }
            .toSet()
            .ifEmpty { langSplits.take(1).toSet() }

        return apkEntries.filter { entry ->
            when (kinds[entry]) {
                is Kind.Abi -> entry in chosenAbi
                is Kind.Density -> entry in chosenDensity
                is Kind.Language -> entry in chosenLang
                else -> true // base, feature splits, unknown splits
            }
        }
    }
}
