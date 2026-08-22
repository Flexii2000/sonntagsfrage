package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.ElectionResult;
import com.fherrmann.wahlen.domain.ResultKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionResultRepository extends JpaRepository<ElectionResult, Long> {

    List<ElectionResult> findByElectionId(Long electionId);

    @Query("""
            select r from ElectionResult r
            join fetch r.party
            where r.election.id = :electionId and r.kind = :kind
            order by r.percent desc
            """)
    List<ElectionResult> findByElectionAndKind(@Param("electionId") Long electionId,
                                               @Param("kind") ResultKind kind);
}
