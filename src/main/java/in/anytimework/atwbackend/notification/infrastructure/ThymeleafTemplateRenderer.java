package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.RenderedEmail;
import in.anytimework.atwbackend.notification.application.TemplateRenderer;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;

/**
 * Renders email templates with Thymeleaf. Templates come from the database as strings, so a
 * {@link StringTemplateResolver} is used (not the file-based default). The subject is rendered in
 * TEXT mode, the body in HTML mode (which escapes {@code [[${...}]]} expressions, guarding against
 * injection).
 */
@Component
public class ThymeleafTemplateRenderer implements TemplateRenderer {

    private final SpringTemplateEngine textEngine = engineFor(TemplateMode.TEXT);
    private final SpringTemplateEngine htmlEngine = engineFor(TemplateMode.HTML);

    // SpringTemplateEngine evaluates expressions with SpEL (not OGNL), matching the rest of the app.
    private static SpringTemplateEngine engineFor(TemplateMode mode) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Override
    public RenderedEmail render(String subjectTemplate, String bodyTemplate,
                               Map<String, Object> variables, Locale locale) {
        Context context = new Context(locale, variables != null ? variables : Map.of());
        String subject = textEngine.process(subjectTemplate, context).strip();
        String body = htmlEngine.process(bodyTemplate, context);
        return new RenderedEmail(subject, body);
    }
}
