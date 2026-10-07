package com.mcverse.jobify.job.repository;

import com.mcverse.jobify.job.model.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepo extends JpaRepository<Location, String> {

    Optional<Location> findByLookupKey(String lookupKey);

    List<Location> findAllByCityKeyAndRemote(String cityKey, boolean remote);

    /**
     * Places with their number of jobs, most jobs first. {@code available} null counts every job; {@code pattern}
     * (already lower case, wildcards escaped with a backslash) null skips the name filter. Only places with at
     * least one counted job appear.
     */
    @Query("""
            select l, count(j) from JobPost j join j.locationRef l
            where (:available is null or j.available = :available)
              and (:pattern is null or lower(l.displayName) like :pattern escape '\\')
            group by l
            order by count(j) desc, l.displayName asc
            """)
    List<Object[]> facets(@Param("available") Boolean available, @Param("pattern") String pattern, Pageable page);
}
