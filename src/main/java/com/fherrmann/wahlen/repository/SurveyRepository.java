package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Survey;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyRepository extends JpaRepository<Survey, Integer> {

    @Query("""
            select new com.fherrmann.wahlen.repository.SurveyRow(
                s.id, s.institute.id, s.tasker.id, s.method.id,
                s.publishedOn, s.periodStart, s.periodEnd, s.surveyedPersons,
                r.id.partyId, r.percent)
            from SurveyResult r
            join r.survey s
            where s.parliament.id = :parliamentId
            order by s.publishedOn desc, s.id desc
            """)
    List<SurveyRow> findRowsByParliament(@Param("parliamentId") Integer parliamentId);

    @Query("select max(s.publishedOn) from Survey s where s.parliament.id = :parliamentId")
    Optional<LocalDate> findLatestPublishedOn(@Param("parliamentId") Integer parliamentId);

    @Query("select new com.fherrmann.wahlen.repository.SurveyHash(s.id, s.contentHash) from Survey s")
    List<SurveyHash> findAllHashes();

    @Modifying
    @Query("delete from SurveyResult r where r.id.surveyId in :surveyIds")
    void deleteResultsBySurveyIds(@Param("surveyIds") Collection<Integer> surveyIds);

    @Modifying
    @Query("delete from Survey s where s.id in :surveyIds")
    void deleteBySurveyIds(@Param("surveyIds") Collection<Integer> surveyIds);

    long countByParliamentId(Integer parliamentId);
}
