package in.anytimework.atwbackend.testapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY smoke-test API to verify the full stack end to end.
 * Delete the whole testapi package (and drop the test_item table) before official work.
 *
 * GET  /api/test/items      -> list all
 * GET  /api/test/items/{id} -> one, or 404
 * POST /api/test/items      -> create (JSON: {"name": "..."})
 */
@RestController
@RequestMapping("/api/test/items")
public class TestItemController {

	private final TestItemRepository repository;

	public TestItemController(TestItemRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<TestItem> list() {
		return repository.findAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<TestItem> getOne(@PathVariable Long id) {
		return repository.findById(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<TestItem> create(@Valid @RequestBody CreateTestItemRequest request) {
		TestItem item = new TestItem();
		item.setName(request.name());
		item.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		TestItem saved = repository.save(item);
		return ResponseEntity.created(URI.create("/api/test/items/" + saved.getId())).body(saved);
	}

	/** Request body for creating a test item. */
	public record CreateTestItemRequest(@NotBlank String name) {
	}
}
