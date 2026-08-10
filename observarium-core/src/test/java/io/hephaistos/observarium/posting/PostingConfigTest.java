package io.hephaistos.observarium.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PostingConfigTest {

  @Test
  void booleanValue_returnsDefault_whenKeyAbsent() {
    assertThat(PostingConfig.booleanValue(Map.of(), "auth", true)).isTrue();
    assertThat(PostingConfig.booleanValue(Map.of(), "auth", false)).isFalse();
  }

  @Test
  void booleanValue_returnsDefault_whenValueBlank() {
    assertThat(PostingConfig.booleanValue(Map.of("auth", "  "), "auth", true)).isTrue();
  }

  @Test
  void booleanValue_parsesTrueAndFalse_ignoringCaseAndWhitespace() {
    assertThat(PostingConfig.booleanValue(Map.of("auth", "TRUE"), "auth", false)).isTrue();
    assertThat(PostingConfig.booleanValue(Map.of("auth", " false "), "auth", true)).isFalse();
  }

  @Test
  void booleanValue_throws_onUnrecognizedValue() {
    assertThatThrownBy(() -> PostingConfig.booleanValue(Map.of("auth", "a"), "auth", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auth")
        .hasMessageContaining("'true' or 'false'")
        .hasMessageContaining("a");
  }

  @Test
  void booleanValue_throws_onYes() {
    assertThatThrownBy(() -> PostingConfig.booleanValue(Map.of("enabled", "yes"), "enabled", false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
