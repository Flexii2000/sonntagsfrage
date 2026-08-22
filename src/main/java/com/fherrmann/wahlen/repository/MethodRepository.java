package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Method;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MethodRepository extends JpaRepository<Method, Integer> {
}
