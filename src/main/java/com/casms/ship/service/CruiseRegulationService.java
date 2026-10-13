
package com.casms.ship.service;

import com.casms.ship.dto.request.CruiseRegulationRequest;
import com.casms.ship.dto.response.CruiseRegulationResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseRegulation;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.repository.CruiseRegulationRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.OperationalRegulationRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class CruiseRegulationService {

    private final CruiseRegulationRepository cruiseRegulationRepository;
    private final CruiseRepository cruiseRepository;
    private final OperationalRegulationRepository operationalRegulationRepository;

    /**
     * Gán quy định có scope CRUISE cho chuyến đi.
     * Quy định GLOBAL tự động áp dụng cho mọi chuyến đi.
     */
    public CruiseRegulationResponse assign(CruiseRegulationRequest request) {
        validateRequest(request);

        UUID cruiseId = request.getCruiseId();
        UUID regulationId = request.getRegulationId();

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cruise not found: " + cruiseId
                ));

        OperationalRegulation regulation =
                operationalRegulationRepository.findById(regulationId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Operational regulation not found: " + regulationId
                        ));

        validateApplicableRegulation(regulation);

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "GLOBAL regulations apply automatically to all cruises "
                            + "and must not be assigned individually."
            );
        }

        checkRegulationConflict(cruiseId, regulationId);

        CruiseRegulation relation = new CruiseRegulation();
        relation.setCruise(cruise);
        relation.setRegulation(regulation);

        CruiseRegulation saved = cruiseRegulationRepository.save(relation);

        return toResponse(saved);
    }

    /**
     * Lấy quy định GLOBAL và quy định CRUISE được gán cho chuyến đi.
     */
    @Transactional(readOnly = true)
    public List<CruiseRegulationResponse> list(UUID cruiseId) {
        if (cruiseId == null) {
            throw new IllegalArgumentException(
                    "Cruise ID must not be null."
            );
        }

        if (!cruiseRepository.existsById(cruiseId)) {
            throw new EntityNotFoundException(
                    "Cruise not found: " + cruiseId
            );
        }

        List<CruiseRegulationResponse> globalRegulations =
                operationalRegulationRepository
                        .findByScope(RegulationScope.GLOBAL)
                        .stream()
                        .map(regulation -> toResponse(
                                cruiseId,
                                regulation.getRegulationId()
                        ))
                        .toList();

        List<CruiseRegulationResponse> cruiseRegulations =
                cruiseRegulationRepository.findByCruise_CruiseId(cruiseId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return Stream.concat(
                globalRegulations.stream(),
                cruiseRegulations.stream()
        ).toList();
    }

    /**
     * Xóa liên kết giữa chuyến đi và quy định.
     * Không xóa chuyến đi hoặc quy định gốc.
     */
    public void remove(UUID cruiseId, UUID regulationId) {
        if (cruiseId == null || regulationId == null) {
            throw new IllegalArgumentException(
                    "Cruise ID and Regulation ID must not be null."
            );
        }

        CruiseRegulation.CruiseRegulationId id =
                new CruiseRegulation.CruiseRegulationId();

        id.setCruise(cruiseId);
        id.setRegulation(regulationId);

        CruiseRegulation relation = cruiseRegulationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Cruise regulation assignment not found."
                ));

        cruiseRegulationRepository.delete(relation);
    }

    /**
     * Kiểm tra scope của quy định có hợp lệ với chuyến đi hay không.
     */
    @Transactional(readOnly = true)
    public void validateApplicableRegulation(
            OperationalRegulation regulation
    ) {
        if (regulation == null) {
            throw new IllegalArgumentException(
                    "Operational regulation must not be null."
            );
        }

        RegulationScope scope = regulation.getScope();

        if (scope != RegulationScope.CRUISE
                && scope != RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "Regulation scope is not applicable to cruises."
            );
        }
    }

    /**
     * Kiểm tra quy định đã được gán cho chuyến đi hay chưa.
     */
    @Transactional(readOnly = true)
    public void checkRegulationConflict(
            UUID cruiseId,
            UUID regulationId
    ) {
        if (cruiseId == null || regulationId == null) {
            throw new IllegalArgumentException(
                    "Cruise ID and Regulation ID must not be null."
            );
        }

        boolean alreadyAssigned =
                cruiseRegulationRepository
                        .existsByCruise_CruiseIdAndRegulation_RegulationId(
                                cruiseId,
                                regulationId
                        );

        if (alreadyAssigned) {
            throw new IllegalStateException(
                    "This regulation is already assigned to the cruise."
            );
        }
    }

    private void validateRequest(CruiseRegulationRequest request) {
        if (request == null
                || request.getCruiseId() == null
                || request.getRegulationId() == null) {
            throw new IllegalArgumentException(
                    "Cruise ID and Regulation ID must not be null."
            );
        }
    }

    /**
     * Chuyển entity sang response DTO.
     */
    private CruiseRegulationResponse toResponse(CruiseRegulation entity) {
        return CruiseRegulationResponse.builder()
                .cruiseId(entity.getCruise().getCruiseId())
                .regulationId(entity.getRegulation().getRegulationId())
                .build();
    }

    /**
     * Tạo response DTO cho quy định GLOBAL.
     */
    private CruiseRegulationResponse toResponse(
            UUID cruiseId,
            UUID regulationId
    ) {
        return CruiseRegulationResponse.builder()
                .cruiseId(cruiseId)
                .regulationId(regulationId)
                .build();
    }
}
