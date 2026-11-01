package com.casms.ship.repository;
import com.casms.ship.entity.PortCallWindowException;
import java.util.*;
public interface PortCallWindowExceptionRepository extends org.springframework.data.jpa.repository.JpaRepository<PortCallWindowException,UUID> {
    List<PortCallWindowException> findByPortPortIdOrderByValidFromAsc(UUID portId);
}
