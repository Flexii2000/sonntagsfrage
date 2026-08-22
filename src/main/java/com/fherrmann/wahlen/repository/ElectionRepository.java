package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Election;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ElectionRepository extends JpaRepository<Election, Long> {

    Optional<Election> findByParliamentIdAndElectionDate(Integer parliamentId, LocalDate electionDate);

    @Query("""
            select e from Election e
            join fetch e.parliament
            order by e.electionDate asc
            """)
    List<Election> findAllWithParliament();
}
