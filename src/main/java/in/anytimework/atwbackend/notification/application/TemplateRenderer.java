package in.anytimework.atwbackend.notification.application;

import java.util.Locale;
import java.util.Map;

/**
 * Renders a template (subject + body) with per-send variables. Keeping this behind an interface lets
 * the templating engine change without touching callers.
 */
public interface TemplateRenderer {

    RenderedEmail render(String subjectTemplate, String bodyTemplate, Map<String, Object> variables, Locale locale);
}
