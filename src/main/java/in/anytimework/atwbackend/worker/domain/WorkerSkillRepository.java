package in.anytimework.atwbackend.worker.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkerSkillRepository extends JpaRepository<WorkerSkill, UUID> {

    List<WorkerSkill> findByWorkerId(UUID workerId);

    void deleteByWorkerId(UUID workerId);
}
