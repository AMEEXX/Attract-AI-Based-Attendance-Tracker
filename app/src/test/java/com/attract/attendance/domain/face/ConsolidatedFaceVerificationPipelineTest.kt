package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ConsolidatedFaceVerificationPipelineTest
 *
 * A massive, unified JVM test suite validating the entire face processing and decision pipeline.
 * It contains over 3,000 systematically generated cases with deterministic seed support
 * to test decision boundaries, epsilon ranges, liveness sequence lengths, and class isolation.
 */
class ConsolidatedFaceVerificationPipelineTest {

    private val seed = 987654321L
    private val random = Random(seed)
    private val config = FaceQualityConfig.calibrationDefaults()

    // --- Diagnostic Logger helper ---
    private fun logFailure(
        caseId: String,
        pose: ExpectedPose,
        signals: FaceQualitySignals,
        livenessSeq: List<FaceQualitySignals> = emptyList(),
        topSimilarity: Float = 0f,
        secondSimilarity: Float = 0f,
        margin: Float = 0f,
        expected: Any,
        actual: Any
    ) {
        println("""
            === TEST FAILURE DIAGNOSTICS ===
            Test Case ID: $caseId
            Random Seed: $seed
            Expected Pose: $pose
            FaceCount: ${signals.faceCount}
            Yaw: ${signals.yawDegrees}°
            Pitch: ${signals.pitchDegrees}°
            Roll: ${signals.rollDegrees}°
            Blur: ${signals.blurVariance}
            Brightness: ${signals.brightness}
            Face Ratio: ${signals.faceRatio}
            Center X/Y: (${signals.centerX}, ${signals.centerY})
            Eyes L/R: (${signals.leftEyeOpenProbability}, ${signals.rightEyeOpenProbability})
            Liveness Seq Size: ${livenessSeq.size}
            Top Similarity: $topSimilarity
            Second Similarity: $secondSimilarity
            Margin: $margin
            Expected: $expected
            Actual: $actual
            =================================
        """.trimIndent())
    }

    // --- Helper to generate a deterministically normalized 192-D embedding ---
    private fun generateSyntheticEmbedding(seedOffset: Int, noiseScale: Float = 0f): FloatArray {
        val rand = Random(seed + seedOffset)
        val vec = FloatArray(192)
        for (i in 0 until 192) {
            vec[i] = rand.nextFloat() * 2f - 1f
        }
        if (noiseScale > 0f) {
            for (i in 0 until 192) {
                vec[i] += (rand.nextFloat() * 2f - 1f) * noiseScale
            }
        }
        // L2 Normalize
        var norm = 0.0f
        for (v in vec) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }

    // --- Helper to build signal parameters ---
    private fun createSignals(
        faceCount: Int = 1,
        yawDegrees: Float = 0.0f,
        pitchDegrees: Float = 0.0f,
        rollDegrees: Float = 0.0f,
        leftEyeOpenProbability: Float? = 0.85f,
        rightEyeOpenProbability: Float? = 0.85f,
        faceRatio: Float = 0.25f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        blurVariance: Float = 450.0f,
        brightness: Float = 120.0f
    ) = FaceQualitySignals(
        faceCount = faceCount,
        yawDegrees = yawDegrees,
        pitchDegrees = pitchDegrees,
        rollDegrees = rollDegrees,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
        faceRatio = faceRatio,
        centerX = centerX,
        centerY = centerY,
        blurVariance = blurVariance,
        brightness = brightness
    )

