package in.anytimework.atwbackend.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    Optional<EmailTemplate> findByCodeAndLocaleAndActiveTrue(String code, String locale);
}
