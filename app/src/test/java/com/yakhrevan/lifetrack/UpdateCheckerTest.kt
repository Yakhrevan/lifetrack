package com.yakhrevan.lifetrack

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    @Test fun newerReleaseIsDetected() {
        org.junit.Assert.assertTrue(UpdateChecker.compareVersions("1.2.0", "1.1.0") > 0)
    }

    @Test fun olderOrEqualReleaseIsNotNewer() {
        org.junit.Assert.assertTrue(UpdateChecker.compareVersions("1.0.9", "1.1.0") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.1.0", "1.1.0"))
    }

    @Test fun versionTagsAndTrailingZerosAreComparedNumerically() {
        assertEquals(0, UpdateChecker.compareVersions("v1.2", "1.2.0"))
        org.junit.Assert.assertTrue(UpdateChecker.compareVersions("v1.10.0", "1.9.9") > 0)
    }

    @Test fun prereleaseSuffixDoesNotBreakNumericComparison() {
        assertEquals(0, UpdateChecker.compareVersions("1.2.0-beta.1", "1.2.0"))
    }
}
