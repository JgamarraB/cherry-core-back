package com.cherrycore.shared.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer();

    @Test
    void escapesHtmlFromCompanyNamesAndTexts() {
        EmailMessage message = renderer.render(new EmailContent("a@b.com", Locale.forLanguageTag("es"),
                new EmailBranding("<script>alert(1)</script>", null), "Asunto", "Hola <b>x</b>",
                "https://example.com/x?token=1", "Botón"));

        assertThat(message.htmlBody())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .contains("Hola &lt;b&gt;x&lt;/b&gt;")
                .contains("href=\"https://example.com/x?token=1\"");
        assertThat(message.textBody()).contains("https://example.com/x?token=1");
    }

    @Test
    void showsLogoWhenAvailableAndNameOtherwise() {
        EmailMessage withLogo = renderer.render(new EmailContent("a@b.com", Locale.ENGLISH,
                new EmailBranding("Lirgar", "https://media.example.com/logo.png"), "S", "B", null, null));
        EmailMessage withoutLogo = renderer.render(new EmailContent("a@b.com", Locale.ENGLISH,
                new EmailBranding("Lirgar", null), "S", "B", null, null));

        assertThat(withLogo.htmlBody()).contains("src=\"https://media.example.com/logo.png\"");
        assertThat(withoutLogo.htmlBody()).doesNotContain("<img").contains("Lirgar");
    }
}
