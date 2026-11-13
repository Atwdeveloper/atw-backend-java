package in.anytimework.atwbackend.worker.application;

import in.anytimework.atwbackend.masterdata.application.lookup.GeographyLookupService;
import in.anytimework.atwbackend.shared.exception.ResourceNotFoundException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.worker.api.admin.dto.UpdateWorkerAddressRequest;
import in.anytimework.atwbackend.worker.api.admin.dto.WorkerAddressResponse;
import in.anytimework.atwbackend.worker.domain.AddressType;
import in.anytimework.atwbackend.worker.domain.WorkerAddress;
import in.anytimework.atwbackend.worker.domain.WorkerAddressRepository;
import in.anytimework.atwbackend.worker.domain.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** A worker's permanent/current address - ordinary demographic data, no masking needed (unlike bank/KYC). */
@Service
public class WorkerAddressService {

    private final WorkerRepository workers;
    private final WorkerAddressRepository addresses;
    private final GeographyLookupService geography;

    public WorkerAddressService(WorkerRepository workers, WorkerAddressRepository addresses, GeographyLookupService geography) {
        this.workers = workers;
        this.addresses = addresses;
        this.geography = geography;
    }

    @Transactional(readOnly = true)
    public WorkerAddressResponse get(UUID workerId, AddressType addressType) {
        requireWorkerExists(workerId);
        return addresses.findByWorkerIdAndAddressType(workerId, addressType).map(this::toDto)
                .orElse(new WorkerAddressResponse(null, null, null, null, null, null, null, null));
    }

    @Transactional
    public WorkerAddressResponse update(UUID workerId, AddressType addressType, UpdateWorkerAddressRequest request) {
        requireWorkerExists(workerId);
        if (request.stateId() != null && !geography.stateExists(request.stateId())) {
            throw new ValidationException("Unknown state.");
        }
        if (request.cityId() != null && !geography.cityExists(request.cityId())) {
            throw new ValidationException("Unknown city.");
        }
        WorkerAddress address = addresses.findByWorkerIdAndAddressType(workerId, addressType)
                .orElseGet(() -> new WorkerAddress(workerId, addressType));
        address.update(request.line1(), request.line2(), request.stateId(), request.cityId(), request.pincode());
        address = addresses.save(address);
        return toDto(address);
    }

    private void requireWorkerExists(UUID workerId) {
        if (!workers.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found");
        }
    }

    private WorkerAddressResponse toDto(WorkerAddress entity) {
        var address = entity.getAddress();
        return new WorkerAddressResponse(address.getLine1(), address.getLine2(), address.getStateId(),
                address.getCityId(), address.getPincode(), address.getLatitude(), address.getLongitude(),
                address.getFormattedAddress());
    }
}