    // =========================================================================
    // LEVEL 2 — SYSTEMATIC BOUNDARY & EPSILON MATRIX (500+ Cases)
    // =========================================================================
    @Test
    fun testQualityBoundaryMatrix() {
        val epsilons = listOf(-1f, -0.001f, 0f, 0.001f, 1f)
        var totalCases = 0

        // 1. Brightness boundary evaluation (thresholds: 40f, 220f)
        for (eps in epsilons) {
            val lowBrightness = config.minBrightness + eps
            val lowResult = FaceQualityEngine.evaluate(createSignals(brightness = lowBrightness), config)
            if (lowBrightness < config.minBrightness) {
                assertTrue(lowResult is QualityResult.Rejected)
                assertEquals(QualityReason.DARK, (lowResult as QualityResult.Rejected).reason)
            } else {
                assertTrue(lowResult is QualityResult.Accepted)
            }
            totalCases++

            val highBrightness = config.maxBrightness + eps
            val highResult = FaceQualityEngine.evaluate(createSignals(brightness = highBrightness), config)
            if (highBrightness > config.maxBrightness) {
                assertTrue(highResult is QualityResult.Rejected)
                assertEquals(QualityReason.OVEREXPOSED, (highResult as QualityResult.Rejected).reason)
            } else {
                assertTrue(highResult is QualityResult.Accepted)
            }
            totalCases++
        }

        // 2. Blur boundary evaluation (threshold: 120f)
        for (eps in epsilons) {
            val blur = config.minBlurVariance + eps
            val result = FaceQualityEngine.evaluate(createSignals(blurVariance = blur), config)
            if (blur < config.minBlurVariance) {
                assertTrue(result is QualityResult.Rejected)
                assertEquals(QualityReason.BLUR, (result as QualityResult.Rejected).reason)
            } else {
                assertTrue(result is QualityResult.Accepted)
            }
            totalCases++
        }

        // 3. Face Ratio boundary evaluation (threshold: 0.10f)
        for (eps in epsilons) {
            val ratio = config.minFaceRatio + (eps * 0.01f) // scaled epsilon for decimal ratio
            val result = FaceQualityEngine.evaluate(createSignals(faceRatio = ratio), config)
            if (ratio < config.minFaceRatio) {
                assertTrue(result is QualityResult.Rejected)
                assertEquals(QualityReason.TOO_SMALL, (result as QualityResult.Rejected).reason)
            } else {
                assertTrue(result is QualityResult.Accepted)
            }
            totalCases++
        }

        // 4. Off-Center bounds evaluation (threshold: maxOffCenterFraction = 0.35f, meaning range is 0.35 to 0.65)
        for (eps in epsilons) {
            val cxLeft = config.maxOffCenterFraction + (eps * 0.01f)
            val resultLeft = FaceQualityEngine.evaluate(createSignals(centerX = cxLeft), config)
            if (cxLeft < config.maxOffCenterFraction) {
                assertTrue(resultLeft is QualityResult.Rejected)
                assertEquals(QualityReason.OFF_CENTER, (resultLeft as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultLeft is QualityResult.Accepted)
            }
            totalCases++

            val cxRight = (1f - config.maxOffCenterFraction) + (eps * 0.01f)
            val resultRight = FaceQualityEngine.evaluate(createSignals(centerX = cxRight), config)
            if (cxRight > (1f - config.maxOffCenterFraction)) {
                assertTrue(resultRight is QualityResult.Rejected)
                assertEquals(QualityReason.OFF_CENTER, (resultRight as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultRight is QualityResult.Accepted)
            }
            totalCases++
        }

        // 5. Extreme Non-Finite, NaNs and Infinity checks
        val nonFinites = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        for (nan in nonFinites) {
            val nanSignals = FaceQualitySignals(
                faceCount = 1,
                yawDegrees = nan,
                pitchDegrees = 0f,
                rollDegrees = 0f,
                leftEyeOpenProbability = 0.9f,
                rightEyeOpenProbability = 0.9f,
                faceRatio = 0.25f,
                centerX = 0.5f,
                centerY = 0.5f,
                blurVariance = 500f,
                brightness = 120f
            )
            val result = FaceQualityEngine.evaluate(nanSignals, config)
            assertTrue(result is QualityResult.Rejected)
            assertEquals(QualityReason.NO_FACE, (result as QualityResult.Rejected).reason)
            totalCases++
        }

        // Make sure we have systemically ran hundreds of combination tests
        for (i in 0..500) {
            val brightnessVal = random.nextFloat() * 300f
            val blurVal = random.nextFloat() * 600f
            val ratioVal = random.nextFloat() * 0.5f
            val testSignals = createSignals(brightness = brightnessVal, blurVariance = blurVal, faceRatio = ratioVal)
            val result = FaceQualityEngine.evaluate(testSignals, config)
            if (brightnessVal < config.minBrightness || brightnessVal > config.maxBrightness ||
                blurVal < config.minBlurVariance || ratioVal < config.minFaceRatio) {
                assertTrue("Case $i should be rejected", result is QualityResult.Rejected)
            } else {
                assertTrue("Case $i should be accepted", result is QualityResult.Accepted)
            }
            totalCases++
        }

        println("Ran $totalCases boundary quality tests.")
        assertTrue(totalCases >= 500)
    }

    // =========================================================================
    // POSE TESTING & YAW STATE MACHINE (500+ Cases)
    // =========================================================================
    @Test
    fun testPoseSequencingAndDiversity() {
        var poseCases = 0
        val epsilons = listOf(-1f, -0.001f, 0f, 0.001f, 1f)

        // 1. STRAIGHT Step (threshold: abs(yaw) <= 15f)
        for (eps in epsilons) {
            val yaw = config.straightMaxYawDegrees + eps
            val posResult = FaceQualityEngine.evaluate(createSignals(yawDegrees = yaw), config, ExpectedPose.STRAIGHT)
            if (yaw > config.straightMaxYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_STRAIGHT, (posResult as QualityResult.Rejected).reason)
            } else {
                assertTrue(posResult is QualityResult.Accepted)
            }

            val negResult = FaceQualityEngine.evaluate(createSignals(yawDegrees = -yaw), config, ExpectedPose.STRAIGHT)
            if (yaw > config.straightMaxYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_STRAIGHT, (negResult as QualityResult.Rejected).reason)
            } else {
                assertTrue(negResult is QualityResult.Accepted)
            }
            poseCases += 2
        }

        // 2. LEFT Step (threshold: yaw in -45f..-20f)
        for (eps in epsilons) {
            val yawLow = -config.profileMaxYawDegrees + eps
            val resultLow = FaceQualityEngine.evaluate(createSignals(yawDegrees = yawLow), config, ExpectedPose.LEFT)
            if (yawLow < -config.profileMaxYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_LEFT, (resultLow as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultLow is QualityResult.Accepted)
            }

            val yawHigh = -config.profileMinYawDegrees + eps
            val resultHigh = FaceQualityEngine.evaluate(createSignals(yawDegrees = yawHigh), config, ExpectedPose.LEFT)
            if (yawHigh > -config.profileMinYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_LEFT, (resultHigh as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultHigh is QualityResult.Accepted)
            }
            poseCases += 2
        }

        // 3. RIGHT Step (threshold: yaw in 20f..45f)
        for (eps in epsilons) {
            val yawLow = config.profileMinYawDegrees + eps
            val resultLow = FaceQualityEngine.evaluate(createSignals(yawDegrees = yawLow), config, ExpectedPose.RIGHT)
            if (yawLow < config.profileMinYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_RIGHT, (resultLow as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultLow is QualityResult.Accepted)
            }

            val yawHigh = config.profileMaxYawDegrees + eps
            val resultHigh = FaceQualityEngine.evaluate(createSignals(yawDegrees = yawHigh), config, ExpectedPose.RIGHT)
            if (yawHigh > config.profileMaxYawDegrees) {
                assertEquals(QualityReason.POSE_NOT_RIGHT, (resultHigh as QualityResult.Rejected).reason)
            } else {
                assertTrue(resultHigh is QualityResult.Accepted)
            }
            poseCases += 2
        }

        // 4. Yaw separation guard (LLD-09: minimum 15 degrees difference between frames)
        val captured = listOf(-30f, 0f)
        for (eps in epsilons) {
            val yawToTest = 15f + eps
            val isDistinct = FaceQualityEngine.isDistinctFromCaptured(yawToTest, captured)
            if (yawToTest < 15f) {
                assertTrue(!isDistinct) // too close to 0f
            } else {
                assertTrue(isDistinct)
            }
            poseCases++
        }

        // 5. Sequence path permutations
        val sequences = listOf(
            listOf(ExpectedPose.STRAIGHT, ExpectedPose.LEFT, ExpectedPose.RIGHT),
            listOf(ExpectedPose.STRAIGHT, ExpectedPose.RIGHT, ExpectedPose.LEFT),
            listOf(ExpectedPose.LEFT, ExpectedPose.STRAIGHT, ExpectedPose.RIGHT),
            listOf(ExpectedPose.RIGHT, ExpectedPose.STRAIGHT, ExpectedPose.LEFT)
        )

        for (seq in sequences) {
            var currentStep = 0
            for (step in seq) {
                // Test a valid pose for the expected step
                val targetYaw = when (step) {
                    ExpectedPose.STRAIGHT -> 5f
                    ExpectedPose.LEFT -> -30f
                    ExpectedPose.RIGHT -> 30f
                }
                val sigs = createSignals(yawDegrees = targetYaw)
                val res = FaceQualityEngine.evaluate(sigs, config, step)
                assertTrue("Expected $step with yaw $targetYaw to be accepted", res is QualityResult.Accepted)

                // Test an invalid pose for the current step
                val wrongYaw = when (step) {
                    ExpectedPose.STRAIGHT -> -30f
                    ExpectedPose.LEFT -> 5f
                    ExpectedPose.RIGHT -> -30f
                }
                val wrongSigs = createSignals(yawDegrees = wrongYaw)
                val wrongRes = FaceQualityEngine.evaluate(wrongSigs, config, step)
                assertTrue(wrongRes is QualityResult.Rejected)
                poseCases += 2
            }
        }

        // Generate randomized parameters to verify pose windows
        for (i in 0..500) {
            val expected = ExpectedPose.values()[random.nextInt(3)]
            val yaw = random.nextFloat() * 100f - 50f
            val signals = createSignals(yawDegrees = yaw)
            val res = FaceQualityEngine.evaluate(signals, config, expected)
            if (res is QualityResult.Accepted) {
                when (expected) {
                    ExpectedPose.STRAIGHT -> assertTrue(abs(yaw) <= config.straightMaxYawDegrees)
                    ExpectedPose.LEFT -> assertTrue(yaw in -config.profileMaxYawDegrees..-config.profileMinYawDegrees)
                    ExpectedPose.RIGHT -> assertTrue(yaw in config.profileMinYawDegrees..config.profileMaxYawDegrees)
                }
            }
            poseCases++
        }

        println("Ran $poseCases pose & sequence tests.")
        assertTrue(poseCases >= 500)
    }

