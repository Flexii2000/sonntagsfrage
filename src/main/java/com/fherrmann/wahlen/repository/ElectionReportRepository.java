package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.ElectionReport;
import com.fherrmann.wahlen.domain.ResultKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionReportRepository extends JpaRepository<ElectionReport, Long> {

    /** Alle Staende einer Wahl, aelteste zuerst — der Verlauf des Abends. */
    @Query("""
            select distinct r from ElectionReport r
            left join fetch r.results res
            left join fetch res.party
            where r.election.id = :electionId
            order by r.reportedAt asc, r.id asc
            """)
    List<ElectionReport> findByElectionWithResults(@Param("electionId") Long electionId);

    /** Der juengste Stand einer Quelle — fuer den Duplikatabgleich automatischer Abrufe. */
    Optional<ElectionReport> findFirstByElectionIdAndSourceOrderByReportedAtDescIdDesc(
            Long electionId, String source);

    long countByElectionId(Long electionId);

    boolean existsByElectionIdAndSourceAndKindAndReportedAt(
            Long electionId, String source, ResultKind kind, Instant reportedAt);
}
