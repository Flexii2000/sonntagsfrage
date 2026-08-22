package com.fherrmann.wahlen.repository;

import com.fherrmann.wahlen.domain.Party;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Integer> {

    List<Party> findAllByOrderBySortOrderAscShortcutAsc();
}
