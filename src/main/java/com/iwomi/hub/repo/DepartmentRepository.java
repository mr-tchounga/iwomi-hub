package com.iwomi.hub.repo;

import com.iwomi.hub.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
