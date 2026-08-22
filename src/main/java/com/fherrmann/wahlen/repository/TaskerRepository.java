package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Tasker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskerRepository extends JpaRepository<Tasker, Integer> {
}
