package in.anytimework.atwbackend.testapi;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TEMPORARY smoke-test repository. Delete together with the rest of the testapi package.
 */
public interface TestItemRepository extends JpaRepository<TestItem, Long> {
}
