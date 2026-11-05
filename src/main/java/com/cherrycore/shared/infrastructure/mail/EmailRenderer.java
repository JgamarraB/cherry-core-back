package com.cherrycore.shared.infrastructure.mail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Convierte un {@link EmailContent} en email HTML (plantilla única con la
 * marca) + versión de texto plano para clientes de correo sin HTML.
 *
 * <p>Thymeleaf escapa todo el contenido: un nombre de empresa con HTML no
 * puede inyectar nada en el email.</p>
 */
@Component
public class EmailRenderer {

    private static final String TEMPLATE_PREFIX = "templates/email/";
    private static final String TEMPLATE_SUFFIX = ".html";
    private static final String LAYOUT_TEMPLATE = "notification";
    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String TEXT_ACTION_SEPARATOR = "\n\n";

    private final TemplateEngine templateEngine;

    public EmailRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_PREFIX);
        resolver.setSuffix(TEMPLATE_SUFFIX);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    public EmailMessage render(EmailContent content) {
        List<String> paragraphs = Arrays.stream(content.body().split(PARAGRAPH_SEPARATOR))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();

        Context context = new Context(content.locale(), Map.of(
                "lang", content.locale().getLanguage(),
                "brandName", content.branding().name(),
                "logoUrl", content.branding().logoUrl() == null ? "" : content.branding().logoUrl(),
                "subject", content.subject(),
                "paragraphs", paragraphs,
                "actionUrl", content.actionUrl() == null ? "" : content.actionUrl(),
                "actionLabel", content.actionLabel() == null ? "" : content.actionLabel()));
        String html = templateEngine.process(LAYOUT_TEMPLATE, context);

        String text = content.actionUrl() == null
                ? content.body()
                : content.body() + TEXT_ACTION_SEPARATOR + content.actionUrl();
        return new EmailMessage(content.to(), content.subject(), text, html);
    }
}
