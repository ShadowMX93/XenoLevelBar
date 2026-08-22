package com.xenolevelbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    @Test
    void newerPatchVersionWins() {
        assertTrue(UpdateService.compareVersions("1.1.1", "1.1.0") > 0);
    }

    @Test
    void leadingVIsIgnored() {
        assertEquals(0, UpdateService.compareVersions("v1.1.0", "1.1.0"));
    }

    @Test
    void missingCorePartsAreZero() {
        assertEquals(0, UpdateService.compareVersions("1.1", "1.1.0"));
    }

    @Test
    void stableReleaseBeatsPrerelease() {
        assertTrue(UpdateService.compareVersions("1.1.0", "1.1.0-beta.1") > 0);
    }

    @Test
    void buildMetadataIsIgnored() {
        assertEquals(0, UpdateService.compareVersions("1.1.0+build.5", "1.1.0"));
    }
}
