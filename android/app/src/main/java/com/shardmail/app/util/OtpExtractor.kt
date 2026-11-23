package com.shardmail.app.util

object OtpExtractor {

    private val LABELLED_PATTERNS = listOf(
        Regex("""(?i)(?:verification|security|confirmation|validation|login|auth|authorization|activation|passcode|otp|pin)\s*(?:code)?\s*(?:is|:|=|-|–|—)\s*[:#]?\s*([0-9]{4,8})"""),
        Regex("""(?i)(?:your|the)\s+(?:verification|security|confirmation|login|auth|activation|one-time|otp|passcode)?\s*code\s*(?:is|:|=|-|–|—)\s*[:#]?\s*([0-9]{4,8})"""),
        Regex("""(?i)(?:enter|use)\s+([0-9]{4,8})\s+(?:to\s+(?:verify|log\s*in|sign\s*in|confirm|continue|authenticate))"""),
        Regex("""(?i)(?:code|otp|pin|passcode)\s*[:#]\s*([0-9]{4,8})"""),
        Regex("""(?i)\bcode\s+is\s*[:\s]\s*([0-9]{4,8})"""),
        Regex("""(?i)(?:one-time\s+password|one-time\s+passcode|otp)\s*(?:is|:)?\s*([0-9]{4,8})"""),
        Regex("""(?i)\b([0-9]{4,8})\s+(?:is\s+your\s+(?:verification|confirmation|login|security|otp|code))""")
    )

    // Standalone 6-digit number (most common OTP length, e.g. 290169, 311891, 123456)
    private val SIX_DIGIT_REGEX = Regex("""(?<![0-9\-\/:#])([0-9]{6})(?![0-9\-\/:#])""")

    fun extractVerificationCode(subject: String?, text: String?): String? {
        val s = subject ?: ""
        val t = text ?: ""
        val combined = "$s\n$t"

        // 1. Explicit labelled patterns
        for (pattern in LABELLED_PATTERNS) {
            val match = pattern.find(combined)
            if (match != null && match.groupValues.size > 1) {
                val code = match.groupValues[1].trim()
                if (!isYearOrDate(code)) {
                    return code
                }
            }
        }

        // 2. Standalone 6-digit regex
        val candidates = mutableListOf<String>()
        val matches = SIX_DIGIT_REGEX.findAll(combined)
        for (m in matches) {
            val candidate = m.groupValues[1].trim()
            if (!isYearOrDate(candidate)) {
                candidates.add(candidate)
            }
        }

        if (candidates.size == 1) {
            return candidates[0]
        } else if (candidates.size > 1) {
            // Check if one appears in subject
            val subjectMatch = SIX_DIGIT_REGEX.find(s)
            if (subjectMatch != null) {
                val candidate = subjectMatch.groupValues[1].trim()
                if (!isYearOrDate(candidate)) {
                    return candidate
                }
            }
            return candidates[0]
        }

        return null
    }

    private fun isYearOrDate(str: String): Boolean {
        val num = str.toIntOrNull() ?: return true
        if (str.length == 4 && num in 1900..2099) {
            return true
        }
        return false
    }
}
