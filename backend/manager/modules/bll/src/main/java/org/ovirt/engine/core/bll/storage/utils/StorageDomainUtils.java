package org.ovirt.engine.core.bll.storage.utils;

import org.ovirt.engine.core.common.businessentities.StorageDomain;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.StorageDomainDao;

/**
 * Shared helpers for storage domain checks used across commands.
 */
public final class StorageDomainUtils {

    private StorageDomainUtils() {
    }

    /**
     * True if the domain exists in the database and is managed block storage.
     */
    public static boolean isManagedBlockStorage(StorageDomainDao storageDomainDao, Guid storageDomainId) {
        if (storageDomainId == null) {
            return false;
        }
        StorageDomain sd = storageDomainDao.get(storageDomainId);
        return sd != null && sd.isManagedBlockStorage();
    }
}
