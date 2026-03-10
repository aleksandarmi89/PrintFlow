package com.printflow.repository;

import com.printflow.entity.PublicOrderRequest;
import com.printflow.entity.enums.PublicOrderRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublicOrderRequestRepository extends JpaRepository<PublicOrderRequest, Long> {

    Page<PublicOrderRequest> findByCompany_IdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    Page<PublicOrderRequest> findByCompany_IdAndStatusOrderByCreatedAtDesc(Long companyId, PublicOrderRequestStatus status, Pageable pageable);

    @Query("""
        select r from PublicOrderRequest r
        where r.company.id = :companyId
          and (lower(r.customerEmail) like lower(concat('%', :q, '%'))
               or lower(r.customerName) like lower(concat('%', :q, '%')))
        order by r.createdAt desc
        """)
    Page<PublicOrderRequest> searchByCompany(@Param("companyId") Long companyId, @Param("q") String query, Pageable pageable);

    @Query("""
        select r from PublicOrderRequest r
        where r.company.id = :companyId
          and r.status = :status
          and (lower(r.customerEmail) like lower(concat('%', :q, '%'))
               or lower(r.customerName) like lower(concat('%', :q, '%')))
        order by r.createdAt desc
        """)
    Page<PublicOrderRequest> searchByCompanyAndStatus(@Param("companyId") Long companyId,
                                                      @Param("status") PublicOrderRequestStatus status,
                                                      @Param("q") String query,
                                                      Pageable pageable);

    Optional<PublicOrderRequest> findByIdAndCompany_Id(Long id, Long companyId);

    @Query("""
        select r from PublicOrderRequest r
        left join fetch r.convertedOrder
        where r.id = :id and r.company.id = :companyId
        """)
    Optional<PublicOrderRequest> findDetailedByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    List<PublicOrderRequest> findByConvertedOrder_IdIn(List<Long> orderIds);
}
