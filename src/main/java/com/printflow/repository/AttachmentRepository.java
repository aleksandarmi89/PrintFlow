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
    List<Attachment> findByCommentIdAndActiveTrueOrderByUploadedAtDesc(Long commentId);
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

    @Query("SELECT SUM(a.fileSize) FROM Attachment a WHERE a.active = true AND a.company.id = :companyId")
    Long sumFileSizeByCompanyId(@Param("companyId") Long companyId);

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
    Optional<Attachment> findByIdAndCompany_Id(Long id, Long companyId);
    Optional<Attachment> findByIdAndCompany_IdAndActiveTrue(Long id, Long companyId);
    Optional<Attachment> findByIdAndComment_IdAndCompany_Id(Long id, Long commentId, Long companyId);
    List<Attachment> findByIdInAndComment_IdAndCompany_Id(List<Long> ids, Long commentId, Long companyId);
    Optional<Attachment> findByApprovalTokenAndWorkOrder_Client_IdAndActiveTrue(String approvalToken, Long clientId);

    @Query("SELECT a FROM Attachment a WHERE a.workOrder.id IN :workOrderIds AND a.active = true")
    List<Attachment> findByWorkOrderIdInAndActiveTrue(@Param("workOrderIds") List<Long> workOrderIds);
    Optional<Attachment> findByIdAndWorkOrder_PublicTokenAndAttachmentTypeAndActiveTrue(Long id, String publicToken, AttachmentType attachmentType);
    Optional<Attachment> findByIdAndWorkOrder_PublicTokenAndWorkOrder_PublicTokenExpiresAtAfterAndAttachmentTypeAndActiveTrue(
        Long id,
        String publicToken,
        java.time.LocalDateTime now,
        AttachmentType attachmentType
    );
	List<Attachment> findByWorkOrder(WorkOrder workOrder);
	List<Attachment> findByWorkOrderIdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(Long workOrderId,
			AttachmentType type);
    List<Attachment> findByWorkOrderIdAndCompany_IdAndAttachmentTypeAndActiveTrueOrderByUploadedAtDesc(Long workOrderId,
                                                                                                       Long companyId,
                                                                                                       AttachmentType type);
    List<Attachment> findByWorkOrderIdAndCompany_IdAndActiveTrueOrderByUploadedAtDesc(Long workOrderId, Long companyId);
    List<Attachment> findByWorkOrderIdAndCompany_IdAndActiveTrue(Long workOrderId, Long companyId);
    List<Attachment> findByTaskIdAndCompany_IdAndActiveTrueOrderByUploadedAtDesc(Long taskId, Long companyId);
    List<Attachment> findByCommentIdAndCompany_IdAndActiveTrueOrderByUploadedAtDesc(Long commentId, Long companyId);
    long countByWorkOrderIdAndAttachmentTypeAndActiveTrue(Long workOrderId, AttachmentType type);
}
