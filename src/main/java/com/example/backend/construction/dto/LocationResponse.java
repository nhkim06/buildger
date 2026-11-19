package com.example.backend.construction.dto;

import com.example.backend.construction.entity.Location;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시공 위치")
public record LocationResponse(
        @Schema(example = "A동") String site,
        @Schema(example = "본관") String building,
        @Schema(example = "3F") String floor,
        @Schema(example = "X1-X3 / Y1-Y4") String zone,
        String address,
        @Schema(example = "37.501274") Double latitude,
        @Schema(example = "127.039585") Double longitude
) {

    public static LocationResponse from(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationResponse(location.getSite(), location.getBuilding(), location.getFloor(),
                location.getZone(), location.getAddress(), location.getLatitude(), location.getLongitude());
    }
}
