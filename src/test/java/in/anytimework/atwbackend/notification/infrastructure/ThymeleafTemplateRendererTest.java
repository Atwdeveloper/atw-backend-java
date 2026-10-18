package in.anytimework.atwbackend.notification.infrastructure;

import in.anytimework.atwbackend.notification.application.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafTemplateRendererTest {

    private final ThymeleafTemplateRenderer renderer = new ThymeleafTemplateRenderer();

    @Test
    void rendersSubjectAndBodyWithVariables() {
        RenderedEmail result = renderer.render(
                "Welcome, [(${name})]!",
                "<p>Hi [[${name}]], your balance is [[${balance}]].</p>",
                Map.of("name", "Asha", "balance", 1500),
                Locale.ENGLISH);

        assertThat(result.subject()).isEqualTo("Welcome, Asha!");
        assertThat(result.htmlBody()).contains("Hi Asha").contains("1500");
    }

    @Test
    void htmlBodyEscapesInjectedMarkup() {
        RenderedEmail result = renderer.render(
                "Hello",
                "<p>[[${name}]]</p>",
                Map.of("name", "<script>alert(1)</script>"),
                Locale.ENGLISH);

        // HTML mode escapes the value, so the raw tag must not appear.
        assertThat(result.htmlBody()).doesNotContain("<script>");
        assertThat(result.htmlBody()).contains("&lt;script&gt;");
    }
}
