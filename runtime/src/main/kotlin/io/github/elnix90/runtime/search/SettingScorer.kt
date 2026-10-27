package io.github.elnix90.runtime.search

import kotlin.math.abs

/**
 * Strategy used by [SettingsSearchIndex] to score how relevant a setting is for
 * a search query.
 *
 * Implement this interface to swap the default fuzzy-matching algorithm for a
 * custom one (e.g. phonetic matching or an ML model) and pass it to the
 * [SettingsSearchIndex] constructor.
 */
public fun interface SettingScorer {
	/**
	 * Scores a single setting against [query]. Higher scores mean more relevant;
	 * [SettingsSearchIndex] drops results below its configured minimum and sorts
	 * the rest in descending order.
	 *
	 * @param query Raw user input, already trimmed.
	 * @param title Localized title of the setting.
	 * @param description Localized description of the setting, when it has one.
	 * @param key Stable key of the setting.
	 */
	public fun score(
		query: String,
		title: String,
		description: String?,
		key: String
	): Float
}

/**
 * Default fuzzy scorer: token-based matching over the normalized title,
 * description and key of a setting.
 *
 * Normalization lowercases the text, strips diacritics (`java.text.Normalizer`)
 * and splits it into whitespace tokens, so `Crème` matches `creme` regardless of
 * the device locale or keyboard.
 *
 * Each query token is matched against every token of a field with one of three
 * grades:
 *
 * - exact or prefix match → 1.0
 * - substring match → 0.7
 * - fuzzy match (subsequence or Levenshtein distance ≤ 2, for tokens of at
 *   least 3 characters) → 0.3
 *
 * The best grade per field is weighted (title > description > key) and the
 * weighted grades are summed into the final score. The whole algorithm is pure
 * JVM so it is unit-tested without Android.
 */
public object DefaultSettingScorer : SettingScorer {
	/** Score multiplier of the title field. */
	private const val TITLE_WEIGHT = 3f

	/** Score multiplier of the description field. */
	private const val DESCRIPTION_WEIGHT = 2f

	/** Score multiplier of the key field. */
	private const val KEY_WEIGHT = 1f

	/** Grade of an exact or prefix match. */
	private const val EXACT_OR_PREFIX = 1.0f

	/** Grade of a substring match. */
	private const val SUBSTRING = 0.7f

	/** Grade of a fuzzy (subsequence or near-constant) match. */
	private const val FUZZY = 0.3f

	/** Shortest query token eligible for fuzzy matching, to avoid noise. */
	private const val MIN_FUZZY_LENGTH = 3

	/** Maximum Levenshtein distance considered a fuzzy match. */
	private const val MAX_LEVENSHTEIN_DISTANCE = 2

	private val combiningMarks = Regex("\\p{M}+")
	private val whitespace = Regex("\\s+")

	override fun score(
		query: String,
		title: String,
		description: String?,
		key: String
	): Float {
		val queryTokens = normalize(query)
		if (queryTokens.isEmpty()) return 0f

		val titleScore = bestMatch(queryTokens, normalize(title)) * TITLE_WEIGHT
		val descriptionScore = bestMatch(queryTokens, normalize(description.orEmpty())) * DESCRIPTION_WEIGHT
		val keyScore = bestMatch(queryTokens, normalize(key)) * KEY_WEIGHT

		return titleScore + descriptionScore + keyScore
	}

	/**
	 * Lowercases [text], strips diacritics and splits it into whitespace tokens.
	 * Exposed publicly so scoring behavior is transparent and testable.
	 */
	public fun normalize(text: String): List<String> {
		if (text.isBlank()) return emptyList()

		val deaccented =
			java.text.Normalizer
				.normalize(text, java.text.Normalizer.Form.NFD)
				.replace(combiningMarks, "")

		return deaccented
			.lowercase()
			.split(whitespace)
			.filter { it.isNotBlank() }
	}

	/** Best grade any query token achieves against any token of the field. */
	private fun bestMatch(
		queryTokens: List<String>,
		fieldTokens: List<String>
	): Float {
		if (fieldTokens.isEmpty()) return 0f

		return queryTokens.maxOfOrNull { queryToken ->
			fieldTokens.maxOfOrNull { fieldToken -> grade(queryToken, fieldToken) } ?: 0f
		} ?: 0f
	}

	private fun grade(
		queryToken: String,
		fieldToken: String
	): Float =
		when {
			fieldToken == queryToken || fieldToken.startsWith(queryToken) -> EXACT_OR_PREFIX

			fieldToken.contains(queryToken) -> SUBSTRING

			queryToken.length >= MIN_FUZZY_LENGTH &&

				fieldToken.contains(queryToken) &&
				(isSubsequence(queryToken, fieldToken) || levenshteinWithin(queryToken, fieldToken, MAX_LEVENSHTEIN_DISTANCE)) -> FUZZY

			else -> 0f
		}

	/** Returns true when every character of [needle] appears in order in [haystack]. */
	private fun isSubsequence(
		needle: String,
		haystack: String
	): Boolean {
		var index = 0
		for (character in haystack) {
			if (character == needle[index]) {
				index++
				if (index == needle.length) return true
			}
		}
		return false
	}

	/**
	 * Returns true when the **[Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)** between [a] and [b] is at most
	 * [maxDistance]. The DP rows are abandoned as soon as they exceed the
	 * bound, keeping the cost negligible for search-as-you-type.
	 */
	private fun levenshteinWithin(
		a: String,
		b: String,
		maxDistance: Int
	): Boolean {
		if (abs(a.length - b.length) > maxDistance) return false

		var previousRow = IntArray(b.length + 1) { it }

		for (i in 1..a.length) {
			val currentRow = IntArray(b.length + 1)
			currentRow[0] = i
			var rowMinimum = currentRow[0]

			for (j in 1..b.length) {
				val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
				currentRow[j] =
					minOf(
						previousRow[j] + 1,
						currentRow[j - 1] + 1,
						previousRow[j - 1] + substitutionCost
					)
				rowMinimum = minOf(rowMinimum, currentRow[j])
			}

			if (rowMinimum > maxDistance) return false
			previousRow = currentRow
		}

		return previousRow[b.length] <= maxDistance
	}
}
