package com.dispatch.device;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    /** The service is fetched in the same query to avoid N+1 selects when rendering device lists. */
    @Override
    @EntityGraph(attributePaths = "service")
    Page<Device> findAll(Specification<Device> spec, Pageable pageable);

    @EntityGraph(attributePaths = "service")
    Optional<Device> findWithServiceById(Long id);

    @EntityGraph(attributePaths = "service")
    Optional<Device> findByIdentifierIgnoreCase(String identifier);

    /**
     * Loads the device with a row lock for the rest of the transaction. Ingestion uses this so events for the
     * same device are processed one at a time and never overwrite each other's status changes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where upper(d.identifier) = upper(:identifier)")
    Optional<Device> lockByIdentifier(String identifier);

    boolean existsByIdentifierIgnoreCase(String identifier);

    long countByServiceId(Long serviceId);

    long countByServiceIdAndStatus(Long serviceId, DeviceStatus status);

    @EntityGraph(attributePaths = "service")
    List<Device> findAllByOrderByIdentifierAsc();

    @Query("select d.service.id, d.status, count(d) from Device d group by d.service.id, d.status")
    List<Object[]> countByServiceAndStatusRaw();

    @Query("select d.status, count(d) from Device d group by d.status")
    List<Object[]> countByStatusRaw();
}
