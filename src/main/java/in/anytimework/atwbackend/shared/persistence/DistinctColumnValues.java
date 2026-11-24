package in.anytimework.atwbackend.shared.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Distinct, non-null values of one attribute actually present on an entity's own rows - the "only
 * offer filter options that occur in this listing's own data" building block (e.g. the 3 states
 * actually referenced by today's workers, not every state in the masterdata table). A plain indexed
 * {@code SELECT DISTINCT}, never a full entity load - callers resolve the returned ids to display
 * names via the relevant masterdata lookup service, exactly as every listing in this codebase already
 * batches id -> name resolution (see e.g. {@code WorkerService.toSummaryResponses}).
 */
public final class DistinctColumnValues {

    private DistinctColumnValues() {
    }

    public static <E, V> List<V> of(EntityManager em, Class<E> entityClass, String attribute, Class<V> valueType) {
        return of(em, entityClass, attribute, valueType, List.of());
    }

    /**
     * @param extraPredicates additional conditions narrowing which rows count. Needed whenever
     *                        {@code entityClass} is NOT an aggregate root with its own
     *                        {@code @SQLRestriction} (e.g. {@code WorkerDesignation}/{@code WorkerSkill}/
     *                        {@code WorkerAddress}, which carry a bare {@code workerId} column with no
     *                        soft-delete filtering of their own) - without a predicate ruling out rows
     *                        whose parent is gone/soft-deleted, a distinct scan could surface a value
     *                        whose only remaining reference belongs to a worker nobody can otherwise see.
     */
    public static <E, V> List<V> of(EntityManager em, Class<E> entityClass, String attribute, Class<V> valueType,
                                    List<Specification<E>> extraPredicates) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<V> query = cb.createQuery(valueType);
        Root<E> root = query.from(entityClass);
        Path<V> path = resolvePath(root, attribute);
        List<Predicate> where = new ArrayList<>();
        where.add(cb.isNotNull(path));
        for (Specification<E> predicate : extraPredicates) {
            where.add(predicate.toPredicate(root, query, cb));
        }
        query.select(path).distinct(true).where(where.toArray(new Predicate[0]));
        return em.createQuery(query).getResultList();
    }

    /** Supports a dotted path (e.g. {@code "address.stateId"}) into an {@code @Embedded} attribute, not just a direct column. */
    @SuppressWarnings("unchecked")
    private static <V> Path<V> resolvePath(Path<?> root, String attribute) {
        Path<?> path = root;
        for (String segment : attribute.split("\\.")) {
            path = path.get(segment);
        }
        return (Path<V>) path;
    }
}
