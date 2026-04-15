package org.ovirt.engine.core.bll;

import java.util.ArrayList;
import java.util.Arrays;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import org.ovirt.engine.core.bll.context.CommandContext;
import org.ovirt.engine.core.bll.snapshots.CreateSnapshotFromTemplateCommand;
import org.ovirt.engine.core.bll.storage.domain.PostDeleteActionHandler;
import org.ovirt.engine.core.bll.storage.utils.StorageDomainUtils;
import org.ovirt.engine.core.bll.storage.utils.VdsCommandsHelper;
import org.ovirt.engine.core.bll.tasks.interfaces.CommandCallback;
import org.ovirt.engine.core.common.FeatureSupported;
import org.ovirt.engine.core.common.VdcObjectType;
import org.ovirt.engine.core.common.action.ActionParametersBase;
import org.ovirt.engine.core.common.action.ActionType;
import org.ovirt.engine.core.common.action.CopyImageGroupWithDataCommandParameters;
import org.ovirt.engine.core.common.action.CreateCloneOfTemplateParameters;
import org.ovirt.engine.core.common.asynctasks.AsyncTaskType;
import org.ovirt.engine.core.common.businessentities.storage.CopyVolumeType;
import org.ovirt.engine.core.common.businessentities.storage.DiskImage;
import org.ovirt.engine.core.common.businessentities.storage.VolumeType;
import org.ovirt.engine.core.common.errors.EngineException;
import org.ovirt.engine.core.common.vdscommands.CopyImageVDSCommandParameters;
import org.ovirt.engine.core.common.vdscommands.VDSCommandType;
import org.ovirt.engine.core.common.vdscommands.VDSReturnValue;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.StorageDomainDao;

/**
 * This command responsible to creating a copy of template image. Usually it
 * will be called during Add Vm From Template.
 */
