package com.casms.ship.repository;
import com.casms.ship.entity.PortCallWindow;
import java.util.*;
public interface PortCallWindowRepository extends org.springframework.data.jpa.repository.JpaRepository<PortCallWindow,UUID> {
    List<PortCallWindow> findByPortPortIdOrderByValidFromAsc(UUID portId);
}
