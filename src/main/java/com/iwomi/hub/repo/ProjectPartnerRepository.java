package com.iwomi.hub.repo;

import com.iwomi.hub.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProjectPartnerRepository extends JpaRepository<ProjectPartner, Long> {
    List<ProjectPartner> findByProjectId(Long projectId);
    List<ProjectPartner> findByPartnerId(Long partnerId);
}
