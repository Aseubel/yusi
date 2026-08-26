package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    Optional<AccountDeletionRequest> findByRequestId(String requestId);

    List<AccountDeletionRequest> findByTargetUserRefAndStatusIn(
            String targetUserRef, Collection<AccountDeletionRequest.Status> statuses);
}
