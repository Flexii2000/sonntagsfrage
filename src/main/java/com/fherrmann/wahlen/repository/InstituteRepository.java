package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Institute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstituteRepository extends JpaRepository<Institute, Integer> {
}
