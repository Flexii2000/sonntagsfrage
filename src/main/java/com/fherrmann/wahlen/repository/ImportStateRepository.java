package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.ImportState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportStateRepository extends JpaRepository<ImportState, String> {
}
