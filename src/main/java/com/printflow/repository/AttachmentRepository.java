package com.printflow.repository;

import com.printflow.entity.Attachment;
import com.printflow.entity.WorkOrder;
import com.printflow.entity.enums.AttachmentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // ==================== WORK ORDER ATTACHMENTS ====================
    List<Attachment> findByWorkOrderIdAndActiveTrueOrderByUploadedAtDesc(Long workOrderId);
    List<Attachment> findByWorkOrderIdAndActiveTrue(Long workOrderId);
    Page<Attachment> findByWorkOrderIdAndActiveTrue(Long workOrderId, Pageable pageable);

    // ==================== TASK ATTACHMENTS (Ovo sada radi!) ====================
    List<Attachment> findByTaskIdAndActiveTrue(Long taskId);
    List<Attachment> findByTaskIdAndActiveTrueOrderByUploadedAtDesc(Long taskId);
    Page<Attachment> findByTaskIdAndActiveTrue(Long taskId, Pageable pageable);

    // ==================== USER ATTACHMENTS ====================
    List<Attachment> findByUploadedByIdAndActiveTrue(Long userId);
    
    // ISPRAVLJENO: a.uploadedBy.id umesto uploadedById
    @Query("SELECT COUNT(a) FROM Attachment a WHERE a.active = true AND a.uploadedBy.id = :userId")
    long countByUploadedByIdAndActiveTrue(@Param("userId") Long userId);

    // ==================== STATISTICS ====================
    @Query("SELECT SUM(a.fileSize) FROM Attachment a WHERE a.active = true AND a.workOrder.id = :workOrderId")
    Long sumFileSizeByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Query("SELECT SUM(a.fileSize) FROM Attachment a WHERE a.active = true AND a.task.id = :taskId")
    Long sumFileSizeByTaskId(@Param("taskId") Long taskId);

    // ==================== BULK OPERATIONS ====================
    @Modifying
    @Transactional
    @Query("UPDATE Attachment a SET a.active = false WHERE a.workOrder.id = :workOrderId")
    int deactivateByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Modifying
    @Transactional
    @Query("UPDATE Attachment a SET a.active = false WHERE a.task.id = :taskId")
    int deactivateByTaskId(@Param("taskId") Long taskId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attachment a WHERE a.workOrder.id = :workOrderId")
    void deleteByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attachment a WHERE a.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);

    Optional<Attachment> findByIdAndActiveTrue(Long id);
	List<Attachment> findByWorkOrder(WorkOrder workOrder);
	List<Attachment> findByWorkOrderIdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(Long workOrderId,
			AttachmentType type);
}