@InternalCommandAttribute
public class CreateCloneOfTemplateCommand<T extends CreateCloneOfTemplateParameters> extends
        CreateSnapshotFromTemplateCommand<T> {

    @Inject
    private PostDeleteActionHandler postDeleteActionHandler;
    @Inject
    private StorageDomainDao storageDomainDao;
    @Inject
    private VdsCommandsHelper vdsCommandsHelper;
    @Inject
    @Typed(ConcurrentChildCommandsExecutionCallback.class)
    private Instance<ConcurrentChildCommandsExecutionCallback> callbackProvider;

    public CreateCloneOfTemplateCommand(T parameters, CommandContext cmdContext) {
        super(parameters, cmdContext);
    }

    @Override
    protected DiskImage cloneDiskImage(Guid newImageGuid) {
        DiskImage returnValue = super.cloneDiskImage(newImageGuid);
        returnValue.setStorageIds(new ArrayList<>(Arrays.asList(getDestinationStorageDomainId())));
        returnValue.setQuotaId(getParameters().getQuotaId());
        returnValue.setDiskProfileId(getParameters().getDiskProfileId());
        // override to have no template
        returnValue.setParentId(VmTemplateHandler.BLANK_VM_TEMPLATE_ID);
        returnValue.setImageTemplateId(VmTemplateHandler.BLANK_VM_TEMPLATE_ID);
        if (getParameters().getDiskImageBase() != null) {
            returnValue.setVolumeType(getParameters().getDiskImageBase().getVolumeType());
            returnValue.setVolumeFormat(getParameters().getDiskImageBase().getVolumeFormat());
        }
        return returnValue;
    }

    @Override
    protected boolean performImageVdsmOperation() {
        setDestinationImageId(Guid.newGuid());
        persistCommandIfNeeded();
        newDiskImage = cloneDiskImage(getDestinationImageId());
        newDiskImage.setId(Guid.newGuid());
        getParameters().setImageGroupID(newDiskImage.getId());
        Guid storagePoolID = newDiskImage.getStoragePoolId() != null ? newDiskImage
                .getStoragePoolId() : Guid.Empty;
        getParameters().setStoragePoolId(storagePoolID);

        if (isDataOperationsByHSM()) {
            Guid destStorageDomainId = getDestinationStorageDomainId();
            boolean destIsMbs = StorageDomainUtils.isManagedBlockStorage(storageDomainDao, destStorageDomainId);
            // #region agent log — NDJSON under $HOME/.cursor/ (e.g. /home/almalinux/.cursor/ on your VM)
            java.io.File agentLogFile =
                    new java.io.File(System.getProperty("user.home"), ".cursor/debug-3b7973.log");
            java.io.File agentLogDir = agentLogFile.getParentFile();
            if (agentLogDir != null && !agentLogDir.isDirectory()) {
                agentLogDir.mkdirs();
            }
            try (java.io.FileWriter fw = new java.io.FileWriter(agentLogFile, true)) {
                fw.write(String.format(
                        "{\"sessionId\":\"3b7973\",\"hypothesisId\":\"H1\",\"location\":\"CreateCloneOfTemplateCommand.performImageVdsmOperation\",\"message\":\"hsmCloneBranch\",\"data\":{\"isDataOperationsByHSM\":true,\"destStorageDomainId\":\"%s\",\"destIsMbs\":%s},\"timestamp\":%d}%n",
                        destStorageDomainId,
                        destIsMbs,
                        System.currentTimeMillis()));
            } catch (Exception e) {
                log.debug("debug log write failed", e);
            }
            // #endregion
            CopyImageGroupWithDataCommandParameters p = new CopyImageGroupWithDataCommandParameters(
                    storagePoolID,
                    getParameters().getStorageDomainId(),
                    destStorageDomainId,
                    getDiskImage().getId(),
                    getImage().getImageId(),
                    newDiskImage.getId(),
                    getDestinationImageId(),
                    newDiskImage.getVolumeFormat(),
                    newDiskImage.getVolumeType(),
                    true);

            p.setParentParameters(getParameters());
            p.setParentCommand(getActionType());
            p.setEndProcedure(ActionParametersBase.EndProcedure.COMMAND_MANAGED);
            // VDSM volume ops (CopyImageGroupWithData) do not apply to MBS; use the same path as
            // CopyImageGroupCommand.performStorageOperation for NFS/file block -> MBS.
            if (destIsMbs) {
                Guid vds = vdsCommandsHelper.getHostForExecution(storagePoolID,
                        host -> FeatureSupported.isHostSupportsMBSCopy(host));
                p.setVdsRunningOn(vds);
                p.setDestinationVolumeType(VolumeType.Preallocated);
                runInternalAction(ActionType.CopyManagedBlockDisk, p);
            } else {
                runInternalAction(ActionType.CopyImageGroupWithData, p);
            }
            return true;
        } else {
            Guid taskId = persistAsyncTaskPlaceHolder(ActionType.AddVmFromTemplate);
            VDSReturnValue vdsReturnValue;
            try {
                vdsReturnValue = runVdsCommand(VDSCommandType.CopyImage,
                        postDeleteActionHandler.fixParameters(
                                new CopyImageVDSCommandParameters(storagePoolID, getParameters().getStorageDomainId(),
                                        getVmTemplateId(), getDiskImage().getId(), getImage().getImageId(),
                                        newDiskImage.getId(), getDestinationImageId(),
                                        "", getDestinationStorageDomainId(), CopyVolumeType.LeafVol,
                                        newDiskImage.getVolumeFormat(), newDiskImage.getVolumeType(),
                                        getDiskImage().isWipeAfterDelete(),
                                        storageDomainDao.get(getDestinationStorageDomainId()).getDiscardAfterDelete(),
                                        false)));

            } catch (EngineException e) {
                log.error("Failed creating snapshot from image id '{}'", getImage().getImageId());
                throw e;
            }

            if (vdsReturnValue.getSucceeded()) {
                getTaskIdList().add(
                        createTask(taskId,
                                vdsReturnValue.getCreationInfo(),
                                ActionType.AddVmFromTemplate,
                                VdcObjectType.Storage,
                                getParameters().getStorageDomainId(),
                                getDestinationStorageDomainId()));
            }

            return vdsReturnValue.getSucceeded();
        }
    }

    @Override
    protected AsyncTaskType getTaskType() {
        return isDataOperationsByHSM() ? AsyncTaskType.notSupported : AsyncTaskType.copyImage;
    }

    @Override
    public CommandCallback getCallback() {
        return isDataOperationsByHSM() ? callbackProvider.get() : null;
    }
}
