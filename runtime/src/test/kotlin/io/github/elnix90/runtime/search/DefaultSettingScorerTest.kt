package io.github.elnix90.runtime.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the default fuzzy scorer. The algorithm is pure JVM, so every
 * regression-prone behavior (normalization, diacritics, grading, ranking) is
 * covered here without Android.
 */
internal class DefaultSettingScorerTest {
	@Test
	fun `normalize lowercases strips diacritics and tokenizes`() {
		assertEquals(listOf("creme", "brulee"), DefaultSettingScorer.normalize("  Crème Brûlée  "))
	}

	@Test
	fun `normalize of blank input is empty`() {
		assertEquals(emptyList(), DefaultSettingScorer.normalize("   "))
	}

	@Test
	fun `prefix match on title and key scores title weight plus key weight`() {
		val score = DefaultSettingScorer.score("dark", "Dark Mode", null, "dark_mode")

		assertEquals(4.0f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `substring match scores seventy percent of the field weight`() {
		val score = DefaultSettingScorer.score("ark", "Dark Mode", null, "unrelated")

		assertEquals(0.7f * 3f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `description contributes with its own weight`() {
		val score = DefaultSettingScorer.score("battery", "Dark Mode", "Saves battery life", "dark_mode")

		// Only the description matches: the query token equals the "battery" token
		// exactly (1.0), weighted by DESCRIPTION_WEIGHT; title and key contribute 0.
		assertEquals(2.0f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `diacritics-insensitive query matches diacritics title`() {
		val score = DefaultSettingScorer.score("creme", "Crème Brûlée", null, "creme")

		assertEquals(4.0f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `unrelated query scores zero`() {
		val score = DefaultSettingScorer.score("zzz", "Dark Mode", null, "dark_mode")

		assertEquals(0f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `blank query scores zero`() {
		assertEquals(0f, DefaultSettingScorer.score("", "Dark Mode", null, "dark_mode"), absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `short tokens are not fuzzy matched to avoid noise`() {
		// "dk" is a subsequence of "dark" but too short for the fuzzy grade.
		val score = DefaultSettingScorer.score("dk", "Dark", null, "unrelated")

		assertEquals(0f, score, absoluteTolerance = TOLERANCE)
	}

	@Test
	fun `title outranks key for the same query`() {
		val byTitle = DefaultSettingScorer.score("alpha", "Alpha", null, "unrelated")
		val byKey = DefaultSettingScorer.score("alpha", "Unrelated", null, "alpha")

		assertTrue(byTitle > byKey, "Expected the title match ($byTitle) to outrank the key match ($byKey)")
	}

	private companion object {
		private const val TOLERANCE = 0.001f
	}
}
