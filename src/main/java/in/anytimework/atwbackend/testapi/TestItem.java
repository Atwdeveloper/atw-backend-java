package in.anytimework.atwbackend.testapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TEMPORARY smoke-test entity. Delete together with the rest of the testapi package.
 */
@Entity
@Table(name = "test_item")
@Getter
@Setter
@NoArgsConstructor
public class TestItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
