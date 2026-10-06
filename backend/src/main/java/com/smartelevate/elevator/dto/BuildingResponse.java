package com.smartelevate.elevator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Edificio con su rango de pisos y sus núcleos")
public record BuildingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "LIMA") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Lima") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "-4") int minFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "10") int maxFloor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Núcleos del edificio, por código")
        List<CoreResponse> cores) {

    public record CoreResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "L3") String code) {
    }
}
