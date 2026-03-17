package io.hephaistos.observarium;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.scrub.ScrubLevel;
import org.junit.jupiter.api.Test;

class ObservariumConfigTest {

  @Test
  void constructor_storesScrubLevel() {
    ObservariumConfig config = new ObservariumConfig(ScrubLevel.STRICT, 2);
    assertEquals(ScrubLevel.STRICT, config.scrubLevel());
  }

  @Test
  void constructor_storesPostingServiceCount() {
    ObservariumConfig config = new ObservariumConfig(ScrubLevel.BASIC, 3);
    assertEquals(3, config.postingServiceCount());
  }

  @Test
  void constructor_zeroPostingServices_isValid() {
    ObservariumConfig config = new ObservariumConfig(ScrubLevel.NONE, 0);
    assertEquals(0, config.postingServiceCount());
    assertEquals(ScrubLevel.NONE, config.scrubLevel());
  }

  @Test
  void twoConfigsWithSameValues_areEqual() {
    ObservariumConfig a = new ObservariumConfig(ScrubLevel.BASIC, 1);
    ObservariumConfig b = new ObservariumConfig(ScrubLevel.BASIC, 1);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void twoConfigsWithDifferentScrubLevel_areNotEqual() {
    ObservariumConfig a = new ObservariumConfig(ScrubLevel.BASIC, 1);
    ObservariumConfig b = new ObservariumConfig(ScrubLevel.STRICT, 1);
    assertNotEquals(a, b);
  }

  @Test
  void twoConfigsWithDifferentServiceCount_areNotEqual() {
    ObservariumConfig a = new ObservariumConfig(ScrubLevel.BASIC, 1);
    ObservariumConfig b = new ObservariumConfig(ScrubLevel.BASIC, 2);
    assertNotEquals(a, b);
  }

  @Test
  void constructor_threeArg_storesMaxDuplicateComments() {
    ObservariumConfig config = new ObservariumConfig(ScrubLevel.BASIC, 1, 10);
    assertEquals(10, config.maxDuplicateComments());
  }

  @Test
  void constructor_twoArg_defaultsToFiveMaxDuplicateComments() {
    ObservariumConfig config = new ObservariumConfig(ScrubLevel.BASIC, 1);
    assertEquals(5, config.maxDuplicateComments());
  }

  @Test
  void twoConfigsWithDifferentMaxDuplicateComments_areNotEqual() {
    ObservariumConfig a = new ObservariumConfig(ScrubLevel.BASIC, 1, 5);
    ObservariumConfig b = new ObservariumConfig(ScrubLevel.BASIC, 1, 10);
    assertNotEquals(a, b);
  }
}
