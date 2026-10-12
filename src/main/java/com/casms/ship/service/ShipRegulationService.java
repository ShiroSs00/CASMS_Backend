
package com.casms.ship.service;

import com.casms.ship.dto.request.ShipRegulationRequest;
import com.casms.ship.dto.response.ShipRegulationResponse;
import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.repository.OperationalRegulationRepository;
import com.casms.ship.repository.ShipRegulationRepository;
import com.casms.ship.repository.ShipRepository;

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
public class ShipRegulationService {

    private final ShipRegulationRepository shipRegulationRepository;
    private final ShipRepository shipRepository;
    private final OperationalRegulationRepository operationalRegulationRepository;

    /**
     * Gán quy định có scope SHIP cho tàu.
     * Quy định GLOBAL tự động áp dụng cho mọi tàu.
     */
    public ShipRegulationResponse assign(ShipRegulationRequest request) {
        validateRequest(request);

        UUID shipId = request.getShipId();
        UUID regulationId = request.getRegulationId();

        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ship not found: " + shipId
                ));

        OperationalRegulation regulation =
                operationalRegulationRepository.findById(regulationId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Operational regulation not found: " + regulationId
                        ));

        validateApplicableRegulation(regulation);

        if (regulation.getScope() == RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "GLOBAL regulations apply automatically to all ships "
                            + "and must not be assigned individually."
            );
        }

        checkRegulationConflict(shipId, regulationId);

        ShipRegulation relation = new ShipRegulation();
        relation.setShip(ship);
        relation.setRegulation(regulation);

        ShipRegulation saved = shipRegulationRepository.save(relation);

        return toResponse(saved);
    }

    /**
     * Lấy các quy định GLOBAL và quy định SHIP được gán cho tàu.
     */
    @Transactional(readOnly = true)
    public List<ShipRegulationResponse> list(UUID shipId) {
        if (shipId == null) {
            throw new IllegalArgumentException(
                    "Ship ID must not be null."
            );
        }

        if (!shipRepository.existsById(shipId)) {
            throw new EntityNotFoundException(
                    "Ship not found: " + shipId
            );
        }

        List<ShipRegulationResponse> globalRegulations =
                operationalRegulationRepository
                        .findByScope(RegulationScope.GLOBAL)
                        .stream()
                        .map(regulation -> toResponse(
                                shipId,
                                regulation.getRegulationId()
                        ))
                        .toList();

        List<ShipRegulationResponse> shipRegulations =
                shipRegulationRepository.findByShip_ShipId(shipId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return Stream.concat(
                globalRegulations.stream(),
                shipRegulations.stream()
        ).toList();
    }

    /**
     * Xóa liên kết giữa tàu và quy định.
     * Không xóa tàu hoặc quy định gốc.
     */
    public void remove(UUID shipId, UUID regulationId) {
        if (shipId == null || regulationId == null) {
            throw new IllegalArgumentException(
                    "Ship ID and Regulation ID must not be null."
            );
        }

        ShipRegulation.ShipRegulationId id =
                new ShipRegulation.ShipRegulationId();

        id.setShip(shipId);
        id.setRegulation(regulationId);

        ShipRegulation relation = shipRegulationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Ship regulation assignment not found."
                ));

        shipRegulationRepository.delete(relation);
    }

    /**
     * Kiểm tra scope của quy định có hợp lệ với tàu hay không.
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

        if (scope != RegulationScope.SHIP
                && scope != RegulationScope.GLOBAL) {
            throw new IllegalArgumentException(
                    "Regulation scope is not applicable to ships."
            );
        }
    }

    /**
     * Kiểm tra quy định đã được gán cho tàu hay chưa.
     */
    @Transactional(readOnly = true)
    public void checkRegulationConflict(
            UUID shipId,
            UUID regulationId
    ) {
        if (shipId == null || regulationId == null) {
            throw new IllegalArgumentException(
                    "Ship ID and Regulation ID must not be null."
            );
        }

        boolean alreadyAssigned =
                shipRegulationRepository
                        .existsByShip_ShipIdAndRegulation_RegulationId(
                                shipId,
                                regulationId
                        );

        if (alreadyAssigned) {
            throw new IllegalStateException(
                    "This regulation is already assigned to the ship."
            );
        }
    }

    private void validateRequest(ShipRegulationRequest request) {
        if (request == null
                || request.getShipId() == null
                || request.getRegulationId() == null) {
            throw new IllegalArgumentException(
                    "Ship ID and Regulation ID must not be null."
            );
        }
    }

    /**
     * Chuyển entity sang response DTO.
     */
    private ShipRegulationResponse toResponse(ShipRegulation entity) {
        return ShipRegulationResponse.builder()
                .shipId(entity.getShip().getShipId())
                .regulationId(entity.getRegulation().getRegulationId())
                .build();
    }

    /**
     * Tạo response DTO cho quy định GLOBAL.
     */
    private ShipRegulationResponse toResponse(
            UUID shipId,
            UUID regulationId
    ) {
        return ShipRegulationResponse.builder()
                .shipId(shipId)
                .regulationId(regulationId)
                .build();
    }
}