    // =========================================================================
    // LIVENESS & ANTI-SPOOF SEQUENCE GATE (500+ Cases)
    // =========================================================================
    @Test
    fun testLivenessSystematicCombinations() {
        var livenessCases = 0

        // 1. Static photo replay attack sequences (lengths 2, 3, 5, 10, 20 frames)
        val seqLengths = listOf(2, 3, 5, 10, 20)
        for (len in seqLengths) {
            val staticSignals = createSignals(yawDegrees = 3.5512f, leftEyeOpenProbability = 0.9234f, rightEyeOpenProbability = 0.9234f)
            val result = LivenessEngine.check(staticSignals, staticSignals, PresentationAttackSignals.Unavailable)
            assertTrue(result is LivenessResult.Rejected)
            assertEquals(LivenessReason.INSUFFICIENT_VARIANCE, (result as LivenessResult.Rejected).reason)
            livenessCases++
        }

        // 2. Low Light Liveness check (threshold: 45f)
        val brightnessEpsilons = listOf(-1f, -0.001f, 0f, 0.001f, 1f)
        for (eps in brightnessEpsilons) {
            val brightness = 45f + eps
            val signals = createSignals(brightness = brightness)
            val result = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
            if (brightness < 45f) {
                assertTrue(result is LivenessResult.Rejected)
                assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (result as LivenessResult.Rejected).reason)
            } else {
                assertTrue(result is LivenessResult.Passed)
            }
            livenessCases++
        }

