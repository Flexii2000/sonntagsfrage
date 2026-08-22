package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Parliament;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParliamentRepository extends JpaRepository<Parliament, Integer> {

    Optional<Parliament> findBySlug(String slug);

    List<Parliament> findAllByOrderBySortOrderAscNameAsc();
}
