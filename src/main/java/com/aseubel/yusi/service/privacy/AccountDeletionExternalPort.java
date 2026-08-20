package com.aseubel.yusi.service.privacy;

/** External data-plane boundary for account deletion. */
public interface AccountDeletionExternalPort {

    void deleteMilvus(AccountDeletionInventory inventory);

    void deleteRedis(AccountDeletionInventory inventory);

    void deleteObjects(AccountDeletionInventory inventory);
}
