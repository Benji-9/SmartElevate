package com.smartelevate.elevator.dto;

import com.smartelevate.elevator.model.ElevatorUsage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ascensor con los pisos donde para")
public record ElevatorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "33") String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "IND2") String core,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Independencia 2") String coreName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "INDEPENDENCIA") String building,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "COMMON, o dedicado a docentes (TEACHERS) o a movilidad reducida (REDUCED_MOBILITY)")
        ElevatorUsage usage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Pisos servidos, de menor a mayor (0 = PB)",
                example = "[-4, -3, -2, -1, 0, 2, 3]")
        List<Integer> floors) {
}
