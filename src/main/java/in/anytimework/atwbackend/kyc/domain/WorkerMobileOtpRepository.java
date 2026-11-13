package in.anytimework.atwbackend.kyc.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkerMobileOtpRepository extends JpaRepository<WorkerMobileOtp, UUID> {
}