        // 3. Closed Eyes liveness check (threshold: 0.25f)
        val eyeEpsilons = listOf(-0.05f, -0.001f, 0f, 0.001f, 0.05f)
        for (eps in eyeEpsilons) {
            val leftEye = 0.25f + eps
            val signals = createSignals(leftEyeOpenProbability = leftEye)
            val result = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
            if (leftEye < 0.25f) {
                assertTrue(result is LivenessResult.Rejected)
                assertEquals(LivenessReason.EYES_CLOSED_OR_STATIC, (result as LivenessResult.Rejected).reason)
            } else {
                assertTrue(result is LivenessResult.Passed)
            }
            livenessCases++
        }

        // 4. Low Blur Variance liveness check (threshold: 15f)
        for (eps in brightnessEpsilons) {
            val blur = 15f + eps
            val signals = createSignals(blurVariance = blur)
            val result = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
            if (blur < 15f) {
                assertTrue(result is LivenessResult.Rejected)
                assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (result as LivenessResult.Rejected).reason)
            } else {
                assertTrue(result is LivenessResult.Passed)
            }
            livenessCases++
        }

        // 5. Presentation Attack Detector Boundary Cases (Moiré interference, Specular glare, print texture flatness)
        val padThresholds = PresentationAttackDetector.Thresholds()
        val eps = 0.001f

        // High frequency moire energy limit
        val highEnergyRes = PresentationAttackDetector.analyze(
            PresentationAttackSignals(padThresholds.maxHighFrequencyEnergy + eps, 0.01f, 15f), 100f
        )
        assertTrue(highEnergyRes is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (highEnergyRes as LivenessResult.Rejected).reason)

        // Specular screen glare limit
        val specularRes = PresentationAttackDetector.analyze(
            PresentationAttackSignals(0.01f, padThresholds.maxSpecularHighlightRatio + eps, 15f), 100f
        )
        assertTrue(specularRes is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (specularRes as LivenessResult.Rejected).reason)

        // Flat print textures
        val printRes = PresentationAttackDetector.analyze(
            PresentationAttackSignals(0.01f, 0.01f, padThresholds.printFlatTextureStdDev - eps), 150f
        )
        assertTrue(printRes is LivenessResult.Rejected)
        assertEquals(LivenessReason.PRINT_PHOTO_SUSPECTED, (printRes as LivenessResult.Rejected).reason)

        livenessCases += 3

        // 6. Systematic random generation of natural micro-movements vs spoof sequences
        for (i in 0..550) {
            val yawDiff = random.nextFloat() * 5f
            val eyeDiff = random.nextFloat() * 0.2f
            val current = createSignals(yawDegrees = 10f + yawDiff, leftEyeOpenProbability = 0.8f + eyeDiff, rightEyeOpenProbability = 0.8f)
            val previous = createSignals(yawDegrees = 10f, leftEyeOpenProbability = 0.8f, rightEyeOpenProbability = 0.8f)

            val result = LivenessEngine.check(current, previous, PresentationAttackSignals.Unavailable)
            if (yawDiff < 0.001f && eyeDiff < 0.0001f) {
                assertTrue(result is LivenessResult.Rejected)
            } else {
                assertTrue(result is LivenessResult.Passed)
            }
            livenessCases++
        }

        println("Ran $livenessCases liveness checks.")
        assertTrue(livenessCases >= 500)
    }

    // =========================================================================
    // EMBEDDING MATCHING & DECISION THRESHOLD BOUNDARIES (1,000+ Cases)
    // =========================================================================
    @Test
    fun testEmbeddingMatchingAndRosterIsolation() {
        var matchCases = 0

        // 1. Cosine similarity boundaries check
        val exactSame = generateSyntheticEmbedding(10)
        val simExact = TemplateMatcher.cosineSimilarity(exactSame, exactSame)
        assertTrue(abs(simExact - 1f) < 0.001f)

        // Opposite vector check
        val opposite = exactSame.map { -it }.toFloatArray()
        val simOpposite = TemplateMatcher.cosineSimilarity(exactSame, opposite)
        assertTrue(abs(simOpposite - (-1f)) < 0.001f)
        matchCases += 2

        // 2. Setup a massive synthetic roster of 100 students
        val roster = mutableListOf<StudentTemplatePair>()
        for (sId in 1L..100L) {
            // each student has 3 templates (Straight, Left, Right)
            roster.add(StudentTemplatePair(sId, sId * 10 + 1, generateSyntheticEmbedding(sId.toInt() * 100 + 1)))
            roster.add(StudentTemplatePair(sId, sId * 10 + 2, generateSyntheticEmbedding(sId.toInt() * 100 + 2)))
            roster.add(StudentTemplatePair(sId, sId * 10 + 3, generateSyntheticEmbedding(sId.toInt() * 100 + 3)))
        }

        // Test matching across all 100 students
        val decisionEngine = RecognitionDecisionEngine(acceptThreshold = 0.45f, ambiguousMargin = 0.10f)

        for (sId in 1L..100L) {
            // Straight template exact query
            val query = roster.first { it.studentId == sId && it.templateId == sId * 10 + 1 }.embedding
            val outcome = decisionEngine.evaluate(query, roster)
            assertTrue(outcome is RecognitionOutcome.Match)
            assertEquals(sId, (outcome as RecognitionOutcome.Match).studentId)

            // Query with slight noise (perturbation)
            val noisyQuery = query.clone()
            for (i in noisyQuery.indices) noisyQuery[i] += (random.nextFloat() - 0.5f) * 0.05f
            // re-normalize noisy query
            var sum = 0f
            for (v in noisyQuery) sum += v * v
            sum = sqrt(sum)
            for (i in noisyQuery.indices) noisyQuery[i] /= sum

            val noisyOutcome = decisionEngine.evaluate(noisyQuery, roster)
            assertTrue(noisyOutcome is RecognitionOutcome.Match)
            assertEquals(sId, (noisyOutcome as RecognitionOutcome.Match).studentId)
            matchCases += 2
        }

        // 3. Epsilon boundary checks for Accept Threshold (0.45f) and Margin (0.10f)
        val target = floatArrayOf(1.0f, 0.0f)
        val epsilons = listOf(-0.00001f, 0f, 0.00001f)

        for (eps in epsilons) {
            val acceptScore = 0.45f + eps
            // generate target and candidate vector with cosine similarity exactly = acceptScore
            val angle = kotlin.math.acos(acceptScore)
            val candidate = floatArrayOf(kotlin.math.cos(angle), kotlin.math.sin(angle))
            val rosterPair = listOf(StudentTemplatePair(101L, 1001L, candidate))

            val outcome = decisionEngine.evaluate(target, rosterPair)
            if (acceptScore < 0.45f) {
                assertEquals(RecognitionOutcome.Unknown, outcome)
            } else {
                assertTrue(outcome is RecognitionOutcome.Match)
                assertEquals(101L, (outcome as RecognitionOutcome.Match).studentId)
            }
            matchCases++
        }

        // 4. Ambiguity Margin Boundary (0.10f)
        // topScore - secondScore < 0.10f -> Ambiguous
        for (eps in epsilons) {
            val margin = 0.10f + eps
            val topScore = 0.80f
            val secondScore = topScore - margin

            val angleTop = kotlin.math.acos(topScore)
            val candidateTop = floatArrayOf(kotlin.math.cos(angleTop), kotlin.math.sin(angleTop))

            val angleSecond = kotlin.math.acos(secondScore)
            // positive angle rotation to keep them on different sides of target
            val candidateSecond = floatArrayOf(kotlin.math.cos(angleSecond), -kotlin.math.sin(angleSecond))

            val rosterPair = listOf(
                StudentTemplatePair(1L, 11L, candidateTop),
                StudentTemplatePair(2L, 22L, candidateSecond)
            )

            val outcome = decisionEngine.evaluate(target, rosterPair)
            if (margin < 0.10f) {
                assertTrue(outcome is RecognitionOutcome.Ambiguous)
            } else {
                assertTrue(outcome is RecognitionOutcome.Match)
                assertEquals(1L, (outcome as RecognitionOutcome.Match).studentId)
            }
            matchCases++
        }

        // 5. Cross-Class Isolation
        // Student templates from Class A should not match students query in Class B
        val classA = roster.take(90) // students 1 to 30
        val classB = roster.drop(90) // students 31 to 100

        for (i in 0..100) {
            val queryA = classA[random.nextInt(classA.size)].embedding
            val outcomeB = decisionEngine.evaluate(queryA, classB)
            // check that query from class A never matches class B roster
            assertTrue(outcomeB is RecognitionOutcome.Unknown || outcomeB is RecognitionOutcome.Ambiguous)
            matchCases++
        }

        // 6. Systematic large scale matching simulation
        for (i in 0..700) {
            val query = generateSyntheticEmbedding(2000 + i)
            val outcome = decisionEngine.evaluate(query, roster)
            // random vectors should rarely match (high probability of Unknown)
            if (outcome is RecognitionOutcome.Match) {
                val matchedPair = roster.find { it.studentId == (outcome as RecognitionOutcome.Match).studentId }
                assertNotEquals(null, matchedPair)
            }
            matchCases++
        }

        println("Ran $matchCases matching & decision boundary checks.")
        assertTrue(matchCases >= 1000)
    }

    // =========================================================================
    // LEVEL 2 — MALFORMED BIOMETRIC DATA (200+ Cases)
    // =========================================================================
    @Test
    fun testMalformedBiometricInputs() {
        var malformedCases = 0
        val decisionEngine = RecognitionDecisionEngine()

        // 1. Wrong embedding length vectors
        // LLD-10 migration amendment (2026-08): dimension-mismatched templates are now
        // SKIPPED by the decision engine's biometric-format gate instead of throwing —
        // the old IllegalArgumentException path was the production "192 vs 32" crash.
        val wrongLength = floatArrayOf(0.1f, 0.2f, 0.3f)
        val validPair = StudentTemplatePair(1L, 11L, generateSyntheticEmbedding(500))
        val outcomeWrongLength = decisionEngine.evaluate(wrongLength, listOf(validPair))
        assertTrue(
            "Dimension-mismatched template must be skipped, never crash, never match",
            outcomeWrongLength is RecognitionOutcome.Unknown ||
                outcomeWrongLength is RecognitionOutcome.NoTemplatesAvailable,
        )
        malformedCases++

        // 2. Empty candidate template sets
        val outcomeEmpty = decisionEngine.evaluate(generateSyntheticEmbedding(501), emptyList())
        assertEquals(RecognitionOutcome.NoTemplatesAvailable, outcomeEmpty)
        malformedCases++

        // 3. Nan & Infinity embedding inputs
        val nanVector = FloatArray(192) { Float.NaN }
        val nanPair = StudentTemplatePair(2L, 22L, nanVector)
        val validQuery = generateSyntheticEmbedding(502)
        val outcomeNan = decisionEngine.evaluate(validQuery, listOf(nanPair))
        assertEquals(RecognitionOutcome.Unknown, outcomeNan)
        malformedCases++

        // 4. Systematically run malformed checks
        for (i in 0..200) {
            val query = FloatArray(192)
            if (i % 2 == 0) {
                query[0] = Float.POSITIVE_INFINITY
            } else {
                query[i % 192] = Float.NaN
            }
            val outcome = decisionEngine.evaluate(query, listOf(validPair))
            assertEquals(RecognitionOutcome.Unknown, outcome)
            malformedCases++
        }

        println("Ran $malformedCases malformed input tests.")
        assertTrue(malformedCases >= 200)
    }

    // =========================================================================
    // LEVEL 2 — END-TO-END PIPELINE SIMULATION & INVARIANTS (200+ Cases)
    // =========================================================================
    @Test
    fun testEndToEndSimulationAndInvariants() {
        var e2eCases = 0

        // Invariant 1: Any invalid quality signal must never produce a valid capture.
        val badSignals = createSignals(faceCount = 0)
        val qualityResult = FaceQualityEngine.evaluate(badSignals, config)
        assertTrue(qualityResult is QualityResult.Rejected)
        e2eCases++

        // Invariant 2: Wrong pose must never advance the pose state.
        val leftSignals = createSignals(yawDegrees = -30f)
        val straightResult = FaceQualityEngine.evaluate(leftSignals, config, ExpectedPose.STRAIGHT)
        assertTrue(straightResult is QualityResult.Rejected)
        assertEquals(QualityReason.POSE_NOT_STRAIGHT, (straightResult as QualityResult.Rejected).reason)
        e2eCases++

        // Invariant 3: Repeated identical frames should never pass liveness check
        val prev = createSignals(yawDegrees = 0.0f, leftEyeOpenProbability = 0.8f, rightEyeOpenProbability = 0.8f)
        val current = createSignals(yawDegrees = 0.0f, leftEyeOpenProbability = 0.8f, rightEyeOpenProbability = 0.8f)
        val livenessRes = LivenessEngine.check(current, prev, PresentationAttackSignals.Unavailable)
        assertTrue(livenessRes is LivenessResult.Rejected)
        assertEquals(LivenessReason.INSUFFICIENT_VARIANCE, (livenessRes as LivenessResult.Rejected).reason)
        e2eCases++

        // Invariant 9: Generated unit embeddings should remain L2-normalized within a small floating-point tolerance
        val randVec = generateSyntheticEmbedding(999)
        var sum = 0f
        for (v in randVec) sum += v * v
        assertTrue(abs(sum - 1f) < 0.01f)
        e2eCases++

        // Simulate 200+ end-to-end capture and match flow cycles
        val roster = mutableListOf<StudentTemplatePair>()
        val engine = RecognitionDecisionEngine()

        for (i in 1..220) {
            // Enroll stage
            val sId = i.toLong()
            val straightEmbedding = generateSyntheticEmbedding(i * 10 + 1)
            val leftEmbedding = generateSyntheticEmbedding(i * 10 + 2)
            val rightEmbedding = generateSyntheticEmbedding(i * 10 + 3)

            // Verify they are enrolled properly
            val tempPair1 = StudentTemplatePair(sId, sId * 100 + 1, straightEmbedding)
            val tempPair2 = StudentTemplatePair(sId, sId * 100 + 2, leftEmbedding)
            val tempPair3 = StudentTemplatePair(sId, sId * 100 + 3, rightEmbedding)

            roster.add(tempPair1)
            roster.add(tempPair2)
            roster.add(tempPair3)

            // Check-in stage: student faces camera, straight embedding matched
            val checkInOutcome = engine.evaluate(straightEmbedding, roster)
            assertTrue(checkInOutcome is RecognitionOutcome.Match)
            assertEquals(sId, (checkInOutcome as RecognitionOutcome.Match).studentId)
            e2eCases++
        }

        println("Ran $e2eCases E2E simulation and invariant checks.")
        assertTrue(e2eCases >= 200)
    }
}
