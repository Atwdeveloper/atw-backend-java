package in.anytimework.atwbackend.masterdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<Designation> findAllByOrderByName();

    List<Designation> findByIndustries_IdOrderByName(UUID industryId);
}
