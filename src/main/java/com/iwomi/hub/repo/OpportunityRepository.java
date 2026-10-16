package com.iwomi.hub.repo;

import com.iwomi.hub.domain.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
    List<Opportunity> findByActiveTrueOrderByIdAsc();
    List<Opportunity> findAllByOrderByIdAsc();
}
