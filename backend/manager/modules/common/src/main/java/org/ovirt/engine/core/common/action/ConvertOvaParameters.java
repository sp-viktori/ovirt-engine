package org.ovirt.engine.core.common.action;

import java.util.List;
import java.util.Map;

import org.ovirt.engine.core.common.businessentities.VmEntityType;
import org.ovirt.engine.core.compat.Guid;

public class ConvertOvaParameters extends ConvertVmParameters {

    private String ovaPath;
    private VmEntityType vmEntityType = VmEntityType.VM;
    private Map<Guid, Map<String, Object>> preAttachedManagedBlockDevicesByDiskId;
    private List<Guid> templateDiskIdsForExtract;
    private Map<Guid, String> ovaTarNameByDiskId;

    public ConvertOvaParameters() {
    }

    public ConvertOvaParameters(Guid vmId) {
        super(vmId);
    }

    public String getOvaPath() {
        return ovaPath;
    }

    public void setOvaPath(String ovaPath) {
        this.ovaPath = ovaPath;
    }

    public VmEntityType getVmEntityType() {
        return vmEntityType;
    }

    public void setVmEntityType(VmEntityType vmEntityType) {
        this.vmEntityType = vmEntityType;
    }

    public Map<Guid, Map<String, Object>> getPreAttachedManagedBlockDevicesByDiskId() {
        return preAttachedManagedBlockDevicesByDiskId;
    }

    public void setPreAttachedManagedBlockDevicesByDiskId(
            Map<Guid, Map<String, Object>> preAttachedManagedBlockDevicesByDiskId) {
        this.preAttachedManagedBlockDevicesByDiskId = preAttachedManagedBlockDevicesByDiskId;
    }

    public List<Guid> getTemplateDiskIdsForExtract() {
        return templateDiskIdsForExtract;
    }

    public void setTemplateDiskIdsForExtract(List<Guid> templateDiskIdsForExtract) {
        this.templateDiskIdsForExtract = templateDiskIdsForExtract;
    }

    /**
     * OVA tar member name per disk id. Keyed rather than positional: the disk
     * order the extract works from is not the order the import command built its
     * mapping in, so pairing them by index silently mismatches disks.
     */
    public Map<Guid, String> getOvaTarNameByDiskId() {
        return ovaTarNameByDiskId;
    }

    public void setOvaTarNameByDiskId(Map<Guid, String> ovaTarNameByDiskId) {
        this.ovaTarNameByDiskId = ovaTarNameByDiskId;
    }
}
