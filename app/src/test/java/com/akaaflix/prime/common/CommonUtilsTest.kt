package com.akaaflix.prime.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonUtilsTest {
    @Test fun normalizeForSearch() {
        assertEquals("cafe", ContentText.normalize("CAFÉ"))
        assertEquals("الاخبار", ContentText.normalize("الأخبار"))
        assertEquals("مدرسه", ContentText.normalize("مَدْرَسَة"))
        assertEquals("%50\\%\\_off%", ContentText.likePattern("50%_off"))
    }

    @Test fun qualityAndYear() {
        assertEquals("4K", ContentText.qualityOf("Movie (2024) 4K"))
        assertEquals("FHD", ContentText.qualityOf("Channel 1080p"))
        assertEquals("HD", ContentText.qualityOf("MBC HD"))
        assertNull(ContentText.qualityOf("Plain"))
        assertTrue(ContentText.isHdr("Title HDR10+"))
        assertEquals(2024, ContentText.yearOf(null, "Dune (2024)"))
        assertNull(ContentText.yearOf("1080p release"))
    }

    @Test fun categoryClassification() {
        assertEquals(CategoryKind.ADULT, CategoryClassifier.classify("XXX Adults 18+"))
        assertEquals(CategoryKind.KIDS, CategoryClassifier.classify("Kids Cartoons"))
        assertEquals(CategoryKind.NEWS, CategoryClassifier.classify("AR | News"))
        assertEquals(CategoryKind.ARABIC, CategoryClassifier.classify("قنوات مصر"))
        assertEquals(CategoryKind.DOCUMENTARY, CategoryClassifier.classify("وثائقية"))
        assertEquals(CategoryKind.INTERNATIONAL, CategoryClassifier.classify("FR | Divertissement"))
        assertEquals("FR", CategoryClassifier.countryCode("FR | Divertissement"))
        assertEquals("UK", CategoryClassifier.countryCode("[UK] Entertainment"))
        assertNull(CategoryClassifier.countryCode("HD | Cinema"))
        assertTrue(CategoryClassifier.isAdult("للكبار فقط"))
        assertFalse(CategoryClassifier.isAdult("Documentary"))
    }

    @Test fun ageRatings() {
        assertEquals(13, AgeRating.minimumAge("PG-13"))
        assertEquals(17, AgeRating.minimumAge("R"))
        assertEquals(16, AgeRating.minimumAge("16+"))
        assertEquals(12, AgeRating.minimumAge("FSK 12"))
        assertEquals(18, AgeRating.minimumAge("+18"))
        assertNull(AgeRating.minimumAge("unknown"))
        assertTrue(AgeRating.isRestricted("16+", 12))
        assertFalse(AgeRating.isRestricted("PG", 12))
        assertFalse(AgeRating.isRestricted("18+", 0))
        assertFalse(AgeRating.isRestricted(null, 12))
    }

    @Test fun base64Epg() {
        assertEquals("Morning News", Base64Text.decodeTextOrSelf("TW9ybmluZyBOZXdz"))
        assertEquals("نشرة", Base64Text.decodeTextOrSelf("2YbYtNix2Kk="))
        assertEquals("Plain title!", Base64Text.decodeTextOrSelf("Plain title!"))
    }

    @Test fun durations() {
        assertEquals("1:05:09", TimeFormat.duration(3_909_000))
        assertEquals("05:09", TimeFormat.duration(309_000))
        assertEquals(7200L, TimeFormat.parseDurationToSeconds("02:00:00"))
        assertEquals(2700L, TimeFormat.parseDurationToSeconds("45"))
        assertEquals(5L, TimeFormat.remainingDays(System.currentTimeMillis() / 1000 + 5 * 86_400 + 60))
        assertNull(TimeFormat.remainingDays(1))
    }
}
